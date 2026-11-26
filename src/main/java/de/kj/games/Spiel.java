package de.kj.games;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;
import java.util.random.RandomGenerator;

import de.kj.games.SpielzustandJson.Wiedergabeansicht;
import de.kj.games.SpielzustandJson.Schussansicht;
import de.kj.games.Nation.Schiff;
import de.kj.games.Spielfeld.Ergebnis;
import de.kj.games.Spielfeld.Schiffsposition;

/**
 * Verwaltet den Zustand einer Einzel- oder Mehrspielerpartie von der Flottenaufstellung bis zum Ergebnis.
 * Wendet die Spielregeln an und stellt den für den jeweiligen Spieler sichtbaren Zustand bereit.
 */
final class Spiel
{
    static final int GROESSE = Spielfeld.GROESSE;
    private static final int FELDER = GROESSE * GROESSE;
    private final Schwierigkeitsgrad schwierigkeitsgrad;
    private final Spieler gastgeber;
    private final Spieler gast;
    private final boolean mehrspieler;
    private final Zugsteuerung zugsteuerung;
    private ComputerZielsuche computer;
    private boolean gestartet;
    private boolean beendet;
    private Spielseite sieger;
    private Schuss letzterSchuss;
    private final List<WiedergabeTreffer> wiedergabeTreffer = new ArrayList<>();
    private long schussnummer;

    Spiel(Schwierigkeitsgrad stufe)
    {
        this(stufe, "", null, 0);
    }

    Spiel(Schwierigkeitsgrad stufe, String gastgebername, String gastname, long zeitpunkt)
    {
        this.schwierigkeitsgrad = stufe;
        this.mehrspieler = gastname != null;
        this.gastgeber = new Spieler(gastgebername, stufe, Nation.DEUTSCHLAND);
        this.gast = new Spieler(gastname, stufe, mehrspieler ? Nation.DEUTSCHLAND : Nation.zufaelligeNation());
        this.zugsteuerung = new Zugsteuerung(mehrspieler, zeitpunkt);
    }

    boolean istMehrspieler()
    {
        return mehrspieler;
    }

    private Spieler spieler(Spielseite spielseite)
    {
        return spielseite == Spielseite.HOST ? gastgeber : gast;
    }

    /**
     * Wechselt vor der Bereitschaftsmeldung die Nation eines Spielers. Ein Wechsel erstellt ein neues Spielfeld
     * für die gewählte Flotte und verwirft die bisherige Aufstellung.
     */
    boolean waehleNation(Spielseite spielseite, Nation nation)
    {
        Spieler spieler = spieler(spielseite);
        if (gestartet || spieler.bereit) return false;
        if (spieler.nation != nation)
        {
            spieler.nation = nation;
            spieler.spielfeld = new Spielfeld(nation.flotte);
        }
        return true;
    }

    boolean platziereSchiff(Spielseite spielseite, int schiffsindex, int feld, Ausrichtung ausrichtung)
    {
        Spieler spieler = spieler(spielseite);
        return !gestartet && !spieler.bereit && spieler.spielfeld.platziereSchiff(schiffsindex, feld, ausrichtung);
    }

    boolean entferneSchiff(Spielseite spielseite, int schiffsindex)
    {
        Spieler spieler = spieler(spielseite);
        return !gestartet && !spieler.bereit && spieler.spielfeld.entferneSchiff(schiffsindex);
    }

    /**
     * Ergänzt die fehlenden Schiffe per Zufall oder ersetzt die gesamte Aufstellung. Übernimmt eine neue
     * Aufstellung nur, wenn die Flotte vollständig platziert werden kann.
     */
    boolean stelleZufaelligAuf(Spielseite spielseite, boolean alleErsetzen)
    {
        Spieler spieler = spieler(spielseite);
        if (gestartet || spieler.bereit) return false;
        Spielfeld spielfeld = alleErsetzen ? new Spielfeld(spieler.nation.flotte) : spieler.spielfeld;
        if (!spielfeld.ergaenzeZufaellig()) return false;
        spieler.spielfeld = spielfeld;
        return true;
    }

    /**
     * Meldet einen Spieler mit vollständiger Aufstellung als bereit. Im Mehrspielermodus beginnt das Gefecht
     * nach der Bereitschaftsmeldung beider Spieler, im Einzelspiel nach dem Aufstellen der Computerflotte.
     */
    boolean starte(Spielseite spielseite, long zeitpunkt)
    {
        if (gestartet) return true;
        Spieler spieler = spieler(spielseite);
        if (spieler.spielfeld.anzahlPlatzierterSchiffe() != spieler.nation.flotte.size()) return false;
        spieler.bereit = true;
        if (mehrspieler)
        {
            if (gastgeber.bereit && gast.bereit) beginneGefecht(zeitpunkt);
        }
        else
        {
            if (!gast.spielfeld.ergaenzeZufaellig()) throw new IllegalStateException("Cannot place computer fleet");
            computer = new ComputerZielsuche(gastgeber.nation.flotte.stream().map(Schiff::laenge).toList(), schwierigkeitsgrad);
            beginneGefecht(zeitpunkt);
        }
        return true;
    }

    /**
     * Startet das Gefecht und setzt die erste Zugfrist. Hat mindestens eine Seite kein Schiff platziert, endet
     * die Partie sofort.
     */
    private void beginneGefecht(long zeitpunkt)
    {
        gestartet = true;
        zugsteuerung.beginneGefecht(zeitpunkt);
        boolean gastgeberOhneSchiffe = gastgeber.spielfeld.anzahlPlatzierterSchiffe() == 0;
        boolean gastOhneSchiffe = gast.spielfeld.anzahlPlatzierterSchiffe() == 0;
        if (gastgeberOhneSchiffe || gastOhneSchiffe)
        {
            beendet = true;
            if (gastgeberOhneSchiffe != gastOhneSchiffe) sieger = gastgeberOhneSchiffe ? Spielseite.GUEST : Spielseite.HOST;
        }
    }

    /**
     * Verarbeitet abgelaufene Aufstellungs- und Zugfristen im Mehrspielermodus. Überspringt bei Bedarf mehrere
     * abgelaufene Züge in einem Aufruf.
     */
    void verarbeiteFristen(long zeitpunkt)
    {
        if (!mehrspieler || beendet) return;
        if (!gestartet && zugsteuerung.fristAbgelaufen(zeitpunkt)) beginneGefecht(zugsteuerung.getFrist());
        if (gestartet && !beendet) zugsteuerung.ueberspringeAbgelaufeneZuege(zeitpunkt);
    }

    /**
     * Führt einen zulässigen Schuss aus und aktualisiert das Wasserschussgedächtnis. Im Mehrspielermodus muss
     * die übermittelte Zugnummer zum aktuellen Zug passen.
     */
    boolean spielerschuss(Spielseite spielseite, int feld, long erwarteteZugnummer, long zeitpunkt)
    {
        if (!gestartet || beendet || zugsteuerung.getAktiveSeite() != spielseite || feld < 0 || feld >= FELDER
                || (mehrspieler && erwarteteZugnummer != zugsteuerung.getZugnummer())) return false;
        Schuss schuss = schiessen(spielseite, feld, zeitpunkt);
        spieler(spielseite).wasserschussHistorie.fuegeHinzu(feld, schuss.ergebnis() == Ergebnis.WATER, ThreadLocalRandom.current());
        return true;
    }

    /**
     * Führt den nächsten Computerschuss im Einzelspiel aus, sobald der Computer am Zug und seine Wartezeit
     * abgelaufen ist.
     */
    boolean computerschuss(long zeitpunkt)
    {
        if (mehrspieler || !gestartet || beendet || zugsteuerung.getAktiveSeite() != Spielseite.GUEST
                || computerWartezeitMillis(zeitpunkt) > 0) return false;
        computerschussAusfuehren(Spielseite.GUEST, computer, ThreadLocalRandom.current(), zeitpunkt);
        return true;
    }

    /**
     * Lässt die Zielsuche ein Feld wählen, wertet den Schuss aus und meldet das Ergebnis an die Zielsuche
     * zurück.
     */
    private void computerschussAusfuehren(Spielseite schuetze, ComputerZielsuche zielsuche, RandomGenerator zufallsgenerator, long zeitpunkt)
    {
        Spielfeld zielSpielfeld = spieler(schuetze.andereSeite()).spielfeld;
        int feld = zielsuche.waehleZiel(zufallsgenerator, zielSpielfeld::istWasser);
        Schuss schuss = schiessen(schuetze, feld, zeitpunkt);
        boolean istTreffer = schuss.ergebnis() != Ergebnis.WATER;
        Schiffsposition versenkt = schuss.versenktesSchiff();
        zielsuche.schussergebnisMerken(feld, istTreffer, versenkt == null ? null : versenkt.platzierung(), zufallsgenerator);
    }

    /**
     * Simuliert ein Duell mit identischen Flottenaufstellungen und unabhängig gewählten Schwierigkeitsstufen.
     * Gibt {@code null} zurück, wenn nach Erreichen des Schusslimits kein Sieger feststeht.
     */
    static Spielseite spieleComputerduell(Schwierigkeitsgrad gastgeberStufe, Schwierigkeitsgrad gastStufe, List<Schiffsplatzierung> flotte,
                                          RandomGenerator zufallsgenerator, int schusslimit)
    {
        Spiel spiel = new Spiel(gastgeberStufe, gastgeberStufe.bezeichnung, gastStufe.bezeichnung, 0);
        for (Spielseite spielseite : Spielseite.values())
        {
            List<Schiff> schiffstypen = spiel.spieler(spielseite).nation.flotte;
            if (flotte.size() != schiffstypen.size()) throw new IllegalArgumentException("Incomplete fleet");
            for (int index = 0; index < flotte.size(); index++)
            {
                Schiffsplatzierung platzierung = flotte.get(index);
                if (platzierung.laenge() != schiffstypen.get(index).laenge()
                        || !spiel.platziereSchiff(spielseite, index, platzierung.start(), platzierung.ausrichtung()))
                {
                    throw new IllegalArgumentException("Invalid placement for ship " + index);
                }
            }
            spiel.starte(spielseite, 0);
        }
        List<Integer> schiffslaengen = flotte.stream().map(Schiffsplatzierung::laenge).toList();
        ComputerZielsuche gastgeberZielsuche = new ComputerZielsuche(schiffslaengen, gastgeberStufe);
        ComputerZielsuche gastZielsuche = new ComputerZielsuche(schiffslaengen, gastStufe);
        for (int schuss = 0; schuss < schusslimit && !spiel.beendet; schuss++)
        {
            Spielseite amZug = spiel.zugsteuerung.getAktiveSeite();
            spiel.computerschussAusfuehren(amZug, amZug == Spielseite.HOST ? gastgeberZielsuche : gastZielsuche, zufallsgenerator, 0);
        }
        return spiel.sieger;
    }

    long computerWartezeitMillis(long zeitpunkt)
    {
        return !mehrspieler && gestartet && !beendet && zugsteuerung.getAktiveSeite() == Spielseite.GUEST
                ? zugsteuerung.restzeitMillis(zeitpunkt) : 0;
    }

    /**
     * Wertet den Schuss aus und aktualisiert Zug, Frist und gegebenenfalls den Sieger. Erfasst neue Treffer und
     * Versenkungen für die spätere Wiedergabe.
     */
    private Schuss schiessen(Spielseite schuetze, int feld, long zeitpunkt)
    {
        Spielfeld zielSpielfeld = spieler(schuetze.andereSeite()).spielfeld;
        var wirkung = zielSpielfeld.beschiessen(feld);
        Ergebnis ergebnis = wirkung.ergebnis();
        if (ergebnis == Ergebnis.SUNK
                && zielSpielfeld.anzahlVersenkterSchiffe() == zielSpielfeld.anzahlPlatzierterSchiffe())
        {
            sieger = schuetze;
            beendet = true;
        }
        Spieler angreifer = spieler(schuetze);
        angreifer.abgegebeneSchuesse++;
        if (ergebnis == Ergebnis.HIT || ergebnis == Ergebnis.SUNK)
        {
            angreifer.treffer++;
            wiedergabeTreffer.add(new WiedergabeTreffer(schuetze.andereSeite(), feld, zugsteuerung.gefechtsdauerMillis(zeitpunkt),
                    ergebnis == Ergebnis.SUNK));
        }
        letzterSchuss = new Schuss(++schussnummer, schuetze, feld, ergebnis, wirkung.versenktesSchiff());
        Spielseite naechsteSeite = ergebnis == Ergebnis.WATER ? schuetze.andereSeite() : zugsteuerung.getAktiveSeite();
        zugsteuerung.aktualisiereNachSchuss(naechsteSeite, zeitpunkt);
        return letzterSchuss;
    }

    /**
     * Stellt die sichtbaren Daten aus Sicht eines Spielers zusammen und übergibt sie zur JSON-Ausgabe.
     */
    String spielzustand(String id, Spielseite spielseite, long zeitpunkt)
    {
        Spieler betrachter = spieler(spielseite);
        Spieler gegner = spieler(spielseite.andereSeite());
        var stammdaten = new SpielzustandJson.Stammdaten(id, schwierigkeitsgrad.bezeichnung, mehrspieler ? "HUMAN" : "COMPUTER",
                betrachter.name, mehrspieler ? gegner.name : "Computer",
                mehrspieler && !beendet ? zugsteuerung.restzeitMillis(zeitpunkt) : null);
        if (!gestartet)
        {
            return SpielzustandJson.aufstellung(stammdaten, new SpielzustandJson.Aufstellung(betrachter.nation.name(),
                    betrachter.spielfeld.sichtbareFelder(true, feld -> true), betrachter.bereit, betrachter.spielfeld.schiffeFuerAufstellung(),
                    betrachter.spielfeld.platzierteFlotte()));
        }
        return SpielzustandJson.gefecht(stammdaten, gefechtsansicht(spielseite, betrachter, gegner, zeitpunkt));
    }

    /**
     * Filtert Flotten, Wasserschüsse und Wiedergabedaten nach den Sichtbarkeitsregeln des Gefechts.
     */
    private SpielzustandJson.Gefecht gefechtsansicht(Spielseite spielseite, Spieler betrachter, Spieler gegner, long zeitpunkt)
    {
        IntPredicate wasserAnzeigen = betrachter.spielfeld::istSchiffInDerNaehe;
        Spielfeld gegnerischesSpielfeld = gegner.spielfeld;
        boolean gegnerAufdecken = beendet && sieger == spielseite.andereSeite();
        List<Schiffsposition> versenkteSchiffe = gegnerischesSpielfeld.versenkteFlotte();
        List<Schiffsposition> aufgedeckteSchiffe = gegnerAufdecken ? gegnerischesSpielfeld.verbleibendeFlotte() : List.of();
        int unbekannteSchiffe = gegnerAufdecken ? 0 : gegnerischesSpielfeld.anzahlPlatzierterSchiffe() - gegnerischesSpielfeld.anzahlVersenkterSchiffe();
        List<Wiedergabeansicht> wiedergabe = beendet ? wiedergabeTreffer.stream().map(hit -> hit.ansichtFuer(spielseite)).toList() : List.of();
        return new SpielzustandJson.Gefecht(betrachter.nation.bezeichnung, betrachter.spielfeld.sichtbareFelder(true, wasserAnzeigen),
                gegnerischesSpielfeld.sichtbareFelder(gegnerAufdecken, betrachter.wasserschussHistorie::istWasserschussGemerkt),
                relativeSeite(zugsteuerung.getAktiveSeite(), spielseite),
                sieger == null ? null : relativeSeite(sieger, spielseite), beendet,
                zugsteuerung.getZugnummer(), betrachter.abgegebeneSchuesse, betrachter.treffer, gegner.abgegebeneSchuesse, gegner.treffer,
                betrachter.spielfeld.platzierteFlotte(), versenkteSchiffe, aufgedeckteSchiffe, sieger == null ? null : spieler(sieger).nation.laendercode,
                unbekannteSchiffe, computerWartezeitMillis(zeitpunkt), letzterSichtbarerSchuss(spielseite, wasserAnzeigen), wiedergabe);
    }

    private Schussansicht letzterSichtbarerSchuss(Spielseite betrachter, IntPredicate wasserAnzeigen)
    {
        if (letzterSchuss == null) return null;
        boolean feldAnzeigen = letzterSchuss.schuetze() == betrachter || letzterSchuss.ergebnis() != Ergebnis.WATER
                || wasserAnzeigen.test(letzterSchuss.feld());
        Schiffsposition versenkt = letzterSchuss.versenktesSchiff();
        return new Schussansicht(letzterSchuss.nummer(), relativeSeite(letzterSchuss.schuetze(), betrachter),
                feldAnzeigen ? letzterSchuss.feld() : null, letzterSchuss.ergebnis().name(), versenkt == null ? null : versenkt.typ());
    }

    private String relativeSeite(Spielseite spielseite, Spielseite betrachter)
    {
        if (spielseite == betrachter) return "PLAYER";
        return mehrspieler ? "OPPONENT" : "COMPUTER";
    }

    /**
     * Unterscheidet die beiden Seiten einer Partie. Im Einzelspiel belegt der Computer den Gastplatz.
     */
    enum Spielseite
    {
        HOST,
        GUEST;

        Spielseite andereSeite()
        {
            return this == HOST ? GUEST : HOST;
        }
    }

    /**
     * Definiert die Ausrichtung eines Schiffs und den Schritt zwischen benachbarten Feldern im eindimensionalen
     * Spielfeld.
     */
    enum Ausrichtung
    {
        HORIZONTAL(1),
        VERTICAL(GROESSE);

        final int schrittweite;

        Ausrichtung(int step)
        {
            this.schrittweite = step;
        }
    }

    /**
     * Erfasst einen ausgewerteten Schuss für die Spielansicht und die Zielsuche. Bei einer Versenkung enthält
     * er auch Typ und Lage des versenkten Schiffs.
     */
    private record Schuss(long nummer, Spielseite schuetze, int feld, Ergebnis ergebnis, Schiffsposition versenktesSchiff)
    {
    }

    /**
     * Speichert einen Treffer mit Zielfeld und Zeitpunkt seit Gefechtsbeginn für die Wiedergabe nach Spielende.
     */
    private record WiedergabeTreffer(Spielseite zielseite, int feld, long zeitMillis, boolean versenkt)
    {
        Wiedergabeansicht ansichtFuer(Spielseite betrachter)
        {
            return new Wiedergabeansicht(zielseite == betrachter, feld, zeitMillis, versenkt);
        }
    }

    /**
     * Hält den Zustand einer Spielseite, darunter Nation, Spielfeld, Bereitschaft und Schussstatistik.
     */
    private static final class Spieler
    {
        private final String name;
        private final WasserschussHistorie wasserschussHistorie;
        private Nation nation;
        private Spielfeld spielfeld;
        private boolean bereit;
        private int abgegebeneSchuesse;
        private int treffer;

        private Spieler(String name, Schwierigkeitsgrad stufe, Nation nation)
        {
            this.name = name;
            this.nation = nation;
            this.spielfeld = new Spielfeld(nation.flotte);
            this.wasserschussHistorie = new WasserschussHistorie(stufe.spielerGarantierteWasserschuesse, stufe.spielerVergesslichkeitProzent);
        }
    }
}
