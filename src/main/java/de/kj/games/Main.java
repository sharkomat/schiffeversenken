package de.kj.games;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Main {
    private final Map<String, Game> games = new HashMap<>();

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
            Game game = id == null ? null : games.get(id);

            if (path.equals("/api/setup") && method.equals("POST")) {
                String selection = body(exchange, 16);
                Difficulty difficulty;
                try {
                    difficulty = selection.isEmpty() ? Difficulty.MITTEL : Difficulty.valueOf(selection);
                } catch (IllegalArgumentException invalid) {
                    json(exchange, 400, "{\"error\":\"Unbekannte Schwierigkeitsstufe.\"}");
                    return;
                }
                if (game != null) games.remove(id);
                id = UUID.randomUUID().toString();
                game = new Game(difficulty);
                games.put(id, game);
                json(exchange, 201, game.state(id));
                return;
            }

            if (game == null) {
                json(exchange, 404, "{\"error\":\"Partie nicht gefunden.\"}");
                return;
            }

            if (path.equals("/api/game") && method.equals("GET")) {
                json(exchange, 200, game.state(id));
            } else if (path.equals("/api/nation") && method.equals("POST")) {
                String selection = body(exchange, 32);
                Nation nation;
                try {
                    nation = Nation.valueOf(selection);
                } catch (IllegalArgumentException invalid) {
                    json(exchange, 400, "{\"error\":\"Unbekannte Nation.\"}");
                    return;
                }
                update(exchange, game, id, game.selectNation(nation), "Die Partie läuft bereits.");
            } else if (path.equals("/api/placement") && method.equals("POST")) {
                String placement = body(exchange, 32);
                if (!placement.matches("[0-4] [0-9]{1,2} (HORIZONTAL|VERTICAL)")) {
                    json(exchange, 400, "{\"error\":\"Ungültige Platzierungsdaten.\"}");
                    return;
                }
                String[] parts = placement.split(" ");
                boolean moved = game.placeShip(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                        Game.Direction.valueOf(parts[2]));
                update(exchange, game, id, moved, "Dieses Schiff kann dort nicht platziert werden.");
            } else if (path.equals("/api/placement") && method.equals("DELETE")) {
                String ship = body(exchange, 2);
                if (!ship.matches("[0-4]")) {
                    json(exchange, 400, "{\"error\":\"Unbekanntes Schiff.\"}");
                    return;
                }
                update(exchange, game, id, game.removeShip(Integer.parseInt(ship)),
                        "Dieses Schiff kann nicht entfernt werden.");
            } else if (path.equals("/api/game") && method.equals("POST")) {
                update(exchange, game, id, game.start(), "Platzieren Sie zuerst alle fünf Schiffe.");
            } else if (path.equals("/api/shot") && method.equals("POST")) {
                String cell = body(exchange, 3);
                if (!cell.matches("[0-9]{1,2}")) {
                    json(exchange, 400, "{\"error\":\"Ungültige Koordinate.\"}");
                } else if (game.playerShot(Integer.parseInt(cell))) {
                    json(exchange, 200, game.state(id));
                } else {
                    json(exchange, 409, "{\"error\":\"Sie sind gerade nicht am Zug.\"}");
                }
            } else if (path.equals("/api/computer") && method.equals("POST")) {
                if (game.computerShot()) {
                    json(exchange, 200, game.state(id));
                } else {
                    String message = game.computerWaitMillis() > 0
                            ? "Der Computer wartet noch auf seinen nächsten Schuss."
                            : "Der Computer ist gerade nicht am Zug.";
                    json(exchange, 409, "{\"error\":\"" + message + "\"}");
                }
            } else {
                json(exchange, 404, "{\"error\":\"Unbekannte Anfrage.\"}");
            }
        }
    }

    private static String body(HttpExchange exchange, int limit) throws IOException {
        return new String(exchange.getRequestBody().readNBytes(limit), StandardCharsets.UTF_8);
    }

    private static void update(HttpExchange exchange, Game game, String id, boolean success, String error)
            throws IOException {
        if (success) {
            json(exchange, 200, game.state(id));
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
                default -> path.matches("/ships/[A-Za-z-]+\\.svg") ? path.substring(1) : null;
            };
            if (resource == null || !exchange.getRequestMethod().equals("GET")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String type = switch (resource) {
                case "index.html" -> "text/html";
                case "style.css" -> "text/css";
                case "game.js" -> "text/javascript";
                default -> "image/svg+xml";
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
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}
