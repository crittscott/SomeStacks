package com.github.crittscott.somestacks.util;

import net.minecraft.core.Direction;

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

    /** Maps the grid's counterclockwise quarter turns to a horizontal block-state facing. */
    public static Direction toDirection(int turns) {
        return switch (normalize(turns)) {
            case 0 -> Direction.NORTH;
            case 1 -> Direction.WEST;
            case 2 -> Direction.SOUTH;
            case 3 -> Direction.EAST;
            default -> throw new IllegalStateException();
        };
    }

    /** Inverse of {@link #toDirection(int)}. */
    public static int fromDirection(Direction direction) {
        return switch (direction) {
            case NORTH -> 0;
            case WEST -> 1;
            case SOUTH -> 2;
            case EAST -> 3;
            default -> throw new IllegalArgumentException("Direction is not horizontal: " + direction);
        };
    }
}
