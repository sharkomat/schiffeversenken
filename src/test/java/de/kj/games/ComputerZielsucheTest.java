package de.kj.games;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputerZielsucheTest
{
    @Test
    void verfolgtOffeneTrefferNachVersenkungWeiter()
    {
        var zufall = new Random(42);
        var zielsuche = new ComputerZielsuche(List.of(2, 3), Schwierigkeitsgrad.SCHWER);
        zielsuche.schussergebnisMerken(54, true, null, zufall);
        zielsuche.schussergebnisMerken(55, true, null, zufall);
        zielsuche.schussergebnisMerken(44, true, null, zufall);
        zielsuche.schussergebnisMerken(45, true,
                new Schiffsplatzierung(44, Spiel.Ausrichtung.HORIZONTAL, 2), zufall);

        int ziel = zielsuche.waehleZiel(zufall, feld -> false);
        assertTrue(ziel == 53 || ziel == 56, "Die offenen Treffer müssen weiter verfolgt werden: " + ziel);
    }
}
