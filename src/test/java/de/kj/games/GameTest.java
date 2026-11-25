package de.kj.games;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import static de.kj.games.Game.Direction.HORIZONTAL;
import static de.kj.games.Game.Direction.VERTICAL;
import static de.kj.games.Game.Seat.GUEST;
import static de.kj.games.Game.Seat.HOST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft Zugregeln und Sichtbarkeit einer Mehrspielerpartie mit festen Zeitwerten und Flotten.
 */
class GameTest
{
    private static final int WATER_CELL = 0;
    private static final List<ShipPlacement> FLEET = List.of(
            new ShipPlacement(11, HORIZONTAL, 5),
            new ShipPlacement(38, VERTICAL, 4),
            new ShipPlacement(40, VERTICAL, 3),
            new ShipPlacement(83, HORIZONTAL, 3),
            new ShipPlacement(8, HORIZONTAL, 2));

    private Game game;

    @BeforeEach
    void startMatch()
    {
        game = new Game(Difficulty.MITTEL, "Gastgeber", "Gast", 0);
        for (Game.Seat seat : Game.Seat.values())
        {
            for (int index = 0; index < FLEET.size(); index++)
            {
                ShipPlacement placement = FLEET.get(index);
                assertTrue(game.placeShip(seat, index, placement.start(), placement.direction()));
            }
            assertTrue(game.start(seat, 0));
        }
    }

    @Test
    void waterShotPassesTheTurnToTheOtherPlayer()
    {
        assertTrue(game.playerShot(HOST, WATER_CELL, 1, 0));
        assertFalse(game.playerShot(HOST, WATER_CELL, 2, 0));
        assertTrue(game.playerShot(GUEST, WATER_CELL, 2, 0));
    }

    @Test
    void rejectsStaleTurnNumberAfterDeadline()
    {
        long deadline = Duration.ofSeconds(12).toNanos();
        game.advance(deadline);

        assertFalse(game.playerShot(HOST, WATER_CELL, 1, deadline));
        assertFalse(game.playerShot(GUEST, WATER_CELL, 1, deadline));
        assertTrue(game.playerShot(GUEST, WATER_CELL, 2, deadline));
    }

    @Test
    void abgelaufeneZuegeWerdenBisZurAktuellenFristNachgeholt()
    {
        long nachZweiZuegen = Duration.ofSeconds(25).toNanos();
        game.advance(nachZweiZuegen);
        String zustand = game.spielzustand("host", HOST, nachZweiZuegen);
        assertEquals("PLAYER", zeichenkettenfeld(zustand, "turn"));
        assertTrue(zustand.contains("\"turnNumber\":3,"));
        assertTrue(zustand.contains("\"remainingMillis\":11000,"));

        long endeDesDrittenZugs = Duration.ofSeconds(36).toNanos();
        game.advance(endeDesDrittenZugs);
        String naechsterZug = game.spielzustand("host", HOST, endeDesDrittenZugs);
        assertEquals("OPPONENT", zeichenkettenfeld(naechsterZug, "turn"));
        assertTrue(naechsterZug.contains("\"turnNumber\":4,"));
        assertTrue(naechsterZug.contains("\"remainingMillis\":12000,"));
    }

    @Test
    void verspaeteterAbrufStartetDasGefechtZurAufstellungsfrist()
    {
        long beginnDerAufstellung = Duration.ofSeconds(10).toNanos();
        var aufstellung = new Game(Difficulty.MITTEL, "Gastgeber", "Gast", beginnDerAufstellung);
        assertTrue(aufstellung.placeShip(HOST, 0, 11, HORIZONTAL));
        assertTrue(aufstellung.placeShip(GUEST, 0, 21, HORIZONTAL));

        long kurzVorAblauf = Duration.ofMillis(69_999).toNanos();
        aufstellung.advance(kurzVorAblauf);
        String vorher = aufstellung.spielzustand("host", HOST, kurzVorAblauf);
        assertEquals("SETUP", zeichenkettenfeld(vorher, "phase"));
        assertTrue(vorher.contains("\"remainingMillis\":1,"));

        long nachAblauf = Duration.ofSeconds(83).toNanos();
        aufstellung.advance(nachAblauf);
        String danach = aufstellung.spielzustand("host", HOST, nachAblauf);
        assertEquals("PLAY", zeichenkettenfeld(danach, "phase"));
        assertEquals("OPPONENT", zeichenkettenfeld(danach, "turn"));
        assertTrue(danach.contains("\"turnNumber\":2,"));
        assertTrue(danach.contains("\"remainingMillis\":11000,"));
    }

    @Test
    void beiFristablaufGewinntDieEinzigeSeiteMitEinemPlatziertenSchiff()
    {
        var aufstellung = new Game(Difficulty.MITTEL, "Gastgeber", "Gast", 0);
        assertTrue(aufstellung.placeShip(GUEST, 0, 21, HORIZONTAL));

        long aufstellungsfrist = Duration.ofSeconds(60).toNanos();
        aufstellung.advance(aufstellungsfrist);
        String zustand = aufstellung.spielzustand("guest", GUEST, aufstellungsfrist);
        assertEquals("PLAYER", zeichenkettenfeld(zustand, "winner"));
        assertTrue(zustand.contains("\"finished\":true,"));
        assertTrue(zustand.contains("\"remainingMillis\":null,"));
    }

    @Test
    void aufstellungZeigtNurEigeneSchiffe()
    {
        var setup = new Game(Difficulty.MITTEL, "Gastgeber", "Gast", 0);
        assertTrue(setup.placeShip(HOST, 0, 11, HORIZONTAL));
        assertTrue(setup.placeShip(GUEST, 0, 21, HORIZONTAL));

        String hostView = setup.spielzustand("host", HOST, 0);
        assertEquals("SETUP", zeichenkettenfeld(hostView, "phase"));
        assertEquals('s', zeichenkettenfeld(hostView, "own").charAt(11));
        assertEquals('.', zeichenkettenfeld(hostView, "own").charAt(21));
        assertFalse(hostView.contains("\"target\""));
        assertFalse(hostView.contains("\"start\":21"));
    }

    @Test
    void gefechtZeigtTrefferUndVersenkteSchiffe()
    {
        String beforeShot = game.spielzustand("host", HOST, 0);
        assertEquals('s', zeichenkettenfeld(beforeShot, "own").charAt(8));
        assertEquals(".".repeat(Game.SIZE * Game.SIZE), zeichenkettenfeld(beforeShot, "target"));
        assertTrue(beforeShot.contains("\"unknown\":5"));

        assertTrue(game.playerShot(HOST, 8, 1, 0));
        String afterHit = game.spielzustand("host", HOST, 0);
        assertEquals('h', zeichenkettenfeld(afterHit, "target").charAt(8));
        assertEquals('.', zeichenkettenfeld(afterHit, "target").charAt(9));
        assertEquals("PLAYER", zeichenkettenfeld(afterHit, "turn"));
        assertEquals("HIT", zeichenkettenfeld(afterHit, "result"));
        assertTrue(afterHit.contains("\"sunk\":[]"));

        assertTrue(game.playerShot(HOST, 9, 2, 0));
        String afterSinking = game.spielzustand("host", HOST, 0);
        assertEquals("PLAYER", zeichenkettenfeld(afterSinking, "turn"));
        assertEquals("SUNK", zeichenkettenfeld(afterSinking, "result"));
        assertTrue(afterSinking.contains("\"sunk\":[{\"type\":"));
        assertTrue(afterSinking.contains("\"unknown\":4"));
        assertEquals('.', zeichenkettenfeld(afterSinking, "target").charAt(11));
        assertTrue(afterSinking.contains("\"revealed\":[]"));
        assertTrue(afterSinking.contains("\"replayHits\":[]"));
    }

    @Test
    void wiederholterTrefferZaehltAlsSchussAberNichtAlsTreffer()
    {
        assertTrue(game.playerShot(HOST, 8, 1, 0));
        assertTrue(game.playerShot(HOST, 8, 2, 0));
        String nachWiederholung = game.spielzustand("host", HOST, 0);
        assertEquals("REPEAT", zeichenkettenfeld(nachWiederholung, "result"));
        assertEquals("PLAYER", zeichenkettenfeld(nachWiederholung, "turn"));
        assertTrue(nachWiederholung.contains("\"shots\":2,\"hits\":1"));
        assertTrue(nachWiederholung.contains("\"sunk\":[]"));

        assertTrue(game.playerShot(HOST, 9, 3, 0));
        String nachVersenkung = game.spielzustand("host", HOST, 0);
        assertEquals("SUNK", zeichenkettenfeld(nachVersenkung, "result"));
        assertTrue(nachVersenkung.contains("\"shots\":3,\"hits\":2"));
    }

    @Test
    void schiffeDuerfenSichNichtUeberlappenUndEntfernenGibtFelderFrei()
    {
        var aufstellung = new Game(Difficulty.MITTEL, "Gastgeber", "Gast", 0);
        assertTrue(aufstellung.placeShip(HOST, 0, 11, HORIZONTAL));
        assertFalse(aufstellung.placeShip(HOST, 1, 15, HORIZONTAL));

        String nachAbweisung = aufstellung.spielzustand("host", HOST, 0);
        assertTrue(nachAbweisung.contains("\"placed\":1"));
        assertEquals('.', zeichenkettenfeld(nachAbweisung, "own").charAt(16));

        assertTrue(aufstellung.removeShip(HOST, 0));
        assertTrue(aufstellung.placeShip(HOST, 1, 15, HORIZONTAL));
        String nachEntfernen = aufstellung.spielzustand("host", HOST, 0);
        assertTrue(nachEntfernen.contains("\"placed\":1"));
        assertEquals('.', zeichenkettenfeld(nachEntfernen, "own").charAt(11));
        assertEquals('s', zeichenkettenfeld(nachEntfernen, "own").charAt(16));
    }

    @Test
    void eingehendeWasserschuesseSindNurInSchiffsnaeheSichtbar()
    {
        assertTrue(game.playerShot(HOST, WATER_CELL, 1, 0));
        String nearMiss = game.spielzustand("guest", GUEST, 0);
        assertEquals('m', zeichenkettenfeld(nearMiss, "own").charAt(WATER_CELL));
        assertTrue(nearMiss.contains("\"cell\":0,\"result\":\"WATER\""));

        int farCell = 99;
        assertTrue(game.playerShot(GUEST, farCell, 2, 0));
        String farMiss = game.spielzustand("host", HOST, 0);
        assertEquals('.', zeichenkettenfeld(farMiss, "own").charAt(farCell));
        assertTrue(farMiss.contains("\"cell\":null,\"result\":\"WATER\""));
        String shooterView = game.spielzustand("guest", GUEST, 0);
        assertEquals('m', zeichenkettenfeld(shooterView, "target").charAt(farCell));
        assertTrue(shooterView.contains("\"cell\":99,\"result\":\"WATER\""));
    }

    @Test
    void spielendeZeigtSiegerflotteUndWiedergabe()
    {
        long turnNumber = 1;
        long hitTime = Duration.ofMillis(250).toNanos();
        for (ShipPlacement ship : FLEET)
        {
            for (int offset = 0; offset < ship.length(); offset++)
            {
                assertTrue(game.playerShot(HOST, ship.cell(offset), turnNumber++, hitTime));
            }
        }

        String winnerView = game.spielzustand("host", HOST, hitTime);
        String loserView = game.spielzustand("guest", GUEST, hitTime);
        assertEquals("PLAYER", zeichenkettenfeld(winnerView, "winner"));
        assertEquals("OPPONENT", zeichenkettenfeld(loserView, "winner"));
        assertTrue(winnerView.contains("\"finished\":true"));
        assertTrue(winnerView.contains("\"revealed\":[]"));
        assertTrue(loserView.contains("\"revealed\":[{\"type\":"));
        assertEquals('s', zeichenkettenfeld(loserView, "target").charAt(11));
        assertTrue(winnerView.contains("\"replayHits\":[{\"own\":false,\"cell\":11,\"timeMillis\":250,\"sunk\":false}"));
        assertTrue(loserView.contains("\"replayHits\":[{\"own\":true,\"cell\":11,\"timeMillis\":250,\"sunk\":false}"));
    }

    private static String zeichenkettenfeld(String json, String field)
    {
        var match = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        assertTrue(match.find(), json);
        return match.group(1);
    }
}
