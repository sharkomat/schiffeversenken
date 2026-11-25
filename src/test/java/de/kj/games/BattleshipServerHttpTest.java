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
class BattleshipServerHttpTest
{
    private static final List<String> FLEET = List.of(
            "0 11 HORIZONTAL", "1 38 VERTICAL", "2 40 VERTICAL", "3 83 HORIZONTAL", "4 8 HORIZONTAL");

    private HttpServer server;
    private HttpClient client;
    private final AtomicLong nanoTime = new AtomicLong();

    @BeforeEach
    void startServer() throws IOException
    {
        server = BattleshipServer.createServer(0, nanoTime::get);
        server.start();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stopServer()
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
    void createdGameCanBeLoadedUsingItsSessionId() throws IOException, InterruptedException
    {
        var setup = client.send(request("/api/setup")
                .POST(HttpRequest.BodyPublishers.ofString("MITTEL"))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(201, setup.statusCode());
        var state = send("GET", "/api/game", stringField(setup.body(), "id"), null);

        assertEquals(200, state.statusCode());
        assertTrue(state.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
        assertEquals(setup.body(), state.body());
    }

    @Test
    void rejectsAnUnknownSessionAndAnUnknownDifficulty() throws IOException, InterruptedException
    {
        assertError(404, "Partie nicht gefunden.", send("GET", "/api/game", "unbekannt", null));
        assertError(400, "Unbekannte Schwierigkeitsstufe.", send("POST", "/api/setup", null, "UNBEKANNT"));
    }

    @Test
    void replacesAnExistingSinglePlayerSessionOnlyAfterSuccessfulSetup() throws IOException, InterruptedException
    {
        String oldId = createGame();
        assertEquals(400, send("POST", "/api/setup", oldId, "UNBEKANNT").statusCode());
        assertEquals(200, send("GET", "/api/game", oldId, null).statusCode());

        var replacement = send("POST", "/api/setup", oldId, "");
        assertEquals(201, replacement.statusCode());
        String newId = stringField(replacement.body(), "id");
        assertEquals("Mittel", stringField(replacement.body(), "difficulty"));
        assertNotEquals(oldId, newId);
        assertEquals(404, send("GET", "/api/game", oldId, null).statusCode());
        assertEquals(200, send("GET", "/api/game", newId, null).statusCode());
    }

    @Test
    void singlePlayerCanSelectNationPlaceShipsAndStart() throws IOException, InterruptedException
    {
        String id = createGame();
        assertEquals(409, send("POST", "/api/game", id, null).statusCode());
        assertEquals(409, send("POST", "/api/computer", id, null).statusCode());

        assertEquals("JAPAN", stringField(assertOk(send("POST", "/api/nation", id, "JAPAN")), "nation"));
        assertTrue(assertOk(send("POST", "/api/placement", id, FLEET.getFirst())).contains("\"placed\":1"));
        assertEquals(409, send("POST", "/api/placement", id, FLEET.getFirst()).statusCode());
        assertTrue(assertOk(send("DELETE", "/api/placement", id, "0")).contains("\"placed\":0"));
        assertTrue(assertOk(send("POST", "/api/placement/random", id, "REMAINING")).contains("\"placed\":5"));
        assertEquals("PLAY", stringField(assertOk(send("POST", "/api/game", id, null)), "phase"));
        assertTrue(assertOk(send("POST", "/api/shot", id, "0")).contains("\"shots\":1"));
    }

    /**
     * Prüft die Zeitgrenze für den Computerschuss und stellt sicher, dass ein sofortiger Folgeaufruf keinen Schuss auslöst.
     */
    @Test
    void computerShootsExactlyOnceWhenItsWaitEnds() throws IOException, InterruptedException
    {
        String id = createGame();
        placeFleet(id);
        assertEquals("PLAY", stringField(assertOk(send("POST", "/api/game", id, null)), "phase"));

        String afterMiss = shootUntilWater(id);
        assertEquals("COMPUTER", stringField(afterMiss, "turn"));
        assertEquals(1_500, intField(afterMiss, "computerWaitMillis"));
        assertEquals(0, intField(afterMiss, "opponentShots"));

        nanoTime.addAndGet(Duration.ofMillis(1_499).toNanos());
        var tooEarly = send("POST", "/api/computer", id, null);
        assertError(409, "Der Computer wartet noch auf seinen nächsten Schuss.", tooEarly);
        assertEquals(0, intField(assertOk(send("GET", "/api/game", id, null)), "opponentShots"));

        nanoTime.addAndGet(Duration.ofMillis(1).toNanos());
        String afterComputerShot = assertOk(send("POST", "/api/computer", id, null));
        assertEquals(1, intField(afterComputerShot, "opponentShots"));
        assertEquals("COMPUTER", stringField(afterComputerShot, "shooter"));

        assertEquals(409, send("POST", "/api/computer", id, null).statusCode());
        assertEquals(1, intField(assertOk(send("GET", "/api/game", id, null)), "opponentShots"));
    }

    @Test
    void rejectsMalformedGameActionsAndUnknownRoutes() throws IOException, InterruptedException
    {
        String id = createGame();
        assertError(400, "Unbekannte Nation.", send("POST", "/api/nation", id, "UNBEKANNT"));
        assertEquals(400, send("POST", "/api/placement/random", id, "UNKNOWN").statusCode());
        assertEquals(400, send("POST", "/api/placement", id, "0 0 DIAGONAL").statusCode());
        assertEquals(400, send("DELETE", "/api/placement", id, "9").statusCode());
        assertError(400, "Ungültige Koordinate.", send("POST", "/api/shot", id, "A1"));
        assertError(404, "Unbekannte Anfrage.", send("GET", "/api/unknown", id, null));
    }

    /**
     * Prüft Einladung, Beitritt und Zugwechsel mit zwei getrennten Sitzungen und fest platzierten Flotten.
     */
    @Test
    void multiplayerSessionsShareTheMatchAndEnforceTurnNumbers() throws IOException, InterruptedException
    {
        var invitation = send("POST", "/api/requests", null, "MITTEL\nGastgeber");
        assertEquals(201, invitation.statusCode());
        String invitationId = stringField(invitation.body(), "id");
        String hostId = stringField(invitation.body(), "token");
        var listed = assertOk(send("GET", "/api/requests", null, null));
        assertEquals(invitationId, stringField(listed, "id"));
        assertFalse(listed.contains(hostId));
        assertEquals("WAITING", stringField(assertOk(send("GET", "/api/requests/status", hostId, null)), "phase"));

        String joinPath = "/api/requests/" + invitationId + "/join";
        assertEquals(409, send("POST", joinPath, hostId, "Gastgeber").statusCode());
        var joined = send("POST", joinPath, null, "Gast");
        assertEquals(201, joined.statusCode());
        String guestId = stringField(joined.body(), "id");
        assertNotEquals(hostId, guestId);
        assertEquals("Gast", stringField(joined.body(), "playerName"));
        assertEquals("Gastgeber", stringField(assertOk(send("GET", "/api/requests/status", hostId, null)),
                "playerName"));
        assertEquals(409, send("POST", joinPath, null, "Weiterer Gast").statusCode());

        placeFleet(hostId);
        placeFleet(guestId);
        assertEquals("SETUP", stringField(assertOk(send("POST", "/api/game", hostId, null)), "phase"));
        assertEquals("PLAY", stringField(assertOk(send("POST", "/api/game", guestId, null)), "phase"));
        assertEquals(409, send("POST", "/api/shot", guestId, "0 1").statusCode());
        assertEquals("OPPONENT", stringField(assertOk(send("POST", "/api/shot", hostId, "0 1")), "turn"));
        assertEquals(409, send("POST", "/api/shot", guestId, "0 1").statusCode());
        assertEquals("OPPONENT", stringField(assertOk(send("POST", "/api/shot", guestId, "0 2")), "turn"));
    }

    @Test
    void rejectsAnInvalidLobbyRequestAndReportsSupportedMethods() throws IOException, InterruptedException
    {
        assertEquals(400, send("POST", "/api/requests", null, "MITTEL\n").statusCode());
        var response = send("DELETE", "/api/requests", null, null);
        assertEquals(405, response.statusCode());
        assertEquals("GET, POST", response.headers().firstValue("Allow").orElseThrow());
        assertEquals("EXPIRED", stringField(assertOk(send("GET", "/api/requests/status", "unbekannt", null)), "phase"));
    }

    @Test
    void liefertSpielseiteUndJavaScriptModuleAus() throws IOException, InterruptedException
    {
        var page = client.send(request("/").build(), HttpResponse.BodyHandlers.ofString());
        var script = client.send(request("/game.js").build(), HttpResponse.BodyHandlers.ofString());
        var abspann = client.send(request("/abspann.js").build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("<script type=\"module\" src=\"/game.js\">"));
        assertEquals(200, script.statusCode());
        assertEquals("text/javascript; charset=utf-8", script.headers().firstValue("Content-Type").orElseThrow());
        assertTrue(script.body().contains("import {erstelleAbspann} from \"./abspann.js\";"));
        assertEquals(200, abspann.statusCode());
        assertEquals("text/javascript; charset=utf-8", abspann.headers().firstValue("Content-Type").orElseThrow());
        assertTrue(abspann.body().contains("export function erstelleAbspann("));
    }

    @Test
    void servesWaterSoundAsWaveAudio() throws IOException, InterruptedException
    {
        var response = client.send(request("/sounds/water.wav").build(), HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, response.statusCode());
        assertEquals("audio/wav", response.headers().firstValue("Content-Type").orElseThrow());
        try (var sound = getClass().getResourceAsStream("/web/sounds/water.wav"))
        {
            assertNotNull(sound, "Die WAV-Datei muss im Anwendungspaket liegen.");
            assertArrayEquals(sound.readAllBytes(), response.body());
        }
    }

    @Test
    void returnsNotFoundForUnknownPathsAndMissingResources() throws IOException, InterruptedException
    {
        assertEquals(404, send("GET", "/unbekannter-pfad", null, null).statusCode());
        assertEquals(404, send("GET", "/ships/Unbekannter-Schiffstyp.svg", null, null).statusCode());
    }

    private HttpRequest.Builder request(String path)
    {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path))
                .timeout(Duration.ofSeconds(5));
    }

    private HttpResponse<String> send(String method, String path, String id, String body)
            throws IOException, InterruptedException
    {
        var builder = request(path);
        if (id != null)
        {
            builder.header("X-Game-Id", id);
        }
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
        return client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String createGame() throws IOException, InterruptedException
    {
        var setup = send("POST", "/api/setup", null, "MITTEL");
        assertEquals(201, setup.statusCode());
        return stringField(setup.body(), "id");
    }

    private void placeFleet(String id) throws IOException, InterruptedException
    {
        for (String placement : FLEET)
        {
            assertOk(send("POST", "/api/placement", id, placement));
        }
    }

    /**
     * Sucht in der ersten Reihe nach einem Wasserschuss. Wegen der Abstandsregeln kann keine Flotte die Reihe ganz füllen.
     */
    private String shootUntilWater(String id) throws IOException, InterruptedException
    {
        for (int cell = 0; cell < Game.SIZE; cell++)
        {
            String state = assertOk(send("POST", "/api/shot", id, Integer.toString(cell)));
            if (stringField(state, "result").equals("WATER"))
            {
                return state;
            }
        }
        throw new AssertionError("Die erste Reihe muss mindestens ein Wasserfeld enthalten.");
    }

    private static String assertOk(HttpResponse<String> response)
    {
        assertEquals(200, response.statusCode(), response.body());
        return response.body();
    }

    private static void assertError(int status, String message, HttpResponse<String> response)
    {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(message, stringField(response.body(), "error"));
    }

    private static String stringField(String json, String field)
    {
        var match = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        assertTrue(match.find(), json);
        return match.group(1);
    }

    private static int intField(String json, String field)
    {
        var match = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*([0-9]+)").matcher(json);
        assertTrue(match.find(), json);
        return Integer.parseInt(match.group(1));
    }
}
