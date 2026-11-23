package de.kj.games;

import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

import static de.kj.games.Game.Direction.HORIZONTAL;
import static de.kj.games.Game.Direction.VERTICAL;

public final class ComputerBenchmark {
    private static final int SHOT_LIMIT = 10_000;
    private static final List<ShipPlacement> FLEET = List.of(
            new ShipPlacement(11, HORIZONTAL, 5), // B2–B6
            new ShipPlacement(38, VERTICAL, 4),   // D9–G9
            new ShipPlacement(40, VERTICAL, 3),   // E1–G1
            new ShipPlacement(83, HORIZONTAL, 3), // I4–I6
            new ShipPlacement(8, HORIZONTAL, 2)); // A9–A10

    public static void main(String[] args) {
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 20261123L;
        if (games < 26 || games % 2 != 0) {
            throw new IllegalArgumentException("Use an even number of games of at least 26.");
        }

        System.out.printf("Games per matchup: %d, seed: %d%n", games, seed);
        System.out.println("Both fleets: B2-B6, D9-G9, E1-G1, I4-I6, A9-A10");
        compare(Difficulty.LEICHT, Difficulty.MITTEL, games, seed);
        compare(Difficulty.LEICHT, Difficulty.SCHWER, games, seed);
        compare(Difficulty.MITTEL, Difficulty.SCHWER, games, seed);
    }

    private static void compare(Difficulty first, Difficulty second, int games, long seed) {
        int firstWins = 0;
        int secondWins = 0;
        SplittableRandom random = new SplittableRandom(seed);
        for (int game = 0; game < games; game++) {
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
