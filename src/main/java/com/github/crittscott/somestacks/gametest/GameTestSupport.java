package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

public final class GameTestSupport {
    public static final String TEMPLATE = "somestacks_empty";
    public static final BlockPos ORIGIN = new BlockPos(2, 1, 2);

    private GameTestSupport() {}

    public static StorageStackBE placeStorage(GameTestHelper helper, BlockPos relative) {
        return place(helper, relative, ModRegistry.STORAGE_STACK_BLOCK.get(), StorageStackBE.class);
    }

    public static SinglesStackBE placeSingles(GameTestHelper helper, BlockPos relative) {
        return place(helper, relative, ModRegistry.SINGLES_STACK_BLOCK.get(), SinglesStackBE.class);
    }

    public static BarStackBE placeBar(GameTestHelper helper, BlockPos relative) {
        return place(helper, relative, ModRegistry.BAR_STACK_BLOCK.get(), BarStackBE.class);
    }

    public static <T extends BlockEntity> T place(
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

    public static IItemHandler capability(BlockEntity blockEntity) {
        IItemHandler handler =
                blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        check(handler != null, "Missing item-handler capability at " + blockEntity.getBlockPos());
        return handler;
    }

    public static Item firstBarItem() {
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (BarStackBE.isValidBarItem(new ItemStack(item))) {
                return item;
            }
        }
        throw new GameTestAssertException("No item is currently valid for Bar Stack tests");
    }

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
     * Puts {@code stack} straight into a slot, bypassing the validity test an insertion runs.
     *
     * <p>This is how a test reaches the state an {@code ss ingot} edit, an {@code ss deny mod} edit
     * or a data pack reload leaves behind: contents a block holds and must keep handing back, which
     * that same block would refuse from a deposit today. Validity gates insertion only, so the
     * stored side of that rule has to be set up without going through insertion.
     * {@link com.github.crittscott.somestacks.block.StorageStackBE#setSlotIfChanged} takes the same
     * route for the same reason.
     */
    public static void seedSlot(IItemHandler handler, int slot, ItemStack stack) {
        check(handler instanceof IItemHandlerModifiable,
                "Handler " + handler.getClass().getSimpleName() + " cannot be seeded directly");
        ((IItemHandlerModifiable) handler).setStackInSlot(slot, stack);
    }

    /**
     * Items of {@code item} still held by the stack block at {@code absolutePos}. A position the
     * block has removed itself from holds none, which is what a caller counting whether an operation
     * conserved its contents wants to see.
     */
    public static int heldAt(GameTestHelper helper, BlockPos absolutePos, Item item) {
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(absolutePos);
        if (blockEntity instanceof StorageStackBE storage) {
            return count(storage.getItems(), item);
        }
        if (blockEntity instanceof SinglesStackBE singles) {
            return count(singles.getItems(), item);
        }
        if (blockEntity instanceof BarStackBE bars) {
            return count(bars.getItems(), item);
        }
        return 0;
    }

    /** Items of {@code item} lying on the ground around {@code center}. */
    public static int droppedNear(GameTestHelper helper, BlockPos center, Item item) {
        return helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(2.0))
                .stream()
                .filter(entity -> entity.getItem().is(item))
                .mapToInt(entity -> entity.getItem().getCount())
                .sum();
    }

    /** The comparator output at {@code relative}, read the way a comparator against it reads. */
    public static int signalAt(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        BlockState state = helper.getLevel().getBlockState(absolute);
        return state.getBlock().getAnalogOutputSignal(state, helper.getLevel(), absolute);
    }

    public static int occupied(IItemHandler handler) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                total++;
            }
        }
        return total;
    }

    public static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    public static void checkEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new GameTestAssertException(
                    message + ": expected " + expected + ", found " + actual);
        }
    }
}
