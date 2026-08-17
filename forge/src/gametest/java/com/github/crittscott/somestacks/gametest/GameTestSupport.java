package com.github.crittscott.somestacks.gametest;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

/**
 * Forge-native scaffolding for the GameTests: the unprefixed template name and
 * {@code IItemHandler} capability access. Everything loader-neutral lives in
 * {@link GameTestScaffold}.
 */
public final class GameTestSupport {
    public static final String TEMPLATE = "somestacks_empty";

    private GameTestSupport() {}

    public static IItemHandler capability(BlockEntity blockEntity) {
        IItemHandler handler =
                blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        GameTestScaffold.check(handler != null,
                "Missing item-handler capability at " + blockEntity.getBlockPos());
        return handler;
    }

    /** {@link GameTestScaffold#count} for a real Forge capability, as {@link #capability} returns. */
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
}
