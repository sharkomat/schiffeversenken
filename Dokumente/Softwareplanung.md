# Schiffeversenken – Softwareplanung

Die erste Fassung soll lokal im Browser laufen und eine vollständige Partie gegen den Computer ermöglichen. Danach wächst das Spiel in kleinen Stufen. Jede Stufe endet mit einem Ergebnis, das sich im Browser vorführen und prüfen lässt.

## Grundlagen

- [Anzeige.md](Anzeige.md) beschreibt die Startseite, die Schiffsplatzierung und die Spielansicht. Die eingebundenen Skizzen dienen als Vorlage für Layout und Beschriftungen.
- [Spielverlauf.md](Spielverlauf.md) beschreibt Platzierung, Schüsse, Zugwechsel, Zeitlimits, Schwierigkeitsstufen und Spielanfragen.
- [Schiffstypen.md](Schiffstypen.md) liefert die Flotten für sieben Nationen. Alle verwenden die Längen **5, 4, 3, 3 und 2**. Für die Platzierungsregeln zählen die belegten Felder.
- Die **35 Bildquellen.md** unter [Schiffsbilder](Schiffsbilder/) dokumentieren Fotos, Quellen und Lizenzangaben zu den Schiffsklassen. Sie dienen als Referenz für die Zuordnung und spätere Ausarbeitung der [vorhandenen SVGs](Schiffsbilder/Vektoren/). Für ein Foto, das später in der Spieloberfläche erscheint, werden Quelle und Lizenz aus der zugehörigen Datei übernommen.

## Stufe 1 · Erste spielbare Browserfassung

1. **Ansicht aufbauen.** Eine lokale Seite mit HTML, CSS und JavaScript zeigt das große anklickbare 10 × 10-Zielraster links, die ersten Schusszähler rechts oben und die kleine eigene Karte darunter. Das vorhandene Java-21-/Maven-Projekt kann die Seite lokal per HTTP bereitstellen. Als erste Kontrolle genügt es, ein Zielfeld anzuklicken und seine Koordinate anzeigen zu lassen.
2. **Partie spielbar machen.** „Spielen“ startet eine Partie gegen einen einfachen Computergegner. **Deutschland** ist die Standardnation für beide Seiten. Bei jedem Start werden die Schiffe beider Flotten automatisch und zufällig platziert. Jedes Schiff liegt waagerecht oder senkrecht vollständig im Raster. Berührungen sind nur bei parallelen Schiffen in benachbarten Zeilen oder Spalten erlaubt. Eigene Schiffe erscheinen als schwarze Felder. Treffer erscheinen als Flammen, Wasserschüsse als hellblaue Punkte.
3. **Grundregeln umsetzen.** Ein Treffer erlaubt einen weiteren Schuss. Wasser wechselt den Zug. Neue und alte Treffer zählen getrennt. Versenkte Schiffe und das Spielende werden angezeigt. Für diese Fassung bleiben die Schussmarkierungen sichtbar. Java verwaltet die Partiedaten im Arbeitsspeicher des lokalen Servers. JavaScript zeigt den zurückgegebenen Spielstand an.

**Abnahme:** Eine Partie lässt sich im Browser starten, bis zum Sieg oder zur Niederlage spielen und neu starten. Bei jedem Start werden für beide Seiten gültige Startaufstellungen mit den fünf Schiffen der deutschen Flotte neu ausgelost. Ein wiederholter Treffer erhöht nur den Zähler für alte Treffer und verursacht keinen weiteren Schaden.

**Lokal starten:** Mit Java 21 im Projektordner zuerst `mvn compile` und dann `java -cp target/classes de.kj.games.Main` ausführen. Alternativ `Main.main()` in IntelliJ starten. Die Seite ist unter [localhost:8080](http://127.0.0.1:8080/) erreichbar.

## Stufe 2 · Startfenster und eigene Schiffe platzieren

1. Startfenster mit Namensfeld und „Gegen Computer spielen“ ergänzen.
2. Auf der Platzierungsseite links die Nation und die verfügbaren Schiffe mit Typ, Länge und einer Vorschau aus den [vorhandenen SVGs](Schiffsbilder/Vektoren/) anzeigen. Die Grafik wird nach Schiffstyp zugeordnet. Rechts steht das große eigene Raster. Die Platzierung zunächst durch Schiffsauswahl und Klick aufs Raster ermöglichen. Eine einfache Schaltfläche wechselt zwischen waagerechter und senkrechter Ausrichtung.
3. Drag-and-drop aus der Schiffsliste ergänzen. Beim Ziehen hebt die Vorschau die vorgesehenen Felder hervor. Nach dem Ablegen erscheinen belegte Felder schwarz. Beide Bedienwege prüfen dieselben Regeln für Rastergrenzen, Überlappung und Berührungen wie die automatische Platzierung in Stufe 1.
4. Für diese Stufe startet eine Schaltfläche „Platzierung abschließen“ die Partie mit der fertig platzierten Flotte. Die nächste Stufe übernimmt den Übergang per Countdown.

**Abnahme:** Die gewählte Nation bestimmt die angebotenen Schiffe mit ihren SVG-Vorschauen. Der Spieler kann ein Schiff aus der Liste aufs Raster ziehen und seine Flotte anschließend in der Minikarte wiederfinden. Ungültige Platzierungen verändern das Spielfeld nicht.

## Stufe 3 · Countdown und Rückmeldung

- **60 Sekunden Platzierung.** Bei Ablauf startet die Partie mit den gültig platzierten Schiffen. Eine leere Flotte führt zum Spielende gemäß Spielverlauf.
- **Zufällige Platzierung.** Alternativ zum manuellen Setzen platziert „Zufällig platzieren“ die gesamte Flotte der gewählten Nation auf dem eigenen Raster. Für beide Varianten gelten dieselben Platzierungsregeln.
- **12 Sekunden pro Schuss für Menschen.** Ohne gültigen Schuss wechselt der Zug. Der Computer wartet vor seinem ersten Schuss **anderthalb Sekunden**.
- **Sofortige Rückmeldung pro Schuss.** Das Ergebnis wird direkt im Raster markiert. Bei sichtbaren Computerschüssen pulsiert zusätzlich der Rahmen des Feldes auf der Minikarte. Bei einem Treffer bleibt dieselbe Seite am Zug. Der Computer wartet auch vor jedem Folgeschuss **anderthalb Sekunden**, gerechnet ab seinem letzten Treffer.
- Countdown in der Platzierungsansicht links und während der Partie im eigenen Rahmen über der Protokolltafel anzeigen. Der Rahmen zeigt durchgehend an, wer am Zug ist.

**Abnahme:** „Zufällig platzieren“ erzeugt eine vollständige, gültige Aufstellung für die gewählte Nation. Ein verspäteter Klick löst keinen Schuss aus. Nach einem Treffer kann ein Mensch sofort erneut schießen und hat dafür wieder 12 Sekunden. Der Computer feuert seinen ersten Schuss und jeden Folgeschuss nach jeweils anderthalb Sekunden ab. Das Ergebnis erscheint sofort und der Zugstatus bleibt sichtbar.

## Stufe 4 · Schwierigkeitsstufen

Die Auswahl **Leicht**, **Mittel** und **Schwer** erscheint im Startfenster. Standard ist **Mittel**. Die Stufe steuert die Schussmarkierungen und die Zielwahl des Computers. Die Treffer- und Versenkungsregeln bleiben für alle Stufen gleich.

| Stufe | Zielraster | Minikarte | Zielwahl des Computers |
| --- | --- | --- | --- |
| **Leicht** | Alle Schiffstreffer und Wasserschüsse bleiben sichtbar. | Alle Schiffstreffer und Wasserschüsse bleiben sichtbar. | Bei **25 %** der Schüsse wählt er zufällig. |
| **Mittel** | Alle Schiffstreffer bleiben sichtbar. Wasserschüsse bleiben sichtbar, solange sie unter den letzten **30 eigenen Schüssen** sind. | Schiffstreffer sind sofort sichtbar. Wasserschüsse werden nur auf Feldern unmittelbar waagerecht, senkrecht oder diagonal neben einem eigenen Schiffsfeld markiert. | Bei **15 %** der Schüsse wählt er zufällig. |
| **Schwer** | Alle Schiffstreffer bleiben sichtbar. Wasserschüsse bleiben sichtbar, solange sie unter den letzten **20 eigenen Schüssen** sind. | Gleiche Anzeige wie auf „Mittel“. | Bei **8 %** der Schüsse wählt er zufällig. |

Der Computer merkt sich Wasserschüsse aus seinen letzten **50** eigenen Schüssen auf „Leicht“, **30** auf „Mittel“ und **20** auf „Schwer“. Schiffstreffer und Versenkungen bleiben ihm bis zum Spielende bekannt. Auf beiden Seiten zählt jeder eigene Schuss, einschließlich Folgeschüssen und Wiederholungen. Ein erneuter Schuss auf dasselbe Wasserfeld erneuert die Markierung oder Erinnerung.

Außerhalb der Zufallsschüsse verwendet der Computer seine taktische Zielsuche. Bei einem Zufallsschuss wählt er gleichverteilt aus den Feldern, die er für unbeschossen hält. Ein vergessenes Wasserfeld kann dadurch oder bei der taktischen Zielsuche erneut beschossen werden.

**Pulsierende Rückmeldung auf der Minikarte.** Gibt die Schwierigkeitsstufe die Position des aktuellen gegnerischen Schusses frei, pulsiert wie auf „Leicht“ der Rahmen des betroffenen Feldes. Bleibt die Position verborgen, pulsiert stattdessen der Außenrahmen der gesamten Minikarte. Die Rückmeldung zeigt dann nur die Art des Ergebnisses. Für beide Rahmen gelten die [bestehenden Farben und die Dauer](Anzeige.md#minikarte). Rund drei Sekunden lang wechselt die Farbe bei Wasser von Hellblau zu Dunkelblau und bei einem Schiffstreffer von Hellgelb zu Dunkelorange. Die Partie läuft während des Pulsierens weiter.

**Abnahme:** Dieselbe Schussfolge erzeugt in allen Stufen dieselben Trefferzahlen. Der Computer vergisst einen Wasserschuss nach 50 weiteren eigenen Schüssen auf „Leicht“, 30 auf „Mittel“ und 20 auf „Schwer“. Das Zielraster blendet Wasser nach 30 weiteren eigenen Schüssen auf „Mittel“ und nach 20 auf „Schwer“ aus. Auf „Leicht“ bleiben die Wassermarkierungen im Zielraster sichtbar. Schiffstreffer bleiben sichtbar und dem Computer bekannt. Auf „Mittel“ und „Schwer“ pulsiert bei angrenzenden Wasserschüssen das jeweilige Feld blau, bei weiter entfernten Wasserschüssen der Außenrahmen. Bei einem Schiffstreffer pulsiert das betroffene Feld ab dem ersten Treffer gelb-orange. Kontrollierte Zufallswerte prüfen die Zufallsquoten auf allen drei Stufen. Zufallsschüsse gehen nur auf Felder, die der Computer für unbeschossen hält.

## Stufe 5 · SVG-Grafiken in der Partie

1. Die digitale Anzeige rechts oben mit zwei umrahmten Flottenbereichen ausstatten. Eigene Schiffe erscheinen dort mit den bereits zugeordneten SVGs gleich ausgerichtet untereinander. Rote Markierungen zeigen die nach der Schwierigkeitsstufe sichtbaren Treffer an den betreffenden Schiffsabschnitten.
2. Gegnerische Schiffe erst beim Versenken mit Grafik, Typ, Länge und Status anzeigen. Von der übrigen gegnerischen Flotte erscheint nur die Anzahl.
3. Die SVG-Darstellung auf dem eigenen Raster anhand der tatsächlichen Schiffsposition und Ausrichtung ergänzen.

**Abnahme:** Die gleiche Position ergibt mit schwarzen Feldern oder mit SVG dieselben belegten Koordinaten. Eine ausgeblendete Trefferinformation lässt sich auch in der Flottenübersicht nicht ablesen.

## Stufe 6 · Spielanfragen erstellen und anzeigen

Der Java-Server verwaltet offene Anfragen zunächst im Arbeitsspeicher. Die Startseite erhält „Spielanfrage erstellen“ und eine Liste mit Name, Schwierigkeitsstufe und laufender Wartezeit im Format **MM:SS**. Zuerst das Erstellen und Anzeigen in zwei Browserfenstern prüfen. Danach das automatische Entfernen nach spätestens **drei Minuten** ergänzen.

**Abnahme:** Eine Anfrage aus Fenster A erscheint in Fenster B mit derselben Schwierigkeitsstufe. Nach Ablauf der Wartezeit verschwindet sie aus beiden Fenstern.

## Stufe 7 · Beitreten und gegen einen Menschen spielen

„Beitreten“ übernimmt die Stufe der Anfrage. Der Server vergibt die Anfrage an genau einen Gegner, entfernt sie aus der Liste und startet für beide Spieler sofort die Platzierung. Für gemeinsame Partien entscheidet der Server über Platzierungen, gültige Schüsse, Zugwechsel, Zeitlimits und Spielende. Er liefert jedem Browser nur seinen sichtbaren Ausschnitt des Spielstands. Die Grundregeln der ersten Fassung dienen als Vorlage für dieselben Abläufe im Mehrspielerbetrieb.

**Abnahme:** Zwei getrennte Browserfenster spielen eine Partie vollständig. Ein drittes Fenster kann einer angenommenen oder abgelaufenen Anfrage nicht mehr beitreten. Verdeckte gegnerische Positionen erscheinen in keinem der beiden Browserdatenstände.

## Stufe 8 · Score berechnen und Bestenliste anzeigen

1. **Anzeige mit Beispieldaten.** Die aufklappbare **Top 30** darstellen. Platz 1 bleibt eingeklappt mit Name, gespielter Nation, Punkten und Datum sichtbar. Die geöffnete Liste verwendet diese Angaben für jeden Eintrag.
2. **Wertung erarbeiten.** In dieser Stufe die Scoreformel festlegen. Dazu gehören die Sortierung bei gleicher Punktzahl und die Frage, welche Computer- und Mehrspielerpartien sowie Schwierigkeitsstufen in dieselbe Wertung eingehen.
3. **Echte Ergebnisse anzeigen.** Nach Abschluss einer gewerteten Partie berechnet der Server den Score und übernimmt das Ergebnis in die Bestenliste im Arbeitsspeicher. Dauerhaftes Speichern und Laden folgt in Stufe 9.

**Abnahme:** Aus einem vorgegebenen Partieergebnis entsteht der nach der vereinbarten Formel erwartete Score. Die Top 30 zeigt die sortierten Ergebnisse, und Platz 1 entspricht dem ersten Eintrag.

## Stufe 9 · Bestenliste dauerhaft speichern und laden

Abgeschlossene gewertete Partien werden serverseitig gespeichert. Ein Ranglisteneintrag enthält Spielername, gespielte Nation, Punktzahl und Datum. Eine Partie erzeugt höchstens einen Eintrag pro gewertetem Spieler. Die Startseite lädt die Top 30 aus dem gewählten Speicher.

- **Datenbank** – Das konkrete System hängt vom Hosting ab. Bei IONOS kommt die im Tarif verfügbare Datenbank infrage. Auf der DiskStation lässt sich die Datenbank unabhängig vom Webhosting betreiben, wenn die Laufzeit dafür verfügbar ist.
- **Editierbare Datei** – Für eine einzelne Serverinstanz ist eine JSON-Datei in einem dauerhaft beschreibbaren Datenordner eine Alternative. Der Server koordiniert gleichzeitige Schreibzugriffe und aktualisiert die Datei so, dass ein abgebrochener Schreibvorgang den bestehenden Stand nicht überschreibt. Auf der DiskStation kann ein Ordner mit Schreibrechten in den Anwendungscontainer eingebunden werden.

Vor der Wahl prüfen, wo das geplante Java-Backend dauerhaft laufen kann und welchen Speicher es dort erreicht. Die Browseroberfläche liest die Rangliste über den Server. Das Speicherformat bleibt für sie gleichgültig.

**Abnahme:** Nach einem Serverneustart sind die gespeicherten Ergebnisse wieder sichtbar. Gleichzeitige Abschlüsse gehen vollständig in die Wertung ein. Ein erneut übertragenes Ergebnis führt zu keinem doppelten Eintrag.

## Offene Entscheidungen

- In Stufe 8 die Scoreformel, die Sortierung bei Gleichstand und die Vergleichbarkeit von Spielarten und Schwierigkeitsstufen klären.
- In Stufe 9 den Hostingtarif oder die DiskStation als Ziel prüfen und zwischen Datenbank und Dateispeicherung entscheiden.
