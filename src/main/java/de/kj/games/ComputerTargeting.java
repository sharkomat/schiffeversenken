package de.kj.games;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.random.RandomGenerator;

import static de.kj.games.Game.SIZE;

final class ComputerTargeting {
    private static final int GUARANTEED_WATER_FIELDS = 15;
    private final List<Position> positions = new ArrayList<>();
    private final int[] remaining = new int[SIZE + 1];
    private final BitSet hits = new BitSet(SIZE * SIZE);
    private final BitSet pursuitHits = new BitSet(SIZE * SIZE);
    private final Difficulty difficulty;
    private final WaterShotHistory waterHistory;
    private final List<List<Position>> sinkAlternatives = new ArrayList<>();

    ComputerTargeting(List<Integer> lengths, Difficulty difficulty) {
        this.difficulty = difficulty;
        waterHistory = new WaterShotHistory(GUARANTEED_WATER_FIELDS, difficulty.computerWaterForgetPercent);
        if (difficulty != Difficulty.SCHWER) return;
        for (int length : lengths) remaining[length]++;
        for (int length : lengths.stream().distinct().toList()) {
            for (Game.Direction direction : Game.Direction.values()) {
                for (int start = 0; start < SIZE * SIZE; start++) {
                    ShipPlacement placement = new ShipPlacement(start, direction, length);
                    if (placement.fits()) positions.add(new Position(placement, cellsOf(placement)));
                }
            }
        }
    }

    void observe(int cell, boolean hit, ShipPlacement sunkPlacement, RandomGenerator random) {
        waterHistory.record(cell, !hit, random);
        if (!hit) return;
        hits.set(cell);
        if (sunkPlacement == null) {
            pursuitHits.set(cell);
            return;
        }
        pursuitHits.andNot(cellsOf(sunkPlacement));
        if (difficulty != Difficulty.SCHWER) return;

        int sunkLength = sunkPlacement.length();
        remaining[sunkLength]--;
        // A ship could only have occupied cells already hit when the sinking was reported.
        sinkAlternatives.add(positions.stream()
                .filter(position -> position.placement().length() == sunkLength && position.cells().get(cell))
                .filter(position -> position.cells().stream().allMatch(hits::get))
                .toList());
    }

    int choose(RandomGenerator random, IntPredicate isWater) {
        if (pursuitHits.isEmpty() && random.nextInt(100) < difficulty.forcedMissPercent) {
            BitSet availableWater = unshotCells();
            for (int cell = availableWater.nextSetBit(0); cell >= 0; cell = availableWater.nextSetBit(cell + 1)) {
                if (!isWater.test(cell)) availableWater.clear(cell);
            }
            if (!availableWater.isEmpty()) return chooseRandom(random, availableWater);
        }
        if (difficulty != Difficulty.SCHWER) return chooseLocal(random);
        int[] matchedHits = new int[SIZE * SIZE];
        double[] scores = new double[SIZE * SIZE];
        scoreSinkAlternatives(0, new ArrayList<>(), hits, matchedHits, scores);

        List<Integer> best = new ArrayList<>();
        int mostHits = 0;
        double bestScore = 0;
        for (int cell = 0; cell < scores.length; cell++) {
            if (hits.get(cell) || waterHistory.remembers(cell)) continue;
            if (matchedHits[cell] > mostHits || (matchedHits[cell] == mostHits && scores[cell] > bestScore)) {
                best.clear();
                mostHits = matchedHits[cell];
                bestScore = scores[cell];
            }
            if (matchedHits[cell] == mostHits && scores[cell] == bestScore) best.add(cell);
        }
        return best.get(random.nextInt(best.size()));
    }

    private int chooseLocal(RandomGenerator random) {
        BitSet unshot = unshotCells();
        BitSet neighbors = new BitSet(SIZE * SIZE);
        BitSet lineEnds = new BitSet(SIZE * SIZE);
        for (int hit = pursuitHits.nextSetBit(0); hit >= 0; hit = pursuitHits.nextSetBit(hit + 1)) {
            int row = hit / SIZE;
            int column = hit % SIZE;
            addNeighbors(unshot, neighbors, lineEnds, column > 0 ? hit - 1 : -1,
                    column < SIZE - 1 ? hit + 1 : -1);
            addNeighbors(unshot, neighbors, lineEnds, row > 0 ? hit - SIZE : -1,
                    row < SIZE - 1 ? hit + SIZE : -1);
        }
        if (!lineEnds.isEmpty()) return chooseRandom(random, lineEnds);
        if (!neighbors.isEmpty()) return chooseRandom(random, neighbors);

        BitSet checkerboard = new BitSet(SIZE * SIZE);
        for (int cell = unshot.nextSetBit(0); cell >= 0; cell = unshot.nextSetBit(cell + 1)) {
            if ((cell / SIZE + cell % SIZE) % 2 == 0) checkerboard.set(cell);
        }
        return chooseRandom(random, checkerboard.isEmpty() ? unshot : checkerboard);
    }

    private void addNeighbors(BitSet unshot, BitSet neighbors, BitSet lineEnds, int before, int after) {
        if (before >= 0 && unshot.get(before)) {
            neighbors.set(before);
            if (after >= 0 && pursuitHits.get(after)) lineEnds.set(before);
        }
        if (after >= 0 && unshot.get(after)) {
            neighbors.set(after);
            if (before >= 0 && pursuitHits.get(before)) lineEnds.set(after);
        }
    }

    private BitSet unshotCells() {
        BitSet unshot = waterHistory.rememberedCells();
        unshot.or(hits);
        unshot.flip(0, SIZE * SIZE);
        return unshot;
    }

    private static int chooseRandom(RandomGenerator random, BitSet candidates) {
        int offset = random.nextInt(candidates.cardinality());
        int cell = candidates.nextSetBit(0);
        for (int i = 0; i < offset; i++) cell = candidates.nextSetBit(cell + 1);
        return cell;
    }

    private void scoreSinkAlternatives(int index, List<ShipPlacement> sunk, BitSet openHits,
                                       int[] matchedHits, double[] scores) {
        if (index == sinkAlternatives.size()) {
            scoreRemaining(sunk, openHits, matchedHits, scores);
            return;
        }

        for (Position position : sinkAlternatives.get(index)) {
            ShipPlacement placement = position.placement();
            if (!compatible(placement, sunk)) continue;
            BitSet stillOpen = without(openHits, position.cells());
            sunk.add(placement);
            scoreSinkAlternatives(index + 1, sunk, stillOpen, matchedHits, scores);
            sunk.removeLast();
        }
    }

    private void scoreRemaining(List<ShipPlacement> sunk, BitSet openHits, int[] matchedHits, double[] scores) {
        List<Target> targets = new ArrayList<>();
        for (Position position : positions) {
            ShipPlacement placement = position.placement();
            if (remaining[placement.length()] == 0 || waterHistory.includesWater(position.cells())
                    || !compatible(placement, sunk)) continue;

            if (openHits.isEmpty()) {
                // Hunt in the unshot cells with the greatest number of legal placements.
                position.cells().stream().forEach(cell -> scores[cell] += remaining[placement.length()]);
            } else if (position.cells().intersects(openHits)) {
                BitSet covered = (BitSet) position.cells().clone();
                covered.and(openHits);
                // A ship with every cell hit would already have produced a sinking.
                if (covered.cardinality() < placement.length()) targets.add(new Target(placement, covered));
            }
        }

        List<ShipPlacement> assigned = new ArrayList<>();
        int[] available = remaining.clone();
        for (Target target : targets) {
            if (canUse(target, openHits, targets, assigned, available)) target.score(matchedHits, scores);
        }
    }

    private static boolean canUse(Target target, BitSet openHits, List<Target> targets,
                                  List<ShipPlacement> assigned, int[] available) {
        ShipPlacement placement = target.placement();
        if (available[placement.length()] == 0 || !compatible(placement, assigned)) return false;
        BitSet stillOpen = without(openHits, target.hits());
        if (stillOpen.isEmpty()) return true;

        assigned.add(placement);
        available[placement.length()]--;
        int nextHit = stillOpen.nextSetBit(0);
        boolean possible = false;
        // Every remaining hit must belong to a compatible ship still available in the fleet.
        for (Target next : targets) {
            if (next.hits().get(nextHit) && canUse(next, stillOpen, targets, assigned, available)) {
                possible = true;
                break;
            }
        }
        available[placement.length()]++;
        assigned.removeLast();
        return possible;
    }

    private static boolean compatible(ShipPlacement placement, List<ShipPlacement> otherShips) {
        return otherShips.stream().allMatch(placement::compatibleWith);
    }

    private static BitSet without(BitSet cells, BitSet excluded) {
        BitSet result = (BitSet) cells.clone();
        result.andNot(excluded);
        return result;
    }

    private static BitSet cellsOf(ShipPlacement placement) {
        BitSet cells = new BitSet(SIZE * SIZE);
        for (int offset = 0; offset < placement.length(); offset++) cells.set(placement.cell(offset));
        return cells;
    }

    private record Position(ShipPlacement placement, BitSet cells) {}

    private record Target(ShipPlacement placement, BitSet hits) {
        void score(int[] matchedHits, double[] scores) {
            int hitCount = hits.cardinality();
            for (int offset = 0; offset < placement.length(); offset++) {
                int cell = placement.cell(offset);
                if (hits.get(cell) || matchedHits[cell] > hitCount) continue;
                if (matchedHits[cell] < hitCount) {
                    matchedHits[cell] = hitCount;
                    scores[cell] = 0;
                }
                int distance = placement.length();
                for (int neighbor = 0; neighbor < placement.length(); neighbor++) {
                    if (hits.get(placement.cell(neighbor))) distance = Math.min(distance, Math.abs(offset - neighbor));
                }
                // Favor the candidates nearest completion and continue close to the observed hits.
                scores[cell] += 1.0 / ((placement.length() - hitCount) * distance);
            }
        }
    }
}
