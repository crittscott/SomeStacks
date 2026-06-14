package com.github.crittscott.somestacks.util;

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
