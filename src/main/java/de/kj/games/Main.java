package de.kj.games;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import de.kj.games.Game.Seat;

public final class Main {
    private final Map<String, Session> sessions = new HashMap<>();
    private final GameRequests gameRequests = new GameRequests();

    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        Main app = new Main();
        server.createContext("/api/", app::api);
        server.createContext("/", Main::page);
        server.start();
        System.out.println("Schiffeversenken läuft unter http://127.0.0.1:" + server.getAddress().getPort());
    }

    private synchronized void api(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String id = exchange.getRequestHeaders().getFirst("X-Game-Id");
            if (path.equals("/api/requests")) {
                requests(exchange, method);
                return;
            }
            if (path.equals("/api/requests/status") && method.equals("GET")) {
                Session session = sessions.get(id);
                if (session != null && session.game().multiplayer()) {
                    json(exchange, 200, session.state(id));
                } else {
                    String phase = gameRequests.waiting(id, System.nanoTime()) ? "WAITING" : "EXPIRED";
                    json(exchange, 200, "{\"phase\":\"" + phase + "\"}");
                }
                return;
            }
            if (path.matches("/api/requests/[0-9a-f-]{36}/join") && method.equals("POST")) {
                join(exchange, path.split("/")[3]);
                return;
            }
            Session session = sessions.get(id);

            if (path.equals("/api/setup") && method.equals("POST")) {
                String selection = body(exchange, 16);
                Difficulty difficulty;
                try {
                    difficulty = selection.isEmpty() ? Difficulty.MITTEL : Difficulty.valueOf(selection);
                } catch (IllegalArgumentException invalid) {
                    json(exchange, 400, "{\"error\":\"Unbekannte Schwierigkeitsstufe.\"}");
                    return;
                }
                if (session != null) sessions.remove(id);
                id = UUID.randomUUID().toString();
                session = new Session(new Game(difficulty), Seat.HOST);
                sessions.put(id, session);
                json(exchange, 201, session.state(id));
                return;
            }

            if (session == null) {
                json(exchange, 404, "{\"error\":\"Partie nicht gefunden.\"}");
                return;
            }

            Game game = session.game();
            Seat seat = session.seat();
            game.advance(System.nanoTime());
            if (path.equals("/api/game") && method.equals("GET")) {
                json(exchange, 200, session.state(id));
            } else if (path.equals("/api/nation") && method.equals("POST")) {
                String selection = body(exchange, 32);
                Nation nation;
                try {
                    nation = Nation.valueOf(selection);
                } catch (IllegalArgumentException invalid) {
                    json(exchange, 400, "{\"error\":\"Unbekannte Nation.\"}");
                    return;
                }
                game.advance(System.nanoTime());
                update(exchange, session, id, game.selectNation(seat, nation), "Die Platzierung ist abgeschlossen.");
            } else if (path.equals("/api/placement/random") && method.equals("POST")) {
                String mode = body(exchange, 16);
                if (!mode.equals("ALL") && !mode.equals("REMAINING")) {
                    json(exchange, 400, "{\"error\":\"Unbekannte Option für die Zufallsplatzierung.\"}");
                    return;
                }
                game.advance(System.nanoTime());
                update(exchange, session, id, game.randomizePlacement(seat, mode.equals("ALL")),
                        "Zufallsplatzierung nicht möglich. Entfernen Sie ein Schiff oder losen Sie die ganze Flotte neu aus.");
            } else if (path.equals("/api/placement") && method.equals("POST")) {
                String placement = body(exchange, 32);
                if (!placement.matches("[0-4] [0-9]{1,2} (HORIZONTAL|VERTICAL)")) {
                    json(exchange, 400, "{\"error\":\"Ungültige Platzierungsdaten.\"}");
                    return;
                }
                String[] parts = placement.split(" ");
                game.advance(System.nanoTime());
                boolean moved = game.placeShip(seat, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                        Game.Direction.valueOf(parts[2]));
                update(exchange, session, id, moved, "Dieses Schiff kann dort nicht platziert werden.");
            } else if (path.equals("/api/placement") && method.equals("DELETE")) {
                String ship = body(exchange, 2);
                if (!ship.matches("[0-4]")) {
                    json(exchange, 400, "{\"error\":\"Unbekanntes Schiff.\"}");
                    return;
                }
                game.advance(System.nanoTime());
                update(exchange, session, id, game.removeShip(seat, Integer.parseInt(ship)),
                        "Dieses Schiff kann nicht entfernt werden.");
            } else if (path.equals("/api/game") && method.equals("POST")) {
                long now = System.nanoTime();
                game.advance(now);
                update(exchange, session, id, game.start(seat, now), "Platzieren Sie zuerst alle fünf Schiffe.");
            } else if (path.equals("/api/shot") && method.equals("POST")) {
                String shot = body(exchange, 24);
                String format = game.multiplayer() ? "[0-9]{1,2} [0-9]{1,18}" : "[0-9]{1,2}";
                if (!shot.matches(format)) {
                    json(exchange, 400, "{\"error\":\"Ungültige Koordinate.\"}");
                } else {
                    String[] parts = shot.split(" ");
                    long expectedTurn = game.multiplayer() ? Long.parseLong(parts[1]) : 0;
                    long now = System.nanoTime();
                    game.advance(now);
                    update(exchange, session, id, game.playerShot(seat, Integer.parseInt(parts[0]), expectedTurn, now),
                            "Sie sind gerade nicht am Zug oder die Schusszeit ist abgelaufen.");
                }
            } else if (path.equals("/api/computer") && method.equals("POST")) {
                long now = System.nanoTime();
                if (game.computerShot(now)) {
                    json(exchange, 200, session.state(id));
                } else {
                    String message = game.computerWaitMillis(now) > 0
                            ? "Der Computer wartet noch auf seinen nächsten Schuss."
                            : "Der Computer ist gerade nicht am Zug.";
                    json(exchange, 409, "{\"error\":\"" + message + "\"}");
                }
            } else {
                json(exchange, 404, "{\"error\":\"Unbekannte Anfrage.\"}");
            }
        }
    }

    private void requests(HttpExchange exchange, String method) throws IOException {
        if (method.equals("GET")) {
            json(exchange, 200, gameRequests.list(System.nanoTime()));
            return;
        }
        if (!method.equals("POST")) {
            exchange.getResponseHeaders().set("Allow", "GET, POST");
            json(exchange, 405, "{\"error\":\"Unbekannte Anfrage.\"}");
            return;
        }
        String[] data = body(exchange, 512).split("\n", 2);
        String name = data.length == 2 ? data[1].strip() : "";
        if (!validName(name)) {
            json(exchange, 400, "{\"error\":\"Geben Sie einen gültigen Spielernamen mit höchstens 60 Zeichen ein.\"}");
            return;
        }
        Difficulty difficulty;
        try {
            difficulty = Difficulty.valueOf(data[0]);
        } catch (IllegalArgumentException invalid) {
            json(exchange, 400, "{\"error\":\"Unbekannte Schwierigkeitsstufe.\"}");
            return;
        }
        json(exchange, 201, gameRequests.create(name, difficulty, System.nanoTime()));
    }

    private void join(HttpExchange exchange, String id) throws IOException {
        String name = body(exchange, 512).strip();
        if (!validName(name)) {
            json(exchange, 400, "{\"error\":\"Geben Sie zuerst einen gültigen Spielernamen ein.\"}");
            return;
        }
        long now = System.nanoTime();
        GameRequests.Request invitation = gameRequests.claim(id, exchange.getRequestHeaders().getFirst("X-Game-Id"), now);
        if (invitation == null) {
            json(exchange, 409, "{\"error\":\"Diese Spielanfrage ist nicht mehr verfügbar.\"}");
            return;
        }
        Game game = new Game(invitation.difficulty(), invitation.name(), name, now);
        String guestId = UUID.randomUUID().toString();
        sessions.put(invitation.token(), new Session(game, Seat.HOST));
        Session guest = new Session(game, Seat.GUEST);
        sessions.put(guestId, guest);
        json(exchange, 201, guest.state(guestId));
    }

    private static boolean validName(String name) {
        return !name.isEmpty() && name.length() <= 60 && name.chars().noneMatch(Character::isISOControl);
    }

    private record Session(Game game, Seat seat) {
        private String state(String id) {
            long now = System.nanoTime();
            game.advance(now);
            return game.state(id, seat, now);
        }
    }

    private static String body(HttpExchange exchange, int limit) throws IOException {
        return new String(exchange.getRequestBody().readNBytes(limit), StandardCharsets.UTF_8);
    }

    private static void update(HttpExchange exchange, Session session, String id, boolean success, String error)
            throws IOException {
        if (success) {
            json(exchange, 200, session.state(id));
        } else {
            json(exchange, 409, "{\"error\":\"" + error + "\"}");
        }
    }

    private static void page(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String resource = switch (path) {
                case "/" -> "index.html";
                case "/style.css" -> "style.css";
                case "/game.js" -> "game.js";
                default -> path.matches("/ships/[A-Za-z-]+\\.svg")
                        || path.matches("/sounds/(?:hit|water)(?:-[0-9]{2})?\\.wav") ? path.substring(1) : null;
            };
            if (resource == null || !exchange.getRequestMethod().equals("GET")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String type = switch (resource) {
                case "index.html" -> "text/html";
                case "style.css" -> "text/css";
                case "game.js" -> "text/javascript";
                default -> resource.startsWith("sounds/") ? "audio/wav" : "image/svg+xml";
            };
            try (var input = Main.class.getResourceAsStream("/web/" + resource)) {
                if (input == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                send(exchange, 200, type, input.readAllBytes());
            }
        }
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        send(exchange, status, "application/json", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type.startsWith("audio/") ? type : type + "; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}
