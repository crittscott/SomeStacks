package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBlock;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.block.StackRun;
import com.github.crittscott.somestacks.block.StackRunItemAccess;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.StorageStackBlock;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.firstBarItem;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

/** Shared regression checks for local batching, cached run bounds, and persisted fill. */
public final class StackFoundationChecks {
    // Reflective access keeps package-private batch controls outside the separate test mod's package.
    private static final Method BEGIN_BATCH = stackMethod("beginBatch");
    private static final Method END_BATCH = stackMethod("endBatch");
    private static final Method PUBLISH = stackMethod("publishIfPending", ServerLevel.class);

    private StackFoundationChecks() {}

    private static Method stackMethod(String name, Class<?>... parameterTypes) {
        try {
            Method method = StackBlockEntity.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot access stack test method " + name, exception);
        }
    }

    private static Object invoke(Method method, StackBlockEntity block, Object... arguments) {
        try {
            return method.invoke(block, arguments);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Stack test method failed: " + method.getName(), exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot invoke stack test method " + method.getName(), exception);
        }
    }

    /**
     * To reproduce in-game: perform several automation edits before a tick, including a Bar
     * extraction that starts a nested collapse. Contents and light publish together after the edits.
     */
    public static void nestedBatchesPublishOnlyAfterTheOuterBatch(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        SinglesStackBE singles = placeSingles(helper, ORIGIN.east(3));
        BarStackBE bars = placeBar(helper, ORIGIN.east(6));
        checkNestedBatch(helper, storage, new ItemStack(Items.GLOWSTONE));
        checkNestedBatch(helper, singles, new ItemStack(Items.GLOWSTONE));
        checkNestedBatch(helper, bars, new ItemStack(firstBarItem()));
        helper.succeed();
    }

    private static void checkNestedBatch(GameTestHelper helper, StackBlockEntity block, ItemStack item) {
        invoke(BEGIN_BATCH, block);
        try {
            invoke(BEGIN_BATCH, block);
            try {
                block.getItems().setStackInSlot(0, item.copy());
            } finally {
                invoke(END_BATCH, block);
            }
            check(!(boolean) invoke(PUBLISH, block, helper.getLevel()), "Inner batch published contents");
            block.getItems().setStackInSlot(1, item.copy());
            check(!(boolean) invoke(PUBLISH, block, helper.getLevel()), "Outer batch published before ending");
        } finally {
            invoke(END_BATCH, block);
        }
        check((boolean) invoke(PUBLISH, block, helper.getLevel()), "Outer batch lost its changes");
        check(!(boolean) invoke(PUBLISH, block, helper.getLevel()), "Batch published twice");
        checkEquals(2, block.getItems().getStackInSlot(0).getCount()
                + block.getItems().getStackInSlot(1).getCount(), "Batch contents");
        if (block instanceof StorageStackBE || block instanceof SinglesStackBE) {
            checkEquals(6, block.getBlockState().getValue(
                    block instanceof StorageStackBE ? StorageStackBlock.LIGHT_LEVEL
                            : SinglesStackBlock.LIGHT_LEVEL), "Batched light");
        }
    }

    /**
     * To reproduce in-game: connect automation to the bottom of two stacked blocks, then remove
     * the top block. Its slot view immediately gains and loses that block without waiting a tick.
     */
    public static void structuralEditsInvalidateRunBoundsWithinTheTick(GameTestHelper helper) {
        checkBounds(helper, placeStorage(helper, ORIGIN), ORIGIN);
        checkBounds(helper, placeSingles(helper, ORIGIN.east(3)), ORIGIN.east(3));
        checkBounds(helper, placeBar(helper, ORIGIN.east(6)), ORIGIN.east(6));
        helper.succeed();
    }

    private static void checkBounds(GameTestHelper helper, StackBlockEntity bottom, BlockPos relative) {
        StackRun<?> initial = (StackRun<?>) bottom.itemRun();
        int localSlots = bottom.getItems().getSlots();
        check(initial == bottom.itemRun(), "Run cache missed within the same tick");
        BlockPos topPos = helper.absolutePos(relative.above());
        helper.getLevel().setBlock(topPos, bottom.getBlockState(), Block.UPDATE_ALL);
        StackBlockEntity top = (StackBlockEntity) helper.getLevel().getBlockEntity(topPos);
        top.getItems().setStackInSlot(0, new ItemStack(Items.PAPER));
        StackRun<?> joined = (StackRun<?>) bottom.itemRun();
        check(joined != initial, "Placement retained stale run bounds");
        checkEquals(2, joined.height(), "Joined height");
        checkEquals(Items.PAPER, joined.getSlot(localSlots).getItem(), "Flat slot mapping");
        int advertisedLevels = ServerConfig.maxPileHeight() > 2 ? 3 : 2;
        checkEquals(localSlots * advertisedLevels, joined.advertisedSlots(), "Joined headroom");
        checkEquals(joined.totalSlots(), ((StackRun<?>) top.itemRun()).totalSlots(), "Upper run view");
        helper.getLevel().setBlock(topPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        StackRun<?> remaining = (StackRun<?>) bottom.itemRun();
        check(remaining != joined, "Removal retained stale run bounds");
        checkEquals(1, remaining.height(), "Remaining height");
        check(remaining.getSlot(localSlots).isEmpty(), "Removed block remained in flat slots");
    }

    /**
     * To reproduce in-game: automate insertion and extraction in each type. Storage accepts normal
     * stacks and settles; Singles shifts the selected column; Bar backfills from its top position.
     */
    public static void runContractPreservesTypeSpecificMutations(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        ItemStack offered = new ItemStack(Items.STONE, 8);
        StackRunItemAccess run = storage.itemRun();
        checkEquals(8, run.insertAt(20, offered, true), "Storage simulated insertion");
        check(storage.isEmpty(), "Storage simulation stored contents");
        checkEquals(8, run.insertAt(20, offered, false), "Storage insertion");
        checkEquals(8, offered.getCount(), "Storage mutated offered stack");
        checkEquals(0, run.insertAt(20, new ItemStack(Items.DIRT), true), "Incompatible insertion");
        checkEquals(3, run.extract(20, 3, true).getCount(), "Storage simulated extraction");
        checkEquals(8, run.getSlot(20).getCount(), "Extraction simulation changed contents");
        checkEquals(3, run.extract(20, 3, false).getCount(), "Storage extraction");
        storage.pile().settle();
        checkEquals(5, storage.getItems().getStackInSlot(0).getCount(), "Storage settlement");

        SinglesStackBE singles = placeSingles(helper, ORIGIN.east(3));
        run = singles.itemRun();
        offered = new ItemStack(Items.STICK, 3);
        checkEquals(0, run.insertAt(63, offered, true), "Unsupported Singles insertion");
        checkEquals(1, run.insertAt(0, offered, true), "Singles simulated insertion");
        check(singles.isEmpty(), "Singles simulation stored contents");
        checkEquals(1, run.insertAt(0, offered, false), "Singles insertion");
        checkEquals(3, offered.getCount(), "Singles mutated offered stack");
        int above = SinglesCubeIdx.indexFromColumn(0, 1);
        checkEquals(1, run.insertAt(above, new ItemStack(Items.PAPER), false), "Supported insertion");
        checkEquals(Items.STICK, run.extract(0, 64, true).getItem(), "Singles simulated extraction");
        checkEquals(Items.STICK, run.getSlot(0).getItem(), "Singles simulation changed contents");
        checkEquals(1, run.extract(0, 64, false).getCount(), "Singles extraction count");
        checkEquals(Items.PAPER, singles.getItems().getStackInSlot(0).getItem(), "Singles gravity");

        BarStackBE bars = placeBar(helper, ORIGIN.east(6));
        run = bars.itemRun();
        offered = new ItemStack(firstBarItem(), 4);
        checkEquals(0, run.insertAt(8, offered, true), "Unsupported Bar insertion");
        checkEquals(1, run.insertAt(0, offered, true), "Bar simulated insertion");
        check(bars.isEmpty(), "Bar simulation stored contents");
        checkEquals(1, run.insertAt(0, offered, false), "Bar insertion");
        checkEquals(1, run.insertAt(1, offered, false), "Second Bar insertion");
        checkEquals(4, offered.getCount(), "Bar mutated offered stack");
        checkEquals(1, run.extract(0, 64, true).getCount(), "Bar simulated extraction");
        checkEquals(1, run.extract(0, 64, false).getCount(), "Bar extraction");
        check(!bars.getItems().getStackInSlot(0).isEmpty(), "Bar backfill left a hole");
        check(bars.getItems().getStackInSlot(1).isEmpty(), "Topmost Bar did not move");
        helper.succeed();
    }

    /**
     * To reproduce in-game: save and reload partly filled stacks, including an unstackable Storage
     * item. Comparators retain their whole-run fill; loading empty contents clears shapes and fill.
     */
    public static void loadedContentsRefreshComparatorFillAndShapes(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        SinglesStackBE singles = placeSingles(helper, ORIGIN.east(3));
        BarStackBE bars = placeBar(helper, ORIGIN.east(6));
        storage.getItems().setStackInSlot(0, new ItemStack(Items.STONE, 32));
        storage.getItems().setStackInSlot(1, new ItemStack(Items.WOODEN_SWORD));
        singles.getItems().setStackInSlot(0, new ItemStack(Items.PAPER));
        bars.getItems().setStackInSlot(0, new ItemStack(firstBarItem()));
        check(Math.abs(storage.pile().fillLevel() - 1.5 / StorageStackBE.SLOTS) < 1.0e-12,
                "Storage fill ignored legal stack sizes");
        checkEquals(1, storage.pile().comparatorSignal(), "Storage comparator");
        checkEquals(1, singles.column().comparatorSignal(), "Singles comparator");
        checkEquals(1, bars.column().comparatorSignal(), "Bar comparator");
        check(!singles.getCachedShape().isEmpty(), "Singles shape missing before load");
        check(!bars.getCachedShape().isEmpty(), "Bar shape missing before load");
        for (StackBlockEntity block : new StackBlockEntity[]{storage, singles, bars}) {
            CompoundTag saved = block.getUpdateTag();
            checkEquals(block.getItems().getSlots(), saved.getCompound("Items").getInt("Size"),
                    "Saved inventory size");
            block.load(saved);
            checkEquals(1, ((StackRun<?>) block.itemRun()).comparatorSignal(), "Loaded comparator");
            CompoundTag empty = saved.copy();
            empty.put("Items", new CompoundTag());
            block.load(empty);
            checkEquals(0, ((StackRun<?>) block.itemRun()).comparatorSignal(), "Empty loaded comparator");
        }
        check(singles.getCachedShape().isEmpty(), "Singles load retained cached shape");
        check(bars.getCachedShape().isEmpty(), "Bar load retained cached shape");
        helper.succeed();
    }
}
