package de.kj.games;

import java.util.BitSet;
import java.util.random.RandomGenerator;

import static de.kj.games.Game.SIZE;

final class WaterShotHistory {
    private final int guaranteed;
    private final int forgetPercent;
    private final BitSet water = new BitSet(SIZE * SIZE);

    WaterShotHistory(int guaranteed, int forgetPercent) {
        this.guaranteed = guaranteed;
        this.forgetPercent = forgetPercent;
    }

    void record(int cell, boolean isWater, RandomGenerator random) {
        if (!isWater || water.get(cell)) return;

        int known = water.cardinality();
        if (known >= guaranteed && random.nextInt(100) < forgetPercent) {
            int forgotten = water.stream().skip(random.nextInt(known)).findFirst().orElseThrow();
            water.clear(forgotten);
        }
        water.set(cell);
    }

    boolean remembers(int cell) {
        return water.get(cell);
    }

    boolean includesWater(BitSet cells) {
        return water.intersects(cells);
    }

    BitSet rememberedCells() {
        return (BitSet) water.clone();
    }
}
