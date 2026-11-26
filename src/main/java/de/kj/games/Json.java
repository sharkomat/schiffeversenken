package de.kj.games;

/**
 * Stellt eine Hilfsfunktion für die im Projekt von Hand erzeugten JSON-Antworten bereit. Sie setzt Text in
 * Anführungszeichen und maskiert darin Anführungszeichen sowie Backslashes.
 */
final class Json
{
    private Json()
    {
    }

    static String inAnfuehrungszeichen(String text)
    {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
