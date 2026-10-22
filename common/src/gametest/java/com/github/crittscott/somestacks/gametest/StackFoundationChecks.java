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
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.CubeGrid;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

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
     * Internal publication-order check with no direct manual reproduction. Reflection opens two
     * nested batches on each stack type; direct writes fill slots 0 and 1 while local fill updates
     * immediately. Publication is deferred until the outer batch ends, then occurs exactly once.
     * Storage and Singles also assert derived light from glowstone; Bar uses an ordinary accepted
     * ingot. The fixture performs no extraction or collapse.
     *
     * <p>For visible Bar collapse checks, see {@link BarColumnChecks#playerExtractionDropsUnsupportedBars}
     * and {@link BarColumnChecks#playerCascadeConsolidatesAcrossBlocks}.
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
                checkLocalFill(block, block.automationSlotLimit() == 1 ? 1.0
                        : 1.0 / item.getMaxStackSize());
            } finally {
                invoke(END_BATCH, block);
            }
            check(!(boolean) invoke(PUBLISH, block, helper.getLevel()), "Inner batch published contents");
            block.getItems().setStackInSlot(1, item.copy());
            checkLocalFill(block, block.automationSlotLimit() == 1 ? 2.0
                    : 2.0 / item.getMaxStackSize());
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
     * To reproduce in-game: attach automation at different heights, add and remove stack blocks,
     * and split a Storage or Singles run. Every attachment sees the new bounds immediately.
     */
    public static void structuralEditsInvalidateRunBoundsWithinTheTick(GameTestHelper helper) {
        StackBlockEntity[] blocks = {
                placeStorage(helper, ORIGIN), placeSingles(helper, ORIGIN.east(3)),
                placeBar(helper, ORIGIN.east(6))
        };
        StackRunItemAccess[] runs = new StackRunItemAccess[blocks.length];
        for (int i = 0; i < blocks.length; i++) {
            checkBounds(helper, blocks[i], ORIGIN.east(i * 3));
            runs[i] = blocks[i].itemRun();
        }
        helper.runAfterDelay(2, () -> {
            for (int i = 0; i < blocks.length; i++) {
                check(blocks[i].itemRun() != runs[i], "Run cache did not expire with the tick");
                checkEquals(1, ((StackRun<?>) blocks[i].itemRun()).height(), "Expired run bounds");
            }
            helper.succeed();
        });
    }

    private static void checkBounds(GameTestHelper helper, StackBlockEntity bottom, BlockPos relative) {
        StackRun<?> initial = (StackRun<?>) bottom.itemRun();
        int localSlots = bottom.getItems().getSlots();
        bottom.getItems().setStackInSlot(0, new ItemStack(Items.PAPER));
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
        check(joined == top.itemRun(), "Members did not share the resolved run");
        helper.getLevel().setBlock(topPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        StackRun<?> remaining = (StackRun<?>) bottom.itemRun();
        check(remaining != joined, "Removal retained stale run bounds");
        checkEquals(1, remaining.height(), "Remaining height");
        check(remaining.getSlot(localSlots).isEmpty(), "Removed block remained in flat slots");
        if (!(bottom instanceof BarStackBE)) {
            helper.getLevel().setBlock(topPos, bottom.getBlockState(), Block.UPDATE_ALL);
            BlockPos upperPos = topPos.above();
            helper.getLevel().setBlock(upperPos, bottom.getBlockState(), Block.UPDATE_ALL);
            StackBlockEntity upper = (StackBlockEntity) helper.getLevel().getBlockEntity(upperPos);
            upper.getItems().setStackInSlot(0, new ItemStack(Items.PAPER));
            StackRun<?> whole = (StackRun<?>) upper.itemRun();
            checkEquals(3, whole.height(), "Three-block run");
            check(whole == bottom.itemRun(), "Upper resolution was not shared with the base");
            helper.getLevel().setBlock(topPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            checkEquals(1, ((StackRun<?>) bottom.itemRun()).height(), "Split lower bounds");
            checkEquals(1, ((StackRun<?>) upper.itemRun()).height(), "Split upper bounds");
            check(bottom.itemRun() != upper.itemRun(), "Split runs shared a stale cache");
        }
    }

    /**
     * To reproduce in-game: automate insertion and extraction in each type. Storage accepts normal
     * stacks and settles; Singles shifts the selected column; Bar backfills from its top position.
     * Blocking and clearing the space above must change growth acceptance immediately. The fixture
     * changes that space within one tick, creates fresh insertion sweeps, and checks commit refusal
     * after a successful preview is obstructed.
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
        checkGrowth(helper, storage, new ItemStack(Items.STONE));
        checkGrowth(helper, singles, new ItemStack(Items.STICK));
        checkGrowth(helper, bars, new ItemStack(firstBarItem()));
        helper.succeed();
    }

    private static void checkGrowth(GameTestHelper helper, StackBlockEntity bottom, ItemStack item) {
        int slots = bottom.getItems().getSlots();
        if (bottom.automationSlotLimit() == 1) {
            for (int slot = 0; slot < slots; slot++) {
                bottom.getItems().setStackInSlot(slot, item.copy());
            }
        }
        StackRun<?> initial = (StackRun<?>) bottom.itemRun();
        BlockPos above = bottom.getBlockPos().above();
        helper.getLevel().setBlock(above, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        StackRunItemAccess.InsertionTest blocked = initial.insertionTest();
        checkEquals(0, blocked.test(slots, item), "Blocked insertion sweep");
        checkEquals(0, blocked.test(slots + 1, item), "Second blocked headroom position");
        helper.getLevel().setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        checkEquals(1, initial.insertionTest().test(slots, item), "Sweep retained same-tick obstruction");
        helper.getLevel().setBlock(above, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        checkEquals(0, initial.insertAt(slots, item, false), "Commit reused a successful growth preview");
        helper.getLevel().setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        checkEquals(1, initial.insertAt(slots, item, true), "Simulated growth insertion");
        checkEquals(1, initial.height(), "Simulation grew the run");
        checkEquals(1, initial.insertAt(slots, item, false), "Growth insertion");
        StackBlockEntity top = (StackBlockEntity) helper.getLevel().getBlockEntity(bottom.getBlockPos().above());
        StackRun<?> grown = (StackRun<?>) top.itemRun();
        checkEquals(2, grown.height(), "Grown run bounds");
        check(grown == bottom.itemRun(), "Growth members did not share their cache");
        checkEquals(item.getItem(), grown.getSlot(slots).getItem(), "Grown contents");
    }

    /**
     * To reproduce: fill Singles and Bar blocks completely, then leave only alternating positions.
     * Their occupied collision/outline regions stay aligned with the visible items at every Singles
     * block rotation. The fixture compares these unions with individually optimized shape merges
     * and checks fixed bounds in both orientations of the Bar layout.
     * Negative and full turns also preserve cell bounds and visual-column round trips. To check
     * the visible facing cycle, Shift-click Storage and Singles with a redstone torch four times:
     * the layouts turn north, west, south, east, then return to north. Negative turns are synthetic
     * setter inputs; normal gestures supply normalized rotations.
     */
    public static void cachedGeometryPreservesOccupiedUnions(GameTestHelper helper) {
        checkEquals(new AABB(1.0 / 16, 0, 0.5 / 16, 7.0 / 16, 2.0 / 16, 3.5 / 16),
                BarCubeIdx.localBox(0), "Bottom east-west bar bounds");
        checkEquals(new AABB(0.5 / 16, 2.0 / 16, 1.0 / 16, 3.5 / 16, 4.0 / 16, 7.0 / 16),
                BarCubeIdx.localBox(8), "First north-south bar bounds");
        checkEquals(new AABB(12.5 / 16, 14.0 / 16, 9.0 / 16, 15.5 / 16, 1, 15.0 / 16),
                BarCubeIdx.localBox(63), "Last bar bounds");
        BarStackBE bars = placeBar(helper, ORIGIN);
        SinglesStackBE singles = placeSingles(helper, ORIGIN.east(3));
        var barItem = firstBarItem();
        for (CubeGrid grid : new CubeGrid[]{StorageCubeIdx.GRID, SinglesCubeIdx.GRID}) {
            for (int turn = 0; turn < CubeGrid.ROTATIONS; turn++) {
                checkEquals(grid.localBox(0, turn), grid.localBox(0, turn - CubeGrid.ROTATIONS),
                        "Negative turn changed cell bounds");
                checkEquals(grid.localBox(0, turn), grid.localBox(0, turn + CubeGrid.ROTATIONS),
                        "Full turn changed cell bounds");
            }
        }
        for (int turn = -CubeGrid.ROTATIONS; turn <= CubeGrid.ROTATIONS; turn++) {
            for (int column = 0; column < SinglesCubeIdx.LAYER_SIZE; column++) {
                int visual = SinglesCubeIdx.visualColumnFromStorage(column, turn);
                checkEquals(column, SinglesCubeIdx.storageColumnFromVisual(visual, turn),
                        "Visual/storage column round trip");
            }
        }
        singles.setCubeRotation(0, -1);
        checkEquals(3, singles.getCubeRotation(0), "Negative item rotation");
        singles.setCubeRotation(0, CubeGrid.ROTATIONS);
        checkEquals(0, singles.getCubeRotation(0), "Full item rotation");
        Direction[] facings = {Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST};
        StorageStackBE storage = placeStorage(helper, ORIGIN.east(6));
        for (int turn = 0; turn < facings.length; turn++) {
            storage.setRotation(turn - CubeGrid.ROTATIONS);
            singles.setRotation(turn - CubeGrid.ROTATIONS);
            checkEquals(facings[turn], storage.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING),
                    "Storage facing order");
            checkEquals(facings[turn], singles.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING),
                    "Singles facing order");
            checkEquals(turn, storage.getRotation(), "Storage facing inverse");
            checkEquals(turn, singles.getRotation(), "Singles facing inverse");
        }
        for (int stride : new int[]{1, 2}) {
            VoxelShape expectedBars = Shapes.empty();
            for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
                boolean occupied = slot % stride == 0;
                bars.getItems().setStackInSlot(slot,
                        occupied ? new ItemStack(barItem) : ItemStack.EMPTY);
                singles.getItems().setStackInSlot(slot,
                        occupied ? new ItemStack(Items.STICK) : ItemStack.EMPTY);
                if (occupied) expectedBars = Shapes.or(expectedBars, BarCubeIdx.shapeFor(slot));
            }
            check(!Shapes.joinIsNotEmpty(expectedBars, bars.getCachedShape(), BooleanOp.NOT_SAME),
                    "Bar union changed occupied regions");
            for (int rotation = 0; rotation < 4; rotation++) {
                singles.setRotation(rotation);
                VoxelShape expectedSingles = Shapes.empty();
                for (int slot = 0; slot < SinglesStackBE.SLOTS; slot += stride) {
                    expectedSingles = Shapes.or(expectedSingles, SinglesCubeIdx.GRID.localShape(slot, rotation));
                }
                check(!Shapes.joinIsNotEmpty(expectedSingles, singles.getCachedShape(), BooleanOp.NOT_SAME),
                        "Singles union changed occupied regions at rotation " + rotation);
            }
        }
        helper.succeed();
    }

    private static void checkLocalFill(StackBlockEntity block, double expected) {
        StackRun<?> run = (StackRun<?>) block.itemRun();
        check(Math.abs(run.fillLevel() - expected / block.getItems().getSlots()) < 1.0e-12,
                "Local fill changed inside a batch or after a load");
    }

    /**
     * To reproduce in-game: save and reload partly filled stacks, including an unstackable Storage
     * item. Comparators retain their whole-run fill; loading empty contents clears shapes and fill.
     * Fill and empty the blocks repeatedly: full reads 15 and empty reads 0 after every cycle.
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
            CompoundTag saved = block.saveWithoutMetadata();
            block.load(saved);
            checkEquals(1, ((StackRun<?>) block.itemRun()).comparatorSignal(), "Loaded comparator");
            CompoundTag empty = saved.copy();
            empty.put("Items", new CompoundTag());
            block.load(empty);
            checkEquals(0, ((StackRun<?>) block.itemRun()).comparatorSignal(), "Empty loaded comparator");
        }
        check(singles.getCachedShape().isEmpty(), "Singles load retained cached shape");
        check(bars.getCachedShape().isEmpty(), "Bar load retained cached shape");
        checkComparatorMutations(storage, new ItemStack(Items.STONE, 64));
        checkComparatorMutations(singles, new ItemStack(Items.PAPER));
        checkComparatorMutations(bars, new ItemStack(firstBarItem()));
        helper.succeed();
    }

    private static void checkComparatorMutations(StackBlockEntity block, ItemStack fullSlot) {
        int slots = block.getItems().getSlots();
        invoke(BEGIN_BATCH, block);
        try {
            for (int cycle = 0; cycle < 32; cycle++) {
                ItemStack partial = block.automationSlotLimit() == 1 ? fullSlot.copy()
                        : new ItemStack(Items.ENDER_PEARL);
                block.getItems().setStackInSlot(0, partial);
                checkLocalFill(block, block.automationSlotLimit() == 1 ? 1.0
                        : 1.0 / partial.getMaxStackSize());
                block.getItems().setStackInSlot(0, ItemStack.EMPTY);
                checkEquals(0, ((StackRun<?>) block.itemRun()).comparatorSignal(), "Empty endpoint");
            }
            for (int slot = 0; slot < slots; slot++) {
                ItemStack full = fullSlot.copy();
                if (block instanceof StorageStackBE) {
                    full = switch (slot % 3) {
                        case 0 -> new ItemStack(Items.STONE, 64);
                        case 1 -> new ItemStack(Items.ENDER_PEARL, 16);
                        default -> new ItemStack(Items.WOODEN_SWORD);
                    };
                }
                block.getItems().setStackInSlot(slot, full);
                checkLocalFill(block, slot + 1.0);
            }
            checkEquals(15, ((StackRun<?>) block.itemRun()).comparatorSignal(), "Full endpoint");
            block.load(block.saveWithoutMetadata());
            checkLocalFill(block, slots);
            for (int slot = 0; slot < slots; slot++) {
                block.getItems().extractItem(slot, 64, false);
                checkLocalFill(block, slots - slot - 1.0);
            }
            checkEquals(0, ((StackRun<?>) block.itemRun()).comparatorSignal(), "Empty after loaded mutations");
        } finally {
            invoke(END_BATCH, block);
        }
    }
}
