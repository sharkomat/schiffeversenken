package de.kj.games;

import java.util.BitSet;

import static de.kj.games.Game.SIZE;

final class WaterShotHistory {
    private final int window;
    private final int[] lastWaterShot = new int[SIZE * SIZE];
    private final BitSet water = new BitSet(SIZE * SIZE);
    private int shots;

    WaterShotHistory(int window) {
        this.window = window;
    }

    void record(int cell, boolean isWater) {
        shots++;
        if (isWater) {
            lastWaterShot[cell] = shots;
            water.set(cell);
        }
        for (int known = water.nextSetBit(0); known >= 0; known = water.nextSetBit(known + 1)) {
            if (shots - lastWaterShot[known] >= window) {
                water.clear(known);
                lastWaterShot[known] = 0;
            }
        }
    }

    int shots() {
        return shots;
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
