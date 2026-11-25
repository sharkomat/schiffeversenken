package de.kj.games;

import org.junit.jupiter.api.Test;

import static de.kj.games.Game.Direction.HORIZONTAL;
import static de.kj.games.Game.Direction.VERTICAL;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft die Spielfeldgrenzen für beide Schiffsausrichtungen.
 */
class ShipPlacementTest
{
    @Test
    void horizontalPlacementMustStayInItsRow()
    {
        assertTrue(new ShipPlacement(7, HORIZONTAL, 3).fits());
        assertFalse(new ShipPlacement(8, HORIZONTAL, 3).fits());
    }

    @Test
    void verticalPlacementMustStayOnTheBoard()
    {
        assertTrue(new ShipPlacement(70, VERTICAL, 3).fits());
        assertFalse(new ShipPlacement(80, VERTICAL, 3).fits());
    }
}
