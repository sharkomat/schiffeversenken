package de.kj.games;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static de.kj.games.Nation.Schiffskategorie.*;

/**
 * Definiert die spielbaren Nationen mit Anzeigename, Ländercode und Flotte.
 */
enum Nation
{
    DEUTSCHLAND("Deutschland", "DE",
            SCHLACHTSCHIFF.schiff("Bismarck-Klasse", 5),
            SCHLACHTSCHIFF.schiff("Scharnhorst-Klasse", 4),
            FREGATTE.schiff("Baden-Württemberg-Klasse", 3),
            FREGATTE.schiff("Sachsen-Klasse", 3),
            U_BOOT.schiff("Klasse 212A", 2)),
    RUSSLAND("Russland", "RU",
            KREUZER.schiff("Admiral-Kusnezow-Klasse", 5, "Schwerer flugzeugtragender Kreuzer"),
            KREUZER.schiff("Slawa-Klasse", 4),
            ZERSTOERER.schiff("Udaloy-Klasse", 3),
            FREGATTE.schiff("Admiral-Grigorowitsch-Klasse", 3),
            U_BOOT.schiff("Improved-Kilo-Klasse", 2)),
    USA("USA", "US",
            FLUGZEUGTRAEGER.schiff("Gerald-R.-Ford-Klasse", 5),
            SCHLACHTSCHIFF.schiff("South-Dakota-Klasse", 4),
            KREUZER.schiff("Atlanta-Klasse", 3, "Leichter Kreuzer"),
            ZERSTOERER.schiff("Fletcher-Klasse", 3),
            U_BOOT.schiff("Gato-Klasse", 2)),
    JAPAN("Japan", "JP",
            HUBSCHRAUBERTRAEGER.schiff("Izumo-Klasse", 5),
            HUBSCHRAUBERTRAEGER.schiff("Hyūga-Klasse", 4),
            ZERSTOERER.schiff("Maya-Klasse", 3, "Lenkwaffenzerstörer"),
            FREGATTE.schiff("Mogami-Klasse", 3),
            U_BOOT.schiff("Taigei-Klasse", 2)),
    FRANKREICH("Frankreich", "FR",
            FLUGZEUGTRAEGER.schiff("Charles de Gaulle", 5),
            HUBSCHRAUBERTRAEGER.schiff("Mistral-Klasse", 4, "Amphibischer Hubschrauberträger"),
            FREGATTE.schiff("Horizon-Klasse", 3, "Luftverteidigungsfregatte"),
            FREGATTE.schiff("Aquitaine-Klasse", 3, "Mehrzweckfregatte"),
            U_BOOT.schiff("Suffren-Klasse", 2)),
    VEREINIGTES_KOENIGREICH("Vereinigtes Königreich", "GB",
            FLUGZEUGTRAEGER.schiff("Queen-Elizabeth-Klasse", 5),
            FLUGZEUGTRAEGER.schiff("Invincible-Klasse", 4, "Leichter Flugzeugträger"),
            ZERSTOERER.schiff("Daring-Klasse", 3),
            FREGATTE.schiff("Duke-Klasse", 3),
            U_BOOT.schiff("Astute-Klasse", 2)),
    CHINA("China", "CN",
            FLUGZEUGTRAEGER.schiff("Fujian (Typ 003)", 5),
            DOCKLANDUNGSSCHIFF.schiff("Typ 071", 4, "Amphibisches Docklandungsschiff"),
            ZERSTOERER.schiff("Typ 052D", 3, "Lenkwaffenzerstörer"),
            FREGATTE.schiff("Typ 054A", 3),
            KORVETTE.schiff("Typ 056A", 2));

    final String bezeichnung;
    final String laendercode;
    final List<Schiff> flotte;

    Nation(String bezeichnung, String laendercode, Schiff... flotte)
    {
        this.bezeichnung = bezeichnung;
        this.laendercode = laendercode;
        this.flotte = List.of(flotte);
    }

    /**
     * Erzeugt die kommaseparierten JSON-Objekte mit ID und Anzeigename für die Nationenauswahl.
     */
    static String nationenAlsJson()
    {
        return Arrays.stream(values())
                .map(nation -> "{\"id\":\"%s\",\"label\":\"%s\"}".formatted(nation.name(), nation.bezeichnung))
                .collect(Collectors.joining(","));
    }

    static Nation zufaelligeNation()
    {
        Nation[] nationen = values();
        return nationen[ThreadLocalRandom.current().nextInt(nationen.length)];
    }

    /**
     * Beschreibt einen Eintrag im Flottenkatalog mit Anzeigename, Schiffsklasse, Länge und Kategorie.
     */
    record Schiff(String name, String schiffsklasse, int laenge, Schiffskategorie typ)
    {
        String json()
        {
            return """
                    {"name":"%s","shipClass":"%s","length":%d,"image":"/ships/%s.svg"}
                    """.formatted(name, schiffsklasse, laenge, typ.bild).stripTrailing();
        }
    }

    /**
     * Definiert Schiffskategorien mit ihrem Standardnamen und der zugehörigen Grafik.
     */
    enum Schiffskategorie
    {
        SCHLACHTSCHIFF("Schlachtschiff", "Schlachtschiff"),
        FREGATTE("Fregatte", "Fregatte"),
        U_BOOT("U-Boot", "U-Boot"),
        KREUZER("Kreuzer", "Kreuzer"),
        ZERSTOERER("Zerstörer", "Zerstoerer"),
        FLUGZEUGTRAEGER("Flugzeugträger", "Flugzeugtraeger"),
        HUBSCHRAUBERTRAEGER("Hubschrauberträger", "Hubschraubertraeger"),
        DOCKLANDUNGSSCHIFF("Docklandungsschiff", "Docklandungsschiff"),
        KORVETTE("Korvette", "Korvette");

        final String bezeichnung;
        final String bild;

        Schiffskategorie(String bezeichnung, String bild)
        {
            this.bezeichnung = bezeichnung;
            this.bild = bild;
        }

        Schiff schiff(String schiffsklasse, int laenge)
        {
            return schiff(schiffsklasse, laenge, bezeichnung);
        }

        Schiff schiff(String schiffsklasse, int laenge, String name)
        {
            return new Schiff(name, schiffsklasse, laenge, this);
        }
    }
}
