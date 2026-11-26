package de.kj.games;

/**
 * Definiert die Schwierigkeitsstufen und ihre Einstellungen für das Wasserschussgedächtnis von Spieler und
 * Computer. Legt außerdem die Wahrscheinlichkeit für einen absichtlichen Wasserschuss des Computers fest.
 */
enum Schwierigkeitsgrad
{
    SEHR_LEICHT("Sehr leicht", 30, 50, 12, 83, 20),
    LEICHT("Leicht", 25, 60, 15, 73, 16),
    MITTEL("Mittel", 21, 65, 15, 57, 8),
    SCHWER("Schwer", 17, 70, 15, 55, 4);

    final String bezeichnung;
    final int spielerGarantierteWasserschuesse;
    final int spielerVergesslichkeitProzent;
    final int computerGarantierteWasserschuesse;
    final int computerVergesslichkeitProzent;
    final int absichtlicherWasserschussProzent;

    Schwierigkeitsgrad(String bezeichnung, int spielerGarantierteWasserschuesse, int spielerVergesslichkeitProzent,
                       int computerGarantierteWasserschuesse, int computerVergesslichkeitProzent, int absichtlicherWasserschussProzent)
    {
        this.bezeichnung = bezeichnung;
        this.spielerGarantierteWasserschuesse = spielerGarantierteWasserschuesse;
        this.spielerVergesslichkeitProzent = spielerVergesslichkeitProzent;
        this.computerGarantierteWasserschuesse = computerGarantierteWasserschuesse;
        this.computerVergesslichkeitProzent = computerVergesslichkeitProzent;
        this.absichtlicherWasserschussProzent = absichtlicherWasserschussProzent;
    }
}
