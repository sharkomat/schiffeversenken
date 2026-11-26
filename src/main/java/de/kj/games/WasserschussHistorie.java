package de.kj.games;

import java.util.BitSet;
import java.util.random.RandomGenerator;

import static de.kj.games.Spiel.GROESSE;

/**
 * Verwaltet die noch erinnerten Wasserschüsse einer Spielseite. Ab einer konfigurierten Anzahl kann ein neuer
 * Wasserschuss einen zufällig ausgewählten Eintrag verdrängen.
 */
final class WasserschussHistorie
{
    private final int garantierteGemerkteAnzahl;
    // Prozentchance ab garantierteGemerkteAnzahl: Spieler 17–30, Computer 12–15 Felder je nach Schwierigkeit.
    private final int vergesslichkeitWahrscheinlichkeit;
    private final BitSet wasserschuesse = new BitSet(GROESSE * GROESSE);

    WasserschussHistorie(int garantierteGemerkteAnzahl, int vergesslichkeitsWahrscheinlichkeit)
    {
        this.garantierteGemerkteAnzahl = garantierteGemerkteAnzahl;
        this.vergesslichkeitWahrscheinlichkeit = vergesslichkeitsWahrscheinlichkeit;
    }

    /**
     * Speichert einen neuen Wasserschuss und kann dabei ab der garantierten Anzahl mit der eingestellten
     * Wahrscheinlichkeit einen früheren Eintrag vergessen.
     */
    void fuegeHinzu(int feld, boolean istWasser, RandomGenerator zufallsgenerator)
    {
        if (!istWasser || wasserschuesse.get(feld)) return;

        int gemerkteAnzahl = wasserschuesse.cardinality();
        if (gemerkteAnzahl >= garantierteGemerkteAnzahl && zufallsgenerator.nextInt(100) < vergesslichkeitWahrscheinlichkeit)
        {
            int vergessenesFeld = wasserschuesse.stream().skip(zufallsgenerator.nextInt(gemerkteAnzahl)).findFirst().orElseThrow();
            wasserschuesse.clear(vergessenesFeld);
        }
        wasserschuesse.set(feld);
    }

    boolean istWasserschussGemerkt(int feld)
    {
        return wasserschuesse.get(feld);
    }

    boolean enthaeltWasserschuss(BitSet felder)
    {
        return wasserschuesse.intersects(felder);
    }

    BitSet gemerkteWasserschussFelder()
    {
        return (BitSet) wasserschuesse.clone();
    }
}
