package de.kj.games;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft die Spiel-API und die Auslieferung der Browserdateien über einen lokalen HTTP-Server.
 */
class SpielserverHttpTest
{
    private static final List<String> FLOTTE = List.of(
            "0 11 HORIZONTAL", "1 38 VERTICAL", "2 40 VERTICAL", "3 83 HORIZONTAL", "4 8 HORIZONTAL");

    private HttpServer server;
    private HttpClient client;
    private final AtomicLong nanoTime = new AtomicLong();

    @BeforeEach
    void serverStarten() throws IOException
    {
        server = Spielserver.erstelleServer(0, nanoTime::get);
        server.start();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void serverStoppen()
    {
        if (server != null)
        {
            server.stop(0);
        }
        if (client != null)
        {
            client.close();
        }
    }

    @Test
    void erstelltePartieKannMitSitzungsIdGeladenWerden() throws IOException, InterruptedException
    {
        var aufstellung = client.send(erstelleAnfrage("/api/setup")
                .POST(HttpRequest.BodyPublishers.ofString("MITTEL"))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(201, aufstellung.statusCode());
        var zustand = sendeAnfrage("GET", "/api/game", zeichenkettenfeld(aufstellung.body(), "id"), null);

        assertEquals(200, zustand.statusCode());
        assertTrue(zustand.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
        assertEquals(aufstellung.body(), zustand.body());
    }

    @Test
    void unbekannteSitzungUndSchwierigkeitWerdenAbgelehnt() throws IOException, InterruptedException
    {
        pruefeFehlerantwort(404, "Partie nicht gefunden.", sendeAnfrage("GET", "/api/game", "unbekannt", null));
        pruefeFehlerantwort(400, "Unbekannte Schwierigkeitsstufe.", sendeAnfrage("POST", "/api/setup", null, "UNBEKANNT"));
    }

    @Test
    void ersetztEinzelspielersitzungNurNachErfolgreichemStart() throws IOException, InterruptedException
    {
        String alteId = erstellePartie();
        assertEquals(400, sendeAnfrage("POST", "/api/setup", alteId, "UNBEKANNT").statusCode());
        assertEquals(200, sendeAnfrage("GET", "/api/game", alteId, null).statusCode());

        var ersatz = sendeAnfrage("POST", "/api/setup", alteId, "");
        assertEquals(201, ersatz.statusCode());
        String neueId = zeichenkettenfeld(ersatz.body(), "id");
        assertEquals("Mittel", zeichenkettenfeld(ersatz.body(), "difficulty"));
        assertNotEquals(alteId, neueId);
        assertEquals(404, sendeAnfrage("GET", "/api/game", alteId, null).statusCode());
        assertEquals(200, sendeAnfrage("GET", "/api/game", neueId, null).statusCode());
    }

    @Test
    void einzelspielerKannNationWaehlenSchiffePlatzierenUndStarten() throws IOException, InterruptedException
    {
        String id = erstellePartie();
        assertEquals(409, sendeAnfrage("POST", "/api/game", id, null).statusCode());
        assertEquals(409, sendeAnfrage("POST", "/api/computer", id, null).statusCode());

        assertEquals("JAPAN", zeichenkettenfeld(pruefeErfolgreicheAntwort(sendeAnfrage("POST", "/api/nation", id, "JAPAN")), "nation"));
        assertTrue(pruefeErfolgreicheAntwort(sendeAnfrage("POST", "/api/placement", id, FLOTTE.getFirst())).contains("\"placed\":1"));
        assertEquals(409, sendeAnfrage("POST", "/api/placement", id, FLOTTE.getFirst()).statusCode());
        assertTrue(pruefeErfolgreicheAntwort(sendeAnfrage("DELETE", "/api/placement", id, "0")).contains("\"placed\":0"));
        assertTrue(pruefeErfolgreicheAntwort(sendeAnfrage("POST", "/api/placement/random", id, "REMAINING")).contains("\"placed\":5"));
        assertEquals("PLAY", zeichenkettenfeld(pruefeErfolgreicheAntwort(sendeAnfrage("POST", "/api/game", id, null)), "phase"));
        assertTrue(pruefeErfolgreicheAntwort(sendeAnfrage("POST", "/api/shot", id, "0")).contains("\"shots\":1"));
    }

    /**
     * Prüft die Zeitgrenze für den Computerschuss und stellt sicher, dass ein sofortiger Folgeaufruf keinen Schuss auslöst.
     */
    @Test
    void computerSchiesstNachWartezeitGenauEinmal() throws IOException, InterruptedException
    {
        String id = erstellePartie();
        platziereFlotte(id);
        assertEquals("PLAY", zeichenkettenfeld(pruefeErfolgreicheAntwort(sendeAnfrage("POST", "/api/game", id, null)), "phase"));

        String nachWasserschuss = schiesseBisWasser(id);
        assertEquals("COMPUTER", zeichenkettenfeld(nachWasserschuss, "turn"));
        assertEquals(1_500, ganzzahlfeld(nachWasserschuss, "computerWaitMillis"));
        assertEquals(0, ganzzahlfeld(nachWasserschuss, "opponentShots"));

        nanoTime.addAndGet(Duration.ofMillis(1_499).toNanos());
        var zuFrueh = sendeAnfrage("POST", "/api/computer", id, null);
        pruefeFehlerantwort(409, "Der Computer wartet noch auf seinen nächsten Schuss.", zuFrueh);
        assertEquals(0, ganzzahlfeld(pruefeErfolgreicheAntwort(sendeAnfrage("GET", "/api/game", id, null)), "opponentShots"));

        nanoTime.addAndGet(Duration.ofMillis(1).toNanos());
        String nachComputerschuss = pruefeErfolgreicheAntwort(sendeAnfrage("POST", "/api/computer", id, null));
        assertEquals(1, ganzzahlfeld(nachComputerschuss, "opponentShots"));
        assertEquals("COMPUTER", zeichenkettenfeld(nachComputerschuss, "shooter"));

        assertEquals(409, sendeAnfrage("POST", "/api/computer", id, null).statusCode());
        assertEquals(1, ganzzahlfeld(pruefeErfolgreicheAntwort(sendeAnfrage("GET", "/api/game", id, null)), "opponentShots"));
    }

    @Test
    void ungueltigeSpielaktionenUndPfadeWerdenAbgelehnt() throws IOException, InterruptedException
    {
        String id = erstellePartie();
        pruefeFehlerantwort(400, "Unbekannte Nation.", sendeAnfrage("POST", "/api/nation", id, "UNBEKANNT"));
        assertEquals(400, sendeAnfrage("POST", "/api/placement/random", id, "UNKNOWN").statusCode());
        assertEquals(400, sendeAnfrage("POST", "/api/placement", id, "0 0 DIAGONAL").statusCode());
        assertEquals(400, sendeAnfrage("DELETE", "/api/placement", id, "9").statusCode());
        pruefeFehlerantwort(400, "Ungültige Koordinate.", sendeAnfrage("POST", "/api/shot", id, "A1"));
        pruefeFehlerantwort(404, "Unbekannte Anfrage.", sendeAnfrage("GET", "/api/unknown", id, null));
    }

    /**
     * Prüft Einladung, Beitritt und Zugwechsel mit zwei getrennten Sitzungen und fest platzierten Flotten.
     */
    @Test
    void mehrspielersitzungenTeilenPartieUndPruefenZugnummern() throws IOException, InterruptedException
    {
        var einladung = sendeAnfrage("POST", "/api/requests", null, "MITTEL\nGastgeber");
        assertEquals(201, einladung.statusCode());
        String einladungsId = zeichenkettenfeld(einladung.body(), "id");
        String gastgeberId = zeichenkettenfeld(einladung.body(), "token");
        var auflistung = pruefeErfolgreicheAntwort(sendeAnfrage("GET", "/api/requests", null, null));
        assertEquals(einladungsId, zeichenkettenfeld(auflistung, "id"));
        assertFalse(auflistung.contains(gastgeberId));
        assertEquals("WAITING", zeichenkettenfeld(pruefeErfolgreicheAntwort(sendeAnfrage("GET", "/api/requests/status", gastgeberId, null)), "phase"));

        String beitrittspfad = "/api/requests/" + einladungsId + "/join";
        assertEquals(409, sendeAnfrage("POST", beitrittspfad, gastgeberId, "Gastgeber").statusCode());
        var beigetreten = sendeAnfrage("POST", beitrittspfad, null, "Gast");
        assertEquals(201, beigetreten.statusCode());
        String gastId = zeichenkettenfeld(beigetreten.body(), "id");
        assertNotEquals(gastgeberId, gastId);
        assertEquals("Gast", zeichenkettenfeld(beigetreten.body(), "playerName"));
        assertEquals("Gastgeber", zeichenkettenfeld(pruefeErfolgreicheAntwort(sendeAnfrage("GET", "/api/requests/status", gastgeberId, null)),
                "playerName"));
        assertEquals(409, sendeAnfrage("POST", beitrittspfad, null, "Weiterer Gast").statusCode());

        platziereFlotte(gastgeberId);
        platziereFlotte(gastId);
        assertEquals("SETUP", zeichenkettenfeld(pruefeErfolgreicheAntwort(sendeAnfrage("POST", "/api/game", gastgeberId, null)), "phase"));
        assertEquals("PLAY", zeichenkettenfeld(pruefeErfolgreicheAntwort(sendeAnfrage("POST", "/api/game", gastId, null)), "phase"));
        assertEquals(409, sendeAnfrage("POST", "/api/shot", gastId, "0 1").statusCode());
        assertEquals("OPPONENT", zeichenkettenfeld(pruefeErfolgreicheAntwort(sendeAnfrage("POST", "/api/shot", gastgeberId, "0 1")), "turn"));
        assertEquals(409, sendeAnfrage("POST", "/api/shot", gastId, "0 1").statusCode());
        assertEquals("OPPONENT", zeichenkettenfeld(pruefeErfolgreicheAntwort(sendeAnfrage("POST", "/api/shot", gastId, "0 2")), "turn"));
    }

    @Test
    void ungueltigeLobbyanfrageWirdAbgelehntUndMethodenWerdenGenannt() throws IOException, InterruptedException
    {
        assertEquals(400, sendeAnfrage("POST", "/api/requests", null, "MITTEL\n").statusCode());
        var antwort = sendeAnfrage("DELETE", "/api/requests", null, null);
        assertEquals(405, antwort.statusCode());
        assertEquals("GET, POST", antwort.headers().firstValue("Allow").orElseThrow());
        assertEquals("EXPIRED", zeichenkettenfeld(pruefeErfolgreicheAntwort(sendeAnfrage("GET", "/api/requests/status", "unbekannt", null)), "phase"));
    }

    @Test
    void liefertSpielseiteUndJavaScriptModuleAus() throws IOException, InterruptedException
    {
        var seite = client.send(erstelleAnfrage("/").build(), HttpResponse.BodyHandlers.ofString());
        var skript = client.send(erstelleAnfrage("/game.js").build(), HttpResponse.BodyHandlers.ofString());
        var abspann = client.send(erstelleAnfrage("/abspann.js").build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, seite.statusCode());
        assertTrue(seite.body().contains("<script type=\"module\" src=\"/game.js\">"));
        assertEquals(200, skript.statusCode());
        assertEquals("text/javascript; charset=utf-8", skript.headers().firstValue("Content-Type").orElseThrow());
        assertTrue(skript.body().contains("import {erstelleAbspann} from \"./abspann.js\";"));
        assertEquals(200, abspann.statusCode());
        assertEquals("text/javascript; charset=utf-8", abspann.headers().firstValue("Content-Type").orElseThrow());
        assertTrue(abspann.body().contains("export function erstelleAbspann("));
    }

    @Test
    void liefertWasserschussAlsWaveAudio() throws IOException, InterruptedException
    {
        var antwort = client.send(erstelleAnfrage("/sounds/water.wav").build(), HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, antwort.statusCode());
        assertEquals("audio/wav", antwort.headers().firstValue("Content-Type").orElseThrow());
        try (var sound = getClass().getResourceAsStream("/web/sounds/water.wav"))
        {
            assertNotNull(sound, "Die WAV-Datei muss im Anwendungspaket liegen.");
            assertArrayEquals(sound.readAllBytes(), antwort.body());
        }
    }

    @Test
    void unbekanntePfadeUndFehlendeRessourcenWerdenNichtGefunden() throws IOException, InterruptedException
    {
        assertEquals(404, sendeAnfrage("GET", "/unbekannter-pfad", null, null).statusCode());
        assertEquals(404, sendeAnfrage("GET", "/ships/Unbekannter-Schiffstyp.svg", null, null).statusCode());
    }

    private HttpRequest.Builder erstelleAnfrage(String pfad)
    {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + pfad))
                .timeout(Duration.ofSeconds(5));
    }

    private HttpResponse<String> sendeAnfrage(String methode, String pfad, String id, String inhalt)
            throws IOException, InterruptedException
    {
        var anfrageBuilder = erstelleAnfrage(pfad);
        if (id != null)
        {
            anfrageBuilder.header("X-Game-Id", id);
        }
        var inhaltSender = inhalt == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(inhalt);
        return client.send(anfrageBuilder.method(methode, inhaltSender).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String erstellePartie() throws IOException, InterruptedException
    {
        var aufstellung = sendeAnfrage("POST", "/api/setup", null, "MITTEL");
        assertEquals(201, aufstellung.statusCode());
        return zeichenkettenfeld(aufstellung.body(), "id");
    }

    private void platziereFlotte(String id) throws IOException, InterruptedException
    {
        for (String platzierung : FLOTTE)
        {
            pruefeErfolgreicheAntwort(sendeAnfrage("POST", "/api/placement", id, platzierung));
        }
    }

    /**
     * Sucht in der ersten Reihe nach einem Wasserschuss. Wegen der Abstandsregeln kann keine Flotte die Reihe ganz füllen.
     */
    private String schiesseBisWasser(String id) throws IOException, InterruptedException
    {
        for (int feld = 0; feld < Spiel.GROESSE; feld++)
        {
            String zustand = pruefeErfolgreicheAntwort(sendeAnfrage("POST", "/api/shot", id, Integer.toString(feld)));
            if (zeichenkettenfeld(zustand, "result").equals("WATER"))
            {
                return zustand;
            }
        }
        throw new AssertionError("Die erste Reihe muss mindestens ein Wasserfeld enthalten.");
    }

    private static String pruefeErfolgreicheAntwort(HttpResponse<String> antwort)
    {
        assertEquals(200, antwort.statusCode(), antwort.body());
        return antwort.body();
    }

    private static void pruefeFehlerantwort(int status, String meldung, HttpResponse<String> antwort)
    {
        assertEquals(status, antwort.statusCode(), antwort.body());
        assertEquals(meldung, zeichenkettenfeld(antwort.body(), "error"));
    }

    private static String zeichenkettenfeld(String json, String feld)
    {
        var treffer = Pattern.compile("\"" + Pattern.quote(feld) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        assertTrue(treffer.find(), json);
        return treffer.group(1);
    }

    private static int ganzzahlfeld(String json, String feld)
    {
        var treffer = Pattern.compile("\"" + Pattern.quote(feld) + "\"\\s*:\\s*([0-9]+)").matcher(json);
        assertTrue(treffer.find(), json);
        return Integer.parseInt(treffer.group(1));
    }
}
