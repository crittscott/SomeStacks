package com.github.crittscott.somestacks.util;

/**
 * What the placement modifier does next, as cycled by the mode gesture: place one of the three
 * stack types, or toggle a Storage pile's permanence. Client-side selection state only; the server
 * learns the choice from the packet the gesture sends.
 *
 * <p>Toggle Permanent is a mode but not a stack type, so {@link #toBlockType()} rejects it and
 * {@link #isBlockType()} is how callers ask first.
 */
public enum StackMode {
    STORAGE_STACK("somestacks.mode.storage_stack"),
    SINGLES_STACK("somestacks.mode.singles_stack"),
    BAR_STACK("somestacks.mode.bar_stack"),
    TOGGLE_PERMANENT("somestacks.mode.toggle_permanent");

    private final String translationKey;

    StackMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public static StackMode fromOrdinal(int ordinal) {
        return values()[ordinal];
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public boolean isBlockType() {
        return this != TOGGLE_PERMANENT;
    }

    public BlockType toBlockType() {
        return switch (this) {
            case STORAGE_STACK -> BlockType.STORAGE_STACK;
            case SINGLES_STACK -> BlockType.SINGLES_STACK;
            case BAR_STACK -> BlockType.BAR_STACK;
            case TOGGLE_PERMANENT -> throw new IllegalStateException("TOGGLE_PERMANENT is not a block type");
        };
    }
}
