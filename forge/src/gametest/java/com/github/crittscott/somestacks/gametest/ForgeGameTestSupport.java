package com.github.crittscott.somestacks.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.function.Function;

/**
 * Forge-native scaffolding for the GameTests: the unprefixed template name, the shared fake player,
 * the real block-use click, and {@code IItemHandler} capability access. Everything loader-neutral
 * lives in {@link GameTestScaffold}.
 */
public final class ForgeGameTestSupport {
    public static final String TEMPLATE = "somestacks_empty";

    private ForgeGameTestSupport() {}

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
            public ItemStack insertStacked(ItemStack stack, boolean simulate) {
                return ItemHandlerHelper.insertItemStacked(handler, stack, simulate);
            }

            @Override
            public int slotLimit(int slot) {
                return handler.getSlotLimit(slot);
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
        IItemHandler handler =
                blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        GameTestScaffold.check(
                handler != null, "Missing item-handler capability at " + blockEntity.getBlockPos());
        return handler;
    }

    /** Runs vanilla's server block use, which posts Forge's event and then calls the block. */
    public static boolean click(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        return player.gameMode
                .useItemOn(player, player.serverLevel(), player.getItemInHand(hand), hand, hit)
                .consumesAction();
    }

    /**
     * {@link GameTestScaffold#count} for a real Forge capability, as {@link #capability} returns.
     */
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

    /** Hands out Forge's shared fake player for the level, holding the given main-hand stack. */
    public static Function<ItemStack, ServerPlayer> players(GameTestHelper helper) {
        return held -> {
            ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            return player;
        };
    }
}
