package de.kj.games;

enum Difficulty {
    LEICHT("Leicht", 25, 70, 73, 16),
    MITTEL("Mittel", 21, 65, 57, 8),
    SCHWER("Schwer", 17, 60, 55, 4);

    final String label;
    final int playerWaterGuaranteed;
    final int playerWaterForgetPercent;
    final int computerWaterForgetPercent;
    final int forcedMissPercent;

    Difficulty(String label, int playerWaterGuaranteed, int playerWaterForgetPercent,
               int computerWaterForgetPercent, int forcedMissPercent) {
        this.label = label;
        this.playerWaterGuaranteed = playerWaterGuaranteed;
        this.playerWaterForgetPercent = playerWaterForgetPercent;
        this.computerWaterForgetPercent = computerWaterForgetPercent;
        this.forcedMissPercent = forcedMissPercent;
    }
}
