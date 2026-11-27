package com.github.crittscott.somestacks.gametest;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.function.Function;

/**
 * NeoForge-native scaffolding for the GameTests: the unprefixed template name, {@code IItemHandler}
 * capability access through {@code Capabilities.ItemHandler.BLOCK} and the shared scenarios'
 * automation view of it, and the level's fake player. Everything loader-neutral lives in {@link
 * GameTestScaffold}.
 */
public final class GameTestSupport {
    /**
     * Bare structure path. NeoForge prepends the {@code @GameTestHolder} namespace, and every
     * holder is {@code @PrefixGameTestTemplate(false)}, so this resolves to {@code
     * data/somestacks/structure/somestacks_empty.nbt} with no class-name segment.
     */
    public static final String TEMPLATE = "somestacks_empty";

    private GameTestSupport() {}

    /**
     * The {@link GameTestScaffold.Automation} view of {@code blockEntity}'s item-handler
     * capability, as a hopper or pipe reaches it.
     */
    public static GameTestScaffold.Automation automation(BlockEntity blockEntity) {
        IItemHandler handler = capability(blockEntity);
        return new GameTestScaffold.Automation() {
            @Override
            public ItemStack extract(int slot, int amount, boolean simulate) {
                return handler.extractItem(slot, amount, simulate);
            }

            @Override
            public ItemStack insert(int slot, ItemStack stack, boolean simulate) {
                return handler.insertItem(slot, stack, simulate);
            }

            @Override
            public int slots() {
                return handler.getSlots();
            }

            @Override
            public ItemStack stackAt(int slot) {
                return handler.getStackInSlot(slot);
            }
        };
    }

    public static IItemHandler capability(BlockEntity blockEntity) {
        IItemHandler handler = handlerOn(blockEntity, null);
        GameTestScaffold.check(
                handler != null, "Missing item-handler capability at " + blockEntity.getBlockPos());
        return handler;
    }

    /**
     * Whether the whole-run item handler is exposed on {@code side}; {@code null} is the sideless
     * query.
     */
    public static boolean capabilityPresent(BlockEntity blockEntity, Direction side) {
        return handlerOn(blockEntity, side) != null;
    }

    private static IItemHandler handlerOn(BlockEntity blockEntity, Direction side) {
        return ((ServerLevel) blockEntity.getLevel())
                .getCapability(
                        Capabilities.ItemHandler.BLOCK,
                        blockEntity.getBlockPos(),
                        blockEntity.getBlockState(),
                        blockEntity,
                        side);
    }

    /**
     * The level's shared fake player, standing in for automation. This is the same actor {@link
     * com.github.crittscott.somestacks.server.NeoForgeEditAuthority} uses, and NeoForge still ships
     * {@code FakePlayerFactory}, so there is nothing to reconstruct by hand.
     */
    public static ServerPlayer fakePlayer(ServerLevel level) {
        return FakePlayerFactory.getMinecraft(level);
    }

    /** A fake-player factory that resets the main hand for each shared check. */
    public static Function<ItemStack, ServerPlayer> playerFactory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return mainHand -> {
            ServerPlayer player = fakePlayer(level);
            player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
            return player;
        };
    }
}
