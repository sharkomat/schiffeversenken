# Schiffeversenken – Anzeige im Browser

## Modus und Countdown

Der Browsertitel lautet **„Schiffeversenken“**.

Der Countdown steht bei der Platzierung links in der Flottenauswahl unter **„Schiffe platzieren“** und beginnt bei **01:00**. Während der Partie steht der Zugstatus im eigenen Rahmen über der Protokolltafel. Dort steht **„Du bist drain“**, **„Der Computer ist dran“** oder bei einer Partie gegen einen Menschen dessen Spielername mit **„ist dran“**. Nach einem Sieg steht dort **„Du hast gewonnen“**. Im eigenen Zug zeigt der Rahmen den Schuss-Countdown ab 12 Sekunden unter **„Verbleibende Zeit“** an. Schussergebnisse werden direkt auf den Rastern markiert. Der Computer wartet ab Zugbeginn oder nach seinem Treffer anderthalb Sekunden bis zum nächsten Schuss.

## Layout vor der Partie

### Startfenster

![Startfenster mit Namensfeld und Schwierigkeitswahl links, offenen Spielanfragen rechts und einer aufklappbaren Bestenliste darunter. Platz 1 ist mit Name, Nation, Punktzahl und Datum sichtbar.](Spielfeld/Startfenster-Skizze.svg)

- **Links · Neue Partie** – Der Spieler trägt seinen Namen ein und wählt **Leicht**, **Mittel** oder **Schwer**. **Mittel** ist vorausgewählt. Die gewählte Stufe bestimmt die Anzeige der Wasserschüsse und die [Zielwahl des Computers](Spielverlauf.md#schwierigkeitsstufen). Darunter stehen die Schaltflächen **„Gegen Computer spielen“** und **„Spielanfrage erstellen“**.
- **Rechts · Offene Spielanfragen** – Jede Zeile zeigt den Namen des wartenden Spielers, die gewählte Schwierigkeitsstufe, die Spalte **„Wartet seit“** und **„Beitreten“**. Die Wartezeit wird seit dem Erstellen der Anfrage im Format **MM:SS** hochgezählt. Beim Beitritt gilt die Stufe der Anfrage. Nach dem Erstellen einer eigenen Anfrage zeigt die Startseite **„Warten auf einen Gegner“**. Ist die Liste leer, erscheint **„Keine offenen Spielanfragen“**.

- **Unten · Bestenliste** – Platz 1 ist auf der Startseite immer mit Spielernamen, gespielter Nation, Punktzahl und dem Datum des Rekords sichtbar. Über **„Top 30 anzeigen“** lässt sich die Rangliste mit bis zu 30 Einträgen aufklappen. Jeder Eintrag zeigt Platz, Spielername, die für diese Partie gewählte Nation, Punktzahl und Datum. Platz 1 bleibt auch beim Zuklappen sichtbar.

Die Punkteberechnung ist noch offen. Der Ablauf der Spielanfragen steht im [Spielverlauf](Spielverlauf.md#partie-starten).

### Schiffe platzieren

![Links die Nation, der Countdown und fünf SVG-Schiffsvorschauen. Rechts das eigene 10 × 10-Raster mit einer Platzierungsvorschau und den Schaltflächen darunter.](Spielfeld/Schiffsplatzierung-Skizze.svg)

- **Links · Flottenauswahl** – Der Spieler wählt die Nation und sieht die dazugehörigen [Schiffe](Schiffstypen.md). Wie in der Flottenübersicht steht die SVG-Grafik links. Rechts stehen Schiffstyp, Klasse und Länge in Feldern auf je einer Zeile. Bereits platzierte Schiffe sind ausgegraut. Darüber läuft im Mehrspielerspiel der **60-Sekunden-Countdown**.
- **Rechts · Eigenes Spielfeld** – Das eigene **10 × 10-Raster** ist während der Platzierung genauso groß wie das gegnerische Zielraster in der späteren Partie.
- **Drag-and-drop** – Der Spieler zieht ein Schiff aus der Liste in das Raster. Beim Ziehen hebt die Vorschau die Felder hervor, die das Schiff belegen würde. Nach dem Ablegen zeigt das Raster ein gültig platziertes Schiff als SVG-Grafik in seiner gewählten Ausrichtung.
- **Überschriften** – Spieltitel und Partiedaten stehen zentriert über der Platzierungsansicht. Bei „Eigenes Spielfeld“ zeigt der Ausrichtungsschalter die aktuelle Richtung als Doppelpfeil mit Text („↔ Waagerecht“ oder „↕ Senkrecht“).
- **Schaltflächen unter dem Spielfeld** – „Flotte zufällig setzen“ und „Fehlende Schiffe ergänzen“ stehen untereinander. Die zweite Schaltfläche erscheint bei einer teilweise gesetzten Flotte. Darunter stehen der Platzierungsfortschritt und „Platzierung abschließen“. Der Abschluss wird verfügbar, sobald alle Schiffe gesetzt sind.

Nach Ablauf des Countdowns wechselt die Ansicht gemäß [Spielverlauf](Spielverlauf.md#flotte-und-platzierung) zur Partie oder zum Ergebnis.

## Layout während der Partie

![Browserlayout mit zwei gleich großen Rastern untereinander links und zwei Flottenübersichten nebeneinander rechts](Spielfeld/Browserlayout-Skizze.svg)

Die Spielansicht besteht aus drei Elementen.

- **Links · Spielfelder** – Das Zielraster „Gegnerische Flotte“ liegt über dem gleich großen eigenen Raster „eigene Flotte“. Der Spieler schießt durch Klick auf ein Feld im oberen Raster.
- **Rechts oben · Titel und Zugstatus** – Der Spieltitel und „Neu spielen“ stehen neben den Rastern. Der Rahmen darunter zeigt, wer am Zug ist. Auf schmalen Bildschirmen stehen Titel und Zugstatus über den Rastern.
- **Darunter · Protokolltafel** – Links stehen die gegnerischen Schusszahlen und Flotteninfos, rechts die eigenen. Auf sehr schmalen Bildschirmen stehen die Bereiche untereinander. Die Legende für Treffer und Wasser steht unter der Protokolltafel.

## Spielfelder

Beide Spielfelder sehen aus wie Ausschnitte einer Seekarte und bestehen aus **10 × 10 gleich großen quadratischen Feldern**. Helle Gitterlinien unterteilen die blaue Wasserfläche. Die Zeilen A bis J stehen links, die Spalten 1 bis 10 oben.

## Schiffe

Schon während der Platzierung zeigt das eigene Raster jedes gesetzte Schiff mit derselben SVG-Grafik wie in der Partie.

Während der Partie zeigt das eigene Raster alle eigenen [Schiffsbilder im SVG-Format](Schiffsbilder/Vektoren/). Im Zielraster erscheint eine Schiffsgrafik, sobald das Schiff versenkt ist. Nach einer Niederlage erscheinen dort zusätzlich die übrigen gegnerischen Schiffe. Beide Raster zeigen die Grafiken entsprechend ihrer Position und Ausrichtung. Die Schussmarkierungen erscheinen darüber.

## Ergebnisansicht

Nach Spielende steht „Gewonnen“, „Verloren“ oder „Unentschieden“ über der Wasserlinie. Alle eingesetzten Schiffe erscheinen zu Beginn. Die gegnerische Flotte fährt vom linken Rand ein, die eigene vom rechten. Innerhalb von 15 Sekunden erscheinen die Schiffstreffer in ihrer tatsächlichen Reihenfolge als Flammen auf den jeweils getroffenen Abschnitten.

Beim Sieg begleitet eine Fanfare mit kräftigen Bläserklängen den Abspann. Sie läuft einmal und blendet beim Schließen der Ergebnisansicht aus. „Abspann erneut abspielen“ startet die Animation und beim Sieg auch die Musik von vorn. Niederlagen und Unentschieden haben derzeit keine Abspannmusik.

Unter Windows kann `%LOCALAPPDATA%/Schiffeversenken/result-win.wav` die mitgelieferte Fanfare lokal ersetzen. Die optionale Datei liegt außerhalb des Projekts und wird vom lokalen Spielserver verwendet.

Die Flammen bleiben auf den getroffenen Abschnitten sichtbar. Nach dem letzten Treffer brennt das Schiff noch einen kurzen Moment an der Oberfläche. Eine halbe Sekunde nach Beginn des Absinkens fangen die Flammen an zu erlöschen. Das Schiff sinkt etwa fünfeinhalb Sekunden lang mit dem Bug oder Heck voraus oder waagerecht. Unter den kleinen Wellen dominiert das Dunkelblau des Ozeans. Die sinkenden Schiffe scheinen dort zu rund 12 Prozent durch. Nach 15 Sekunden stehen die überlebenden Schiffe der Siegerflotte mittig auf der Wasserlinie. Das zuletzt versenkte Schiff kann danach noch absinken. Bei reduzierter Bewegung erscheint das Endbild sofort. „Spielfeld ansehen“ schließt die Ergebnisansicht.

## Darstellung auf dem Spielfeld

![Beschriftete Pfeile zeigen auf blaues Wasser, ein ungetroffenes Schiff, eine Flamme als Schiffstreffer und einen runden hellblauen Punkt als Wassertreffer.](Spielfeld/Schussmarkierungen-Kompakt.png)

- **Schiffstreffer** – Eine orange-gelbe Flamme mittig auf dem getroffenen Feld. Mehrere getroffene Felder desselben Schiffs erhalten je eine eigene Flamme. Bei einem erneuten Treffer auf dieselbe Koordinate bleibt es bei einer Flamme auf dem Feld.
- **Wasserschuss** – Ein einzelner runder, hellblauer Punkt mittig auf dem beschossenen Wasserfeld.

Im Zielraster bleiben Schiffstreffer sichtbar. Auf „Leicht“ bleiben mindestens 25 Wasserfelder markiert, sobald so viele getroffen wurden. Auf „Mittel“ sind es 21 und auf „Schwer“ 17. Bei jedem neu hinzukommenden Wasserfeld kann danach jeweils eine ältere Markierung verschwinden. Die Wahrscheinlichkeit beträgt auf „Leicht“ 70 %, auf „Mittel“ 65 % und auf „Schwer“ 60 %. Ein erneuter Schuss auf ein bereits markiertes Wasserfeld lässt die Markierungen unverändert.

Bei Wasser ertönt ein Platscher von 1,75 Sekunden. Bei Schiffstreffern ertönt ein dumpfes Klirren von 1,44 Sekunden. Beide Töne setzen mit dem Schussergebnis ein. Die Wasseraufnahme wurde gekürzt und in der Lautstärke angepasst. Die Metallaufnahme wurde gekürzt, tiefer abgespielt und in den Höhen gedämpft. [„water splash.wav“ von VacekH (CC0)](https://freesound.org/people/VacekH/sounds/189504/), [„Metallic clang“ von tomwilkinson (CC0)](https://freesound.org/people/tomwilkinson/sounds/568786/)


Beim Versenken eines Schiffs ertönt zufällig eines von sieben [SOS-Sendesignalen](Sounds/sos/Soundauswahl.html). Die Signale dauern etwa drei bis vier Sekunden.

## Protokolltafel

Die Protokolltafel wirkt wie ein Tabletbildschirm mit einem schmalen Rahmen und einer klar gegliederten digitalen Anzeige. „Protokolltafel“ ist der interne Name. Sichtbare Überschriften sind **„Gegnerische Flotte“** und **„Eigene Flotte“**. Beide Bereiche haben innerhalb des Tablets jeweils einen eigenen Rahmen.

Schüsse und Treffer stehen pro Flotte in zwei Zeilen auf einer gemeinsamen Fläche. Die Bezeichnungen sind links, die Zahlen rechts bündig ausgerichtet.

- **Gegnerische Flotte** – Oben stehen die abgegebenen Schüsse und Treffer des Gegners mit der Zahl direkt neben der Bezeichnung. Darunter erhält jedes versenkte gegnerische Schiff eine Zeile mit SVG-Grafik, roten Treffermarkierungen, Typ, Länge und Status „Versenkt“. Von den noch unbekannten gegnerischen Schiffen erscheint die Anzahl.
- **Eigene Flotte** – Oben stehen die abgegebenen Schüsse und Treffer des Spielers im selben Format. Darunter erhält jedes eigene Schiff eine Zeile mit seinem Typ, seiner Länge, seinem Zustand und der passenden SVG-Grafik. Die Zeilen haben eine feste Reihenfolge, und alle Grafiken sind gleich ausgerichtet. Eine rote Markierung zeigt jeden sichtbaren Treffer direkt auf dem betroffenen Schiffsabschnitt. Versenkte eigene Schiffe erhalten den Status „Versenkt“.

Die Flottenübersicht zeigt Treffer an eigenen Schiffen in allen [Schwierigkeitsstufen](Spielverlauf.md#schwierigkeitsstufen) ab dem ersten Treffer.

## Eigenes Raster

Das eigene 10 × 10-Raster unter dem Zielraster zeigt die tatsächlichen Schiffspositionen und -ausrichtungen. Gegnerische Schiffstreffer erscheinen sofort als Flammen. Auf allen Stufen erscheinen Wasserschüsse als hellblaue Punkte, wenn ihr Feld höchstens zwei Schritte von einem eigenen Schiffsfeld entfernt liegt. Jeder Schritt kann waagerecht, senkrecht oder diagonal verlaufen.

Bei einem sichtbaren Computerschuss pulsiert zusätzlich der Rahmen des beschossenen Feldes rund drei Sekunden lang. Bleibt ein Wasserschuss verborgen, pulsiert der Außenrahmen des eigenen Rasters. Bei Wasser wechselt die Farbe zwischen Hell- und Dunkelblau, bei einem Schiffstreffer zwischen Hellgelb und Dunkelorange. Währenddessen läuft die Partie normal weiter.
