# Schiffeversenken – Spielanleitung

Der Spieler tritt gegen den Computer oder gegen einen anderen Menschen an. Beide Seiten platzieren ihre Flotten verdeckt und schießen auf das gegnerische Spielfeld. Nach jedem Treffer darf dieselbe Seite erneut schießen. Bei einem Wasserschuss wechselt der Zug. Wer zuerst alle gegnerischen Schiffe versenkt, gewinnt.

## Vorbereitung

### Partie starten

Für alle drei Startwege trägt der Spieler zuerst seinen Namen ein. Beim direkten Spiel gegen den Computer und beim Erstellen einer Spielanfrage wählt der Spieler die Schwierigkeitsstufe. **Mittel** ist voreingestellt. Die Stufe bestimmt, welche Schussmarkierungen im Zielraster und auf der Minikarte sichtbar sind.

- **Gegen den Computer spielen** – Die Platzierungsphase beginnt sofort.
- **Eine Spielanfrage erstellen** – Die Anfrage erscheint mit Spielername und gewählter Stufe auf der Startseite. Der Ersteller wartet dort auf einen Gegner. Nimmt niemand die Anfrage an, verschwindet sie spätestens **drei Minuten nach dem Erstellen**.
- **Einer Spielanfrage beitreten** – Beide Spieler übernehmen die in der Anfrage festgelegte Stufe. Sobald der zweite Spieler beitritt, verschwindet die Anfrage aus der Liste und die Platzierungsphase mit dem 60-Sekunden-Countdown beginnt für beide sofort.

### Spielfelder

Das eigene Spielfeld und das gegnerische Zielraster verwenden standardmäßig je ein Raster mit 10 × 10 Feldern. Auf dem eigenen Feld platziert der Spieler seine Flotte, im gegnerischen Zielraster wählt er seine Schusskoordinaten. Während der Partie erscheint das eigene Feld als kleinere Minikarte. Die Gestaltung im Browser ist in [Anzeige.md](Anzeige.md) beschrieben.

### Flotte und Platzierung

Vor der Platzierung stehen beiden Spielern dieselben Schiffslängen zur Verfügung. Jeder Spieler hat ein Schiff mit fünf Feldern, eines mit vier, zwei mit je drei und eines mit zwei Feldern zur Auswahl. Das sind insgesamt 17 Schiffsfelder pro Spieler. Welche Schiffstypen und Klassen diese Längen repräsentieren, darf je nach Nation unterschiedlich sein. Für Platzierung und Treffer zählt die Länge in Spielfeldern. Die recherchierten Vorbilder stehen in [Schiffstypen](Schiffstypen.md).

Mit dem Start einer Partie beginnt die Platzierungsphase. Beide Spieler haben **60 Sekunden**, um ihre Schiffe verdeckt zu platzieren. Ein Countdown zeigt die verbleibende Zeit.

Die Schiffe werden waagerecht oder senkrecht platziert. Alle Schiffsfelder müssen innerhalb des Rasters liegen. Jedes Feld kann nur von einem Schiff belegt sein. Eine Berührung über eine Kante oder Ecke ist nur erlaubt, wenn die Schiffe parallel in benachbarten Zeilen oder Spalten liegen. Quer zueinander oder direkt hintereinander müssen sie Abstand halten.

Mit Ablauf des Countdowns endet die Platzierung. Nur gültig platzierte Schiffe gehören zur Flotte, alle übrigen entfallen. Ab dann sind die Schiffspositionen fest. Hat nur ein Spieler kein Schiff platziert, verliert er sofort. Hat keiner der beiden ein Schiff platziert, endet die Partie ohne Sieger. Andernfalls beginnt die Schussphase mit den tatsächlich platzierten Flotten.

## Schüsse und Treffer

Ein Zug beginnt, wenn eine Seite an der Reihe ist. Ein menschlicher Spieler hat **12 Sekunden**, um im gegnerischen Zielraster eine Koordinate wie `D4` auszuwählen. Ein Countdown zeigt die verbleibende Zeit. Ein Klick außerhalb des Spielfelds löst keinen Schuss aus und der Countdown läuft weiter. Verstreichen die 12 Sekunden ohne gültigen Schuss, wechselt der Zug automatisch zum Gegner. Der Computer wartet vor jedem Schuss **anderthalb Sekunden**.

Nach jedem gültigen Schuss wird das Ergebnis direkt auf dem betreffenden Raster markiert, soweit die Schwierigkeitsstufe es sichtbar macht. Ein sichtbarer Computerschuss erhält zusätzlich einen pulsierenden Feldrahmen auf der Minikarte. Bei Wasser wechselt der Zug unmittelbar zum Gegner. Nach einem Treffer, auch auf einem bereits getroffenen Feld, bleibt dieselbe Seite am Zug. Ein Mensch kann sofort erneut schießen. Für den Computer beginnt die Wartezeit von anderthalb Sekunden sofort nach dem Zugwechsel oder seinem Treffer. Beim Versenken des letzten gegnerischen Schiffs zeigt der Zugstatus das Ergebnis der Partie.

Der Computer merkt sich Treffer, Wasserschüsse und Versenkungen. Aus der gemeldeten Schiffslänge und den Treffern zum Zeitpunkt der Versenkung leitet er die möglichen Lagen des versenkten Schiffs ab. Passen mehrere Lagen zu den Platzierungsregeln und den bisherigen Schüssen, prüft er sie weiter. Bei offenen Treffern bevorzugt er Positionen, die mehrere Treffer erklären und nur noch wenige ungetroffene Felder haben. Für die Zielsuche erhält er dieselben Schussmeldungen wie ein Mensch und kennt die möglichen Schiffslängen.

- **Wasser** erscheint bei einem Schuss auf ein leeres Feld, auch wenn es schon einmal beschossen wurde. Danach ist der andere Spieler am Zug.
- **Neuer Treffer** erscheint, wenn ein Schiffsfeld zum ersten Mal getroffen wird.
- **Alter Treffer** erscheint, wenn ein bereits getroffenes Schiffsfeld erneut getroffen wird. Der Schuss verursacht keinen weiteren Schaden und der Spieler darf erneut schießen.
- **Schiff versenkt** erscheint, wenn das letzte ungetroffene Feld eines Schiffs getroffen wird. Dieser Schuss zählt als neuer Treffer und gibt den Schiffstyp bekannt.

## Protokolltafel

Die eigene Protokolltafel zeigt die Zähler für die laufende Partie.

- **Abgegebene Schüsse** zählt jeden eigenen Schuss auf eine gültige Koordinate, einschließlich Wasser, neuer Treffer und alter Treffer.
- **Neue Treffer** zählt jedes erstmals getroffene gegnerische Schiffsfeld einmal. Der Treffer, der ein Schiff versenkt, zählt mit.
- **Alte Treffer** zählt jeden erneuten Treffer auf ein bereits getroffenes gegnerisches Schiffsfeld. Zwei weitere Schüsse auf dasselbe getroffene Feld ergeben zwei alte Treffer.

Die Zähler erscheinen ohne Koordinaten. Erst beim Versenken erfährt der schießende Spieler den Typ des getroffenen Schiffs. Das versenkte Schiff erscheint mit seinem **Typ**, seiner **Länge in Feldern** und dem Status **Versenkt** auf der Tafel. Der Eintrag bleibt in allen Schwierigkeitsstufen bis zum Ende der Partie sichtbar. Die Anzahl der verbleibenden unbekannten gegnerischen Schiffe bezieht sich auf die tatsächlich platzierte Flotte.

> **Abgegebene Schüsse** 8 
> **Neue Treffer** 3 
> **Alte Treffer** 1  
> 
> **Versenkte Schiffe:**<br>
> **Kreuzer** · 3 Felder · **Versenkt**  
> 
> **Unbekannte Schiffe verbleibend** 4

Unter der Tafel zeigt eine separate **Minikarte** das eigene 10 × 10-Raster mit den Schiffspositionen. Welche gegnerischen Schüsse darauf markiert werden, hängt von der Schwierigkeitsstufe ab.

## Schwierigkeitsstufen

Für die Anzeige im Zielraster und das Gedächtnis des Computers zählt jeder eigene Schuss, auch ein Folgeschuss oder eine Wiederholung. Ein erneuter Schuss auf dasselbe Wasserfeld erneuert die Markierung oder Erinnerung. Vergessene Wasserfelder gelten für den Computer wieder als unbeschossen. Die Minikarte pulsiert bei Wasser blau und bei einem Schiffstreffer gelb-orange.

### Leicht

- **Zielraster** – Alle Schiffstreffer und Wasserschüsse bleiben sichtbar.
- **Minikarte** – Alle gegnerischen Schiffstreffer und Wasserschüsse erscheinen sofort auf ihren jeweiligen Feldern. Der Rahmen des beschossenen Feldes pulsiert.
- **Computer** – Er merkt sich Wasserschüsse aus seinen letzten **50 eigenen Schüssen** und behält alle Schiffstreffer und Versenkungen bis zum Spielende im Gedächtnis. Bei **25 %** seiner Schüsse wählt er gleichverteilt unter den Feldern, die er für unbeschossen hält. Bei seinen übrigen Schüssen sucht er taktisch anhand seiner bekannten Schüsse und der Platzierungsregeln.

### Mittel

- **Zielraster** – Alle Schiffstreffer bleiben sichtbar. Wasserschüsse bleiben markiert, solange sie unter den letzten **30 eigenen Schüssen** sind.
- **Minikarte** – Schiffstreffer erscheinen sofort. Ein Wasserschuss wird markiert, wenn sein Feld unmittelbar waagerecht, senkrecht oder diagonal an ein eigenes Schiffsfeld grenzt. Bei sichtbaren Schüssen pulsiert der Feldrahmen, bei verborgenem Wasser der Außenrahmen.
- **Computer** – Er merkt sich Wasserschüsse aus seinen letzten **30 eigenen Schüssen** und behält alle Schiffstreffer und Versenkungen bis zum Spielende im Gedächtnis. Bei **15 %** seiner Schüsse wählt er gleichverteilt unter den Feldern, die er für unbeschossen hält. Bei seinen übrigen Schüssen sucht er taktisch anhand seiner bekannten Schüsse und der Platzierungsregeln.

### Schwer

- **Zielraster** – Alle Schiffstreffer bleiben sichtbar. Wasserschüsse bleiben markiert, solange sie unter den letzten **20 eigenen Schüssen** sind.
- **Minikarte** – Schiffstreffer erscheinen sofort. Ein Wasserschuss wird markiert, wenn sein Feld unmittelbar waagerecht, senkrecht oder diagonal an ein eigenes Schiffsfeld grenzt. Bei sichtbaren Schüssen pulsiert der Feldrahmen, bei verborgenem Wasser der Außenrahmen.
- **Computer** – Er merkt sich Wasserschüsse aus seinen letzten **20 eigenen Schüssen** und behält alle Schiffstreffer und Versenkungen bis zum Spielende im Gedächtnis. Bei **8 %** seiner Schüsse wählt er gleichverteilt unter den Feldern, die er für unbeschossen hält. Bei seinen übrigen Schüssen sucht er taktisch anhand seiner bekannten Schüsse und der Platzierungsregeln.

## Spielende

Sobald ein Spieler das letzte gegnerische Schiff versenkt, endet die Partie und er gewinnt. Die Partie kann auch schon mit Ablauf der Platzierungszeit enden, wenn mindestens eine Flotte leer ist.
