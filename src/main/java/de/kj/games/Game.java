package de.kj.games;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;
import java.util.random.RandomGenerator;

import de.kj.games.GameStateJson.ReplayView;
import de.kj.games.GameStateJson.ShotView;
import de.kj.games.Nation.ShipType;
import de.kj.games.Spielfeld.Ergebnis;
import de.kj.games.Spielfeld.Schiffsposition;

/**
 * Verwaltet den Zustand einer Einzel- oder Mehrspielerpartie von der Flottenaufstellung bis zum Ergebnis.
 * Wendet die Spielregeln an und stellt den für den jeweiligen Spieler sichtbaren Zustand bereit.
 */
final class Game
{
    static final int SIZE = Spielfeld.GROESSE;
    private static final int CELLS = SIZE * SIZE;
    private final Difficulty difficulty;
    private final Player host;
    private final Player guest;
    private final boolean multiplayer;
    private final Zugsteuerung zugsteuerung;
    private ComputerTargeting computer;
    private boolean started;
    private boolean finished;
    private Seat winner;
    private Shot lastShot;
    private final List<ReplayHit> replayHits = new ArrayList<>();
    private long shotNumber;

    Game(Difficulty difficulty)
    {
        this(difficulty, "", null, 0);
    }

    Game(Difficulty difficulty, String hostName, String guestName, long now)
    {
        this.difficulty = difficulty;
        this.multiplayer = guestName != null;
        this.host = new Player(hostName, difficulty, Nation.DEUTSCHLAND);
        this.guest = new Player(guestName, difficulty, multiplayer ? Nation.DEUTSCHLAND : Nation.random());
        this.zugsteuerung = new Zugsteuerung(multiplayer, now);
    }

    boolean multiplayer()
    {
        return multiplayer;
    }

    private Player player(Seat seat)
    {
        return seat == Seat.HOST ? host : guest;
    }

    /**
     * Wechselt vor der Bereitschaftsmeldung die Nation eines Spielers. Ein Wechsel erstellt ein neues Spielfeld
     * für die gewählte Flotte und verwirft die bisherige Aufstellung.
     */
    boolean selectNation(Seat seat, Nation nation)
    {
        Player player = player(seat);
        if (started || player.ready) return false;
        if (player.nation != nation)
        {
            player.nation = nation;
            player.board = new Spielfeld(nation.fleet);
        }
        return true;
    }

    boolean placeShip(Seat seat, int ship, int cell, Direction direction)
    {
        Player player = player(seat);
        return !started && !player.ready && player.board.platziere(ship, cell, direction);
    }

    boolean removeShip(Seat seat, int ship)
    {
        Player player = player(seat);
        return !started && !player.ready && player.board.entferne(ship);
    }

    /**
     * Ergänzt die fehlenden Schiffe per Zufall oder ersetzt die gesamte Aufstellung. Übernimmt eine neue
     * Aufstellung nur, wenn die Flotte vollständig platziert werden kann.
     */
    boolean randomizePlacement(Seat seat, boolean replaceAll)
    {
        Player player = player(seat);
        if (started || player.ready) return false;
        Spielfeld board = replaceAll ? new Spielfeld(player.nation.fleet) : player.board;
        if (!board.stelleZufaelligAuf()) return false;
        player.board = board;
        return true;
    }

    /**
     * Meldet einen Spieler mit vollständiger Aufstellung als bereit. Im Mehrspielermodus beginnt das Gefecht
     * nach der Bereitschaftsmeldung beider Spieler, im Einzelspiel nach dem Aufstellen der Computerflotte.
     */
    boolean start(Seat seat, long now)
    {
        if (started) return true;
        Player player = player(seat);
        if (player.board.anzahlPlatzierterSchiffe() != player.nation.fleet.size()) return false;
        player.ready = true;
        if (multiplayer)
        {
            if (host.ready && guest.ready) beginBattle(now);
        }
        else
        {
            if (!guest.board.stelleZufaelligAuf()) throw new IllegalStateException("Cannot place computer fleet");
            computer = new ComputerTargeting(host.nation.fleet.stream().map(ShipType::length).toList(), difficulty);
            beginBattle(now);
        }
        return true;
    }

    /**
     * Startet das Gefecht und setzt die erste Zugfrist. Hat mindestens eine Seite kein Schiff platziert, endet
     * die Partie sofort.
     */
    private void beginBattle(long now)
    {
        started = true;
        zugsteuerung.beginneGefecht(now);
        boolean hostEmpty = host.board.anzahlPlatzierterSchiffe() == 0;
        boolean guestEmpty = guest.board.anzahlPlatzierterSchiffe() == 0;
        if (hostEmpty || guestEmpty)
        {
            finished = true;
            if (hostEmpty != guestEmpty) winner = hostEmpty ? Seat.GUEST : Seat.HOST;
        }
    }

    /**
     * Verarbeitet abgelaufene Aufstellungs- und Zugfristen im Mehrspielermodus. Überspringt bei Bedarf mehrere
     * abgelaufene Züge in einem Aufruf.
     */
    void advance(long now)
    {
        if (!multiplayer || finished) return;
        if (!started && zugsteuerung.fristAbgelaufen(now)) beginBattle(zugsteuerung.getFrist());
        if (started && !finished) zugsteuerung.ueberspringeAbgelaufeneZuege(now);
    }

    /**
     * Führt einen zulässigen Schuss aus und aktualisiert das Wasserschussgedächtnis. Im Mehrspielermodus muss
     * die übermittelte Zugnummer zum aktuellen Zug passen.
     */
    boolean playerShot(Seat seat, int cell, long expectedTurn, long now)
    {
        if (!started || finished || zugsteuerung.getAktiveSeite() != seat || cell < 0 || cell >= CELLS
                || (multiplayer && expectedTurn != zugsteuerung.getZugnummer())) return false;
        Shot shot = schiessen(seat, cell, now);
        player(seat).waterHistory.record(cell, shot.result() == Ergebnis.WATER, ThreadLocalRandom.current());
        return true;
    }

    /**
     * Führt den nächsten Computerschuss im Einzelspiel aus, sobald der Computer am Zug und seine Wartezeit
     * abgelaufen ist.
     */
    boolean computerShot(long now)
    {
        if (multiplayer || !started || finished || zugsteuerung.getAktiveSeite() != Seat.GUEST
                || computerWaitMillis(now) > 0) return false;
        computerschussAusfuehren(Seat.GUEST, computer, ThreadLocalRandom.current(), now);
        return true;
    }

    /**
     * Lässt die Zielsuche ein Feld wählen, wertet den Schuss aus und meldet das Ergebnis an die Zielsuche
     * zurück.
     */
    private void computerschussAusfuehren(Seat shooter, ComputerTargeting targeting, RandomGenerator random, long now)
    {
        Spielfeld target = player(shooter.other()).board;
        int cell = targeting.choose(random, target::istWasser);
        Shot shot = schiessen(shooter, cell, now);
        boolean hit = shot.result() != Ergebnis.WATER;
        Schiffsposition versenkt = shot.versenktesSchiff();
        targeting.observe(cell, hit, versenkt == null ? null : versenkt.platzierung(), random);
    }

    /**
     * Simuliert ein Duell mit identischen Flottenaufstellungen und unabhängig gewählten Schwierigkeitsstufen.
     * Gibt {@code null} zurück, wenn nach Erreichen des Schusslimits kein Sieger feststeht.
     */
    static Seat playComputerDuel(Difficulty hostLevel, Difficulty guestLevel, List<ShipPlacement> fleet,
                                 RandomGenerator random, int shotLimit)
    {
        Game game = new Game(hostLevel, hostLevel.label, guestLevel.label, 0);
        for (Seat seat : Seat.values())
        {
            List<ShipType> shipTypes = game.player(seat).nation.fleet;
            if (fleet.size() != shipTypes.size()) throw new IllegalArgumentException("Incomplete fleet");
            for (int index = 0; index < fleet.size(); index++)
            {
                ShipPlacement placement = fleet.get(index);
                if (placement.length() != shipTypes.get(index).length()
                        || !game.placeShip(seat, index, placement.start(), placement.direction()))
                {
                    throw new IllegalArgumentException("Invalid placement for ship " + index);
                }
            }
            game.start(seat, 0);
        }
        List<Integer> lengths = fleet.stream().map(ShipPlacement::length).toList();
        ComputerTargeting hostTargeting = new ComputerTargeting(lengths, hostLevel);
        ComputerTargeting guestTargeting = new ComputerTargeting(lengths, guestLevel);
        for (int shot = 0; shot < shotLimit && !game.finished; shot++)
        {
            Seat amZug = game.zugsteuerung.getAktiveSeite();
            game.computerschussAusfuehren(amZug, amZug == Seat.HOST ? hostTargeting : guestTargeting, random, 0);
        }
        return game.winner;
    }

    long computerWaitMillis(long now)
    {
        return !multiplayer && started && !finished && zugsteuerung.getAktiveSeite() == Seat.GUEST
                ? zugsteuerung.restzeitMillis(now) : 0;
    }

    /**
     * Wertet den Schuss aus und aktualisiert Zug, Frist und gegebenenfalls den Sieger. Erfasst neue Treffer und
     * Versenkungen für die spätere Wiedergabe.
     */
    private Shot schiessen(Seat shooter, int cell, long now)
    {
        Spielfeld target = player(shooter.other()).board;
        var wirkung = target.beschiessen(cell);
        Ergebnis result = wirkung.ergebnis();
        if (result == Ergebnis.SUNK
                && target.anzahlVersenkterSchiffe() == target.anzahlPlatzierterSchiffe())
        {
            winner = shooter;
            finished = true;
        }
        Player attacker = player(shooter);
        attacker.shotsFired++;
        if (result == Ergebnis.HIT || result == Ergebnis.SUNK)
        {
            attacker.hits++;
            replayHits.add(new ReplayHit(shooter.other(), cell, zugsteuerung.gefechtsdauerMillis(now),
                    result == Ergebnis.SUNK));
        }
        lastShot = new Shot(++shotNumber, shooter, cell, result, wirkung.versenktesSchiff());
        Seat naechsteSeite = result == Ergebnis.WATER ? shooter.other() : zugsteuerung.getAktiveSeite();
        zugsteuerung.aktualisiereNachSchuss(naechsteSeite, now);
        return lastShot;
    }

    /**
     * Stellt die sichtbaren Daten aus Sicht eines Spielers zusammen und übergibt sie zur JSON-Ausgabe.
     */
    String spielzustand(String id, Seat seat, long now)
    {
        Player viewer = player(seat);
        Player opponent = player(seat.other());
        var common = new GameStateJson.Common(id, difficulty.label, multiplayer ? "HUMAN" : "COMPUTER",
                viewer.name, multiplayer ? opponent.name : "Computer",
                multiplayer && !finished ? zugsteuerung.restzeitMillis(now) : null);
        if (!started)
        {
            return GameStateJson.aufstellung(common, new GameStateJson.Setup(viewer.nation.name(),
                    viewer.board.sichtbareFelder(true, cell -> true), viewer.ready, viewer.board.schiffeFuerAufstellung(),
                    viewer.board.platzierteFlotte()));
        }
        return GameStateJson.gefecht(common, gefechtsansicht(seat, viewer, opponent, now));
    }

    /**
     * Filtert Flotten, Wasserschüsse und Wiedergabedaten nach den Sichtbarkeitsregeln des Gefechts.
     */
    private GameStateJson.Battle gefechtsansicht(Seat seat, Player viewer, Player opponent, long now)
    {
        IntPredicate showWater = viewer.board::hatSchiffInDerNaehe;
        Spielfeld opponentBoard = opponent.board;
        boolean revealOpponent = finished && winner == seat.other();
        List<Schiffsposition> sunk = opponentBoard.versenkteFlotte();
        List<Schiffsposition> revealed = revealOpponent ? opponentBoard.verbleibendeFlotte() : List.of();
        int unknown = revealOpponent ? 0 : opponentBoard.anzahlPlatzierterSchiffe() - opponentBoard.anzahlVersenkterSchiffe();
        List<ReplayView> replay = finished ? replayHits.stream().map(hit -> hit.ansichtFuer(seat)).toList() : List.of();
        return new GameStateJson.Battle(viewer.nation.label, viewer.board.sichtbareFelder(true, showWater),
                opponentBoard.sichtbareFelder(revealOpponent, viewer.waterHistory::remembers),
                relativeSide(zugsteuerung.getAktiveSeite(), seat),
                winner == null ? null : relativeSide(winner, seat), finished,
                zugsteuerung.getZugnummer(), viewer.shotsFired, viewer.hits, opponent.shotsFired, opponent.hits,
                viewer.board.platzierteFlotte(), sunk, revealed, winner == null ? null : player(winner).nation.countryCode,
                unknown, computerWaitMillis(now), letzterSichtbarerSchuss(seat, showWater), replay);
    }

    private ShotView letzterSichtbarerSchuss(Seat viewer, IntPredicate showWater)
    {
        if (lastShot == null) return null;
        boolean showLocation = lastShot.shooter() == viewer || lastShot.result() != Ergebnis.WATER
                || showWater.test(lastShot.cell());
        Schiffsposition versenkt = lastShot.versenktesSchiff();
        return new ShotView(lastShot.number(), relativeSide(lastShot.shooter(), viewer),
                showLocation ? lastShot.cell() : null, lastShot.result().name(), versenkt == null ? null : versenkt.typ());
    }

    private String relativeSide(Seat seat, Seat viewer)
    {
        if (seat == viewer) return "PLAYER";
        return multiplayer ? "OPPONENT" : "COMPUTER";
    }

    /**
     * Unterscheidet die beiden Seiten einer Partie. Im Einzelspiel belegt der Computer den Gastplatz.
     */
    enum Seat
    {
        HOST,
        GUEST;

        Seat other()
        {
            return this == HOST ? GUEST : HOST;
        }
    }

    /**
     * Definiert die Ausrichtung eines Schiffs und den Schritt zwischen benachbarten Feldern im eindimensionalen
     * Spielfeld.
     */
    enum Direction
    {
        HORIZONTAL(1),
        VERTICAL(SIZE);

        final int step;

        Direction(int step)
        {
            this.step = step;
        }
    }

    /**
     * Erfasst einen ausgewerteten Schuss für die Spielansicht und die Zielsuche. Bei einer Versenkung enthält
     * er auch Typ und Lage des versenkten Schiffs.
     */
    private record Shot(long number, Seat shooter, int cell, Ergebnis result, Schiffsposition versenktesSchiff)
    {
    }

    /**
     * Speichert einen Treffer mit Zielfeld und Zeitpunkt seit Gefechtsbeginn für die Wiedergabe nach Spielende.
     */
    private record ReplayHit(Seat target, int cell, long timeMillis, boolean sunk)
    {
        ReplayView ansichtFuer(Seat viewer)
        {
            return new ReplayView(target == viewer, cell, timeMillis, sunk);
        }
    }

    /**
     * Hält den Zustand einer Spielseite, darunter Nation, Spielfeld, Bereitschaft und Schussstatistik.
     */
    private static final class Player
    {
        private final String name;
        private final WaterShotHistory waterHistory;
        private Nation nation;
        private Spielfeld board;
        private boolean ready;
        private int shotsFired;
        private int hits;

        private Player(String name, Difficulty difficulty, Nation nation)
        {
            this.name = name;
            this.nation = nation;
            this.board = new Spielfeld(nation.fleet);
            this.waterHistory = new WaterShotHistory(difficulty.playerWaterGuaranteed, difficulty.playerWaterForgetPercent);
        }
    }
}
