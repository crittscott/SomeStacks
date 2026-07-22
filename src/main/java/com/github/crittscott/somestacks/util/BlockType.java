package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeConfigSpec;

public enum BlockType {
    STORAGE_STACK,
    SINGLES_STACK,
    BAR_STACK;

    public static BlockType fromOrdinal(int ordinal) {
        return values()[ordinal];
    }

    public Block getBlock() {
        return switch (this) {
            case STORAGE_STACK -> ModRegistry.STORAGE_STACK_BLOCK.get();
            case SINGLES_STACK -> ModRegistry.SINGLES_STACK_BLOCK.get();
            case BAR_STACK -> ModRegistry.BAR_STACK_BLOCK.get();
        };
    }

    public ForgeConfigSpec.BooleanValue getConfigValue() {
        return switch (this) {
            case STORAGE_STACK -> ServerConfig.ENABLE_STORAGE_STACK_BLOCK;
            case SINGLES_STACK -> ServerConfig.ENABLE_SINGLES_STACK_BLOCK;
            case BAR_STACK -> ServerConfig.ENABLE_BAR_STACK_BLOCK;
        };
    }
}
