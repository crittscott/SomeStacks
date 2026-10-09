package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;

/**
 * The three stack types, as a value that can be named in a packet and looked up in the registry or
 * the server config. Ordinals are the wire form, so their order is a protocol detail.
 */
public enum BlockType {
    STORAGE_STACK,
    SINGLES_STACK,
    BAR_STACK;

    /** The type whose block this is, or null when it names none of the three. */
    @Nullable
    public static BlockType of(Block block) {
        for (BlockType type : values()) {
            if (type.getBlock() == block) {
                return type;
            }
        }
        return null;
    }

    public Block getBlock() {
        return switch (this) {
            case STORAGE_STACK -> CommonRegistry.storageStackBlock();
            case SINGLES_STACK -> CommonRegistry.singlesStackBlock();
            case BAR_STACK -> CommonRegistry.barStackBlock();
        };
    }

    public boolean isEnabled() {
        return switch (this) {
            case STORAGE_STACK -> ServerConfig.enableStorageStackBlock();
            case SINGLES_STACK -> ServerConfig.enableSinglesStackBlock();
            case BAR_STACK -> ServerConfig.enableBarStackBlock();
        };
    }
}
