package de.kj.games;

import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

import static de.kj.games.Spiel.Ausrichtung.HORIZONTAL;
import static de.kj.games.Spiel.Ausrichtung.VERTICAL;

/**
 * Vergleicht die Schwierigkeitsstufen in simulierten Duellen mit identischen Flottenaufstellungen. Gibt Siege,
 * Siegquoten und die Anzahl der Partien ohne Ergebnis aus.
 */
public final class ComputerLeistungsvergleich
{
    private static final int SCHUSSLIMIT = 10_000;
    private static final List<Schiffsplatzierung> FLOTTE = List.of(
            new Schiffsplatzierung(11, HORIZONTAL, 5), // B2–B6
            new Schiffsplatzierung(38, VERTICAL, 4),   // D9–G9
            new Schiffsplatzierung(40, VERTICAL, 3),   // E1–G1
            new Schiffsplatzierung(83, HORIZONTAL, 3), // I4–I6
            new Schiffsplatzierung(8, HORIZONTAL, 2)); // A9–A10

    /**
     * Startet den Vergleich für jedes Paar von Schwierigkeitsstufen. Optional können die Anzahl der Partien pro
     * Paar und der Startwert für den Zufallsgenerator angegeben werden.
     * @param args optionale gerade Partienzahl ab 26 und optionaler Startwert
     */
    public static void main(String[] args)
    {
        int partien = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        long startwert = args.length > 1 ? Long.parseLong(args[1]) : 20261123L;
        if (partien < 26 || partien % 2 != 0)
        {
            throw new IllegalArgumentException("Use an even number of games of at least 26.");
        }

        System.out.printf("Games per matchup: %d, seed: %d%n", partien, startwert);
        System.out.println("Both fleets: B2-B6, D9-G9, E1-G1, I4-I6, A9-A10");
        Schwierigkeitsgrad[] stufen = Schwierigkeitsgrad.values();
        for (int erste = 0; erste < stufen.length; erste++)
        {
            for (int zweite = erste + 1; zweite < stufen.length; zweite++)
            {
                vergleiche(stufen[erste], stufen[zweite], partien, startwert);
            }
        }
    }

    /**
     * Simuliert die Duelle zweier Stufen mit wechselndem Startrecht und einem festen Startwert für den
     * Zufallsgenerator. Gibt die Siege beider Stufen sowie die Anzahl der bis zum Schusslimit nicht
     * abgeschlossenen Duelle aus.
     */
    private static void vergleiche(Schwierigkeitsgrad erste, Schwierigkeitsgrad zweite, int partien, long startwert)
    {
        int siegeErste = 0;
        int siegeZweite = 0;
        SplittableRandom zufallsgenerator = new SplittableRandom(startwert);
        for (int partie = 0; partie < partien; partie++)
        {
            boolean ersteBeginnt = partie % 2 == 0;
            Schwierigkeitsgrad gastgeber = ersteBeginnt ? erste : zweite;
            Schwierigkeitsgrad gast = ersteBeginnt ? zweite : erste;
            Spiel.Spielseite sieger = Spiel.spieleComputerduell(gastgeber, gast, FLOTTE, zufallsgenerator.split(), SCHUSSLIMIT);
            if (sieger == null) continue;
            if ((sieger == Spiel.Spielseite.HOST) == ersteBeginnt) siegeErste++;
            else siegeZweite++;
        }
        System.out.printf(Locale.ROOT, "%s vs. %s: %d (%.1f%%) - %d (%.1f%%), unfinished: %d%n",
                erste.bezeichnung, zweite.bezeichnung, siegeErste, 100.0 * siegeErste / partien,
                siegeZweite, 100.0 * siegeZweite / partien, partien - siegeErste - siegeZweite);
    }
}
