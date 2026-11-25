package de.kj.games;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import de.kj.games.Game.Direction;
import de.kj.games.Nation.ShipType;

/**
 * Verwaltet die Schiffsaufstellung und Schussmarkierungen einer Spielseite.
 * Prüft Platzierungen, wertet Schüsse auf das Feld aus und stellt sichtbare Felder bereit.
 */
final class Spielfeld
{
    static final int GROESSE = 10;
    private static final int FELDER = GROESSE * GROESSE;

    private final Ship[] ships = new Ship[FELDER];
    private final boolean[] fired = new boolean[FELDER];
    private final List<Ship> sunk = new ArrayList<>();
    private final List<ShipType> fleet;
    private final Ship[] placed;

    Spielfeld(List<ShipType> fleet)
    {
        this.fleet = fleet;
        this.placed = new Ship[fleet.size()];
    }

    /**
     * Probiert gültige Positionen für die noch fehlenden Schiffe in zufälliger Reihenfolge. Nimmt
     * Platzierungen zurück, wenn sich die Flotte damit nicht vervollständigen lässt.
     */
    private boolean ergaenzeZufaellig(int ship)
    {
        while (ship < placed.length && placed[ship] != null) ship++;
        if (ship == placed.length) return true;

        int length = fleet.get(ship).length();
        List<ShipPlacement> available = new ArrayList<>();
        for (Direction direction : Direction.values())
        {
            IntStream.range(0, FELDER)
                    .mapToObj(cell -> new ShipPlacement(cell, direction, length))
                    .filter(this::istPlatzierungZulaessig)
                    .forEach(available::add);
        }
        Collections.shuffle(available, ThreadLocalRandom.current());
        for (ShipPlacement placement : available)
        {
            platziere(ship, placement.start(), placement.direction());
            if (ergaenzeZufaellig(ship + 1)) return true;
            entferne(ship);
        }
        return false;
    }

    /**
     * Prüft die Platzierung und trägt das Schiff sowohl in der Flottenliste als auch auf allen belegten
     * Feldern ein.
     */
    boolean platziere(int index, int start, Direction direction)
    {
        if (index < 0 || index >= placed.length || placed[index] != null) return false;
        ShipType type = fleet.get(index);
        ShipPlacement placement = new ShipPlacement(start, direction, type.length());
        if (!istPlatzierungZulaessig(placement)) return false;
        Ship ship = new Ship(type, placement);
        for (int i = 0; i < type.length(); i++) ships[placement.cell(i)] = ship;
        placed[index] = ship;
        return true;
    }

    boolean entferne(int index)
    {
        if (index < 0 || index >= placed.length || placed[index] == null) return false;
        Ship ship = placed[index];
        for (int offset = 0; offset < ship.placement.length(); offset++)
        {
            ships[ship.placement.cell(offset)] = null;
        }
        placed[index] = null;
        return true;
    }

    int anzahlPlatzierterSchiffe()
    {
        return (int) Arrays.stream(placed).filter(ship -> ship != null).count();
    }

    List<Schiffsposition> platzierteFlotte()
    {
        return Arrays.stream(placed).filter(ship -> ship != null).map(Ship::alsPlatzierung).toList();
    }

    List<Schiffsposition> verbleibendeFlotte()
    {
        return Arrays.stream(placed).filter(ship -> ship != null && !sunk.contains(ship))
                .map(Ship::alsPlatzierung).toList();
    }

    /**
     * Ermittelt für die Aufstellungsansicht je Schiff den Platzierungsstatus und die zulässigen
     * Startfelder für beide Ausrichtungen.
     */
    List<Aufstellungsoption> schiffeFuerAufstellung()
    {
        return IntStream.range(0, fleet.size()).mapToObj(index ->
        {
            ShipType type = fleet.get(index);
            boolean isPlaced = placed[index] != null;
            return new Aufstellungsoption(type, isPlaced,
                    isPlaced ? "" : gueltigeStartfelder(Direction.HORIZONTAL, type.length()),
                    isPlaced ? "" : gueltigeStartfelder(Direction.VERTICAL, type.length()));
        }).toList();
    }

    /**
     * Kodiert für die gegebene Ausrichtung und Länge jedes Spielfeld als gültiges ({@code 1}) oder
     * ungültiges ({@code 0}) Startfeld.
     */
    private String gueltigeStartfelder(Direction direction, int length)
    {
        StringBuilder starts = new StringBuilder(FELDER);
        for (int cell = 0; cell < FELDER; cell++)
        {
            starts.append(istPlatzierungZulaessig(new ShipPlacement(cell, direction, length)) ? '1' : '0');
        }
        return starts.toString();
    }

    private boolean istPlatzierungZulaessig(ShipPlacement placement)
    {
        return placement.fits() && Arrays.stream(placed)
                .allMatch(ship -> ship == null || placement.compatibleWith(ship.placement));
    }

    /**
     * Prüft, ob innerhalb eines Abstands von höchstens zwei Zeilen und zwei Spalten ein Schiffsfeld liegt.
     */
    boolean hatSchiffInDerNaehe(int cell)
    {
        int row = cell / GROESSE;
        int col = cell % GROESSE;
        for (int neighborRow = Math.max(0, row - 2); neighborRow <= Math.min(GROESSE - 1, row + 2); neighborRow++)
        {
            for (int neighborCol = Math.max(0, col - 2); neighborCol <= Math.min(GROESSE - 1, col + 2); neighborCol++)
            {
                if (ships[neighborRow * GROESSE + neighborCol] != null) return true;
            }
        }
        return false;
    }

    /**
     * Kodiert Treffer, sichtbare Wasserschüsse und aufgedeckte Schiffsfelder für den Browser. Alle anderen
     * Felder erscheinen als unbekannt.
     */
    String sichtbareFelder(boolean revealShips, IntPredicate showWater)
    {
        StringBuilder cells = new StringBuilder(FELDER);
        for (int i = 0; i < FELDER; i++)
        {
            if (fired[i])
            {
                cells.append(ships[i] != null ? 'h' : showWater.test(i) ? 'm' : '.');
            }
            else
            {
                cells.append(revealShips && ships[i] != null ? 's' : '.');
            }
        }
        return cells.toString();
    }

    boolean stelleZufaelligAuf()
    {
        return ergaenzeZufaellig(0);
    }

    boolean istWasser(int feld)
    {
        return ships[feld] == null;
    }

    int anzahlVersenkterSchiffe()
    {
        return sunk.size();
    }

    List<Schiffsposition> versenkteFlotte()
    {
        return sunk.stream().map(Ship::alsPlatzierung).toList();
    }

    /**
     * Markiert das Feld als beschossen und unterscheidet Wasser, Treffer, Wiederholung und Versenkung.
     * Bei einer Versenkung enthält das Ergebnis Typ und Lage des Schiffs.
     */
    Schusswirkung beschiessen(int feld)
    {
        Ship ship = ships[feld];
        boolean ersterSchuss = !fired[feld];
        fired[feld] = true;
        if (ship == null) return new Schusswirkung(Ergebnis.WATER, null);
        if (!ersterSchuss) return new Schusswirkung(Ergebnis.REPEAT, null);
        if (++ship.hits == ship.type.length())
        {
            sunk.add(ship);
            return new Schusswirkung(Ergebnis.SUNK, ship.alsPlatzierung());
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
    record Schiffsposition(ShipType typ, ShipPlacement platzierung)
    {
    }

    /**
     * Enthält den Aufstellungsstatus eines Schiffs und die zulässigen Startfelder je Ausrichtung.
     */
    record Aufstellungsoption(ShipType typ, boolean platziert, String horizontal, String vertikal)
    {
    }

    /**
     * Verbindet den Typ eines platzierten Schiffs mit seiner Position und der Zahl seiner Treffer.
     */
    private static final class Ship
    {
        private final ShipType type;
        private final ShipPlacement placement;
        private int hits;

        private Ship(ShipType type, ShipPlacement placement)
        {
            this.type = type;
            this.placement = placement;
        }

        private Schiffsposition alsPlatzierung()
        {
            return new Schiffsposition(type, placement);
        }
    }
}
