package com.github.crittscott.somestacks.gametest;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.function.Function;

/**
 * NeoForge-native scaffolding for the GameTests: {@code ResourceHandler}
 * capability access through {@code Capabilities.Item.BLOCK}, and the level's fake player.
 * Everything loader-neutral lives in {@link GameTestScaffold}.
 */
public final class GameTestSupport {
    private GameTestSupport() {}

    public static ResourceHandler<ItemResource> capability(BlockEntity blockEntity) {
        ResourceHandler<ItemResource> handler = handlerOn(blockEntity, null);
        GameTestScaffold.check(handler != null,
                "Missing item-resource capability at " + blockEntity.getBlockPos());
        return handler;
    }

    /** Whether the whole-run item handler is exposed on {@code side}; {@code null} is the sideless query. */
    public static boolean capabilityPresent(BlockEntity blockEntity, Direction side) {
        return handlerOn(blockEntity, side) != null;
    }

    private static ResourceHandler<ItemResource> handlerOn(BlockEntity blockEntity, Direction side) {
        return ((ServerLevel) blockEntity.getLevel()).getCapability(
                Capabilities.Item.BLOCK,
                blockEntity.getBlockPos(),
                blockEntity.getBlockState(),
                blockEntity,
                side);
    }

    /** {@link GameTestScaffold#count} for a real NeoForge capability, as {@link #capability} returns. */
    public static int count(ResourceHandler<ItemResource> handler, Item item) {
        int total = 0;
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemStack stack = handler.getResource(slot).toStack(handler.getAmountAsInt(slot));
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Offers through the native transaction API; an uncommitted offer is rolled back on close. */
    public static int offer(ResourceHandler<ItemResource> handler, int slot, ItemStack stack, boolean commit) {
        try (Transaction transaction = Transaction.openRoot()) {
            int accepted = handler.insert(slot, ItemResource.of(stack), stack.getCount(), transaction);
            if (commit) transaction.commit();
            return accepted;
        }
    }

    /**
     * The level's shared fake player, standing in for automation. This is the same actor
     * {@link com.github.crittscott.somestacks.server.NeoForgeEditAuthority} uses, and NeoForge still
     * ships {@code FakePlayerFactory}, so there is nothing to reconstruct by hand.
     */
    public static ServerPlayer fakePlayer(ServerLevel level) {
        return FakePlayerFactory.getMinecraft(level);
    }

    /** A fake-player factory that resets the main hand for each shared check. */
    public static Function<ItemStack, ServerPlayer> playerFactory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return mainHand -> {
            ServerPlayer player = fakePlayer(level);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, mainHand);
            return player;
        };
    }
}
