package de.kj.games;

final class Json {
    private Json() {}

    static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
