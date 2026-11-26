package de.kj.games;

import java.util.concurrent.TimeUnit;

import de.kj.games.Spiel.Spielseite;

/**
 * Verwaltet die aktive Spielseite, die Zugnummer und die Fristen ab Beginn der Aufstellung.
 * Alle Zeitpunkte stammen aus derselben monotonen Zeitquelle und werden in Nanosekunden übergeben.
 */
final class Zugsteuerung
{
    private static final long AUFSTELLUNGSZEIT = TimeUnit.SECONDS.toNanos(60);
    private static final long ZUGZEIT = TimeUnit.SECONDS.toNanos(12);
    private static final long COMPUTERPAUSE = TimeUnit.MILLISECONDS.toNanos(1_500);
    private static final long NANOS_PRO_MILLISEKUNDE = TimeUnit.MILLISECONDS.toNanos(1);

    private final long schussintervall;
    private Spielseite aktiveSeite = Spielseite.HOST;
    private long zugnummer;
    private long frist;
    private long gefechtBegonnenUm;

    Zugsteuerung(boolean mehrspieler, long jetzt)
    {
        schussintervall = mehrspieler ? ZUGZEIT : COMPUTERPAUSE;
        frist = jetzt + AUFSTELLUNGSZEIT;
    }

    Spielseite getAktiveSeite()
    {
        return aktiveSeite;
    }

    long getZugnummer()
    {
        return zugnummer;
    }

    long getFrist()
    {
        return frist;
    }

    boolean fristAbgelaufen(long jetzt)
    {
        return jetzt - frist >= 0;
    }

    void beginneGefecht(long zeitpunkt)
    {
        gefechtBegonnenUm = zeitpunkt;
        zugnummer = 1;
        frist = zeitpunkt + ZUGZEIT;
    }

    /**
     * Holt alle abgelaufenen Gefechtszüge nach. Die neue Frist richtet sich nach dem bisherigen Zugtakt.
     */
    void ueberspringeAbgelaufeneZuege(long jetzt)
    {
        if (!fristAbgelaufen(jetzt)) return;
        long abgelaufen = 1 + (jetzt - frist) / ZUGZEIT;
        if (abgelaufen % 2 == 1) aktiveSeite = aktiveSeite.andereSeite();
        zugnummer += abgelaufen;
        frist += abgelaufen * ZUGZEIT;
    }

    /**
     * Übernimmt die nächste Spielseite, erhöht die Zugnummer und setzt die Frist für den nächsten Schuss.
     */
    void aktualisiereNachSchuss(Spielseite naechsteSeite, long jetzt)
    {
        aktiveSeite = naechsteSeite;
        zugnummer++;
        frist = jetzt + schussintervall;
    }

    long restzeitMillis(long jetzt)
    {
        return Math.max(0, Math.ceilDiv(frist - jetzt, NANOS_PRO_MILLISEKUNDE));
    }

    long gefechtsdauerMillis(long jetzt)
    {
        return TimeUnit.NANOSECONDS.toMillis(jetzt - gefechtBegonnenUm);
    }
}
