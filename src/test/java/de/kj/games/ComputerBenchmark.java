package de.kj.games;

import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

import static de.kj.games.Game.Direction.HORIZONTAL;
import static de.kj.games.Game.Direction.VERTICAL;

/**
 * Vergleicht die Schwierigkeitsstufen in simulierten Duellen mit identischen Flottenaufstellungen. Gibt Siege,
 * Siegquoten und die Anzahl der Partien ohne Ergebnis aus.
 */
public final class ComputerBenchmark
{
    private static final int SHOT_LIMIT = 10_000;
    private static final List<ShipPlacement> FLEET = List.of(
            new ShipPlacement(11, HORIZONTAL, 5), // B2–B6
            new ShipPlacement(38, VERTICAL, 4),   // D9–G9
            new ShipPlacement(40, VERTICAL, 3),   // E1–G1
            new ShipPlacement(83, HORIZONTAL, 3), // I4–I6
            new ShipPlacement(8, HORIZONTAL, 2)); // A9–A10

    /**
     * Startet den Vergleich für jedes Paar von Schwierigkeitsstufen. Optional können die Anzahl der Partien pro
     * Paar und der Startwert für den Zufallsgenerator angegeben werden.
     * @param args optionale gerade Partienzahl ab 26 und optionaler Startwert
     */
    public static void main(String[] args)
    {
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 20261123L;
        if (games < 26 || games % 2 != 0)
        {
            throw new IllegalArgumentException("Use an even number of games of at least 26.");
        }

        System.out.printf("Games per matchup: %d, seed: %d%n", games, seed);
        System.out.println("Both fleets: B2-B6, D9-G9, E1-G1, I4-I6, A9-A10");
        Difficulty[] levels = Difficulty.values();
        for (int first = 0; first < levels.length; first++)
        {
            for (int second = first + 1; second < levels.length; second++)
            {
                vergleiche(levels[first], levels[second], games, seed);
            }
        }
    }

    /**
     * Simuliert die Duelle zweier Stufen mit wechselndem Startrecht und einem festen Startwert für den
     * Zufallsgenerator. Gibt die Siege beider Stufen sowie die Anzahl der bis zum Schusslimit nicht
     * abgeschlossenen Duelle aus.
     */
    private static void vergleiche(Difficulty first, Difficulty second, int games, long seed)
    {
        int firstWins = 0;
        int secondWins = 0;
        SplittableRandom random = new SplittableRandom(seed);
        for (int game = 0; game < games; game++)
        {
            boolean firstStarts = game % 2 == 0;
            Difficulty host = firstStarts ? first : second;
            Difficulty guest = firstStarts ? second : first;
            Game.Seat winner = Game.playComputerDuel(host, guest, FLEET, random.split(), SHOT_LIMIT);
            if (winner == null) continue;
            if ((winner == Game.Seat.HOST) == firstStarts) firstWins++;
            else secondWins++;
        }
        System.out.printf(Locale.ROOT, "%s vs. %s: %d (%.1f%%) - %d (%.1f%%), unfinished: %d%n",
                first.label, second.label, firstWins, 100.0 * firstWins / games,
                secondWins, 100.0 * secondWins / games, games - firstWins - secondWins);
    }
}
