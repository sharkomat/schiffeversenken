package de.kj.games;

import static de.kj.games.Game.SIZE;

/**
 * Beschreibt die Position eines Schiffs über Startfeld, Ausrichtung und Länge. Dieselben Platzierungsregeln
 * gelten beim Aufstellen und bei der Zielsuche des Computers.
 */
record ShipPlacement(int start, Game.Direction direction, int length)
{
    int cell(int offset)
    {
        return start + offset * direction.step;
    }

    /**
     * Prüft, ob das Startfeld gültig ist und das Schiff in seiner Ausrichtung vollständig auf das Spielfeld
     * passt.
     */
    boolean fits()
    {
        int offset = direction == Game.Direction.HORIZONTAL ? start % SIZE : start / SIZE;
        return start >= 0 && start < SIZE * SIZE && offset + length <= SIZE;
    }

    /**
     * Prüft den erforderlichen Abstand zu einer anderen Platzierung. Parallel ausgerichtete Schiffe dürfen in
     * unmittelbar benachbarten Reihen oder Spalten liegen.
     */
    boolean compatibleWith(ShipPlacement other)
    {
        int end = cell(length - 1);
        int otherEnd = other.cell(other.length - 1);
        int rowGap = gap(start / SIZE, end / SIZE, other.start / SIZE, otherEnd / SIZE);
        int columnGap = gap(start % SIZE, end % SIZE, other.start % SIZE, otherEnd % SIZE);
        if (rowGap > 1 || columnGap > 1) return true;
        return direction == other.direction
                && (direction == Game.Direction.HORIZONTAL ? rowGap == 1 : columnGap == 1);
    }

    private static int gap(int start, int end, int otherStart, int otherEnd)
    {
        return Math.max(0, Math.max(otherStart - end, start - otherEnd));
    }
}
