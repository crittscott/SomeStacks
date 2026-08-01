package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;

public enum BlockType {
    STORAGE_STACK,
    SINGLES_STACK,
    BAR_STACK;

    /** The type with this ordinal, or null when the ordinal names none. */
    @Nullable
    public static BlockType fromOrdinal(int ordinal) {
        BlockType[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
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
