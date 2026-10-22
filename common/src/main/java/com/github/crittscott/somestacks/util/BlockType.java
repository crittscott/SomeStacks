package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.block.BarStackBE;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;

/**
 * The three stack types and their registered blocks and server enablement settings.
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
            case STORAGE_STACK -> CommonRegistry.STORAGE_STACK_BLOCK.get();
            case SINGLES_STACK -> CommonRegistry.SINGLES_STACK_BLOCK.get();
            case BAR_STACK -> CommonRegistry.BAR_STACK_BLOCK.get();
        };
    }

    public boolean isEnabled() {
        return switch (this) {
            case STORAGE_STACK -> ServerConfig.enableStorageStackBlock();
            case SINGLES_STACK -> ServerConfig.enableSinglesStackBlock();
            case BAR_STACK -> ServerConfig.enableBarStackBlock();
        };
    }

    /** Silent admission for simulation/commit; null denotes automation, which ignores exact-item denial. */
    public boolean admits(ItemStack stack, @Nullable ServerPlayer depositor) {
        if (policyDenial(stack, depositor != null) != Denial.NONE) return false;
        return switch (this) {
            case STORAGE_STACK -> true;
            case SINGLES_STACK -> !stack.is(BarStackBE.INGOTS);
            case BAR_STACK -> stack.is(BarStackBE.INGOTS);
        };
    }

    /**
     * Player policy gate before placement-height/support checks. Type admission remains silent and
     * is checked during preflight/commit. Mod denial takes message precedence.
     */
    public static boolean permitsPlayerItemAndNotify(ItemStack stack, ServerPlayer player) {
        Denial denial = policyDenial(stack, true);
        if (denial == Denial.MOD || denial == Denial.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            player.displayClientMessage(denial == Denial.MOD
                    ? Component.translatable("somestacks.message.disabled_mod", id.getNamespace())
                    : Component.translatable("somestacks.message.disabled_item", id), true);
        }
        return denial == Denial.NONE;
    }

    private static Denial policyDenial(ItemStack stack, boolean playerDeposit) {
        if (stack.isEmpty()) return Denial.EMPTY;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (ServerConfig.isModDisabled(id.getNamespace())) return Denial.MOD;
        if (playerDeposit && ServerConfig.isItemDisabled(id)) return Denial.ITEM;
        return Denial.NONE;
    }

    private enum Denial { NONE, EMPTY, MOD, ITEM }
}
