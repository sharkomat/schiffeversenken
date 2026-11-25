package de.kj.games;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.kj.games.Nation.ShipType;
import de.kj.games.Spielfeld.Aufstellungsoption;
import de.kj.games.Spielfeld.Schiffsposition;

/**
 * Formatiert die bereits gefilterte Spielansicht als JSON für die Browser-API.
 */
final class GameStateJson
{
    private GameStateJson()
    {
    }

    /**
     * Enthält die Angaben, die der Browser in beiden Spielphasen erhält.
     */
    record Common(String id, String difficulty, String mode, String playerName, String opponentName,
                  Long remainingMillis)
    {
        private String stammdatenAlsJson()
        {
            return """
                    "id":"%s","difficulty":"%s","mode":"%s","playerName":%s,"opponentName":%s
                    """.formatted(id, difficulty, mode, Json.quote(playerName), Json.quote(opponentName)).stripTrailing();
        }
    }

    /**
     * Enthält das eigene Spielfeld und die Platzierungsoptionen für die Aufstellungsphase.
     */
    record Setup(String nation, String own, boolean ready, List<Aufstellungsoption> ships,
                 List<Schiffsposition> ownFleet)
    {
    }

    /**
     * Enthält die sichtbaren Spielfelder, den Zug, die Schussstatistik und gegebenenfalls das Ergebnis.
     */
    record Battle(String nation, String own, String target, String turn, String winner, boolean finished,
                  long turnNumber, int shots, int hits, int opponentShots, int opponentHits,
                  List<Schiffsposition> ownFleet, List<Schiffsposition> sunk, List<Schiffsposition> revealed,
                  String winnerCountryCode, int unknown, long computerWaitMillis, ShotView lastShot,
                  List<ReplayView> replayHits)
    {
    }

    /**
     * Beschreibt den letzten Schuss aus Sicht des Spielers, gegebenenfalls mit verborgenem Zielfeld.
     */
    record ShotView(long number, String shooter, Integer cell, String result, ShipType ship)
    {
        private String alsJson()
        {
            return "{\"number\":%d,\"shooter\":\"%s\",\"cell\":%s,\"result\":\"%s\",\"ship\":%s}"
                    .formatted(number, shooter, cell, result, ship == null ? "null" : ship.json());
        }
    }

    /**
     * Beschreibt einen Treffer für die Wiedergabe aus Sicht des Spielers.
     */
    record ReplayView(boolean own, int cell, long timeMillis, boolean sunk)
    {
        private String alsJson()
        {
            return "{\"own\":%s,\"cell\":%d,\"timeMillis\":%d,\"sunk\":%s}"
                    .formatted(own, cell, timeMillis, sunk);
        }
    }

    /**
     * Serialisiert die Aufstellung mit der eigenen Flotte und den wählbaren Nationen.
     */
    static String aufstellung(Common common, Setup view)
    {
        return """
                {%s,"phase":"SETUP","nation":"%s","own":"%s","placed":%d,"ready":%s,
                 "remainingMillis":%s,"ships":[%s],"ownFleet":[%s],"nations":[%s]}
                """.formatted(common.stammdatenAlsJson(), view.nation(), view.own(), view.ownFleet().size(), view.ready(),
                common.remainingMillis(), eintraegeAlsJson(view.ships(), GameStateJson::aufstellungsoptionAlsJson),
                eintraegeAlsJson(view.ownFleet(), GameStateJson::schiffspositionAlsJson), Nation.optionsJson());
    }

    /**
     * Serialisiert die sichtbare Gefechtsansicht mit optionalem Ergebnis und Wiedergabedaten.
     */
    static String gefecht(Common common, Battle view)
    {
        return """
                {%s,"phase":"PLAY","nation":"%s","own":"%s","target":"%s","turn":"%s","winner":%s,"finished":%s,
                 "remainingMillis":%s,"turnNumber":%d,"shots":%d,"hits":%d,"opponentShots":%d,"opponentHits":%d,
                 "ownFleet":[%s],"sunk":[%s],"revealed":[%s],"winnerCountryCode":%s,"unknown":%d,
                 "computerWaitMillis":%d,"lastShot":%s,"replayHits":[%s]}
                """.formatted(common.stammdatenAlsJson(), view.nation(), view.own(), view.target(), view.turn(),
                textOderNull(view.winner()), view.finished(), common.remainingMillis(), view.turnNumber(),
                view.shots(), view.hits(), view.opponentShots(), view.opponentHits(),
                eintraegeAlsJson(view.ownFleet(), GameStateJson::schiffspositionAlsJson),
                eintraegeAlsJson(view.sunk(), GameStateJson::schiffspositionAlsJson),
                eintraegeAlsJson(view.revealed(), GameStateJson::schiffspositionAlsJson),
                textOderNull(view.winnerCountryCode()), view.unknown(),
                view.computerWaitMillis(), view.lastShot() == null ? "null" : view.lastShot().alsJson(),
                eintraegeAlsJson(view.replayHits(), ReplayView::alsJson));
    }

    private static String schiffspositionAlsJson(Schiffsposition schiff)
    {
        ShipPlacement platzierung = schiff.platzierung();
        return "{\"type\":%s,\"start\":%d,\"direction\":\"%s\"}"
                .formatted(schiff.typ().json(), platzierung.start(), platzierung.direction());
    }

    private static String aufstellungsoptionAlsJson(Aufstellungsoption schiff)
    {
        return """
                {"type":%s,"placed":%s,"horizontal":"%s","vertical":"%s"}
                """.formatted(schiff.typ().json(), schiff.platziert(), schiff.horizontal(), schiff.vertikal())
                .stripTrailing();
    }

    private static String textOderNull(String value)
    {
        return value == null ? "null" : Json.quote(value);
    }

    private static <T> String eintraegeAlsJson(List<T> values, Function<T, String> toJson)
    {
        return values.stream().map(toJson).collect(Collectors.joining(","));
    }
}
