package com.github.crittscott.somestacks.gametest;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * NeoForge-native scaffolding for the GameTests: the unprefixed template name, {@code IItemHandler}
 * capability access through {@code Capabilities.ItemHandler.BLOCK}, and the level's fake player.
 * Everything loader-neutral lives in {@link GameTestScaffold}.
 */
public final class GameTestSupport {
    /**
     * Bare structure path. NeoForge prepends the {@code @GameTestHolder} namespace, and every holder
     * is {@code @PrefixGameTestTemplate(false)}, so this resolves to
     * {@code data/somestacks/structure/somestacks_empty.nbt} with no class-name segment.
     */
    public static final String TEMPLATE = "somestacks_empty";

    private GameTestSupport() {}

    public static IItemHandler capability(BlockEntity blockEntity) {
        IItemHandler handler = handlerOn(blockEntity, null);
        GameTestScaffold.check(handler != null,
                "Missing item-handler capability at " + blockEntity.getBlockPos());
        return handler;
    }

    /** Whether the whole-run item handler is exposed on {@code side}; {@code null} is the sideless query. */
    public static boolean capabilityPresent(BlockEntity blockEntity, Direction side) {
        return handlerOn(blockEntity, side) != null;
    }

    private static IItemHandler handlerOn(BlockEntity blockEntity, Direction side) {
        return ((ServerLevel) blockEntity.getLevel()).getCapability(
                Capabilities.ItemHandler.BLOCK,
                blockEntity.getBlockPos(),
                blockEntity.getBlockState(),
                blockEntity,
                side);
    }

    /** {@link GameTestScaffold#count} for a real NeoForge capability, as {@link #capability} returns. */
    public static int count(IItemHandler handler, Item item) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * The level's shared fake player, standing in for automation. This is the same actor
     * {@link com.github.crittscott.somestacks.server.NeoForgeEditAuthority} uses, and NeoForge still
     * ships {@code FakePlayerFactory}, so there is nothing to reconstruct by hand.
     */
    public static ServerPlayer fakePlayer(ServerLevel level) {
        return FakePlayerFactory.getMinecraft(level);
    }
}
