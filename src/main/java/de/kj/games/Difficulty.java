package de.kj.games;

enum Difficulty {
    LEICHT("Leicht", 50, 25),
    MITTEL("Mittel", 30, 15),
    SCHWER("Schwer", 20, 8);

    final String label;
    final int computerWaterShotWindow;
    final int randomShotPercent;

    Difficulty(String label, int computerWaterShotWindow, int randomShotPercent) {
        this.label = label;
        this.computerWaterShotWindow = computerWaterShotWindow;
        this.randomShotPercent = randomShotPercent;
    }

    int playerWaterShotWindow() {
        return this == LEICHT ? Integer.MAX_VALUE : computerWaterShotWindow;
    }
}
