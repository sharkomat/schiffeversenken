package de.kj.games;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import de.kj.games.Spiel.Spielseite;

/**
 * Verarbeitet die HTTP-Spiel-API für Einzelspieler und Mehrspieler. Verwaltet Sitzungen und Lobby-Einladungen.
 */
final class SpielApiHandler implements HttpHandler
{
    private final Map<String, Sitzung> sitzungen = new HashMap<>();
    private final Spieleinladungen spieleinladungen = new Spieleinladungen();
    private final LongSupplier nanoZeit;

    SpielApiHandler(LongSupplier nanoZeit)
    {
        this.nanoZeit = nanoZeit;
    }

    /**
     * Ordnet eine Anfrage anhand von Pfad, HTTP-Methode und Sitzungs-ID einer Partie oder der Lobby zu. Der
     * synchronisierte Aufruf führt Spielaktionen und Sitzungsänderungen nacheinander aus.
     */
    @Override
    public synchronized void handle(HttpExchange anfrage) throws IOException
    {
        try (anfrage)
        {
            String pfad = anfrage.getRequestURI().getPath();
            String methode = anfrage.getRequestMethod();
            String id = anfrage.getRequestHeaders().getFirst("X-Game-Id");
            if (pfad.equals("/api/requests"))
            {
                einladungen(anfrage, methode);
                return;
            }
            if (pfad.equals("/api/requests/status") && methode.equals("GET"))
            {
                einladungsstatus(anfrage, id);
                return;
            }
            if (pfad.matches("/api/requests/[0-9a-f-]{36}/join") && methode.equals("POST"))
            {
                beitreten(anfrage, pfad.split("/")[3]);
                return;
            }
            if (pfad.equals("/api/setup") && methode.equals("POST"))
            {
                spielErstellen(anfrage, id);
                return;
            }

            Sitzung sitzung = sitzungen.get(id);
            if (sitzung == null)
            {
                json(anfrage, 404, "{\"error\":\"Partie nicht gefunden.\"}");
                return;
            }
            verarbeiteSpielaktion(anfrage, methode + " " + pfad, sitzung, id);
        }
    }

    private void einladungsstatus(HttpExchange anfrage, String id) throws IOException
    {
        Sitzung sitzung = sitzungen.get(id);
        if (sitzung != null && sitzung.spiel().istMehrspieler())
        {
            json(anfrage, 200, sitzung.spielzustand(id, nanoZeit.getAsLong()));
        }
        else
        {
            String phase = spieleinladungen.wartetAufMitspieler(id, nanoZeit.getAsLong()) ? "WAITING" : "EXPIRED";
            json(anfrage, 200, "{\"phase\":\"" + phase + "\"}");
        }
    }

    /**
     * Erstellt eine Einzelspielerpartie nach Prüfung der Schwierigkeit und ersetzt bei Erfolg eine alte Sitzung.
     */
    private void spielErstellen(HttpExchange anfrage, String vorherigeId) throws IOException
    {
        String auswahl = anfrageinhalt(anfrage, 16);
        Schwierigkeitsgrad schwierigkeitsgrad;
        try
        {
            schwierigkeitsgrad = auswahl.isEmpty() ? Schwierigkeitsgrad.MITTEL : Schwierigkeitsgrad.valueOf(auswahl);
        }
        catch (IllegalArgumentException ungueltig)
        {
            json(anfrage, 400, "{\"error\":\"Unbekannte Schwierigkeitsstufe.\"}");
            return;
        }
        sitzungen.remove(vorherigeId);
        String id = UUID.randomUUID().toString();
        Sitzung sitzung = new Sitzung(new Spiel(schwierigkeitsgrad), Spielseite.HOST);
        sitzungen.put(id, sitzung);
        json(anfrage, 201, sitzung.spielzustand(id, nanoZeit.getAsLong()));
    }

    /**
     * Verarbeitet abgelaufene Fristen der zugeordneten Partie und leitet die Anfrage an die passende Spielaktion weiter.
     */
    private void verarbeiteSpielaktion(HttpExchange anfrage, String aktionspfad, Sitzung sitzung, String id)
            throws IOException
    {
        sitzung.spiel().verarbeiteFristen(nanoZeit.getAsLong());
        switch (aktionspfad)
        {
            case "GET /api/game" -> json(anfrage, 200, sitzung.spielzustand(id, nanoZeit.getAsLong()));
            case "POST /api/nation" -> waehleNation(anfrage, sitzung, id);
            case "POST /api/placement/random" -> stelleZufaelligAuf(anfrage, sitzung, id);
            case "POST /api/placement" -> platziereSchiff(anfrage, sitzung, id);
            case "DELETE /api/placement" -> entferneSchiff(anfrage, sitzung, id);
            case "POST /api/game" -> starteSpiel(anfrage, sitzung, id);
            case "POST /api/shot" -> spielerschuss(anfrage, sitzung, id);
            case "POST /api/computer" -> computerschuss(anfrage, sitzung, id);
            default -> json(anfrage, 404, "{\"error\":\"Unbekannte Anfrage.\"}");
        }
    }

    private void waehleNation(HttpExchange anfrage, Sitzung sitzung, String id) throws IOException
    {
        String auswahl = anfrageinhalt(anfrage, 32);
        Nation nation;
        try
        {
            nation = Nation.valueOf(auswahl);
        }
        catch (IllegalArgumentException ungueltig)
        {
            json(anfrage, 400, "{\"error\":\"Unbekannte Nation.\"}");
            return;
        }
        Spiel spiel = sitzung.spiel();
        spiel.verarbeiteFristen(nanoZeit.getAsLong());
        aktualisiere(anfrage, sitzung, id, spiel.waehleNation(sitzung.spielseite(), nation), "Die Platzierung ist abgeschlossen.");
    }

    private void stelleZufaelligAuf(HttpExchange anfrage, Sitzung sitzung, String id) throws IOException
    {
        String modus = anfrageinhalt(anfrage, 16);
        if (!modus.equals("ALL") && !modus.equals("REMAINING"))
        {
            json(anfrage, 400, "{\"error\":\"Unbekannte Option für die Zufallsplatzierung.\"}");
            return;
        }
        Spiel spiel = sitzung.spiel();
        spiel.verarbeiteFristen(nanoZeit.getAsLong());
        aktualisiere(anfrage, sitzung, id, spiel.stelleZufaelligAuf(sitzung.spielseite(), modus.equals("ALL")),
                "Zufallsplatzierung nicht möglich. Entfernen Sie ein Schiff oder losen Sie die ganze Flotte neu aus.");
    }

    /**
     * Liest Schiff, Startfeld und Ausrichtung aus der Anfrage und prüft die Platzierung nach Aktualisierung der Frist.
     */
    private void platziereSchiff(HttpExchange anfrage, Sitzung sitzung, String id) throws IOException
    {
        String platzierung = anfrageinhalt(anfrage, 32);
        if (!platzierung.matches("[0-4] [0-9]{1,2} (HORIZONTAL|VERTICAL)"))
        {
            json(anfrage, 400, "{\"error\":\"Ungültige Platzierungsdaten.\"}");
            return;
        }
        String[] teile = platzierung.split(" ");
        Spiel spiel = sitzung.spiel();
        spiel.verarbeiteFristen(nanoZeit.getAsLong());
        boolean platziert = spiel.platziereSchiff(sitzung.spielseite(), Integer.parseInt(teile[0]), Integer.parseInt(teile[1]),
                Spiel.Ausrichtung.valueOf(teile[2]));
        aktualisiere(anfrage, sitzung, id, platziert, "Dieses Schiff kann dort nicht platziert werden.");
    }

    private void entferneSchiff(HttpExchange anfrage, Sitzung sitzung, String id) throws IOException
    {
        String schiffsindex = anfrageinhalt(anfrage, 2);
        if (!schiffsindex.matches("[0-4]"))
        {
            json(anfrage, 400, "{\"error\":\"Unbekanntes Schiff.\"}");
            return;
        }
        Spiel spiel = sitzung.spiel();
        spiel.verarbeiteFristen(nanoZeit.getAsLong());
        aktualisiere(anfrage, sitzung, id, spiel.entferneSchiff(sitzung.spielseite(), Integer.parseInt(schiffsindex)),
                "Dieses Schiff kann nicht entfernt werden.");
    }

    private void starteSpiel(HttpExchange anfrage, Sitzung sitzung, String id) throws IOException
    {
        long zeitpunkt = nanoZeit.getAsLong();
        Spiel spiel = sitzung.spiel();
        spiel.verarbeiteFristen(zeitpunkt);
        aktualisiere(anfrage, sitzung, id, spiel.starte(sitzung.spielseite(), zeitpunkt), "Platzieren Sie zuerst alle fünf Schiffe.");
    }

    /**
     * Liest das Zielfeld und im Mehrspielermodus die erwartete Zugnummer. Aktualisiert die Frist vor dem Schuss.
     */
    private void spielerschuss(HttpExchange anfrage, Sitzung sitzung, String id) throws IOException
    {
        Spiel spiel = sitzung.spiel();
        String schuss = anfrageinhalt(anfrage, 24);
        String format = spiel.istMehrspieler() ? "[0-9]{1,2} [0-9]{1,18}" : "[0-9]{1,2}";
        if (!schuss.matches(format))
        {
            json(anfrage, 400, "{\"error\":\"Ungültige Koordinate.\"}");
            return;
        }
        String[] teile = schuss.split(" ");
        long erwarteteZugnummer = spiel.istMehrspieler() ? Long.parseLong(teile[1]) : 0;
        long zeitpunkt = nanoZeit.getAsLong();
        spiel.verarbeiteFristen(zeitpunkt);
        aktualisiere(anfrage, sitzung, id, spiel.spielerschuss(sitzung.spielseite(), Integer.parseInt(teile[0]), erwarteteZugnummer, zeitpunkt),
                "Sie sind gerade nicht am Zug oder die Schusszeit ist abgelaufen.");
    }

    /**
     * Fordert einen Computerschuss an und unterscheidet bei Ablehnung zwischen Wartezeit und falschem Zug.
     */
    private void computerschuss(HttpExchange anfrage, Sitzung sitzung, String id) throws IOException
    {
        long zeitpunkt = nanoZeit.getAsLong();
        Spiel spiel = sitzung.spiel();
        if (spiel.computerschuss(zeitpunkt))
        {
            json(anfrage, 200, sitzung.spielzustand(id, nanoZeit.getAsLong()));
        }
        else
        {
            String meldung = spiel.computerWartezeitMillis(zeitpunkt) > 0
                    ? "Der Computer wartet noch auf seinen nächsten Schuss."
                    : "Der Computer ist gerade nicht am Zug.";
            json(anfrage, 409, "{\"error\":\"" + meldung + "\"}");
        }
    }

    /**
     * Liefert offene Lobby-Einladungen oder legt nach Prüfung von Spielername und Schwierigkeitsgrad eine neue
     * Einladung an.
     */
    private void einladungen(HttpExchange anfrage, String methode) throws IOException
    {
        if (methode.equals("GET"))
        {
            json(anfrage, 200, spieleinladungen.listeEinladungenAuf(nanoZeit.getAsLong()));
            return;
        }
        if (!methode.equals("POST"))
        {
            anfrage.getResponseHeaders().set("Allow", "GET, POST");
            json(anfrage, 405, "{\"error\":\"Unbekannte Anfrage.\"}");
            return;
        }
        String[] daten = anfrageinhalt(anfrage, 512).split("\n", 2);
        String name = daten.length == 2 ? daten[1].strip() : "";
        if (!istGueltigerName(name))
        {
            json(anfrage, 400, "{\"error\":\"Geben Sie einen gültigen Spielernamen mit höchstens 60 Zeichen ein.\"}");
            return;
        }
        Schwierigkeitsgrad schwierigkeitsgrad;
        try
        {
            schwierigkeitsgrad = Schwierigkeitsgrad.valueOf(daten[0]);
        }
        catch (IllegalArgumentException ungueltig)
        {
            json(anfrage, 400, "{\"error\":\"Unbekannte Schwierigkeitsstufe.\"}");
            return;
        }
        json(anfrage, 201, spieleinladungen.erstelleEinladung(name, schwierigkeitsgrad, nanoZeit.getAsLong()));
    }

    /**
     * Löst eine offene Einladung ein und erstellt eine gemeinsame Partie mit getrennten Sitzungen für Gastgeber
     * und Gast.
     */
    private void beitreten(HttpExchange anfrage, String id) throws IOException
    {
        String name = anfrageinhalt(anfrage, 512).strip();
        if (!istGueltigerName(name))
        {
            json(anfrage, 400, "{\"error\":\"Geben Sie zuerst einen gültigen Spielernamen ein.\"}");
            return;
        }
        long zeitpunkt = nanoZeit.getAsLong();
        Spieleinladungen.Einladung einladung = spieleinladungen.beansprucheEinladung(id, anfrage.getRequestHeaders().getFirst("X-Game-Id"), zeitpunkt);
        if (einladung == null)
        {
            json(anfrage, 409, "{\"error\":\"Diese Spielanfrage ist nicht mehr verfügbar.\"}");
            return;
        }
        Spiel spiel = new Spiel(einladung.schwierigkeitsgrad(), einladung.name(), name, zeitpunkt);
        String gastId = UUID.randomUUID().toString();
        sitzungen.put(einladung.token(), new Sitzung(spiel, Spielseite.HOST));
        Sitzung gast = new Sitzung(spiel, Spielseite.GUEST);
        sitzungen.put(gastId, gast);
        json(anfrage, 201, gast.spielzustand(gastId, nanoZeit.getAsLong()));
    }

    private static boolean istGueltigerName(String name)
    {
        return !name.isEmpty() && name.length() <= 60 && name.chars().noneMatch(Character::isISOControl);
    }

    /**
     * Verknüpft eine HTTP-Sitzung mit einer Partie und dem Sitz des zugehörigen Spielers.
     */
    private record Sitzung(Spiel spiel, Spielseite spielseite)
    {
        /**
         * Verarbeitet abgelaufene Fristen und erzeugt den Spielzustand aus Sicht dieser Sitzung.
         */
        private String spielzustand(String id, long zeitpunkt)
        {
            spiel.verarbeiteFristen(zeitpunkt);
            return spiel.spielzustand(id, spielseite, zeitpunkt);
        }
    }

    private static String anfrageinhalt(HttpExchange anfrage, int grenze) throws IOException
    {
        return new String(anfrage.getRequestBody().readNBytes(grenze), StandardCharsets.UTF_8);
    }

    private void aktualisiere(HttpExchange anfrage, Sitzung sitzung, String id, boolean erfolg, String fehler)
            throws IOException
    {
        if (erfolg)
        {
            json(anfrage, 200, sitzung.spielzustand(id, nanoZeit.getAsLong()));
        }
        else
        {
            json(anfrage, 409, "{\"error\":\"" + fehler + "\"}");
        }
    }

    private static void json(HttpExchange anfrage, int status, String inhalt) throws IOException
    {
        anfrage.getResponseHeaders().set("Cache-Control", "no-store");
        anfrage.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] daten = inhalt.getBytes(StandardCharsets.UTF_8);
        anfrage.sendResponseHeaders(status, daten.length);
        anfrage.getResponseBody().write(daten);
    }
}
