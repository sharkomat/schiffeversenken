package de.kj.games;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Liefert die Browseroberfläche und die zugehörigen Grafiken und Sounds über HTTP aus.
 */
final class WebResourceHandler implements HttpHandler
{
    /**
     * Liefert freigegebene Webressourcen mit dem passenden Inhaltstyp aus. Für die Siegermusik hat eine lesbare
     * lokale Datei Vorrang vor der mitgelieferten Ressource.
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException
    {
        try (exchange)
        {
            String path = exchange.getRequestURI().getPath();
            String resource = switch (path)
            {
                case "/" -> "index.html";
                case "/style.css" -> "style.css";
                case "/game.js" -> "game.js";
                case "/abspann.js" -> "abspann.js";
                default -> path.matches("/ships/[A-Za-z-]+\\.svg")
                        || path.matches("/sounds/(?:hit|water)(?:-[0-9]{2})?\\.wav")
                        || path.equals("/sounds/result-win.wav")
                        || path.matches("/sounds/sos/SOS-[0-9]{2}-[A-Za-z-]+\\.wav") ? path.substring(1) : null;
            };
            if (resource == null || !exchange.getRequestMethod().equals("GET"))
            {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String type = switch (resource)
            {
                case "index.html" -> "text/html";
                case "style.css" -> "text/css";
                case "game.js", "abspann.js" -> "text/javascript";
                default -> resource.startsWith("sounds/") ? "audio/wav" : "image/svg+xml";
            };
            if (resource.equals("sounds/result-win.wav"))
            {
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                byte[] localMusic = localVictoryMusic();
                if (localMusic != null)
                {
                    send(exchange, type, localMusic);
                    return;
                }
            }
            try (var input = WebResourceHandler.class.getResourceAsStream("/web/" + resource))
            {
                if (input == null)
                {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                send(exchange, type, input.readAllBytes());
            }
        }
    }

    /**
     * Liest die optionale Siegermusik aus dem lokalen Anwendungsdatenordner.
     */
    private static byte[] localVictoryMusic()
    {
        String appData = System.getenv("LOCALAPPDATA");
        if (appData == null || appData.isBlank()) return null;
        Path file = Path.of(appData, "Schiffeversenken", "result-win.wav");
        try
        {
            return Files.isReadable(file) ? Files.readAllBytes(file) : null;
        }
        catch (IOException unavailable)
        {
            return null;
        }
    }

    private static void send(HttpExchange exchange, String type, byte[] body) throws IOException
    {
        exchange.getResponseHeaders().set("Content-Type", type.startsWith("audio/") ? type : type + "; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
    }
}
