package de.kj.games;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import de.kj.games.Spiel.Ausrichtung;
import de.kj.games.Nation.Schiff;

/**
 * Verwaltet die Schiffsaufstellung und Schussmarkierungen einer Spielseite.
 * Prüft Platzierungen, wertet Schüsse auf das Feld aus und stellt sichtbare Felder bereit.
 */
final class Spielfeld
{
    static final int GROESSE = 10;
    private static final int FELDER = GROESSE * GROESSE;

    private final PlatziertesSchiff[] schiffe = new PlatziertesSchiff[FELDER];
    private final boolean[] beschossen = new boolean[FELDER];
    private final List<PlatziertesSchiff> versenkteSchiffe = new ArrayList<>();
    private final List<Schiff> flotte;
    private final PlatziertesSchiff[] platzierteSchiffe;

    Spielfeld(List<Schiff> flotte)
    {
        this.flotte = flotte;
        this.platzierteSchiffe = new PlatziertesSchiff[flotte.size()];
    }

    /**
     * Probiert gültige Positionen für die noch fehlenden Schiffe in zufälliger Reihenfolge. Nimmt
     * Platzierungen zurück, wenn sich die Flotte damit nicht vervollständigen lässt.
     */
    private boolean ergaenzeZufaellig(int schiffsindex)
    {
        while (schiffsindex < platzierteSchiffe.length && platzierteSchiffe[schiffsindex] != null) schiffsindex++;
        if (schiffsindex == platzierteSchiffe.length) return true;

        int laenge = flotte.get(schiffsindex).laenge();
        List<Schiffsplatzierung> moeglichePlatzierungen = new ArrayList<>();
        for (Ausrichtung ausrichtung : Ausrichtung.values())
        {
            IntStream.range(0, FELDER)
                    .mapToObj(feld -> new Schiffsplatzierung(feld, ausrichtung, laenge))
                    .filter(this::istPlatzierungZulaessig)
                    .forEach(moeglichePlatzierungen::add);
        }
        Collections.shuffle(moeglichePlatzierungen, ThreadLocalRandom.current());
        for (Schiffsplatzierung platzierung : moeglichePlatzierungen)
        {
            platziereSchiff(schiffsindex, platzierung.start(), platzierung.ausrichtung());
            if (ergaenzeZufaellig(schiffsindex + 1)) return true;
            entferneSchiff(schiffsindex);
        }
        return false;
    }

    /**
     * Prüft die Platzierung und trägt das Schiff sowohl in der Flottenliste als auch auf allen belegten
     * Feldern ein.
     */
    boolean platziereSchiff(int index, int start, Ausrichtung ausrichtung)
    {
        if (index < 0 || index >= platzierteSchiffe.length || platzierteSchiffe[index] != null) return false;
        Schiff typ = flotte.get(index);
        Schiffsplatzierung platzierung = new Schiffsplatzierung(start, ausrichtung, typ.laenge());
        if (!istPlatzierungZulaessig(platzierung)) return false;
        PlatziertesSchiff schiff = new PlatziertesSchiff(typ, platzierung);
        for (int i = 0; i < typ.laenge(); i++) schiffe[platzierung.feld(i)] = schiff;
        platzierteSchiffe[index] = schiff;
        return true;
    }

    boolean entferneSchiff(int index)
    {
        if (index < 0 || index >= platzierteSchiffe.length || platzierteSchiffe[index] == null) return false;
        PlatziertesSchiff schiff = platzierteSchiffe[index];
        for (int versatz = 0; versatz < schiff.platzierung.laenge(); versatz++)
        {
            schiffe[schiff.platzierung.feld(versatz)] = null;
        }
        platzierteSchiffe[index] = null;
        return true;
    }

    int anzahlPlatzierterSchiffe()
    {
        return (int) Arrays.stream(platzierteSchiffe).filter(Objects::nonNull).count();
    }

    List<Schiffsposition> platzierteFlotte()
    {
        return Arrays.stream(platzierteSchiffe).filter(Objects::nonNull).map(PlatziertesSchiff::alsPlatzierung).toList();
    }

    List<Schiffsposition> verbleibendeFlotte()
    {
        return Arrays.stream(platzierteSchiffe).filter(schiff -> schiff != null && !versenkteSchiffe.contains(schiff))
                .map(PlatziertesSchiff::alsPlatzierung).toList();
    }

    /**
     * Ermittelt für die Aufstellungsansicht je Schiff den Platzierungsstatus und die zulässigen
     * Startfelder für beide Ausrichtungen.
     */
    List<Aufstellungsoption> schiffeFuerAufstellung()
    {
        return IntStream.range(0, flotte.size()).mapToObj(index ->
        {
            Schiff typ = flotte.get(index);
            boolean istPlatziert = platzierteSchiffe[index] != null;
            return new Aufstellungsoption(typ, istPlatziert,
                    istPlatziert ? "" : gueltigeStartfelder(Ausrichtung.HORIZONTAL, typ.laenge()),
                    istPlatziert ? "" : gueltigeStartfelder(Ausrichtung.VERTICAL, typ.laenge()));
        }).toList();
    }

    /**
     * Kodiert für die gegebene Ausrichtung und Länge jedes Spielfeld als gültiges ({@code 1}) oder
     * ungültiges ({@code 0}) Startfeld.
     */
    private String gueltigeStartfelder(Ausrichtung ausrichtung, int laenge)
    {
        StringBuilder startfelder = new StringBuilder(FELDER);
        for (int feld = 0; feld < FELDER; feld++)
        {
            startfelder.append(istPlatzierungZulaessig(new Schiffsplatzierung(feld, ausrichtung, laenge)) ? '1' : '0');
        }
        return startfelder.toString();
    }

    private boolean istPlatzierungZulaessig(Schiffsplatzierung platzierung)
    {
        return platzierung.passtAufSpielfeld() && Arrays.stream(platzierteSchiffe)
                .allMatch(schiff -> schiff == null || platzierung.istVertraeglichMit(schiff.platzierung));
    }

    /**
     * Prüft, ob innerhalb eines Abstands von höchstens zwei Zeilen und zwei Spalten ein Schiffsfeld liegt.
     */
    boolean istSchiffInDerNaehe(int feld)
    {
        int zeile = feld / GROESSE;
        int spalte = feld % GROESSE;
        for (int nachbarzeile = Math.max(0, zeile - 2); nachbarzeile <= Math.min(GROESSE - 1, zeile + 2); nachbarzeile++)
        {
            for (int nachbarspalte = Math.max(0, spalte - 2); nachbarspalte <= Math.min(GROESSE - 1, spalte + 2); nachbarspalte++)
            {
                if (schiffe[nachbarzeile * GROESSE + nachbarspalte] != null) return true;
            }
        }
        return false;
    }

    /**
     * Kodiert Treffer, sichtbare Wasserschüsse und aufgedeckte Schiffsfelder für den Browser. Alle anderen
     * Felder erscheinen als unbekannt.
     */
    String sichtbareFelder(boolean schiffeAufdecken, IntPredicate wasserAnzeigen)
    {
        StringBuilder cells = new StringBuilder(FELDER);
        for (int i = 0; i < FELDER; i++)
        {
            if (beschossen[i])
            {
                cells.append(schiffe[i] != null ? 'h' : wasserAnzeigen.test(i) ? 'm' : '.');
            }
            else
            {
                cells.append(schiffeAufdecken && schiffe[i] != null ? 's' : '.');
            }
        }
        return cells.toString();
    }

    boolean ergaenzeZufaellig()
    {
        return ergaenzeZufaellig(0);
    }

    boolean istWasser(int feld)
    {
        return schiffe[feld] == null;
    }

    int anzahlVersenkterSchiffe()
    {
        return versenkteSchiffe.size();
    }

    List<Schiffsposition> versenkteFlotte()
    {
        return versenkteSchiffe.stream().map(PlatziertesSchiff::alsPlatzierung).toList();
    }

    /**
     * Markiert das Feld als beschossen und unterscheidet Wasser, Treffer, Wiederholung und Versenkung.
     * Bei einer Versenkung enthält das Ergebnis Typ und Lage des Schiffs.
     */
    Schusswirkung beschiessen(int feld)
    {
        PlatziertesSchiff schiff = schiffe[feld];
        boolean ersterSchuss = !beschossen[feld];
        beschossen[feld] = true;
        if (schiff == null) return new Schusswirkung(Ergebnis.WATER, null);
        if (!ersterSchuss) return new Schusswirkung(Ergebnis.REPEAT, null);
        if (++schiff.treffer == schiff.schiff.laenge())
        {
            versenkteSchiffe.add(schiff);
            return new Schusswirkung(Ergebnis.SUNK, schiff.alsPlatzierung());
        }
        return new Schusswirkung(Ergebnis.HIT, null);
    }

    /**
     * Kennzeichnet die Auswirkung eines Schusses auf das Zielfeld.
     */
    enum Ergebnis
    {
        WATER,
        HIT,
        REPEAT,
        SUNK
    }

    /**
     * Enthält das Ergebnis eines Schusses und bei einer Versenkung Typ und Lage des Schiffs.
     */
    record Schusswirkung(Ergebnis ergebnis, Schiffsposition versenktesSchiff)
    {
    }

    /**
     * Beschreibt den Typ und die Lage eines platzierten Schiffs.
     */
    record Schiffsposition(Schiff typ, Schiffsplatzierung platzierung)
    {
    }

    /**
     * Enthält den Aufstellungsstatus eines Schiffs und die zulässigen Startfelder je Ausrichtung.
     */
    record Aufstellungsoption(Schiff typ, boolean platziert, String horizontal, String vertikal)
    {
    }

    /**
     * Verbindet den Typ eines platzierten Schiffs mit seiner Position und der Zahl seiner Treffer.
     */
    private static final class PlatziertesSchiff
    {
        private final Schiff schiff;
        private final Schiffsplatzierung platzierung;
        private int treffer;

        private PlatziertesSchiff(Schiff typ, Schiffsplatzierung platzierung)
        {
            this.schiff = typ;
            this.platzierung = platzierung;
        }

        private Schiffsposition alsPlatzierung()
        {
            return new Schiffsposition(schiff, platzierung);
        }
    }
}
