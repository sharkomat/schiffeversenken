package de.kj.games;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Liefert die Browseroberfläche und die zugehörigen Grafiken und Sounds über HTTP aus.
 */
final class WebRessourcenHandler implements HttpHandler
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
            String pfad = exchange.getRequestURI().getPath();
            String ressource = switch (pfad)
            {
                case "/" -> "index.html";
                case "/style.css" -> "style.css";
                case "/game.js" -> "game.js";
                case "/abspann.js" -> "abspann.js";
                default -> pfad.matches("/ships/[A-Za-z-]+\\.svg")
                        || pfad.matches("/sounds/(?:hit|water)(?:-[0-9]{2})?\\.wav")
                        || pfad.equals("/sounds/result-win.wav")
                        || pfad.matches("/sounds/sos/SOS-[0-9]{2}-[A-Za-z-]+\\.wav") ? pfad.substring(1) : null;
            };
            if (ressource == null || !exchange.getRequestMethod().equals("GET"))
            {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String inhaltstyp = switch (ressource)
            {
                case "index.html" -> "text/html";
                case "style.css" -> "text/css";
                case "game.js", "abspann.js" -> "text/javascript";
                default -> ressource.startsWith("sounds/") ? "audio/wav" : "image/svg+xml";
            };
            if (ressource.equals("sounds/result-win.wav"))
            {
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                byte[] lokaleMusik = lokaleSiegesmusik();
                if (lokaleMusik != null)
                {
                    sende(exchange, inhaltstyp, lokaleMusik);
                    return;
                }
            }
            try (var eingabe = WebRessourcenHandler.class.getResourceAsStream("/web/" + ressource))
            {
                if (eingabe == null)
                {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                sende(exchange, inhaltstyp, eingabe.readAllBytes());
            }
        }
    }

    /**
     * Liest die optionale Siegermusik aus dem lokalen Anwendungsdatenordner.
     */
    private static byte[] lokaleSiegesmusik()
    {
        String appData = System.getenv("LOCALAPPDATA");
        if (appData == null || appData.isBlank()) return null;
        Path datei = Path.of(appData, "Schiffeversenken", "result-win.wav");
        try
        {
            return Files.isReadable(datei) ? Files.readAllBytes(datei) : null;
        }
        catch (IOException nichtVerfuegbar)
        {
            return null;
        }
    }

    private static void sende(HttpExchange anfrage, String inhaltstyp, byte[] inhalt) throws IOException
    {
        anfrage.getResponseHeaders().set("Content-Type", inhaltstyp.startsWith("audio/") ? inhaltstyp : inhaltstyp + "; charset=utf-8");
        anfrage.sendResponseHeaders(200, inhalt.length);
        anfrage.getResponseBody().write(inhalt);
    }
}
