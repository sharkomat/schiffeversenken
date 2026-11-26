package de.kj.games;

import org.junit.jupiter.api.Test;

import static de.kj.games.Spiel.Ausrichtung.HORIZONTAL;
import static de.kj.games.Spiel.Ausrichtung.VERTICAL;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft die Spielfeldgrenzen für beide Schiffsausrichtungen.
 */
class SchiffsplatzierungTest
{
    @Test
    void waagerechtePlatzierungBleibtInDerZeile()
    {
        assertTrue(new Schiffsplatzierung(7, HORIZONTAL, 3).passtAufSpielfeld());
        assertFalse(new Schiffsplatzierung(8, HORIZONTAL, 3).passtAufSpielfeld());
    }

    @Test
    void senkrechtePlatzierungBleibtAufDemSpielfeld()
    {
        assertTrue(new Schiffsplatzierung(70, VERTICAL, 3).passtAufSpielfeld());
        assertFalse(new Schiffsplatzierung(80, VERTICAL, 3).passtAufSpielfeld());
    }
}
