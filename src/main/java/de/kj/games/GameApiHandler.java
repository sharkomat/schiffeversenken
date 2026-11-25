package de.kj.games;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import de.kj.games.Game.Seat;

/**
 * Verarbeitet die HTTP-Spiel-API für Einzelspieler und Mehrspieler. Verwaltet Sitzungen und Lobby-Einladungen.
 */
final class GameApiHandler implements HttpHandler
{
    private final Map<String, Session> sessions = new HashMap<>();
    private final GameRequests gameRequests = new GameRequests();
    private final LongSupplier nanoTime;

    GameApiHandler(LongSupplier nanoTime)
    {
        this.nanoTime = nanoTime;
    }

    /**
     * Ordnet eine Anfrage anhand von Pfad, HTTP-Methode und Sitzungs-ID einer Partie oder der Lobby zu. Der
     * synchronisierte Aufruf führt Spielaktionen und Sitzungsänderungen nacheinander aus.
     */
    @Override
    public synchronized void handle(HttpExchange exchange) throws IOException
    {
        try (exchange)
        {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String id = exchange.getRequestHeaders().getFirst("X-Game-Id");
            if (path.equals("/api/requests"))
            {
                requests(exchange, method);
                return;
            }
            if (path.equals("/api/requests/status") && method.equals("GET"))
            {
                requestStatus(exchange, id);
                return;
            }
            if (path.matches("/api/requests/[0-9a-f-]{36}/join") && method.equals("POST"))
            {
                join(exchange, path.split("/")[3]);
                return;
            }
            if (path.equals("/api/setup") && method.equals("POST"))
            {
                setup(exchange, id);
                return;
            }

            Session session = sessions.get(id);
            if (session == null)
            {
                json(exchange, 404, "{\"error\":\"Partie nicht gefunden.\"}");
                return;
            }
            handleGameAction(exchange, method + " " + path, session, id);
        }
    }

    private void requestStatus(HttpExchange exchange, String id) throws IOException
    {
        Session session = sessions.get(id);
        if (session != null && session.game().multiplayer())
        {
            json(exchange, 200, session.state(id, nanoTime.getAsLong()));
        }
        else
        {
            String phase = gameRequests.waiting(id, nanoTime.getAsLong()) ? "WAITING" : "EXPIRED";
            json(exchange, 200, "{\"phase\":\"" + phase + "\"}");
        }
    }

    /**
     * Erstellt eine Einzelspielerpartie nach Prüfung der Schwierigkeit und ersetzt bei Erfolg eine alte Sitzung.
     */
    private void setup(HttpExchange exchange, String previousId) throws IOException
    {
        String selection = body(exchange, 16);
        Difficulty difficulty;
        try
        {
            difficulty = selection.isEmpty() ? Difficulty.MITTEL : Difficulty.valueOf(selection);
        }
        catch (IllegalArgumentException invalid)
        {
            json(exchange, 400, "{\"error\":\"Unbekannte Schwierigkeitsstufe.\"}");
            return;
        }
        sessions.remove(previousId);
        String id = UUID.randomUUID().toString();
        Session session = new Session(new Game(difficulty), Seat.HOST);
        sessions.put(id, session);
        json(exchange, 201, session.state(id, nanoTime.getAsLong()));
    }

    /**
     * Verarbeitet abgelaufene Fristen der zugeordneten Partie und leitet die Anfrage an die passende Spielaktion weiter.
     */
    private void handleGameAction(HttpExchange exchange, String route, Session session, String id)
            throws IOException
    {
        session.game().advance(nanoTime.getAsLong());
        switch (route)
        {
            case "GET /api/game" -> json(exchange, 200, session.state(id, nanoTime.getAsLong()));
            case "POST /api/nation" -> selectNation(exchange, session, id);
            case "POST /api/placement/random" -> randomizePlacement(exchange, session, id);
            case "POST /api/placement" -> placeShip(exchange, session, id);
            case "DELETE /api/placement" -> removeShip(exchange, session, id);
            case "POST /api/game" -> startGame(exchange, session, id);
            case "POST /api/shot" -> playerShot(exchange, session, id);
            case "POST /api/computer" -> computerShot(exchange, session, id);
            default -> json(exchange, 404, "{\"error\":\"Unbekannte Anfrage.\"}");
        }
    }

    private void selectNation(HttpExchange exchange, Session session, String id) throws IOException
    {
        String selection = body(exchange, 32);
        Nation nation;
        try
        {
            nation = Nation.valueOf(selection);
        }
        catch (IllegalArgumentException invalid)
        {
            json(exchange, 400, "{\"error\":\"Unbekannte Nation.\"}");
            return;
        }
        Game game = session.game();
        game.advance(nanoTime.getAsLong());
        update(exchange, session, id, game.selectNation(session.seat(), nation), "Die Platzierung ist abgeschlossen.");
    }

    private void randomizePlacement(HttpExchange exchange, Session session, String id) throws IOException
    {
        String mode = body(exchange, 16);
        if (!mode.equals("ALL") && !mode.equals("REMAINING"))
        {
            json(exchange, 400, "{\"error\":\"Unbekannte Option für die Zufallsplatzierung.\"}");
            return;
        }
        Game game = session.game();
        game.advance(nanoTime.getAsLong());
        update(exchange, session, id, game.randomizePlacement(session.seat(), mode.equals("ALL")),
                "Zufallsplatzierung nicht möglich. Entfernen Sie ein Schiff oder losen Sie die ganze Flotte neu aus.");
    }

    /**
     * Liest Schiff, Startfeld und Ausrichtung aus der Anfrage und prüft die Platzierung nach Aktualisierung der Frist.
     */
    private void placeShip(HttpExchange exchange, Session session, String id) throws IOException
    {
        String placement = body(exchange, 32);
        if (!placement.matches("[0-4] [0-9]{1,2} (HORIZONTAL|VERTICAL)"))
        {
            json(exchange, 400, "{\"error\":\"Ungültige Platzierungsdaten.\"}");
            return;
        }
        String[] parts = placement.split(" ");
        Game game = session.game();
        game.advance(nanoTime.getAsLong());
        boolean placed = game.placeShip(session.seat(), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                Game.Direction.valueOf(parts[2]));
        update(exchange, session, id, placed, "Dieses Schiff kann dort nicht platziert werden.");
    }

    private void removeShip(HttpExchange exchange, Session session, String id) throws IOException
    {
        String ship = body(exchange, 2);
        if (!ship.matches("[0-4]"))
        {
            json(exchange, 400, "{\"error\":\"Unbekanntes Schiff.\"}");
            return;
        }
        Game game = session.game();
        game.advance(nanoTime.getAsLong());
        update(exchange, session, id, game.removeShip(session.seat(), Integer.parseInt(ship)),
                "Dieses Schiff kann nicht entfernt werden.");
    }

    private void startGame(HttpExchange exchange, Session session, String id) throws IOException
    {
        long now = nanoTime.getAsLong();
        Game game = session.game();
        game.advance(now);
        update(exchange, session, id, game.start(session.seat(), now), "Platzieren Sie zuerst alle fünf Schiffe.");
    }

    /**
     * Liest das Zielfeld und im Mehrspielermodus die erwartete Zugnummer. Aktualisiert die Frist vor dem Schuss.
     */
    private void playerShot(HttpExchange exchange, Session session, String id) throws IOException
    {
        Game game = session.game();
        String shot = body(exchange, 24);
        String format = game.multiplayer() ? "[0-9]{1,2} [0-9]{1,18}" : "[0-9]{1,2}";
        if (!shot.matches(format))
        {
            json(exchange, 400, "{\"error\":\"Ungültige Koordinate.\"}");
            return;
        }
        String[] parts = shot.split(" ");
        long expectedTurn = game.multiplayer() ? Long.parseLong(parts[1]) : 0;
        long now = nanoTime.getAsLong();
        game.advance(now);
        update(exchange, session, id, game.playerShot(session.seat(), Integer.parseInt(parts[0]), expectedTurn, now),
                "Sie sind gerade nicht am Zug oder die Schusszeit ist abgelaufen.");
    }

    /**
     * Fordert einen Computerschuss an und unterscheidet bei Ablehnung zwischen Wartezeit und falschem Zug.
     */
    private void computerShot(HttpExchange exchange, Session session, String id) throws IOException
    {
        long now = nanoTime.getAsLong();
        Game game = session.game();
        if (game.computerShot(now))
        {
            json(exchange, 200, session.state(id, nanoTime.getAsLong()));
        }
        else
        {
            String message = game.computerWaitMillis(now) > 0
                    ? "Der Computer wartet noch auf seinen nächsten Schuss."
                    : "Der Computer ist gerade nicht am Zug.";
            json(exchange, 409, "{\"error\":\"" + message + "\"}");
        }
    }

    /**
     * Liefert offene Lobby-Einladungen oder legt nach Prüfung von Spielername und Schwierigkeitsgrad eine neue
     * Einladung an.
     */
    private void requests(HttpExchange exchange, String method) throws IOException
    {
        if (method.equals("GET"))
        {
            json(exchange, 200, gameRequests.list(nanoTime.getAsLong()));
            return;
        }
        if (!method.equals("POST"))
        {
            exchange.getResponseHeaders().set("Allow", "GET, POST");
            json(exchange, 405, "{\"error\":\"Unbekannte Anfrage.\"}");
            return;
        }
        String[] data = body(exchange, 512).split("\n", 2);
        String name = data.length == 2 ? data[1].strip() : "";
        if (!validName(name))
        {
            json(exchange, 400, "{\"error\":\"Geben Sie einen gültigen Spielernamen mit höchstens 60 Zeichen ein.\"}");
            return;
        }
        Difficulty difficulty;
        try
        {
            difficulty = Difficulty.valueOf(data[0]);
        }
        catch (IllegalArgumentException invalid)
        {
            json(exchange, 400, "{\"error\":\"Unbekannte Schwierigkeitsstufe.\"}");
            return;
        }
        json(exchange, 201, gameRequests.create(name, difficulty, nanoTime.getAsLong()));
    }

    /**
     * Löst eine offene Einladung ein und erstellt eine gemeinsame Partie mit getrennten Sitzungen für Gastgeber
     * und Gast.
     */
    private void join(HttpExchange exchange, String id) throws IOException
    {
        String name = body(exchange, 512).strip();
        if (!validName(name))
        {
            json(exchange, 400, "{\"error\":\"Geben Sie zuerst einen gültigen Spielernamen ein.\"}");
            return;
        }
        long now = nanoTime.getAsLong();
        GameRequests.Request invitation = gameRequests.claim(id, exchange.getRequestHeaders().getFirst("X-Game-Id"), now);
        if (invitation == null)
        {
            json(exchange, 409, "{\"error\":\"Diese Spielanfrage ist nicht mehr verfügbar.\"}");
            return;
        }
        Game game = new Game(invitation.difficulty(), invitation.name(), name, now);
        String guestId = UUID.randomUUID().toString();
        sessions.put(invitation.token(), new Session(game, Seat.HOST));
        Session guest = new Session(game, Seat.GUEST);
        sessions.put(guestId, guest);
        json(exchange, 201, guest.state(guestId, nanoTime.getAsLong()));
    }

    private static boolean validName(String name)
    {
        return !name.isEmpty() && name.length() <= 60 && name.chars().noneMatch(Character::isISOControl);
    }

    /**
     * Verknüpft eine HTTP-Sitzung mit einer Partie und dem Sitz des zugehörigen Spielers.
     */
    private record Session(Game game, Seat seat)
    {
        /**
         * Verarbeitet abgelaufene Fristen und erzeugt den Spielzustand aus Sicht dieser Sitzung.
         */
        private String state(String id, long now)
        {
            game.advance(now);
            return game.spielzustand(id, seat, now);
        }
    }

    private static String body(HttpExchange exchange, int limit) throws IOException
    {
        return new String(exchange.getRequestBody().readNBytes(limit), StandardCharsets.UTF_8);
    }

    private void update(HttpExchange exchange, Session session, String id, boolean success, String error)
            throws IOException
    {
        if (success)
        {
            json(exchange, 200, session.state(id, nanoTime.getAsLong()));
        }
        else
        {
            json(exchange, 409, "{\"error\":\"" + error + "\"}");
        }
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException
    {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
