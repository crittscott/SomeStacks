package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.FabricRegistry;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.util.SlotAccess;
import com.github.crittscott.somestacks.util.StackItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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

import java.util.List;
import java.util.Objects;

/**
 * Shared scaffolding for the Fabric GameTests: placing each stack type on the empty template,
 * seeding handlers directly, and the assertion and Transfer API helpers the tests read through.
 *
 * <p>Seeding writes to a handler rather than going through a deposit, so a test can build a state
 * the ordinary rules would not produce and check what happens next.
 */
public final class FabricGameTestSupport {
    public static final String TEMPLATE = "somestacks:somestacks_empty";
    public static final BlockPos ORIGIN = new BlockPos(2, 1, 2);

    private FabricGameTestSupport() {}

    public static StorageStackBE placeStorage(GameTestHelper helper, BlockPos relative) {
        return place(helper, relative, FabricRegistry.STORAGE_STACK_BLOCK, StorageStackBE.class);
    }

    public static SinglesStackBE placeSingles(GameTestHelper helper, BlockPos relative) {
        return place(helper, relative, FabricRegistry.SINGLES_STACK_BLOCK, SinglesStackBE.class);
    }

    public static BarStackBE placeBar(GameTestHelper helper, BlockPos relative) {
        return place(helper, relative, FabricRegistry.BAR_STACK_BLOCK, BarStackBE.class);
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

    /**
     * The registered whole-run storage for {@code blockEntity}, queried the way an external
     * automation caller would: through {@link ItemStorage#SIDED}, not the block entity directly.
     */
    @SuppressWarnings("unchecked")
    public static SlottedStorage<ItemVariant> capability(BlockEntity blockEntity) {
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(
                (ServerLevel) blockEntity.getLevel(),
                blockEntity.getBlockPos(),
                blockEntity.getBlockState(),
                blockEntity,
                null);
        check(storage instanceof SlottedStorage, "Missing item storage at " + blockEntity.getBlockPos());
        return (SlottedStorage<ItemVariant>) storage;
    }

    /**
     * Inserts {@code stack} into {@code slot}, mirroring Forge's
     * {@code IItemHandler.insertItem(slot, stack, simulate)}: returns the remainder left over, and
     * a {@code simulate} transaction is opened and never committed.
     */
    public static ItemStack insertAt(
            SlottedStorage<ItemVariant> storage, int slot, ItemStack stack, boolean simulate) {
        long inserted;
        try (Transaction transaction = Transaction.openOuter()) {
            inserted = storage.getSlot(slot).insert(
                    ItemVariant.of(stack), stack.getCount(), transaction);
            if (!simulate) {
                transaction.commit();
            }
        }
        ItemStack remainder = stack.copy();
        remainder.shrink((int) inserted);
        return remainder;
    }

    /**
     * Walks every advertised slot from 0 upward inserting as much of {@code stack} as each slot
     * will take, mirroring Forge's {@code ItemHandlerHelper.insertItemStacked}. Returns the
     * remainder left over once the stack is exhausted or every slot has been offered it.
     */
    public static ItemStack insertWalkingSlots(
            SlottedStorage<ItemVariant> storage, ItemStack stack, boolean simulate) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < storage.getSlotCount() && !remaining.isEmpty(); slot++) {
            remaining = insertAt(storage, slot, remaining, simulate);
        }
        return remaining;
    }

    /**
     * Extracts up to {@code amount} from {@code slot}, mirroring Forge's
     * {@code IItemHandler.extractItem(slot, amount, simulate)}.
     */
    public static ItemStack extractAt(
            SlottedStorage<ItemVariant> storage, int slot, int amount, boolean simulate) {
        SingleSlotStorage<ItemVariant> slotStorage = storage.getSlot(slot);
        ItemVariant resource = slotStorage.getResource();
        long extracted;
        try (Transaction transaction = Transaction.openOuter()) {
            extracted = slotStorage.extract(resource, amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
        }
        return resource.toStack((int) extracted);
    }

    public static ItemStack stackAt(SlottedStorage<ItemVariant> storage, int slot) {
        SingleSlotStorage<ItemVariant> slotStorage = storage.getSlot(slot);
        return slotStorage.getResource().toStack((int) slotStorage.getAmount());
    }

    public static int slotLimit(SlottedStorage<ItemVariant> storage, int slot) {
        return (int) storage.getSlot(slot).getCapacity();
    }

    public static int count(SlottedStorage<ItemVariant> storage, Item item) {
        int total = 0;
        for (int slot = 0; slot < storage.getSlotCount(); slot++) {
            ItemStack stack = stackAt(storage, slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static int occupied(SlottedStorage<ItemVariant> storage) {
        int total = 0;
        for (int slot = 0; slot < storage.getSlotCount(); slot++) {
            if (!storage.getSlot(slot).isResourceBlank()) {
                total++;
            }
        }
        return total;
    }

    public static int count(SlotAccess handler, Item item) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static int occupied(SlotAccess handler) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                total++;
            }
        }
        return total;
    }

    public static Item firstBarItem() {
        for (Item item : BuiltInRegistries.ITEM) {
            if (BarStackBE.isValidBarItem(new ItemStack(item))) {
                return item;
            }
        }
        throw new GameTestAssertException("No item is currently valid for Bar Stack tests");
    }

    /**
     * Puts {@code stack} straight into a slot, bypassing the validity test an insertion runs.
     *
     * <p>This is how a test reaches the state an {@code ss ingot} edit, an {@code ss deny mod} edit
     * or a data pack reload leaves behind: contents a block holds and must keep handing back, which
     * that same block would refuse from a deposit today. Validity gates insertion only, so the
     * stored side of that rule has to be set up without going through insertion.
     */
    public static void seedSlot(StackItemStorage handler, int slot, ItemStack stack) {
        handler.setStackInSlot(slot, stack);
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

    /** Item entities around the vertical span from {@code bottom} through {@code top}. */
    public static List<ItemEntity> droppedInColumn(
            GameTestHelper helper,
            BlockPos bottom,
            BlockPos top) {
        AABB bounds = new AABB(
                Math.min(bottom.getX(), top.getX()),
                Math.min(bottom.getY(), top.getY()),
                Math.min(bottom.getZ(), top.getZ()),
                Math.max(bottom.getX(), top.getX()) + 1.0,
                Math.max(bottom.getY(), top.getY()) + 1.0,
                Math.max(bottom.getZ(), top.getZ()) + 1.0).inflate(2.0);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds);
    }

    /** The comparator output at {@code relative}, read the way a comparator against it reads. */
    public static int signalAt(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        BlockState state = helper.getLevel().getBlockState(absolute);
        return state.getBlock().getAnalogOutputSignal(state, helper.getLevel(), absolute);
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
