package com.github.crittscott.somestacks.util;

import javax.annotation.Nullable;

/**
 * What the placement modifier does next, as cycled by the mode gesture: place one of the three
 * stack types, or toggle a Storage pile's permanence. It is the client's selected mode and the
 * server's record of it ({@code ServerGestureState}); {@code ClientGestures.syncState} sends it in
 * a {@code GestureStatePkt} when it changes.
 *
 * <p>Toggle Permanent is a mode but not a stack type, so {@link #toBlockType()} rejects it and
 * {@link #isBlockType()} is how callers ask first. The wire id and the cycle order are explicit, so
 * the constants can be reordered freely.
 */
public enum StackMode {
    BAR_STACK(2, "somestacks.mode.bar_stack"),
    SINGLES_STACK(1, "somestacks.mode.singles_stack"),
    STORAGE_STACK(0, "somestacks.mode.storage_stack"),
    TOGGLE_PERMANENT(3, "somestacks.mode.toggle_permanent");

    private final int id;
    private final String translationKey;

    StackMode(int id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }

    /** The mode with wire id {@code id}, or null when no mode has it. */
    @Nullable
    public static StackMode byId(int id) {
        for (StackMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return null;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    /** The stable id this mode has in the gesture-state packet. */
    public int id() {
        return id;
    }

    public boolean isBlockType() {
        return this != TOGGLE_PERMANENT;
    }

    /** The mode after this one in the mode gesture's cycle. */
    public StackMode next() {
        return switch (this) {
            case STORAGE_STACK -> SINGLES_STACK;
            case SINGLES_STACK -> BAR_STACK;
            case BAR_STACK -> TOGGLE_PERMANENT;
            case TOGGLE_PERMANENT -> STORAGE_STACK;
        };
    }

    public BlockType toBlockType() {
        return switch (this) {
            case STORAGE_STACK -> BlockType.STORAGE_STACK;
            case SINGLES_STACK -> BlockType.SINGLES_STACK;
            case BAR_STACK -> BlockType.BAR_STACK;
            case TOGGLE_PERMANENT ->
                    throw new IllegalStateException("TOGGLE_PERMANENT is not a block type");
        };
    }
}
