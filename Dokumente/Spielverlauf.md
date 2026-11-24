# Schiffeversenken – Spielanleitung

Der Spieler tritt gegen den Computer oder gegen einen anderen Menschen an. Beide Seiten platzieren ihre Flotten verdeckt und schießen auf das gegnerische Spielfeld. Nach jedem Treffer darf dieselbe Seite erneut schießen. Bei einem Wasserschuss wechselt der Zug. Wer zuerst alle gegnerischen Schiffe versenkt, gewinnt.

## Vorbereitung

### Partie starten

Für alle drei Startwege trägt der Spieler zuerst seinen Namen ein. Beim direkten Spiel gegen den Computer und beim Erstellen einer Spielanfrage wählt der Spieler die Schwierigkeitsstufe. **Mittel** ist voreingestellt. Die Stufe bestimmt die Wassermarkierungen im Zielraster und die Zielwahl des Computers.

- **Gegen den Computer spielen** – Die Platzierungsphase beginnt sofort.
- **Eine Spielanfrage erstellen** – Die Anfrage erscheint mit Spielername und gewählter Stufe auf der Startseite. Der Ersteller wartet dort auf einen Gegner. Nimmt niemand die Anfrage an, verschwindet sie spätestens **drei Minuten nach dem Erstellen**.
- **Einer Spielanfrage beitreten** – Beide Spieler übernehmen die in der Anfrage festgelegte Stufe. Sobald der zweite Spieler beitritt, verschwindet die Anfrage aus der Liste und die Platzierungsphase mit dem 60-Sekunden-Countdown beginnt für beide sofort.

### Spielfelder

Das eigene Spielfeld und das gegnerische Zielraster verwenden standardmäßig je ein Raster mit 10 × 10 Feldern. Auf dem eigenen Feld platziert der Spieler seine Flotte, im gegnerischen Zielraster wählt er seine Schusskoordinaten. Während der Partie erscheint das eigene Feld gleich groß unter dem gegnerischen Zielraster. Die Gestaltung im Browser ist in [Anzeige.md](Anzeige.md) beschrieben.

### Flotte und Platzierung

Vor der Platzierung stehen beiden Spielern dieselben Schiffslängen zur Verfügung. Jeder Spieler hat ein Schiff mit fünf Feldern, eines mit vier, zwei mit je drei und eines mit zwei Feldern zur Auswahl. Das sind insgesamt 17 Schiffsfelder pro Spieler. Welche Schiffstypen und Klassen diese Längen repräsentieren, darf je nach Nation unterschiedlich sein. Für Platzierung und Treffer zählt die Länge in Spielfeldern. Die recherchierten Vorbilder stehen in [Schiffstypen](Schiffstypen.md).

Mit dem Start einer Partie beginnt die Platzierungsphase. Der Computer erhält bei jeder neuen Partie eine zufällig ausgewählte Nation aus der Flottenauswahl. Im Mehrspielerspiel haben beide Spieler **60 Sekunden**, um ihre Schiffe verdeckt zu platzieren. Ein Countdown zeigt die verbleibende Zeit.

Die Schiffe werden waagerecht oder senkrecht platziert. Alle Schiffsfelder müssen innerhalb des Rasters liegen. Jedes Feld kann nur von einem Schiff belegt sein. Eine Berührung über eine Kante oder Ecke ist nur erlaubt, wenn die Schiffe parallel in benachbarten Zeilen oder Spalten liegen. Quer zueinander oder direkt hintereinander müssen sie Abstand halten.

Im Mehrspielerspiel können beide Spieler nach dem Platzieren aller fünf Schiffe ihre Aufstellung bestätigen. Die Aufstellung des jeweiligen Spielers ist danach fest. Sobald beide bestätigt haben, beginnt die Schussphase. Andernfalls endet die Platzierung mit Ablauf des Countdowns. Nur gültig platzierte Schiffe gehören zur Flotte, alle übrigen entfallen. Hat nur ein Spieler kein Schiff platziert, verliert er sofort. Hat keiner der beiden ein Schiff platziert, endet die Partie ohne Sieger. Andernfalls beginnt der Ersteller der Anfrage mit dem ersten Schuss.

## Schüsse und Treffer

Ein Zug beginnt, wenn eine Seite an der Reihe ist. Im Mehrspielerspiel hat der jeweils aktive Spieler **12 Sekunden**, um im gegnerischen Zielraster eine Koordinate wie `D4` auszuwählen. Ein Countdown zeigt die verbleibende Zeit. Ein Klick außerhalb des Spielfelds löst keinen Schuss aus und der Countdown läuft weiter. Verstreichen die 12 Sekunden ohne gültigen Schuss, wechselt der Zug automatisch zum Gegner. Der Computer wartet vor jedem Schuss **anderthalb Sekunden**.

Nach jedem gültigen Schuss wird das Ergebnis direkt auf dem betreffenden Raster markiert, soweit die Schwierigkeitsstufe es sichtbar macht. Ein sichtbarer gegnerischer Schuss erhält zusätzlich einen pulsierenden Feldrahmen im eigenen Raster. Bei Wasser wechselt der Zug unmittelbar zum Gegner. Nach einem Treffer, auch auf einem bereits getroffenen Feld, bleibt dieselbe Seite am Zug. Ein Mensch kann sofort erneut schießen. Für den Computer beginnt die Wartezeit von anderthalb Sekunden sofort nach dem Zugwechsel oder seinem Treffer. Beim Versenken des letzten gegnerischen Schiffs zeigt der Zugstatus das Ergebnis der Partie.

Für die normale Zielsuche verwendet der Computer seine bisherigen Schussmeldungen. Solange Treffer an noch schwimmenden Schiffen offen sind, verfolgt er diese Treffer. Erst bei der freien Suche kann das Spiel ein zufälliges Wasserfeld wählen, das der Computer noch nie beschossen oder inzwischen vergessen hat. Gibt es kein solches Feld, verwendet er seine normale Zielwahl. Ein auf diese Weise gewählter Wasserschuss beendet den Zug und geht wie jeder andere Wasserschuss ins Gedächtnis ein.

- **Wasser** erscheint bei einem Schuss auf ein leeres Feld, auch wenn es schon einmal beschossen wurde. Danach ist der andere Spieler am Zug.
- **Neuer Treffer** erscheint, wenn ein Schiffsfeld zum ersten Mal getroffen wird.
- Bei einem erneuten Schuss auf ein bereits getroffenes Schiffsfeld darf der Spieler noch einmal schießen. Das Schiff nimmt dabei keinen weiteren Schaden.
- **Schiff versenkt** erscheint, wenn das letzte ungetroffene Feld eines Schiffs getroffen wird. Dieser Schuss zählt als neuer Treffer und gibt den Schiffstyp bekannt. Mit dem Ergebnis ertönt zufällig eines der sieben SOS-Sendesignale.

## Protokolltafel

Die Protokolltafel zeigt links den Gegner und rechts den Spieler. In beiden Bereichen stehen die Zahlen direkt neben den jeweiligen Bezeichnungen.

- **Abgegebene Schüsse** zählt alle gültigen Schüsse der jeweiligen Seite, einschließlich Wasserschüssen und Wiederholungen.
- **Treffer** zählt jedes erstmals getroffene Schiffsfeld der anderen Seite einmal. Der Treffer, der ein Schiff versenkt, zählt mit.

Die Zähler erscheinen ohne Koordinaten. Erst beim Versenken erfährt der schießende Spieler den Typ des getroffenen Schiffs. Das versenkte Schiff erscheint mit seinem **Typ**, seiner **Länge in Feldern** und dem Status **Versenkt** auf der Tafel. Der Eintrag bleibt in allen Schwierigkeitsstufen bis zum Ende der Partie sichtbar. Die Anzahl der verbleibenden unbekannten gegnerischen Schiffe bezieht sich auf die tatsächlich platzierte Flotte.

| Gegnerische Flotte | Eigene Flotte |
| --- | --- |
| Abgegebene Schüsse **7** | Abgegebene Schüsse **8** |
| Treffer **2** | Treffer **3** |

Unter dem Zielraster zeigt das **eigene Raster** die Schiffspositionen. Die Sichtbarkeitsregel für gegnerische Schüsse gilt auf allen Stufen gleich.

## Schwierigkeitsstufen

Für die Anzeige im Zielraster und das Gedächtnis des Computers zählt jedes aktuell bekannte Wasserfeld einmal. Ist die garantierte Anzahl erreicht, kann bei jedem neu hinzukommenden Wasserfeld nach der jeweiligen Wahrscheinlichkeit eines der bisher bekannten Felder wegfallen. Dieses Feld wird gleichverteilt aus den bisher bekannten Wasserfeldern gewählt. Das neu beschossene Wasserfeld wird angezeigt oder gespeichert. Treffer und Wiederholungen auf bereits bekannten Wasserfeldern lösen kein Vergessen aus. Vergessene Wasserfelder gelten für den Computer wieder als unbeschossen. Das eigene Raster pulsiert bei Wasser blau und bei einem Schiffstreffer gelb-orange.

### Leicht

- **Zielraster** – Alle Schiffstreffer bleiben sichtbar. Bis zu **25 Wasserfelder** bleiben zuverlässig markiert. Darüber hinaus verschwindet mit **70 %** Wahrscheinlichkeit eine ältere Wassermarkierung.
- **Eigenes Raster** – Schiffstreffer erscheinen sofort. Ein Wasserschuss wird markiert, wenn sein Feld höchstens zwei waagerechte, senkrechte oder diagonale Schritte von einem eigenen Schiffsfeld entfernt liegt. Bei sichtbaren Schüssen pulsiert der Feldrahmen, bei verborgenen Wasserschüssen der Außenrahmen.
- **Computer** – Er merkt sich bis zu **15 Wasserfelder** zuverlässig. Darüber hinaus vergisst er mit **73 %** Wahrscheinlichkeit ein älteres bekanntes Wasserfeld. Treffer behält er dauerhaft im Gedächtnis.
Nach einem Treffer probiert er die direkten Nachbarfelder. Liegen zwei Treffer direkt nebeneinander, bevorzugt er freie Felder entlang ihrer Linie. Bei der freien Suche bevorzugt er ein Schachbrettmuster. Mit **16 %** Wahrscheinlichkeit löst das Spiel stattdessen einen erzwungenen Wasserschuss aus.

### Mittel

- **Zielraster** – Alle Schiffstreffer bleiben sichtbar. Bis zu **21 Wasserfelder** bleiben zuverlässig markiert. Darüber hinaus verschwindet mit **65 %** Wahrscheinlichkeit eine ältere Wassermarkierung.
- **Eigenes Raster** – Gleiche Anzeige wie auf „Leicht“.
- **Computer** – Er merkt sich bis zu **15 Wasserfelder** zuverlässig. Darüber hinaus vergisst er mit **57 %** Wahrscheinlichkeit ein älteres bekanntes Wasserfeld. Treffer behält er dauerhaft im Gedächtnis.
Nach einem Treffer probiert er die direkten Nachbarfelder. Liegen zwei Treffer direkt nebeneinander, bevorzugt er freie Felder entlang ihrer Linie. Bei der freien Suche bevorzugt er ein Schachbrettmuster. Mit **8 %** Wahrscheinlichkeit löst das Spiel stattdessen einen erzwungenen Wasserschuss aus.

### Schwer

- **Zielraster** – Alle Schiffstreffer bleiben sichtbar. Bis zu **17 Wasserfelder** bleiben zuverlässig markiert. Darüber hinaus verschwindet mit **60 %** Wahrscheinlichkeit eine ältere Wassermarkierung.
- **Eigenes Raster** – Gleiche Anzeige wie auf „Leicht“.
- **Computer** – Er merkt sich bis zu **15 Wasserfelder** zuverlässig. Darüber hinaus vergisst er mit **55 %** Wahrscheinlichkeit ein älteres bekanntes Wasserfeld. Treffer behält er dauerhaft im Gedächtnis.
Er bewertet mögliche Schiffslagen anhand der bisherigen Treffer, Versenkungen, bekannten Wasserschüsse, möglichen Schiffslängen und Platzierungsregeln. Bei der freien Suche löst das Spiel mit **4 %** Wahrscheinlichkeit stattdessen einen erzwungenen Wasserschuss aus.

## Spielende

Sobald ein Spieler das letzte gegnerische Schiff versenkt, endet die Partie und er gewinnt. Die Partie kann auch schon mit Ablauf der Platzierungszeit enden, wenn mindestens eine Flotte leer ist.

Nach einer Niederlage zeigt das Zielraster alle übrigen gegnerischen Schiffe mit ihren SVG-Grafiken an ihrer tatsächlichen Position und Ausrichtung.

Der Abspann zeigt beide Flotten und spielt die Schiffstreffer in 15 Sekunden in ihrer tatsächlichen Reihenfolge ab. Versenkte Schiffe sinken. Die überlebenden Schiffe der Siegerflotte erreichen die Mitte der Wasserlinie. Über „Spielfeld ansehen“ lässt sich die Ergebnisansicht schließen.
