package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StackRunItemAccess;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.count;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.droppedNear;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.firstBarItem;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.heldAt;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.occupied;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.seedSlot;

/** Loader-neutral automation semantics exercised directly through {@link StackRunItemAccess}. */
public final class AutomationChecks {
    private AutomationChecks() {}

    /**
     * To reproduce in-game: target a named Storage slot with item automation; that slot accepts a
     * compatible stack, refuses an incompatible one, and settling later packs it toward the base.
     */
    public static void storageInsertionAnswersForTheSlotItIsGiven(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        StackRunItemAccess run = storage.itemRun();
        ItemStack offered = new ItemStack(Items.STONE, 8);

        checkEquals(8, run.insertAt(20, offered, true), "Simulated insertion");
        checkEquals(8, run.insertAt(20, offered, false), "Committed insertion");
        checkEquals(8, offered.getCount(), "Automation mutated caller input");
        checkEquals(8, storage.getItems().getStackInSlot(20).getCount(), "Named slot count");
        checkEquals(0, run.insertAt(20, new ItemStack(Items.DIRT, 8), true),
                "Incompatible insertion");

        StoragePile pile = storage.pile();
        check(pile != null, "Pile did not resolve");
        pile.settle();
        checkEquals(8, storage.getItems().getStackInSlot(0).getCount(), "Settled base count");
        checkEquals(0, count(storage.getItems(), Items.DIRT), "Refused insertion stored items");
        helper.succeed();
    }

    /**
     * To reproduce in-game: fill a Storage pile, obstruct the space above it, and insert through
     * item automation; simulation and mutation both report no room and leave the obstruction.
     */
    public static void storageObstructionPreventsGrowthAndSimulationReportsNoRoom(
            GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
        }
        BlockPos above = helper.absolutePos(ORIGIN.above());
        helper.getLevel().setBlock(above, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        ItemStack offered = new ItemStack(Items.STONE, 4);
        int headroom = StorageStackBE.SLOTS;

        checkEquals(0, storage.itemRun().insertAt(headroom, offered, true),
                "Simulated accepted count");
        checkEquals(0, storage.itemRun().insertAt(headroom, offered, false),
                "Committed accepted count");
        checkEquals(4, offered.getCount(), "Automation mutated caller input");
        check(helper.getLevel().getBlockState(above).is(Blocks.STONE), "Obstruction changed");
        helper.succeed();
    }

    /**
     * To reproduce in-game: target individual Singles cells with item automation; an unsupported
     * cell refuses the item while a grounded empty cell accepts exactly one.
     */
    public static void singlesInsertionAnswersForTheCellItIsGiven(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        StackRunItemAccess run = singles.itemRun();
        ItemStack offered = new ItemStack(Items.STICK, 3);

        checkEquals(0, run.insertAt(63, offered, true), "Simulated unsupported insertion");
        checkEquals(0, run.insertAt(63, offered, false), "Committed unsupported insertion");
        checkEquals(0, occupied(singles.getItems()), "Unsupported cell stored an item");
        checkEquals(1, run.insertAt(0, offered, true), "Simulated grounded insertion");
        checkEquals(0, occupied(singles.getItems()), "Simulation changed occupancy");
        checkEquals(1, run.insertAt(0, offered, false), "Committed grounded insertion");
        checkEquals(3, offered.getCount(), "Automation mutated caller input");
        checkEquals(Items.STICK, singles.getItems().getStackInSlot(0).getItem(),
                "Named cell item");
        helper.succeed();
    }

    /**
     * To reproduce in-game: let item automation walk advertised Singles cells in order; the first
     * supported empty cells accept the offered items without mutating the caller's stack.
     */
    public static void singlesWalkFillsFromTheBottom(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        ItemStack offered = new ItemStack(Items.STICK, 3);

        checkEquals(3, insertWalkingSlots(singles.itemRun(), offered), "Accepted item count");
        checkEquals(3, offered.getCount(), "Automation mutated caller input");
        for (int cell = 0; cell < 3; cell++) {
            checkEquals(Items.STICK, singles.getItems().getStackInSlot(cell).getItem(),
                    "Lowest cell " + cell);
        }
        helper.succeed();
    }

    /**
     * To reproduce in-game: extract a lower Singles cell through item automation; the item above
     * shifts down by the same gravity path used for player extraction.
     */
    public static void singlesExtractionUsesThePlayerGravityPath(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        int bottom = SinglesCubeIdx.indexFromColumn(0, 0);
        int above = SinglesCubeIdx.indexFromColumn(0, 1);
        singles.getItems().insertItem(bottom, new ItemStack(Items.STICK), false);
        singles.getItems().insertItem(above, new ItemStack(Items.PAPER), false);

        ItemStack simulated = singles.itemRun().extract(bottom, 64, true);
        checkEquals(Items.STICK, simulated.getItem(), "Simulated extracted item");
        checkEquals(Items.STICK, singles.getItems().getStackInSlot(bottom).getItem(),
                "Simulation mutated contents");
        ItemStack extracted = singles.itemRun().extract(bottom, 64, false);
        checkEquals(1, extracted.getCount(), "Extracted count");
        checkEquals(Items.PAPER, singles.getItems().getStackInSlot(bottom).getItem(),
                "Column did not shift");
        helper.succeed();
    }

    /**
     * To reproduce in-game: fill a supported Bar column, automate extraction from its base, and
     * verify the top bar backfills the hole without an item entity being dropped.
     */
    public static void barAutomationBackfillsWithoutDropping(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        checkEquals(10, insertWalkingSlots(bars.itemRun(), new ItemStack(barItem, 10)),
                "Inserted bar count");

        ItemStack extracted = bars.itemRun().extract(0, 64, false);
        checkEquals(1, extracted.getCount(), "Automated extracted count");
        checkEquals(9, count(bars.getItems(), barItem), "Count after extraction");
        checkEquals(barItem, bars.getItems().getStackInSlot(0).getItem(), "Backfilled item");
        checkEquals(0, droppedNear(helper, bars.getBlockPos(), barItem), "Dropped bar count");
        helper.succeed();
    }

    /**
     * To reproduce in-game: target individual Bar positions with automation; unsupported positions
     * refuse insertion while a grounded empty position accepts exactly one bar.
     */
    public static void barInsertionAnswersForThePositionItIsGiven(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        ItemStack offered = new ItemStack(firstBarItem(), 4);
        StackRunItemAccess run = bars.itemRun();

        checkEquals(0, run.insertAt(8, offered, true), "Simulated unsupported insertion");
        checkEquals(0, run.insertAt(8, offered, false), "Committed unsupported insertion");
        checkEquals(0, occupied(bars.getItems()), "Unsupported position stored a bar");
        checkEquals(1, run.insertAt(0, offered, true), "Simulated grounded insertion");
        checkEquals(1, run.insertAt(0, offered, false), "Committed grounded insertion");
        checkEquals(4, offered.getCount(), "Automation mutated caller input");
        checkEquals(1, occupied(bars.getItems()), "Occupied position count");
        helper.succeed();
    }

    /**
     * To reproduce in-game: narrow the ingot rule after storing a bar, then automate extraction
     * below it; backfill retains or drops the now-ineligible stored item without deleting it.
     */
    public static void barBackfillKeepsAStoredItemNoLongerAccepted(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        BlockPos pos = bars.getBlockPos();
        check(!BarStackBE.isValidBarItem(new ItemStack(Items.STICK)),
                "Test needs an item Bar refuses");
        bars.getItems().insertItem(0, new ItemStack(barItem), false);
        seedSlot(bars.getItems(), 1, new ItemStack(Items.STICK));

        ItemStack extracted = bars.itemRun().extract(0, 64, false);
        checkEquals(barItem, extracted.getItem(), "Automated extracted item");
        checkEquals(1, extracted.getCount(), "Automated extracted count");
        int accounted = heldAt(helper, pos, Items.STICK) + droppedNear(helper, pos, Items.STICK);
        checkEquals(1, accounted, "Stored item after backfill");
        helper.succeed();
    }

    private static int insertWalkingSlots(StackRunItemAccess run, ItemStack stack) {
        int remaining = stack.getCount();
        for (int slot = 0; slot < run.advertisedSlots() && remaining > 0; slot++) {
            int accepted = run.insertAt(slot, stack.copyWithCount(remaining), false);
            remaining -= accepted;
        }
        return stack.getCount() - remaining;
    }
}
