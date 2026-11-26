# TODO-Liste

- WasserschussHistorie: Kommentar und Parameter istWasser prüfen; eine Aufteilung der Methode fuegeHinzu in zwei Methoden erwägen.
- WasserschussHistorie: Eine stufenweise Vergesslichkeit erwägen, die mit der Anzahl gemerkter Wasserfelder steigt. (erst umsetzen wenn ich es beschlossen habe - wir können aber gerne darüber reden. Gib mir deine Meinung dazu).
- Umbenennungen fortsetzen: Den verbleibenden Java-Code und die Tests auf eigene englische Klassen-, Methoden- und Variablennamen prüfen und eindeutig übersetzen. Bei fachlich unklaren Namen den Nutzer fragen. Schiffsnamen unverändert lassen; Umlaute in Java-Namen als ae, oe und ue schreiben. Java-Bibliotheksnamen sowie die bestehenden JSON-Felder, API-Pfade und Protokollwerte beibehalten. JUnit-Tests gelegentlich zur Kontrolle ausführen.
- Spielfeld.ergaenzeZufaellig: Prüfen, wie die ausführende Aktion und die Bedeutung des boolean-Rückgabewerts (true bei erfolgreicher vollständiger Ergänzung, false wenn keine vollständige Aufstellung gefunden wurde) klarer ausgedrückt werden können.
- ComputerZielsuche.istVertraeglich: Namen und gegebenenfalls Kommentar präzisieren; die Methode prüft, ob eine Schiffsplatzierung mit allen übergebenen anderen Schiffsplatzierungen nach den Platzierungsregeln vereinbar ist.
