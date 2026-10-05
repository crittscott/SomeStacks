package com.github.crittscott.somestacks.util;

/** Normalization and display conversion for block and item quarter-turn rotations. */
public final class QuarterTurns {
    public static final int COUNT = 4;
    public static final int DEGREES_PER_TURN = 90;

    private QuarterTurns() {}

    public static int normalize(int turns) {
        return Math.floorMod(turns, COUNT);
    }

    public static int next(int turns) {
        return normalize(turns + 1);
    }

    public static int inverse(int turns) {
        return normalize(-turns);
    }

    public static int degrees(int turns) {
        return normalize(turns) * DEGREES_PER_TURN;
    }
}
