package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.util.BlockType;

/**
 * The loader-neutral client copy of which stack types the server has enabled, delivered by the configuration
 * sync. It decides only what the placement-mode gesture will cycle through; the server enforces the
 * setting itself, so a stale copy here cannot create a block the server has disabled.
 */
public final class StackState {
    private StackState() {}

    private static boolean enableStackBlock = true;
    private static boolean enableSinglesBlock = true;
    private static boolean enableBarBlock = true;

    public static void setBlockEnabled(BlockType blockType, boolean enabled) {
        switch (blockType) {
            case STORAGE_STACK -> enableStackBlock = enabled;
            case SINGLES_STACK -> enableSinglesBlock = enabled;
            case BAR_STACK -> enableBarBlock = enabled;
        }
    }

    public static boolean isBlockTypeEnabled(BlockType blockType) {
        return switch (blockType) {
            case STORAGE_STACK -> enableStackBlock;
            case SINGLES_STACK -> enableSinglesBlock;
            case BAR_STACK -> enableBarBlock;
        };
    }
}
