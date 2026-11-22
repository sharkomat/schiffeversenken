package de.kj.games;

import static de.kj.games.Game.SIZE;

record ShipPlacement(int start, Game.Direction direction, int length) {
    int cell(int offset) {
        return start + offset * direction.step;
    }

    boolean fits() {
        int offset = direction == Game.Direction.HORIZONTAL ? start % SIZE : start / SIZE;
        return start >= 0 && start < SIZE * SIZE && offset + length <= SIZE;
    }

    boolean compatibleWith(ShipPlacement other) {
        int end = cell(length - 1);
        int otherEnd = other.cell(other.length - 1);
        int rowGap = gap(start / SIZE, end / SIZE, other.start / SIZE, otherEnd / SIZE);
        int columnGap = gap(start % SIZE, end % SIZE, other.start % SIZE, otherEnd % SIZE);
        if (rowGap > 1 || columnGap > 1) return true;
        return direction == other.direction
                && (direction == Game.Direction.HORIZONTAL ? rowGap == 1 : columnGap == 1);
    }

    private static int gap(int start, int end, int otherStart, int otherEnd) {
        return Math.max(0, Math.max(otherStart - end, start - otherEnd));
    }
}
