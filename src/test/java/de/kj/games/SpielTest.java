package de.kj.games;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import static de.kj.games.Spiel.Ausrichtung.HORIZONTAL;
import static de.kj.games.Spiel.Ausrichtung.VERTICAL;
import static de.kj.games.Spiel.Spielseite.GUEST;
import static de.kj.games.Spiel.Spielseite.HOST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft Zugregeln und Sichtbarkeit einer Mehrspielerpartie mit festen Zeitwerten und Flotten.
 */
class SpielTest
{
    private static final int WASSERFELD = 0;
    private static final List<Schiffsplatzierung> FLOTTE = List.of(
            new Schiffsplatzierung(11, HORIZONTAL, 5),
            new Schiffsplatzierung(38, VERTICAL, 4),
            new Schiffsplatzierung(40, VERTICAL, 3),
            new Schiffsplatzierung(83, HORIZONTAL, 3),
            new Schiffsplatzierung(8, HORIZONTAL, 2));

    private Spiel spiel;

    @BeforeEach
    void partieStarten()
    {
        spiel = new Spiel(Schwierigkeitsgrad.MITTEL, "Gastgeber", "Gast", 0);
        for (Spiel.Spielseite spielseite : Spiel.Spielseite.values())
        {
            for (int index = 0; index < FLOTTE.size(); index++)
            {
                Schiffsplatzierung platzierung = FLOTTE.get(index);
                assertTrue(spiel.platziereSchiff(spielseite, index, platzierung.start(), platzierung.ausrichtung()));
            }
            assertTrue(spiel.starte(spielseite, 0));
        }
    }

    @Test
    void wasserschussWechseltDieSpielseite()
    {
        assertTrue(spiel.spielerschuss(HOST, WASSERFELD, 1, 0));
        assertFalse(spiel.spielerschuss(HOST, WASSERFELD, 2, 0));
        assertTrue(spiel.spielerschuss(GUEST, WASSERFELD, 2, 0));
    }

    @Test
    void abgelaufeneZugnummerWirdAbgelehnt()
    {
        long frist = Duration.ofSeconds(12).toNanos();
        spiel.verarbeiteFristen(frist);

        assertFalse(spiel.spielerschuss(HOST, WASSERFELD, 1, frist));
        assertFalse(spiel.spielerschuss(GUEST, WASSERFELD, 1, frist));
        assertTrue(spiel.spielerschuss(GUEST, WASSERFELD, 2, frist));
    }

    @Test
    void abgelaufeneZuegeWerdenBisZurAktuellenFristNachgeholt()
    {
        long nachZweiZuegen = Duration.ofSeconds(25).toNanos();
        spiel.verarbeiteFristen(nachZweiZuegen);
        String zustand = spiel.spielzustand("host", HOST, nachZweiZuegen);
        assertEquals("PLAYER", zeichenkettenfeld(zustand, "turn"));
        assertTrue(zustand.contains("\"turnNumber\":3,"));
        assertTrue(zustand.contains("\"remainingMillis\":11000,"));

        long endeDesDrittenZugs = Duration.ofSeconds(36).toNanos();
        spiel.verarbeiteFristen(endeDesDrittenZugs);
        String naechsterZug = spiel.spielzustand("host", HOST, endeDesDrittenZugs);
        assertEquals("OPPONENT", zeichenkettenfeld(naechsterZug, "turn"));
        assertTrue(naechsterZug.contains("\"turnNumber\":4,"));
        assertTrue(naechsterZug.contains("\"remainingMillis\":12000,"));
    }

    @Test
    void verspaeteterAbrufStartetDasGefechtZurAufstellungsfrist()
    {
        long beginnDerAufstellung = Duration.ofSeconds(10).toNanos();
        var aufstellung = new Spiel(Schwierigkeitsgrad.MITTEL, "Gastgeber", "Gast", beginnDerAufstellung);
        assertTrue(aufstellung.platziereSchiff(HOST, 0, 11, HORIZONTAL));
        assertTrue(aufstellung.platziereSchiff(GUEST, 0, 21, HORIZONTAL));

        long kurzVorAblauf = Duration.ofMillis(69_999).toNanos();
        aufstellung.verarbeiteFristen(kurzVorAblauf);
        String vorher = aufstellung.spielzustand("host", HOST, kurzVorAblauf);
        assertEquals("SETUP", zeichenkettenfeld(vorher, "phase"));
        assertTrue(vorher.contains("\"remainingMillis\":1,"));

        long nachAblauf = Duration.ofSeconds(83).toNanos();
        aufstellung.verarbeiteFristen(nachAblauf);
        String danach = aufstellung.spielzustand("host", HOST, nachAblauf);
        assertEquals("PLAY", zeichenkettenfeld(danach, "phase"));
        assertEquals("OPPONENT", zeichenkettenfeld(danach, "turn"));
        assertTrue(danach.contains("\"turnNumber\":2,"));
        assertTrue(danach.contains("\"remainingMillis\":11000,"));
    }

    @Test
    void beiFristablaufGewinntDieEinzigeSeiteMitEinemPlatziertenSchiff()
    {
        var aufstellung = new Spiel(Schwierigkeitsgrad.MITTEL, "Gastgeber", "Gast", 0);
        assertTrue(aufstellung.platziereSchiff(GUEST, 0, 21, HORIZONTAL));

        long aufstellungsfrist = Duration.ofSeconds(60).toNanos();
        aufstellung.verarbeiteFristen(aufstellungsfrist);
        String zustand = aufstellung.spielzustand("guest", GUEST, aufstellungsfrist);
        assertEquals("PLAYER", zeichenkettenfeld(zustand, "winner"));
        assertTrue(zustand.contains("\"finished\":true,"));
        assertTrue(zustand.contains("\"remainingMillis\":null,"));
    }

    @Test
    void aufstellungZeigtNurEigeneSchiffe()
    {
        var aufstellung = new Spiel(Schwierigkeitsgrad.MITTEL, "Gastgeber", "Gast", 0);
        assertTrue(aufstellung.platziereSchiff(HOST, 0, 11, HORIZONTAL));
        assertTrue(aufstellung.platziereSchiff(GUEST, 0, 21, HORIZONTAL));

        String gastgeberAnsicht = aufstellung.spielzustand("host", HOST, 0);
        assertEquals("SETUP", zeichenkettenfeld(gastgeberAnsicht, "phase"));
        assertEquals('s', zeichenkettenfeld(gastgeberAnsicht, "own").charAt(11));
        assertEquals('.', zeichenkettenfeld(gastgeberAnsicht, "own").charAt(21));
        assertFalse(gastgeberAnsicht.contains("\"target\""));
        assertFalse(gastgeberAnsicht.contains("\"start\":21"));
    }

    @Test
    void gefechtZeigtTrefferUndVersenkteSchiffe()
    {
        String vorDemSchuss = spiel.spielzustand("host", HOST, 0);
        assertEquals('s', zeichenkettenfeld(vorDemSchuss, "own").charAt(8));
        assertEquals(".".repeat(Spiel.GROESSE * Spiel.GROESSE), zeichenkettenfeld(vorDemSchuss, "target"));
        assertTrue(vorDemSchuss.contains("\"unknown\":5"));

        assertTrue(spiel.spielerschuss(HOST, 8, 1, 0));
        String nachTreffer = spiel.spielzustand("host", HOST, 0);
        assertEquals('h', zeichenkettenfeld(nachTreffer, "target").charAt(8));
        assertEquals('.', zeichenkettenfeld(nachTreffer, "target").charAt(9));
        assertEquals("PLAYER", zeichenkettenfeld(nachTreffer, "turn"));
        assertEquals("HIT", zeichenkettenfeld(nachTreffer, "result"));
        assertTrue(nachTreffer.contains("\"sunk\":[]"));

        assertTrue(spiel.spielerschuss(HOST, 9, 2, 0));
        String nachVersenkung = spiel.spielzustand("host", HOST, 0);
        assertEquals("PLAYER", zeichenkettenfeld(nachVersenkung, "turn"));
        assertEquals("SUNK", zeichenkettenfeld(nachVersenkung, "result"));
        assertTrue(nachVersenkung.contains("\"sunk\":[{\"type\":"));
        assertTrue(nachVersenkung.contains("\"unknown\":4"));
        assertEquals('.', zeichenkettenfeld(nachVersenkung, "target").charAt(11));
        assertTrue(nachVersenkung.contains("\"revealed\":[]"));
        assertTrue(nachVersenkung.contains("\"replayHits\":[]"));
    }

    @Test
    void wiederholterTrefferZaehltAlsSchussAberNichtAlsTreffer()
    {
        assertTrue(spiel.spielerschuss(HOST, 8, 1, 0));
        assertTrue(spiel.spielerschuss(HOST, 8, 2, 0));
        String nachWiederholung = spiel.spielzustand("host", HOST, 0);
        assertEquals("REPEAT", zeichenkettenfeld(nachWiederholung, "result"));
        assertEquals("PLAYER", zeichenkettenfeld(nachWiederholung, "turn"));
        assertTrue(nachWiederholung.contains("\"shots\":2,\"hits\":1"));
        assertTrue(nachWiederholung.contains("\"sunk\":[]"));

        assertTrue(spiel.spielerschuss(HOST, 9, 3, 0));
        String nachVersenkung = spiel.spielzustand("host", HOST, 0);
        assertEquals("SUNK", zeichenkettenfeld(nachVersenkung, "result"));
        assertTrue(nachVersenkung.contains("\"shots\":3,\"hits\":2"));
    }

    @Test
    void schiffeDuerfenSichNichtUeberlappenUndEntfernenGibtFelderFrei()
    {
        var aufstellung = new Spiel(Schwierigkeitsgrad.MITTEL, "Gastgeber", "Gast", 0);
        assertTrue(aufstellung.platziereSchiff(HOST, 0, 11, HORIZONTAL));
        assertFalse(aufstellung.platziereSchiff(HOST, 1, 15, HORIZONTAL));

        String nachAbweisung = aufstellung.spielzustand("host", HOST, 0);
        assertTrue(nachAbweisung.contains("\"placed\":1"));
        assertEquals('.', zeichenkettenfeld(nachAbweisung, "own").charAt(16));

        assertTrue(aufstellung.entferneSchiff(HOST, 0));
        assertTrue(aufstellung.platziereSchiff(HOST, 1, 15, HORIZONTAL));
        String nachEntfernen = aufstellung.spielzustand("host", HOST, 0);
        assertTrue(nachEntfernen.contains("\"placed\":1"));
        assertEquals('.', zeichenkettenfeld(nachEntfernen, "own").charAt(11));
        assertEquals('s', zeichenkettenfeld(nachEntfernen, "own").charAt(16));
    }

    @Test
    void eingehendeWasserschuesseSindNurInSchiffsnaeheSichtbar()
    {
        assertTrue(spiel.spielerschuss(HOST, WASSERFELD, 1, 0));
        String naherWasserschuss = spiel.spielzustand("guest", GUEST, 0);
        assertEquals('m', zeichenkettenfeld(naherWasserschuss, "own").charAt(WASSERFELD));
        assertTrue(naherWasserschuss.contains("\"cell\":0,\"result\":\"WATER\""));

        int farCell = 99;
        assertTrue(spiel.spielerschuss(GUEST, farCell, 2, 0));
        String fernerWasserschuss = spiel.spielzustand("host", HOST, 0);
        assertEquals('.', zeichenkettenfeld(fernerWasserschuss, "own").charAt(farCell));
        assertTrue(fernerWasserschuss.contains("\"cell\":null,\"result\":\"WATER\""));
        String ansichtDesSchuetzen = spiel.spielzustand("guest", GUEST, 0);
        assertEquals('m', zeichenkettenfeld(ansichtDesSchuetzen, "target").charAt(farCell));
        assertTrue(ansichtDesSchuetzen.contains("\"cell\":99,\"result\":\"WATER\""));
    }

    @Test
    void spielendeZeigtSiegerflotteUndWiedergabe()
    {
        long zugnummer = 1;
        long trefferZeit = Duration.ofMillis(250).toNanos();
        for (Schiffsplatzierung schiff : FLOTTE)
        {
            for (int versatz = 0; versatz < schiff.laenge(); versatz++)
            {
                assertTrue(spiel.spielerschuss(HOST, schiff.feld(versatz), zugnummer++, trefferZeit));
            }
        }

        String siegerAnsicht = spiel.spielzustand("host", HOST, trefferZeit);
        String verliererAnsicht = spiel.spielzustand("guest", GUEST, trefferZeit);
        assertEquals("PLAYER", zeichenkettenfeld(siegerAnsicht, "winner"));
        assertEquals("OPPONENT", zeichenkettenfeld(verliererAnsicht, "winner"));
        assertTrue(siegerAnsicht.contains("\"finished\":true"));
        assertTrue(siegerAnsicht.contains("\"revealed\":[]"));
        assertTrue(verliererAnsicht.contains("\"revealed\":[{\"type\":"));
        assertEquals('s', zeichenkettenfeld(verliererAnsicht, "target").charAt(11));
        assertTrue(siegerAnsicht.contains("\"replayHits\":[{\"own\":false,\"cell\":11,\"timeMillis\":250,\"sunk\":false}"));
        assertTrue(verliererAnsicht.contains("\"replayHits\":[{\"own\":true,\"cell\":11,\"timeMillis\":250,\"sunk\":false}"));
    }

    private static String zeichenkettenfeld(String json, String feld)
    {
        var treffer = Pattern.compile("\"" + Pattern.quote(feld) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        assertTrue(treffer.find(), json);
        return treffer.group(1);
    }
}
