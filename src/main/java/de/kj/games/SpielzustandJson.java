package de.kj.games;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.kj.games.Nation.Schiff;
import de.kj.games.Spielfeld.Aufstellungsoption;
import de.kj.games.Spielfeld.Schiffsposition;

/**
 * Formatiert die bereits gefilterte Spielansicht als JSON für die Browser-API.
 */
final class SpielzustandJson
{
    private SpielzustandJson()
    {
    }

    /**
     * Enthält die Angaben, die der Browser in beiden Spielphasen erhält.
     */
    record Stammdaten(String id, String schwierigkeitsgrad, String spielmodus, String spielername, String gegnername,
                      Long restzeitMillis)
    {
        private String stammdatenAlsJson()
        {
            return """
                    "id":"%s","difficulty":"%s","mode":"%s","playerName":%s,"opponentName":%s
                    """.formatted(id, schwierigkeitsgrad, spielmodus, Json.inAnfuehrungszeichen(spielername), Json.inAnfuehrungszeichen(gegnername)).stripTrailing();
        }
    }

    /**
     * Enthält das eigene Spielfeld und die Platzierungsoptionen für die Aufstellungsphase.
     */
    record Aufstellung(String nation, String eigenesSpielfeld, boolean bereit, List<Aufstellungsoption> schiffe,
                       List<Schiffsposition> eigeneFlotte)
    {
    }

    /**
     * Enthält die sichtbaren Spielfelder, den Zug, die Schussstatistik und gegebenenfalls das Ergebnis.
     */
    record Gefecht(String nation, String eigenesSpielfeld, String gegnerischesSpielfeld, String amZug, String sieger, boolean beendet,
                   long zugnummer, int schuesse, int treffer, int gegnerischeSchuesse, int gegnerischeTreffer,
                   List<Schiffsposition> eigeneFlotte, List<Schiffsposition> versenkteSchiffe, List<Schiffsposition> aufgedeckteSchiffe,
                   String siegerLaendercode, int unbekannteSchiffe, long computerWartezeitMillis, Schussansicht letzterSchuss,
                   List<Wiedergabeansicht> wiedergabeTreffer)
    {
    }

    /**
     * Beschreibt den letzten Schuss aus Sicht des Spielers, gegebenenfalls mit verborgenem Zielfeld.
     */
    record Schussansicht(long nummer, String schuetze, Integer feld, String ergebnis, Schiff schiff)
    {
        private String alsJson()
        {
            return "{\"number\":%d,\"shooter\":\"%s\",\"cell\":%s,\"result\":\"%s\",\"ship\":%s}"
                    .formatted(nummer, schuetze, feld, ergebnis, schiff == null ? "null" : schiff.json());
        }
    }

    /**
     * Beschreibt einen Treffer für die Wiedergabe aus Sicht des Spielers.
     */
    record Wiedergabeansicht(boolean eigenesFeld, int feld, long zeitMillis, boolean versenkt)
    {
        private String alsJson()
        {
            return "{\"own\":%s,\"cell\":%d,\"timeMillis\":%d,\"sunk\":%s}"
                    .formatted(eigenesFeld, feld, zeitMillis, versenkt);
        }
    }

    /**
     * Serialisiert die Aufstellung mit der eigenen Flotte und den wählbaren Nationen.
     */
    static String aufstellung(Stammdaten stammdaten, Aufstellung ansicht)
    {
        return """
                {%s,"phase":"SETUP","nation":"%s","own":"%s","placed":%d,"ready":%s,
                 "remainingMillis":%s,"ships":[%s],"ownFleet":[%s],"nations":[%s]}
                """.formatted(stammdaten.stammdatenAlsJson(), ansicht.nation(), ansicht.eigenesSpielfeld(), ansicht.eigeneFlotte().size(), ansicht.bereit(),
                stammdaten.restzeitMillis(), eintraegeAlsJson(ansicht.schiffe(), SpielzustandJson::aufstellungsoptionAlsJson),
                eintraegeAlsJson(ansicht.eigeneFlotte(), SpielzustandJson::schiffspositionAlsJson), Nation.nationenAlsJson());
    }

    /**
     * Serialisiert die sichtbare Gefechtsansicht mit optionalem Ergebnis und Wiedergabedaten.
     */
    static String gefecht(Stammdaten stammdaten, Gefecht ansicht)
    {
        return """
                {%s,"phase":"PLAY","nation":"%s","own":"%s","target":"%s","turn":"%s","winner":%s,"finished":%s,
                 "remainingMillis":%s,"turnNumber":%d,"shots":%d,"hits":%d,"opponentShots":%d,"opponentHits":%d,
                 "ownFleet":[%s],"sunk":[%s],"revealed":[%s],"winnerCountryCode":%s,"unknown":%d,
                 "computerWaitMillis":%d,"lastShot":%s,"replayHits":[%s]}
                """.formatted(stammdaten.stammdatenAlsJson(), ansicht.nation(), ansicht.eigenesSpielfeld(), ansicht.gegnerischesSpielfeld(), ansicht.amZug(),
                textOderNull(ansicht.sieger()), ansicht.beendet(), stammdaten.restzeitMillis(), ansicht.zugnummer(),
                ansicht.schuesse(), ansicht.treffer(), ansicht.gegnerischeSchuesse(), ansicht.gegnerischeTreffer(),
                eintraegeAlsJson(ansicht.eigeneFlotte(), SpielzustandJson::schiffspositionAlsJson),
                eintraegeAlsJson(ansicht.versenkteSchiffe(), SpielzustandJson::schiffspositionAlsJson),
                eintraegeAlsJson(ansicht.aufgedeckteSchiffe(), SpielzustandJson::schiffspositionAlsJson),
                textOderNull(ansicht.siegerLaendercode()), ansicht.unbekannteSchiffe(),
                ansicht.computerWartezeitMillis(), ansicht.letzterSchuss() == null ? "null" : ansicht.letzterSchuss().alsJson(),
                eintraegeAlsJson(ansicht.wiedergabeTreffer(), Wiedergabeansicht::alsJson));
    }

    private static String schiffspositionAlsJson(Schiffsposition schiff)
    {
        Schiffsplatzierung platzierung = schiff.platzierung();
        return "{\"type\":%s,\"start\":%d,\"direction\":\"%s\"}"
                .formatted(schiff.typ().json(), platzierung.start(), platzierung.ausrichtung());
    }

    private static String aufstellungsoptionAlsJson(Aufstellungsoption schiff)
    {
        return """
                {"type":%s,"placed":%s,"horizontal":"%s","vertical":"%s"}
                """.formatted(schiff.typ().json(), schiff.platziert(), schiff.horizontal(), schiff.vertikal())
                .stripTrailing();
    }

    private static String textOderNull(String wert)
    {
        return wert == null ? "null" : Json.inAnfuehrungszeichen(wert);
    }

    private static <T> String eintraegeAlsJson(List<T> eintraege, Function<T, String> alsJson)
    {
        return eintraege.stream().map(alsJson).collect(Collectors.joining(","));
    }
}
