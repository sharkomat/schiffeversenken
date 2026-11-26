package de.kj.games;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.random.RandomGenerator;

import static de.kj.games.Spiel.GROESSE;

/**
 * Wählt anhand bisheriger Schussergebnisse und der Schwierigkeit das nächste Ziel des Computers. Auf der Stufe
 * Schwer bewertet die Klasse zusätzlich mögliche Positionen der gegnerischen Schiffe.
 */
final class ComputerZielsuche
{
    private final List<Position> positionen = new ArrayList<>();
    private final int[] verbleibendeSchiffe = new int[GROESSE + 1];
    private final BitSet treffer = new BitSet(GROESSE * GROESSE);
    private final BitSet offeneTreffer = new BitSet(GROESSE * GROESSE);
    private final Schwierigkeitsgrad schwierigkeitsgrad;
    private final WasserschussHistorie wasserschussHistorie;
    private final List<Schiffsplatzierung> versenkteSchiffe = new ArrayList<>();

    /**
     * Initialisiert das Wasserschussgedächtnis für die gewählte Stufe. Auf Schwer berechnet der Konstruktor
     * außerdem alle möglichen Positionen für die benötigten Schiffslängen vor.
     */
    ComputerZielsuche(List<Integer> schiffslaengen, Schwierigkeitsgrad schwierigkeitsgrad)
    {
        this.schwierigkeitsgrad = schwierigkeitsgrad;
        wasserschussHistorie = new WasserschussHistorie(schwierigkeitsgrad.computerGarantierteWasserschuesse, schwierigkeitsgrad.computerVergesslichkeitProzent);
        if (schwierigkeitsgrad != Schwierigkeitsgrad.SCHWER) return;
        for (int laenge : schiffslaengen) verbleibendeSchiffe[laenge]++;
        for (int laenge : schiffslaengen.stream().distinct().toList())
        {
            for (Spiel.Ausrichtung ausrichtung : Spiel.Ausrichtung.values())
            {
                for (int start = 0; start < GROESSE * GROESSE; start++)
                {
                    Schiffsplatzierung platzierung = new Schiffsplatzierung(start, ausrichtung, laenge);
                    if (platzierung.passtAufSpielfeld()) positionen.add(new Position(platzierung, felderVon(platzierung)));
                }
            }
        }
    }

    /**
     * Speichert das Schussergebnis und aktualisiert die offenen Treffer. Auf Schwer wird bei einer Versenkung
     * die tatsächliche Lage des Schiffs für die weitere Zielsuche gespeichert.
     */
    void schussergebnisMerken(int feld, boolean istTreffer, Schiffsplatzierung platzierungVersenktesSchiff, RandomGenerator zufallsgenerator)
    {
        wasserschussHistorie.fuegeHinzu(feld, !istTreffer, zufallsgenerator);
        if (!istTreffer) return;
        treffer.set(feld);
        if (platzierungVersenktesSchiff == null)
        {
            offeneTreffer.set(feld);
            return;
        }
        offeneTreffer.andNot(felderVon(platzierungVersenktesSchiff));
        if (schwierigkeitsgrad != Schwierigkeitsgrad.SCHWER) return;

        int laengeVersenktesSchiff = platzierungVersenktesSchiff.laenge();
        verbleibendeSchiffe[laengeVersenktesSchiff]--;
        versenkteSchiffe.add(platzierungVersenktesSchiff);
    }

    /**
     * Wählt ein Ziel anhand lokaler Treffer oder einer Bewertung möglicher Schiffspositionen. Das übergebene
     * Prädikat dient ausschließlich dazu, bei der Suche einen gelegentlichen absichtlichen Wasserschuss zu
     * ermöglichen.
     */
    int waehleZiel(RandomGenerator zufallsgenerator, IntPredicate istWasser)
    {
        if (offeneTreffer.isEmpty() && zufallsgenerator.nextInt(100) < schwierigkeitsgrad.absichtlicherWasserschussProzent)
        {
            BitSet verfuegbaresWasser = moeglicheZielfelder();
            for (int feld = verfuegbaresWasser.nextSetBit(0); feld >= 0; feld = verfuegbaresWasser.nextSetBit(feld + 1))
            {
                if (!istWasser.test(feld)) verfuegbaresWasser.clear(feld);
            }
            if (!verfuegbaresWasser.isEmpty()) return waehleZufaellig(zufallsgenerator, verfuegbaresWasser);
        }
        if (schwierigkeitsgrad != Schwierigkeitsgrad.SCHWER) return waehleLokalesZiel(zufallsgenerator);
        int[] zugeordneteTreffer = new int[GROESSE * GROESSE];
        double[] bewertungen = new double[GROESSE * GROESSE];
        bewerteVerbleibendeSchiffe(versenkteSchiffe, offeneTreffer, zugeordneteTreffer, bewertungen);

        List<Integer> besteFelder = new ArrayList<>();
        int meisteTreffer = 0;
        double besteBewertung = 0;
        for (int feld = 0; feld < bewertungen.length; feld++)
        {
            if (treffer.get(feld) || wasserschussHistorie.istWasserschussGemerkt(feld)) continue;
            if (zugeordneteTreffer[feld] > meisteTreffer || (zugeordneteTreffer[feld] == meisteTreffer && bewertungen[feld] > besteBewertung))
            {
                besteFelder.clear();
                meisteTreffer = zugeordneteTreffer[feld];
                besteBewertung = bewertungen[feld];
            }
            if (zugeordneteTreffer[feld] == meisteTreffer && bewertungen[feld] == besteBewertung) besteFelder.add(feld);
        }
        return besteFelder.get(zufallsgenerator.nextInt(besteFelder.size()));
    }

    /**
     * Bevorzugt die Verlängerung einer Trefferreihe und danach Nachbarfelder offener Treffer. Ohne solche
     * Kandidaten sucht die Methode bevorzugt im Schachbrettmuster.
     */
    private int waehleLokalesZiel(RandomGenerator zufallsgenerator)
    {
        BitSet moeglicheFelder = moeglicheZielfelder();
        BitSet nachbarfelder = new BitSet(GROESSE * GROESSE);
        BitSet reihenenden = new BitSet(GROESSE * GROESSE);
        for (int trefferFeld = offeneTreffer.nextSetBit(0); trefferFeld >= 0; trefferFeld = offeneTreffer.nextSetBit(trefferFeld + 1))
        {
            int zeile = trefferFeld / GROESSE;
            int spalte = trefferFeld % GROESSE;
            fuegeNachbarfelderHinzu(moeglicheFelder, nachbarfelder, reihenenden, spalte > 0 ? trefferFeld - 1 : -1,
                    spalte < GROESSE - 1 ? trefferFeld + 1 : -1);
            fuegeNachbarfelderHinzu(moeglicheFelder, nachbarfelder, reihenenden, zeile > 0 ? trefferFeld - GROESSE : -1,
                    zeile < GROESSE - 1 ? trefferFeld + GROESSE : -1);
        }
        if (!reihenenden.isEmpty()) return waehleZufaellig(zufallsgenerator, reihenenden);
        if (!nachbarfelder.isEmpty()) return waehleZufaellig(zufallsgenerator, nachbarfelder);

        BitSet schachbrett = new BitSet(GROESSE * GROESSE);
        for (int feld = moeglicheFelder.nextSetBit(0); feld >= 0; feld = moeglicheFelder.nextSetBit(feld + 1))
        {
            if ((feld / GROESSE + feld % GROESSE) % 2 == 0) schachbrett.set(feld);
        }
        return waehleZufaellig(zufallsgenerator, schachbrett.isEmpty() ? moeglicheFelder : schachbrett);
    }

    /**
     * Sammelt Kandidaten beidseits eines Treffers und markiert die Verlängerung einer Trefferreihe, wenn auf
     * der gegenüberliegenden Seite ein weiterer offener Treffer liegt.
     */
    private void fuegeNachbarfelderHinzu(BitSet moeglicheFelder, BitSet nachbarfelder, BitSet reihenenden, int davor, int danach)
    {
        if (davor >= 0 && moeglicheFelder.get(davor))
        {
            nachbarfelder.set(davor);
            if (danach >= 0 && offeneTreffer.get(danach)) reihenenden.set(davor);
        }
        if (danach >= 0 && moeglicheFelder.get(danach))
        {
            nachbarfelder.set(danach);
            if (davor >= 0 && offeneTreffer.get(davor)) reihenenden.set(danach);
        }
    }

    /**
     * Liefert alle Felder außer bekannten Treffern und erinnerten Wasserschüssen. Ein vergessener Wasserschuss
     * kann wieder als Ziel gewählt werden.
     */
    private BitSet moeglicheZielfelder()
    {
        BitSet moeglicheFelder = wasserschussHistorie.gemerkteWasserschussFelder();
        moeglicheFelder.or(treffer);
        moeglicheFelder.flip(0, GROESSE * GROESSE);
        return moeglicheFelder;
    }

    private static int waehleZufaellig(RandomGenerator zufallsgenerator, BitSet kandidaten)
    {
        int versatz = zufallsgenerator.nextInt(kandidaten.cardinality());
        int feld = kandidaten.nextSetBit(0);
        for (int i = 0; i < versatz; i++) feld = kandidaten.nextSetBit(feld + 1);
        return feld;
    }

    /**
     * Bewertet auf Basis der bekannten Lage versenkter Schiffe die Felder der verbleibenden
     * Flotte. Bei offenen Treffern zählen nur Positionen, die Teil einer widerspruchsfreien Zuordnung aller
     * offenen Treffer sein können.
     */
    private void bewerteVerbleibendeSchiffe(List<Schiffsplatzierung> versenkteSchiffe, BitSet nochOffeneTreffer, int[] zugeordneteTreffer, double[] bewertungen)
    {
        List<Zielkandidat> zielkandidaten = new ArrayList<>();
        for (Position position : positionen)
        {
            Schiffsplatzierung platzierung = position.platzierung();
            if (verbleibendeSchiffe[platzierung.laenge()] == 0 || wasserschussHistorie.enthaeltWasserschuss(position.felder())
                    || !istPlatzierungGueltig(platzierung, versenkteSchiffe)) continue;

            if (nochOffeneTreffer.isEmpty())
            {
                // Bevorzuge Felder mit möglichst vielen zulässigen Schiffspositionen.
                position.felder().stream().forEach(feld -> bewertungen[feld] += verbleibendeSchiffe[platzierung.laenge()]);
            }
            else if (position.felder().intersects(nochOffeneTreffer))
            {
                BitSet abgedeckt = (BitSet) position.felder().clone();
                abgedeckt.and(nochOffeneTreffer);
                // Ein vollständig getroffenes Schiff wäre bereits versenkt.
                if (abgedeckt.cardinality() < platzierung.laenge()) zielkandidaten.add(new Zielkandidat(platzierung, abgedeckt));
            }
        }

        List<Schiffsplatzierung> zugeordnet = new ArrayList<>();
        int[] verfuegbar = verbleibendeSchiffe.clone();
        for (Zielkandidat zielkandidat : zielkandidaten)
        {
            if (kannVerwendetWerden(zielkandidat, nochOffeneTreffer, zielkandidaten, zugeordnet, verfuegbar)) zielkandidat.bewerte(zugeordneteTreffer, bewertungen);
        }
    }

    /**
     * Prüft rekursiv, ob sich die offenen Treffer mit dem bisherigen Kandidaten, kompatiblen Positionen und den
     * verbleibenden Schiffslängen vollständig erklären lassen. Setzt die probeweise geänderten Zuordnungen und
     * Bestände vor der Rückkehr zurück.
     */
    private static boolean kannVerwendetWerden(Zielkandidat zielkandidat, BitSet nochOffeneTreffer, List<Zielkandidat> zielkandidaten,
                                               List<Schiffsplatzierung> zugeordnet, int[] verfuegbar)
    {
        Schiffsplatzierung platzierung = zielkandidat.platzierung();
        if (verfuegbar[platzierung.laenge()] == 0 || !istPlatzierungGueltig(platzierung, zugeordnet)) return false;
        BitSet weiterhinOffen = ohne(nochOffeneTreffer, zielkandidat.treffer());
        if (weiterhinOffen.isEmpty()) return true;

        zugeordnet.add(platzierung);
        verfuegbar[platzierung.laenge()]--;
        int naechsterTreffer = weiterhinOffen.nextSetBit(0);
        boolean moeglich = false;
        // Jeder offene Treffer muss zu einem passenden verbleibenden Schiff gehören.
        for (Zielkandidat naechsterKandidat : zielkandidaten)
        {
            if (naechsterKandidat.treffer().get(naechsterTreffer) && kannVerwendetWerden(naechsterKandidat, weiterhinOffen, zielkandidaten, zugeordnet, verfuegbar))
            {
                moeglich = true;
                break;
            }
        }
        verfuegbar[platzierung.laenge()]++;
        zugeordnet.removeLast();
        return moeglich;
    }

    private static boolean istPlatzierungGueltig(Schiffsplatzierung platzierung, List<Schiffsplatzierung> andereSchiffe)
    {
        return andereSchiffe.stream().allMatch(platzierung::istVertraeglichMit);
    }

    private static BitSet ohne(BitSet felder, BitSet ausgeschlosseneFelder)
    {
        BitSet ergebnis = (BitSet) felder.clone();
        ergebnis.andNot(ausgeschlosseneFelder);
        return ergebnis;
    }

    private static BitSet felderVon(Schiffsplatzierung platzierung)
    {
        BitSet felder = new BitSet(GROESSE * GROESSE);
        for (int versatz = 0; versatz < platzierung.laenge(); versatz++) felder.set(platzierung.feld(versatz));
        return felder;
    }

    /**
     * Hält eine mögliche Schiffsposition und die Menge ihrer belegten Felder.
     */
    private record Position(Schiffsplatzierung platzierung, BitSet felder)
    {
    }

    /**
     * Verknüpft eine mögliche Position eines verbliebenen Schiffs mit den offenen Treffern, die sie abdeckt.
     */
    private record Zielkandidat(Schiffsplatzierung platzierung, BitSet treffer)
    {
        /**
         * Bewertet freie Felder einer möglichen Schiffsposition vorrangig nach der Zahl der erklärten Treffer.
         * Bei gleicher Trefferzahl erhalten Felder nahe einem Treffer und fast vollständig getroffene Schiffe
         * mehr Gewicht.
         */
        void bewerte(int[] zugeordneteTreffer, double[] bewertungen)
        {
            int trefferzahl = treffer.cardinality();
            for (int versatz = 0; versatz < platzierung.laenge(); versatz++)
            {
                int feld = platzierung.feld(versatz);
                if (treffer.get(feld) || zugeordneteTreffer[feld] > trefferzahl) continue;
                if (zugeordneteTreffer[feld] < trefferzahl)
                {
                    zugeordneteTreffer[feld] = trefferzahl;
                    bewertungen[feld] = 0;
                }
                int abstand = platzierung.laenge();
                for (int nachbar = 0; nachbar < platzierung.laenge(); nachbar++)
                {
                    if (treffer.get(platzierung.feld(nachbar))) abstand = Math.min(abstand, Math.abs(versatz - nachbar));
                }
                // Bevorzuge fast versenkte Schiffe und Felder nahe bekannten Treffern.
                bewertungen[feld] += 1.0 / ((platzierung.laenge() - trefferzahl) * abstand);
            }
        }
    }
}
