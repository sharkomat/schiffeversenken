package de.kj.games;

import java.util.BitSet;
import java.util.random.RandomGenerator;

import static de.kj.games.Game.SIZE;

/**
 * Verwaltet die noch erinnerten Wasserschüsse einer Spielseite. Ab einer konfigurierten Anzahl kann ein neuer
 * Wasserschuss einen zufällig ausgewählten Eintrag verdrängen.
 */
final class WaterShotHistory
{
    private final int guaranteed;
    private final int forgetPercent;
    private final BitSet water = new BitSet(SIZE * SIZE);

    WaterShotHistory(int guaranteed, int forgetPercent)
    {
        this.guaranteed = guaranteed;
        this.forgetPercent = forgetPercent;
    }

    /**
     * Speichert einen neuen Wasserschuss und kann dabei ab der garantierten Anzahl mit der eingestellten
     * Wahrscheinlichkeit einen früheren Eintrag vergessen.
     */
    void record(int cell, boolean isWater, RandomGenerator random)
    {
        if (!isWater || water.get(cell)) return;

        int known = water.cardinality();
        if (known >= guaranteed && random.nextInt(100) < forgetPercent)
        {
            int forgotten = water.stream().skip(random.nextInt(known)).findFirst().orElseThrow();
            water.clear(forgotten);
        }
        water.set(cell);
    }

    boolean remembers(int cell)
    {
        return water.get(cell);
    }

    boolean includesWater(BitSet cells)
    {
        return water.intersects(cells);
    }

    BitSet rememberedCells()
    {
        return (BitSet) water.clone();
    }
}
