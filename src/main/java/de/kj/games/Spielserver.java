package de.kj.games;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.function.LongSupplier;

/**
 * Startet den lokalen HTTP-Server für Schiffeversenken und registriert die Handler für Spiel-API und Browserdateien.
 */
public final class Spielserver
{
    private Spielserver()
    {
    }

    /**
     * Startet den Server auf der Loopback-Adresse. Das erste Argument kann den Standardport 8080 überschreiben.
     *
     * @param args optionale Portnummer als erstes Argument
     * @throws IOException wenn der HTTP-Server nicht eingerichtet werden kann
     */
    public static void main(String[] args) throws IOException
    {
        int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        HttpServer server = erstelleServer(port);
        server.start();
        System.out.println("Schiffeversenken läuft unter http://127.0.0.1:" + server.getAddress().getPort());
    }

    /**
     * Bindet den Server an die Loopback-Adresse und registriert die Handler. Port 0 lässt das System einen freien Port wählen.
     */
    static HttpServer erstelleServer(int port) throws IOException
    {
        return erstelleServer(port, System::nanoTime);
    }

    /**
     * Registriert die Spiel-API mit einer vorgegebenen monotonen Zeitquelle.
     */
    static HttpServer erstelleServer(int port, LongSupplier nanoTime) throws IOException
    {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/", new SpielApiHandler(nanoTime));
        server.createContext("/", new WebRessourcenHandler());
        return server;
    }
}
