package de.kj.games;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static de.kj.games.Nation.Type.*;

enum Nation {
    DEUTSCHLAND("Deutschland", "DE",
            SCHLACHTSCHIFF.ship("Bismarck-Klasse", 5),
            SCHLACHTSCHIFF.ship("Scharnhorst-Klasse", 4),
            FREGATTE.ship("Baden-Württemberg-Klasse", 3),
            FREGATTE.ship("Sachsen-Klasse", 3),
            U_BOOT.ship("Klasse 212A", 2)),
    RUSSLAND("Russland", "RU",
            KREUZER.ship("Admiral-Kusnezow-Klasse", 5, "Schwerer flugzeugtragender Kreuzer"),
            KREUZER.ship("Slawa-Klasse", 4),
            ZERSTOERER.ship("Udaloy-Klasse", 3),
            FREGATTE.ship("Admiral-Grigorowitsch-Klasse", 3),
            U_BOOT.ship("Improved-Kilo-Klasse", 2)),
    USA("USA", "US",
            FLUGZEUGTRAEGER.ship("Gerald-R.-Ford-Klasse", 5),
            SCHLACHTSCHIFF.ship("South-Dakota-Klasse", 4),
            KREUZER.ship("Atlanta-Klasse", 3, "Leichter Kreuzer"),
            ZERSTOERER.ship("Fletcher-Klasse", 3),
            U_BOOT.ship("Gato-Klasse", 2)),
    JAPAN("Japan", "JP",
            HUBSCHRAUBERTRAEGER.ship("Izumo-Klasse", 5),
            HUBSCHRAUBERTRAEGER.ship("Hyūga-Klasse", 4),
            ZERSTOERER.ship("Maya-Klasse", 3, "Lenkwaffenzerstörer"),
            FREGATTE.ship("Mogami-Klasse", 3),
            U_BOOT.ship("Taigei-Klasse", 2)),
    FRANKREICH("Frankreich", "FR",
            FLUGZEUGTRAEGER.ship("Charles de Gaulle", 5),
            HUBSCHRAUBERTRAEGER.ship("Mistral-Klasse", 4, "Amphibischer Hubschrauberträger"),
            FREGATTE.ship("Horizon-Klasse", 3, "Luftverteidigungsfregatte"),
            FREGATTE.ship("Aquitaine-Klasse", 3, "Mehrzweckfregatte"),
            U_BOOT.ship("Suffren-Klasse", 2)),
    VEREINIGTES_KOENIGREICH("Vereinigtes Königreich", "GB",
            FLUGZEUGTRAEGER.ship("Queen-Elizabeth-Klasse", 5),
            FLUGZEUGTRAEGER.ship("Invincible-Klasse", 4, "Leichter Flugzeugträger"),
            ZERSTOERER.ship("Daring-Klasse", 3),
            FREGATTE.ship("Duke-Klasse", 3),
            U_BOOT.ship("Astute-Klasse", 2)),
    CHINA("China", "CN",
            FLUGZEUGTRAEGER.ship("Fujian (Typ 003)", 5),
            DOCKLANDUNGSSCHIFF.ship("Typ 071", 4, "Amphibisches Docklandungsschiff"),
            ZERSTOERER.ship("Typ 052D", 3, "Lenkwaffenzerstörer"),
            FREGATTE.ship("Typ 054A", 3),
            KORVETTE.ship("Typ 056A", 2));

    final String label;
    final String countryCode;
    final List<ShipType> fleet;

    Nation(String label, String countryCode, ShipType... fleet) {
        this.label = label;
        this.countryCode = countryCode;
        this.fleet = List.of(fleet);
    }

    static String optionsJson() {
        return Arrays.stream(values())
                .map(nation -> "{\"id\":\"%s\",\"label\":\"%s\"}".formatted(nation.name(), nation.label))
                .collect(Collectors.joining(","));
    }

    static Nation random() {
        Nation[] nations = values();
        return nations[ThreadLocalRandom.current().nextInt(nations.length)];
    }

    record ShipType(String name, String shipClass, int length, Type type) {
        String json() {
            return """
                    {"name":"%s","shipClass":"%s","length":%d,"image":"/ships/%s.svg"}
                    """.formatted(name, shipClass, length, type.image).stripTrailing();
        }
    }

    enum Type {
        SCHLACHTSCHIFF("Schlachtschiff", "Schlachtschiff"),
        FREGATTE("Fregatte", "Fregatte"),
        U_BOOT("U-Boot", "U-Boot"),
        KREUZER("Kreuzer", "Kreuzer"),
        ZERSTOERER("Zerstörer", "Zerstoerer"),
        FLUGZEUGTRAEGER("Flugzeugträger", "Flugzeugtraeger"),
        HUBSCHRAUBERTRAEGER("Hubschrauberträger", "Hubschraubertraeger"),
        DOCKLANDUNGSSCHIFF("Docklandungsschiff", "Docklandungsschiff"),
        KORVETTE("Korvette", "Korvette");

        final String label;
        final String image;

        Type(String label, String image) {
            this.label = label;
            this.image = image;
        }

        ShipType ship(String shipClass, int length) {
            return ship(shipClass, length, label);
        }

        ShipType ship(String shipClass, int length, String name) {
            return new ShipType(name, shipClass, length, this);
        }
    }
}
