package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

final class GameTestSupport {
    static final String TEMPLATE = "somestacks_empty";
    static final BlockPos ORIGIN = new BlockPos(2, 1, 2);

    private GameTestSupport() {}

    static StorageStackBE placeStorage(GameTestHelper helper, BlockPos relative) {
        return place(helper, relative, ModRegistry.STORAGE_STACK_BLOCK.get(), StorageStackBE.class);
    }

    static SinglesStackBE placeSingles(GameTestHelper helper, BlockPos relative) {
        return place(helper, relative, ModRegistry.SINGLES_STACK_BLOCK.get(), SinglesStackBE.class);
    }

    static BarStackBE placeBar(GameTestHelper helper, BlockPos relative) {
        return place(helper, relative, ModRegistry.BAR_STACK_BLOCK.get(), BarStackBE.class);
    }

    static <T extends BlockEntity> T place(
            GameTestHelper helper,
            BlockPos relative,
            Block block,
            Class<T> type) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(relative);
        check(level.setBlock(absolute, block.defaultBlockState(), Block.UPDATE_ALL),
                "Could not place " + block + " at " + relative);
        BlockEntity blockEntity = level.getBlockEntity(absolute);
        check(type.isInstance(blockEntity),
                "Expected " + type.getSimpleName() + " at " + relative + ", found " + blockEntity);
        return type.cast(blockEntity);
    }

    static IItemHandler capability(BlockEntity blockEntity) {
        IItemHandler handler =
                blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        check(handler != null, "Missing item-handler capability at " + blockEntity.getBlockPos());
        return handler;
    }

    static Item firstBarItem() {
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (BarStackBE.isValidBarItem(new ItemStack(item))) {
                return item;
            }
        }
        throw new GameTestAssertException("No item is currently valid for Bar Stack tests");
    }

    static int count(IItemHandler handler, Item item) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    static int occupied(IItemHandler handler) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                total++;
            }
        }
        return total;
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    static void checkEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new GameTestAssertException(
                    message + ": expected " + expected + ", found " + actual);
        }
    }
}
