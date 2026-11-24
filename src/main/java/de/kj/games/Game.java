package de.kj.games;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import de.kj.games.Nation.ShipType;

final class Game {
    static final int SIZE = 10;
    private static final int CELLS = SIZE * SIZE;
    private static final long COMPUTER_DELAY_NANOS = TimeUnit.MILLISECONDS.toNanos(1500);
    private static final long PLACEMENT_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static final long SHOT_NANOS = TimeUnit.SECONDS.toNanos(12);
    private final Difficulty difficulty;
    private final Player host;
    private final Player guest;
    private final boolean multiplayer;
    private ComputerTargeting computer;
    private boolean started;
    private boolean finished;
    private Seat turn = Seat.HOST;
    private Seat winner;
    private Shot lastShot;
    private final List<ReplayHit> replayHits = new ArrayList<>();
    private long shotNumber;
    private long turnNumber;
    private long deadline;
    private long battleStartedAt;

    Game(Difficulty difficulty) {
        this(difficulty, "", null, 0);
    }

    Game(Difficulty difficulty, String hostName, String guestName, long now) {
        this.difficulty = difficulty;
        this.multiplayer = guestName != null;
        this.host = new Player(hostName, difficulty, Nation.DEUTSCHLAND);
        this.guest = new Player(guestName, difficulty, multiplayer ? Nation.DEUTSCHLAND : Nation.random());
        this.deadline = now + PLACEMENT_NANOS;
    }

    boolean multiplayer() {
        return multiplayer;
    }

    private Player player(Seat seat) {
        return seat == Seat.HOST ? host : guest;
    }

    boolean selectNation(Seat seat, Nation nation) {
        Player player = player(seat);
        if (started || player.ready) return false;
        if (player.nation != nation) {
            player.nation = nation;
            player.board = new Board(nation.fleet);
        }
        return true;
    }

    boolean placeShip(Seat seat, int ship, int cell, Direction direction) {
        Player player = player(seat);
        return !started && !player.ready && player.board.place(ship, cell, direction);
    }

    boolean removeShip(Seat seat, int ship) {
        Player player = player(seat);
        return !started && !player.ready && player.board.remove(ship);
    }

    boolean randomizePlacement(Seat seat, boolean replaceAll) {
        Player player = player(seat);
        if (started || player.ready) return false;
        Board board = replaceAll ? new Board(player.nation.fleet) : player.board;
        if (!board.placeRandomFleet(0)) return false;
        player.board = board;
        return true;
    }

    boolean start(Seat seat, long now) {
        if (started) return true;
        Player player = player(seat);
        if (player.board.placedCount() != player.nation.fleet.size()) return false;
        player.ready = true;
        if (multiplayer) {
            if (host.ready && guest.ready) beginBattle(now);
        } else {
            if (!guest.board.placeRandomFleet(0)) throw new IllegalStateException("Cannot place computer fleet");
            computer = new ComputerTargeting(host.nation.fleet.stream().map(ShipType::length).toList(), difficulty);
            beginBattle(now);
        }
        return true;
    }

    private void beginBattle(long now) {
        started = true;
        battleStartedAt = now;
        turnNumber = 1;
        deadline = now + SHOT_NANOS;
        boolean hostEmpty = host.board.placedCount() == 0;
        boolean guestEmpty = guest.board.placedCount() == 0;
        if (hostEmpty || guestEmpty) {
            finished = true;
            if (hostEmpty != guestEmpty) winner = hostEmpty ? Seat.GUEST : Seat.HOST;
        }
    }

    void advance(long now) {
        if (!multiplayer || finished) return;
        if (!started && now - deadline >= 0) beginBattle(deadline);
        if (started && !finished && now - deadline >= 0) {
            long skipped = 1 + (now - deadline) / SHOT_NANOS;
            if (skipped % 2 == 1) turn = turn.other();
            turnNumber += skipped;
            deadline += skipped * SHOT_NANOS;
        }
    }

    boolean playerShot(Seat seat, int cell, long expectedTurn, long now) {
        if (!started || finished || turn != seat || cell < 0 || cell >= CELLS
                || (multiplayer && expectedTurn != turnNumber)) return false;
        Shot shot = shoot(seat, cell, now);
        player(seat).waterHistory.record(cell, shot.result() == Result.WATER, ThreadLocalRandom.current());
        return true;
    }

    boolean computerShot(long now) {
        if (multiplayer || !started || finished || turn != Seat.GUEST || computerWaitMillis(now) > 0) return false;
        shootComputer(Seat.GUEST, computer, ThreadLocalRandom.current(), now);
        return true;
    }

    private void shootComputer(Seat shooter, ComputerTargeting targeting, RandomGenerator random, long now) {
        Board target = player(shooter.other()).board;
        int cell = targeting.choose(random, candidate -> target.ships[candidate] == null);
        Shot shot = shoot(shooter, cell, now);
        boolean hit = shot.result() != Result.WATER;
        ShipPlacement sunkPlacement = shot.result() == Result.SUNK ? target.ships[cell].placement : null;
        targeting.observe(cell, hit, sunkPlacement, random);
    }

    static Seat playComputerDuel(Difficulty hostLevel, Difficulty guestLevel, List<ShipPlacement> fleet,
                                 RandomGenerator random, int shotLimit) {
        Game game = new Game(hostLevel, hostLevel.label, guestLevel.label, 0);
        for (Seat seat : Seat.values()) {
            Board board = game.player(seat).board;
            if (fleet.size() != board.fleet.size()) throw new IllegalArgumentException("Incomplete fleet");
            for (int index = 0; index < fleet.size(); index++) {
                ShipPlacement placement = fleet.get(index);
                if (placement.length() != board.fleet.get(index).length()
                        || !game.placeShip(seat, index, placement.start(), placement.direction())) {
                    throw new IllegalArgumentException("Invalid placement for ship " + index);
                }
            }
            game.start(seat, 0);
        }
        List<Integer> lengths = fleet.stream().map(ShipPlacement::length).toList();
        ComputerTargeting hostTargeting = new ComputerTargeting(lengths, hostLevel);
        ComputerTargeting guestTargeting = new ComputerTargeting(lengths, guestLevel);
        for (int shot = 0; shot < shotLimit && !game.finished; shot++) {
            if (game.turn == Seat.HOST) game.shootComputer(Seat.HOST, hostTargeting, random, 0);
            else game.shootComputer(Seat.GUEST, guestTargeting, random, 0);
        }
        return game.winner;
    }

    long computerWaitMillis(long now) {
        return !multiplayer && started && !finished && turn == Seat.GUEST ? remainingMillis(now) : 0;
    }

    private long remainingMillis(long now) {
        return Math.max(0, Math.ceilDiv(deadline - now, 1_000_000L));
    }

    private Shot shoot(Seat shooter, int cell, long now) {
        Board target = player(shooter.other()).board;
        Ship ship = target.ships[cell];
        boolean firstShotAtCell = !target.fired[cell];
        target.fired[cell] = true;

        Result result;
        if (ship == null) {
            result = Result.WATER;
            turn = shooter.other();
        } else if (!firstShotAtCell) {
            result = Result.REPEAT;
        } else if (++ship.hits == ship.type.length()) {
            result = Result.SUNK;
            target.sunk.add(ship);
            if (target.sunk.size() == target.placedCount()) {
                winner = shooter;
                finished = true;
            }
        } else {
            result = Result.HIT;
        }
        Player attacker = player(shooter);
        attacker.shotsFired++;
        if (result == Result.HIT || result == Result.SUNK) {
            attacker.hits++;
            replayHits.add(new ReplayHit(shooter.other(), cell, TimeUnit.NANOSECONDS.toMillis(now - battleStartedAt),
                    result == Result.SUNK));
        }
        lastShot = new Shot(++shotNumber, shooter, cell, result, result == Result.SUNK ? ship.type : null);
        turnNumber++;
        deadline = now + (multiplayer ? SHOT_NANOS : COMPUTER_DELAY_NANOS);
        return lastShot;
    }

    String state(String id, Seat seat, long now) {
        Player viewer = player(seat);
        Player opponent = player(seat.other());
        String common = """
                "id":"%s","difficulty":"%s","mode":"%s","playerName":%s,"opponentName":%s
                """.formatted(id, difficulty.label, multiplayer ? "HUMAN" : "COMPUTER",
                Json.quote(viewer.name), Json.quote(multiplayer ? opponent.name : "Computer")).stripTrailing();
        String remaining = multiplayer && !finished ? Long.toString(remainingMillis(now)) : "null";
        if (!started) {
            return """
                    {%s,"phase":"SETUP","nation":"%s","own":"%s","placed":%d,"ready":%s,
                     "remainingMillis":%s,"ships":[%s],"ownFleet":[%s],"nations":[%s]}
                    """.formatted(common, viewer.nation.name(), viewer.board.visibleCells(true, cell -> true),
                    viewer.board.placedCount(), viewer.ready, remaining, viewer.board.setupShips(),
                    viewer.board.placedFleetJson(), Nation.optionsJson());
        }
        IntPredicate showWater = viewer.board::hasShipWithinTwoCells;
        Board opponentBoard = opponent.board;
        boolean revealOpponent = finished && winner == seat.other();
        String sunk = opponentBoard.sunk.stream().map(Ship::placementJson).collect(Collectors.joining(","));
        String revealed = revealOpponent ? opponentBoard.remainingFleetJson() : "";
        int unknown = revealOpponent ? 0 : opponentBoard.placedCount() - opponentBoard.sunk.size();
        String replay = finished ? replayHits.stream().map(hit -> hit.json(seat)).collect(Collectors.joining(",")) : "";
        return """
                {%s,"phase":"PLAY","nation":"%s","own":"%s","target":"%s","turn":"%s","winner":%s,"finished":%s,
                 "remainingMillis":%s,"turnNumber":%d,"shots":%d,"hits":%d,"opponentShots":%d,"opponentHits":%d,
                 "ownFleet":[%s],"sunk":[%s],"revealed":[%s],"winnerCountryCode":%s,"unknown":%d,
                 "computerWaitMillis":%d,"lastShot":%s,"replayHits":[%s]}
                """.formatted(common, viewer.nation.label, viewer.board.visibleCells(true, showWater),
                opponentBoard.visibleCells(revealOpponent, viewer.waterHistory::remembers), relativeSide(turn, seat),
                winner == null ? "null" : Json.quote(relativeSide(winner, seat)), finished,
                remaining, turnNumber, viewer.shotsFired, viewer.hits, opponent.shotsFired, opponent.hits,
                viewer.board.placedFleetJson(), sunk, revealed,
                winner == null ? "null" : Json.quote(player(winner).nation.countryCode), unknown,
                computerWaitMillis(now), lastShot == null ? "null" : lastShot.json(relativeSide(lastShot.shooter(), seat),
                        lastShot.shooter() == seat || lastShot.result() != Result.WATER || showWater.test(lastShot.cell())), replay);
    }

    private String relativeSide(Seat seat, Seat viewer) {
        if (seat == viewer) return "PLAYER";
        return multiplayer ? "OPPONENT" : "COMPUTER";
    }

    enum Seat {
        HOST, GUEST;

        Seat other() {
            return this == HOST ? GUEST : HOST;
        }
    }

    private enum Result { WATER, HIT, REPEAT, SUNK }

    enum Direction {
        HORIZONTAL(1), VERTICAL(SIZE);

        final int step;

        Direction(int step) {
            this.step = step;
        }
    }

    private record Shot(long number, Seat shooter, int cell, Result result, ShipType ship) {
        String json(String relativeShooter, boolean showLocation) {
            return "{\"number\":%d,\"shooter\":\"%s\",\"cell\":%s,\"result\":\"%s\",\"ship\":%s}"
                    .formatted(number, relativeShooter, showLocation ? Integer.toString(cell) : "null",
                            result, ship == null ? "null" : ship.json());
        }
    }

    private record ReplayHit(Seat target, int cell, long timeMillis, boolean sunk) {
        String json(Seat viewer) {
            return "{\"own\":%s,\"cell\":%d,\"timeMillis\":%d,\"sunk\":%s}"
                    .formatted(target == viewer, cell, timeMillis, sunk);
        }
    }

    private static final class Player {
        private final String name;
        private final WaterShotHistory waterHistory;
        private Nation nation;
        private Board board;
        private boolean ready;
        private int shotsFired;
        private int hits;

        private Player(String name, Difficulty difficulty, Nation nation) {
            this.name = name;
            this.nation = nation;
            this.board = new Board(nation.fleet);
            this.waterHistory = new WaterShotHistory(difficulty.playerWaterGuaranteed, difficulty.playerWaterForgetPercent);
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

        private String placementJson() {
            return "{\"type\":%s,\"start\":%d,\"direction\":\"%s\"}"
                    .formatted(type.json(), placement.start(), placement.direction());
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

        private boolean placeRandomFleet(int ship) {
            while (ship < placed.length && placed[ship] != null) ship++;
            if (ship == placed.length) return true;

            int length = fleet.get(ship).length();
            List<ShipPlacement> available = new ArrayList<>();
            for (Direction direction : Direction.values()) {
                IntStream.range(0, CELLS)
                        .mapToObj(cell -> new ShipPlacement(cell, direction, length))
                        .filter(this::canPlace)
                        .forEach(available::add);
            }
            Collections.shuffle(available, ThreadLocalRandom.current());
            for (ShipPlacement placement : available) {
                place(ship, placement.start(), placement.direction());
                if (placeRandomFleet(ship + 1)) return true;
                remove(ship);
            }
            return false;
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

        private String placedFleetJson() {
            return Arrays.stream(placed).filter(ship -> ship != null).map(Ship::placementJson)
                    .collect(Collectors.joining(","));
        }

        private String remainingFleetJson() {
            return Arrays.stream(placed).filter(ship -> ship != null && !sunk.contains(ship))
                    .map(Ship::placementJson).collect(Collectors.joining(","));
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

        private boolean hasShipWithinTwoCells(int cell) {
            int row = cell / SIZE;
            int col = cell % SIZE;
            for (int neighborRow = Math.max(0, row - 2); neighborRow <= Math.min(SIZE - 1, row + 2); neighborRow++) {
                for (int neighborCol = Math.max(0, col - 2); neighborCol <= Math.min(SIZE - 1, col + 2); neighborCol++) {
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
