package de.kj.games;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import de.kj.games.Nation.ShipType;

final class Game {
    static final int SIZE = 10;
    private static final int CELLS = SIZE * SIZE;
    private static final long COMPUTER_DELAY_NANOS = TimeUnit.MILLISECONDS.toNanos(1500);
    private final Difficulty difficulty;
    private final WaterShotHistory playerShots;
    private Board own;
    private Board opponent;
    private ComputerTargeting computer;
    private Nation nation;
    private int newHits;
    private int oldHits;
    private long shotNumber;
    private Side turn = Side.PLAYER;
    private Side winner;
    private Shot lastShot;
    private long nextComputerShotAt;

    Game(Difficulty difficulty) {
        this.difficulty = difficulty;
        this.playerShots = new WaterShotHistory(difficulty.playerWaterShotWindow());
        selectNation(Nation.DEUTSCHLAND);
    }

    boolean selectNation(Nation selection) {
        if (opponent != null) return false;
        if (nation != selection) {
            nation = selection;
            own = new Board(nation.fleet);
        }
        return true;
    }

    boolean placeShip(int ship, int cell, Direction direction) {
        return opponent == null && own.place(ship, cell, direction);
    }

    boolean removeShip(int ship) {
        return opponent == null && own.remove(ship);
    }

    boolean start() {
        if (opponent != null) return true;
        if (own.placedCount() != nation.fleet.size()) return false;
        opponent = new Board(Nation.DEUTSCHLAND.fleet);
        opponent.placeRandomFleet();
        computer = new ComputerTargeting(nation.fleet.stream().map(ShipType::length).toList(), difficulty);
        return true;
    }

    boolean playerShot(int cell) {
        if (opponent == null || winner != null || turn != Side.PLAYER || cell < 0 || cell >= CELLS) return false;
        Shot shot = shoot(Side.PLAYER, opponent, cell);
        playerShots.record(cell, shot.result() == Result.WATER);
        switch (shot.result()) {
            case HIT, SUNK -> newHits++;
            case REPEAT -> oldHits++;
        }
        return true;
    }

    boolean computerShot() {
        if (winner != null || turn != Side.COMPUTER || computerWaitMillis() > 0) return false;
        Shot shot = shoot(Side.COMPUTER, own, computer.choose(ThreadLocalRandom.current()));
        computer.observe(shot.cell(), shot.result() != Result.WATER, shot.ship() == null ? 0 : shot.ship().length());
        return true;
    }

    long computerWaitMillis() {
        if (winner != null || turn != Side.COMPUTER) return 0;
        return Math.max(0, Math.ceilDiv(nextComputerShotAt - System.nanoTime(), 1_000_000L));
    }

    private Shot shoot(Side shooter, Board target, int cell) {
        Ship ship = target.ships[cell];
        boolean firstShotAtCell = !target.fired[cell];
        target.fired[cell] = true;

        Result result;
        if (ship == null) {
            result = Result.WATER;
            turn = shooter == Side.PLAYER ? Side.COMPUTER : Side.PLAYER;
        } else if (!firstShotAtCell) {
            result = Result.REPEAT;
        } else if (++ship.hits == ship.type.length()) {
            result = Result.SUNK;
            target.sunk.add(ship);
            if (target.sunk.size() == target.fleet.size()) winner = shooter;
        } else {
            result = Result.HIT;
        }
        lastShot = new Shot(++shotNumber, shooter, cell, result, result == Result.SUNK ? ship.type : null);
        if (turn == Side.COMPUTER && winner == null) {
            nextComputerShotAt = System.nanoTime() + COMPUTER_DELAY_NANOS;
        }
        return lastShot;
    }

    String state(String id) {
        if (opponent == null) {
            return """
                    {"id":"%s","phase":"SETUP","difficulty":"%s","nation":"%s","own":"%s","placed":%d,
                     "ships":[%s],"nations":[%s]}
                    """.formatted(id, difficulty.label, nation.name(), own.visibleCells(true, cell -> true),
                    own.placedCount(), own.setupShips(), Nation.optionsJson());
        }
        // The opposing board is projected from shots alone. Only sunk ships expose their type.
        String sunk = opponent.sunk.stream().map(ship -> ship.type.json()).collect(Collectors.joining(","));
        return """
                {"id":"%s","phase":"PLAY","difficulty":"%s","nation":"%s","own":"%s","target":"%s","turn":"%s","winner":%s,
                 "shots":%d,"newHits":%d,"oldHits":%d,"sunk":[%s],"unknown":%d,
                 "computerWaitMillis":%d,"lastShot":%s}
                """.formatted(id, difficulty.label, nation.label, own.visibleCells(true, this::showOwnWater),
                opponent.visibleCells(false, playerShots::remembers), turn,
                winner == null ? "null" : "\"" + winner + "\"",
                playerShots.shots(), newHits, oldHits, sunk, opponent.fleet.size() - opponent.sunk.size(),
                computerWaitMillis(),
                lastShot == null ? "null" : lastShot.json(showShotLocation(lastShot)));
    }

    private boolean showOwnWater(int cell) {
        return difficulty == Difficulty.LEICHT || own.adjoinsShip(cell);
    }

    private boolean showShotLocation(Shot shot) {
        return shot.shooter() == Side.PLAYER || shot.result() != Result.WATER || showOwnWater(shot.cell());
    }

    private enum Side { PLAYER, COMPUTER }

    private enum Result { WATER, HIT, REPEAT, SUNK }

    enum Direction {
        HORIZONTAL(1), VERTICAL(SIZE);

        final int step;

        Direction(int step) {
            this.step = step;
        }
    }

    private record Shot(long number, Side shooter, int cell, Result result, ShipType ship) {
        String json(boolean showLocation) {
            return "{\"number\":%d,\"shooter\":\"%s\",\"cell\":%s,\"result\":\"%s\",\"ship\":%s}"
                    .formatted(number, shooter, showLocation ? Integer.toString(cell) : "null",
                            result, ship == null ? "null" : ship.json());
        }
    }

    private static final class Ship {
        private final ShipType type;
        private final ShipPlacement placement;
        private int hits;

        private Ship(ShipType type, ShipPlacement placement) {
            this.type = type;
            this.placement = placement;
        }
    }

    private static final class Board {
        private final Ship[] ships = new Ship[CELLS];
        private final boolean[] fired = new boolean[CELLS];
        private final List<Ship> sunk = new ArrayList<>();
        private final List<ShipType> fleet;
        private final Ship[] placed;

        private Board(List<ShipType> fleet) {
            this.fleet = fleet;
            this.placed = new Ship[fleet.size()];
        }

        private void placeRandomFleet() {
            int ship = 0;
            while (ship < fleet.size()) {
                List<ShipPlacement> available = new ArrayList<>();
                int length = fleet.get(ship).length();
                for (Direction direction : Direction.values()) {
                    IntStream.range(0, CELLS)
                            .mapToObj(cell -> new ShipPlacement(cell, direction, length))
                            .filter(this::canPlace)
                            .forEach(available::add);
                }
                if (available.isEmpty()) {
                    Arrays.fill(ships, null);
                    Arrays.fill(placed, null);
                    ship = 0;
                    continue;
                }
                ShipPlacement placement = available.get(ThreadLocalRandom.current().nextInt(available.size()));
                place(ship++, placement.start(), placement.direction());
            }
        }

        private boolean place(int index, int start, Direction direction) {
            if (index < 0 || index >= placed.length || placed[index] != null) return false;
            ShipType type = fleet.get(index);
            ShipPlacement placement = new ShipPlacement(start, direction, type.length());
            if (!canPlace(placement)) return false;
            Ship ship = new Ship(type, placement);
            for (int i = 0; i < type.length(); i++) ships[placement.cell(i)] = ship;
            placed[index] = ship;
            return true;
        }

        private boolean remove(int index) {
            if (index < 0 || index >= placed.length || placed[index] == null) return false;
            Ship ship = placed[index];
            for (int offset = 0; offset < ship.placement.length(); offset++) {
                ships[ship.placement.cell(offset)] = null;
            }
            placed[index] = null;
            return true;
        }

        private int placedCount() {
            return (int) Arrays.stream(placed).filter(ship -> ship != null).count();
        }

        private String setupShips() {
            return IntStream.range(0, fleet.size()).mapToObj(index -> {
                ShipType type = fleet.get(index);
                boolean isPlaced = placed[index] != null;
                return """
                        {"type":%s,"placed":%s,"horizontal":"%s","vertical":"%s"}
                        """.formatted(type.json(), isPlaced,
                        isPlaced ? "" : validStarts(Direction.HORIZONTAL, type.length()),
                        isPlaced ? "" : validStarts(Direction.VERTICAL, type.length())).stripTrailing();
            }).collect(Collectors.joining(","));
        }

        private String validStarts(Direction direction, int length) {
            StringBuilder starts = new StringBuilder(CELLS);
            for (int cell = 0; cell < CELLS; cell++) {
                starts.append(canPlace(new ShipPlacement(cell, direction, length)) ? '1' : '0');
            }
            return starts.toString();
        }

        private boolean canPlace(ShipPlacement placement) {
            return placement.fits() && Arrays.stream(placed)
                    .allMatch(ship -> ship == null || placement.compatibleWith(ship.placement));
        }

        private boolean adjoinsShip(int cell) {
            int row = cell / SIZE;
            int col = cell % SIZE;
            for (int neighborRow = Math.max(0, row - 1); neighborRow <= Math.min(SIZE - 1, row + 1); neighborRow++) {
                for (int neighborCol = Math.max(0, col - 1); neighborCol <= Math.min(SIZE - 1, col + 1); neighborCol++) {
                    if (ships[neighborRow * SIZE + neighborCol] != null) return true;
                }
            }
            return false;
        }

        private String visibleCells(boolean revealShips, IntPredicate showWater) {
            StringBuilder cells = new StringBuilder(CELLS);
            for (int i = 0; i < CELLS; i++) {
                if (fired[i]) {
                    cells.append(ships[i] != null ? 'h' : showWater.test(i) ? 'm' : '.');
                } else {
                    cells.append(revealShips && ships[i] != null ? 's' : '.');
                }
            }
            return cells.toString();
        }
    }
}
