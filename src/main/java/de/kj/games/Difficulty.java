package de.kj.games;

enum Difficulty {
    SEHR_LEICHT("Sehr leicht", 30, 50, 12, 83, 20),
    LEICHT("Leicht", 25, 60, 15, 73, 16),
    MITTEL("Mittel", 21, 65, 15, 57, 8),
    SCHWER("Schwer", 17, 70, 15, 55, 4);

    final String label;
    final int playerWaterGuaranteed;
    final int playerWaterForgetPercent;
    final int computerWaterGuaranteed;
    final int computerWaterForgetPercent;
    final int forcedMissPercent;

    Difficulty(String label, int playerWaterGuaranteed, int playerWaterForgetPercent,
               int computerWaterGuaranteed, int computerWaterForgetPercent, int forcedMissPercent) {
        this.label = label;
        this.playerWaterGuaranteed = playerWaterGuaranteed;
        this.playerWaterForgetPercent = playerWaterForgetPercent;
        this.computerWaterGuaranteed = computerWaterGuaranteed;
        this.computerWaterForgetPercent = computerWaterForgetPercent;
        this.forcedMissPercent = forcedMissPercent;
    }
}
