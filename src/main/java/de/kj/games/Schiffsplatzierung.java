package de.kj.games;

import static de.kj.games.Spiel.GROESSE;

/**
 * Beschreibt die Position eines Schiffs über Startfeld, Ausrichtung und Länge. Dieselben Platzierungsregeln
 * gelten beim Aufstellen und bei der Zielsuche des Computers.
 */
record Schiffsplatzierung(int start, Spiel.Ausrichtung ausrichtung, int laenge)
{
    int feld(int versatz)
    {
        return start + versatz * ausrichtung.schrittweite;
    }

    /**
     * Prüft, ob das Startfeld gültig ist und das Schiff in seiner Ausrichtung vollständig auf das Spielfeld
     * passt.
     */
    boolean passtAufSpielfeld()
    {
        int versatz = ausrichtung == Spiel.Ausrichtung.HORIZONTAL ? start % GROESSE : start / GROESSE;
        return start >= 0 && start < GROESSE * GROESSE && versatz + laenge <= GROESSE;
    }

    /**
     * Prüft den erforderlichen Abstand zu einer anderen Platzierung. Parallel ausgerichtete Schiffe dürfen in
     * unmittelbar benachbarten Reihen oder Spalten liegen.
     */
    boolean istVertraeglichMit(Schiffsplatzierung andere)
    {
        int ende = feld(laenge - 1);
        int anderesEnde = andere.feld(andere.laenge - 1);
        int zeilenabstand = abstand(start / GROESSE, ende / GROESSE, andere.start / GROESSE, anderesEnde / GROESSE);
        int spaltenabstand = abstand(start % GROESSE, ende % GROESSE, andere.start % GROESSE, anderesEnde % GROESSE);
        if (zeilenabstand > 1 || spaltenabstand > 1) return true;
        return ausrichtung == andere.ausrichtung
                && (ausrichtung == Spiel.Ausrichtung.HORIZONTAL ? zeilenabstand == 1 : spaltenabstand == 1);
    }

    private static int abstand(int start, int ende, int andererStart, int anderesEnde)
    {
        return Math.max(0, Math.max(andererStart - ende, start - anderesEnde));
    }
}
