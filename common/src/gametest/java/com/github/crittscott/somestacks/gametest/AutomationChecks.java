package com.github.crittscott.somestacks.gametest;

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
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.withPolicy;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.RunEdit;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.function.Function;

/**
 * Automation scenarios shared by every loader, each driven through that loader's own automation
 * surface: the {@link GameTestScaffold.Automation} its support class builds over Forge's or
 * NeoForge's item-handler capability or Fabric's Transfer API storage.
 */
public final class AutomationChecks {
    private AutomationChecks() {}

    /**
     * No in-game form: a simulation has no visible effect. This guards that a simulated automation
     * extraction from a Bar Stack reports the bar without changing the stored contents.
     */
    public static void barSimulationDoesNotMutateExtraction(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        check(bars.depositAt(0, new ItemStack(barItem)), "Bar fixture deposit failed");

        ItemStack simulated = automation.apply(bars).extract(0, 64, true);

        checkEquals(barItem, simulated.getItem(), "Simulated item");
        checkEquals(1, simulated.getCount(), "Simulated count");
        checkEquals(
                barItem,
                bars.getItems().getStackInSlot(0).getItem(),
                "Simulation mutated contents");
        helper.succeed();
    }

    /**
     * To reproduce in-game: fill a supported Bar column, automate extraction from its base, and
     * verify the top bar backfills the hole without an item entity being dropped.
     */
    public static void barAutomationBackfillsWithoutDropping(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        GameTestScaffold.Automation access = automation.apply(bars);
        check(access.insertWalking(new ItemStack(barItem, 10)).isEmpty(), "Bars were refused");

        ItemStack extracted = access.extract(0, 64, false);
        checkEquals(1, extracted.getCount(), "Automated extracted count");
        checkEquals(9, count(bars.getItems(), barItem), "Count after extraction");
        checkEquals(barItem, bars.getItems().getStackInSlot(0).getItem(), "Backfilled item");
        checkEquals(0, droppedNear(helper, bars.getBlockPos(), barItem), "Dropped bar count");
        helper.succeed();
    }

    /**
     * To reproduce in-game: narrow the ingot rule after storing a bar, then automate extraction
     * below it; backfill retains or drops the now-ineligible stored item without deleting it.
     */
    public static void barBackfillKeepsAStoredItemNoLongerAccepted(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        BlockPos pos = bars.getBlockPos();
        check(
                !BarStackBE.isValidBarItem(new ItemStack(Items.STICK)),
                "Test needs an item Bar refuses");
        bars.getItems().insertItem(0, new ItemStack(barItem), false);
        seedSlot(bars.getItems(), 1, new ItemStack(Items.STICK));

        ItemStack extracted = automation.apply(bars).extract(0, 64, false);
        checkEquals(barItem, extracted.getItem(), "Automated extracted item");
        checkEquals(1, extracted.getCount(), "Automated extracted count");
        int accounted = heldAt(helper, pos, Items.STICK) + droppedNear(helper, pos, Items.STICK);
        checkEquals(1, accounted, "Stored item after backfill");
        helper.succeed();
    }

    /**
     * To reproduce in-game: target individual Bar positions with automation; unsupported positions
     * refuse insertion while a grounded empty position accepts exactly one bar.
     */
    public static void barInsertionAnswersForThePositionItIsGiven(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        ItemStack offered = new ItemStack(firstBarItem(), 4);
        GameTestScaffold.Automation access = automation.apply(bars);

        checkEquals(4, access.insert(8, offered, true).getCount(), "Simulated unsupported");
        checkEquals(4, access.insert(8, offered, false).getCount(), "Committed unsupported");
        checkEquals(0, occupied(bars.getItems()), "Unsupported position stored a bar");
        checkEquals(3, access.insert(0, offered, true).getCount(), "Simulated grounded");
        checkEquals(3, access.insert(0, offered, false).getCount(), "Committed grounded");
        checkEquals(4, offered.getCount(), "Automation mutated caller input");
        checkEquals(1, occupied(bars.getItems()), "Occupied position count");
        helper.succeed();
    }

    /**
     * No in-game form: an ordinary player cannot make automation re-enter a run's handler. This
     * guards the refusal of an automation insertion that arrives while another structural edit is
     * still running (as a neighbor woken by that edit could send), and that the next insertion
     * succeeds once the edit ends.
     */
    public static void reentrantStructuralEditIsRefused(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        GameTestScaffold.Automation access = automation.apply(placeSingles(helper, ORIGIN));
        ItemStack offered = new ItemStack(Items.STICK);
        boolean claimed = RunEdit.begin();
        try {
            check(claimed, "Structural edit guard was already claimed");
            checkEquals(
                    1,
                    access.insert(0, offered, false).getCount(),
                    "Reentrant insertion accepted an item");
        } finally {
            if (claimed) {
                RunEdit.end();
            }
        }
        check(access.insert(0, offered, false).isEmpty(), "Released guard still refused insertion");
        helper.succeed();
    }

    /**
     * To reproduce in-game: set {@code max_pile_height} to 2, connect item automation to a
     * one-block stack of each type, and verify it can address one additional block of empty
     * positions for growth; set it to 1 and verify it addresses only the existing block.
     */
    public static void singleBlocksAdvertiseConfiguredHeadroom(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        GameTestScaffold.Automation storage = automation.apply(placeStorage(helper, ORIGIN));
        GameTestScaffold.Automation singles =
                automation.apply(placeSingles(helper, ORIGIN.east(3)));
        GameTestScaffold.Automation bar = automation.apply(placeBar(helper, ORIGIN.east(6)));

        // Each pinned height and the levels a one-block run advertises under it: one headroom
        // block below the limit, none at it.
        for (int[] pin : new int[][] {{2, 2}, {1, 1}}) {
            int levels = pin[1];
            withPolicy(
                    Map.of(ServerConfig.Setting.MAX_PILE_HEIGHT, pin[0]),
                    () -> {
                        checkEquals(
                                StorageStackBE.SLOTS * levels,
                                storage.slots(),
                                "Storage advertised slots at height " + pin[0]);
                        checkEquals(
                                SinglesStackBE.SLOTS * levels,
                                singles.slots(),
                                "Singles advertised slots at height " + pin[0]);
                        checkEquals(
                                BarStackBE.SLOTS * levels,
                                bar.slots(),
                                "Bar advertised slots at height " + pin[0]);
                    });
        }
        helper.succeed();
    }

    /**
     * To reproduce in-game: extract a lower Singles cell through item automation; the item above
     * shifts down by the same gravity path used for player extraction.
     */
    public static void singlesExtractionUsesThePlayerGravityPath(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        int bottom = SinglesCubeIdx.indexFromColumn(0, 0);
        int above = SinglesCubeIdx.indexFromColumn(0, 1);
        singles.getItems().insertItem(bottom, new ItemStack(Items.STICK), false);
        singles.getItems().insertItem(above, new ItemStack(Items.PAPER), false);
        GameTestScaffold.Automation access = automation.apply(singles);

        ItemStack simulated = access.extract(bottom, 64, true);
        checkEquals(Items.STICK, simulated.getItem(), "Simulated extracted item");
        checkEquals(
                Items.STICK,
                singles.getItems().getStackInSlot(bottom).getItem(),
                "Simulation mutated contents");
        ItemStack extracted = access.extract(bottom, 64, false);
        checkEquals(1, extracted.getCount(), "Extracted count");
        checkEquals(
                Items.PAPER,
                singles.getItems().getStackInSlot(bottom).getItem(),
                "Column did not shift");
        helper.succeed();
    }

    /**
     * To reproduce in-game: target individual Singles cells with item automation; an unsupported
     * cell refuses the item while a grounded empty cell accepts exactly one.
     */
    public static void singlesInsertionAnswersForTheCellItIsGiven(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        GameTestScaffold.Automation access = automation.apply(singles);
        ItemStack offered = new ItemStack(Items.STICK, 3);

        checkEquals(3, access.insert(63, offered, true).getCount(), "Simulated unsupported");
        checkEquals(3, access.insert(63, offered, false).getCount(), "Committed unsupported");
        checkEquals(0, occupied(singles.getItems()), "Unsupported cell stored an item");
        checkEquals(2, access.insert(0, offered, true).getCount(), "Simulated grounded");
        checkEquals(0, occupied(singles.getItems()), "Simulation changed occupancy");
        checkEquals(2, access.insert(0, offered, false).getCount(), "Committed grounded");
        checkEquals(3, offered.getCount(), "Automation mutated caller input");
        checkEquals(Items.STICK, singles.getItems().getStackInSlot(0).getItem(), "Named cell item");
        helper.succeed();
    }

    /**
     * No in-game form: a simulation has no visible effect. This guards that a simulated automation
     * insertion leaves a Singles column and the offered stack unchanged while reporting what one
     * cell would accept.
     */
    public static void singlesSimulationDoesNotMutateAnyStack(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        ItemStack offered = new ItemStack(Items.APPLE, 5);

        // Cell 0 is a bottom-layer cell of a column standing on the world, so it is grounded
        // outright and the simulation has something to accept.
        ItemStack remainder = automation.apply(singles).insert(0, offered, true);

        checkEquals(5, offered.getCount(), "Simulation mutated its input");
        checkEquals(0, count(singles.getItems(), Items.APPLE), "Simulation mutated the column");
        checkEquals(
                4, remainder.getCount(), "A cell takes one item, so one call should accept one");
        helper.succeed();
    }

    /**
     * To reproduce in-game: let item automation walk advertised Singles cells in order; the first
     * supported empty cells accept the offered items without mutating the caller's stack.
     */
    public static void singlesWalkFillsFromTheBottom(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        ItemStack offered = new ItemStack(Items.STICK, 3);

        check(automation.apply(singles).insertWalking(offered).isEmpty(), "Sticks were refused");
        checkEquals(3, offered.getCount(), "Automation mutated caller input");
        for (int cell = 0; cell < 3; cell++) {
            checkEquals(
                    Items.STICK,
                    singles.getItems().getStackInSlot(cell).getItem(),
                    "Lowest cell " + cell);
        }
        helper.succeed();
    }

    /**
     * To reproduce in-game: connect item automation separately to the lower and upper blocks of one
     * Storage pile; both connections expose the same items and capacity.
     */
    public static void storageFromEveryBlockAddressesTheSamePile(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        StorageStackBE lower = placeStorage(helper, ORIGIN);
        StorageStackBE upper = placeStorage(helper, ORIGIN.above());
        lower.getItems().insertItem(0, new ItemStack(Items.STONE, 12), false);
        GameTestScaffold.Automation lowerAccess = automation.apply(lower);
        GameTestScaffold.Automation upperAccess = automation.apply(upper);

        checkEquals(12, lowerAccess.stackAt(0).getCount(), "Lower block count");
        checkEquals(12, upperAccess.stackAt(0).getCount(), "Upper block count");
        checkEquals(lowerAccess.slots(), upperAccess.slots(), "Advertised shape");
        helper.succeed();
    }

    /**
     * To reproduce in-game: target a named Storage slot with item automation; that slot accepts a
     * compatible stack, refuses an incompatible one, and a moment later the pile packs the stack
     * toward the base.
     */
    public static void storageInsertionAnswersForTheSlotItIsGiven(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        GameTestScaffold.Automation access = automation.apply(storage);
        ItemStack offered = new ItemStack(Items.STONE, 8);

        check(access.insert(20, offered, true).isEmpty(), "Simulated insertion was refused");
        check(access.insert(20, offered, false).isEmpty(), "Committed insertion was refused");
        checkEquals(8, offered.getCount(), "Automation mutated caller input");
        checkEquals(8, storage.getItems().getStackInSlot(20).getCount(), "Named slot count");
        checkEquals(
                8,
                access.insert(20, new ItemStack(Items.DIRT, 8), true).getCount(),
                "Incompatible insertion");

        // The insertion scheduled the settlement that packs the stack down; wait for it.
        helper.runAfterDelay(
                2,
                () -> {
                    checkEquals(
                            8, storage.getItems().getStackInSlot(0).getCount(), "Settled base");
                    check(storage.getItems().getStackInSlot(20).isEmpty(), "Stack did not move");
                    checkEquals(0, count(storage.getItems(), Items.DIRT), "Refused dirt stored");
                    helper.succeed();
                });
    }

    /**
     * To reproduce in-game: fill a Storage pile, obstruct the space above it, and insert through
     * item automation; simulation and mutation both report no room and leave the obstruction.
     */
    public static void storageObstructionPreventsGrowthAndSimulationReportsNoRoom(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
        }
        BlockPos above = helper.absolutePos(ORIGIN.above());
        helper.getLevel().setBlock(above, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        ItemStack offered = new ItemStack(Items.STONE, 4);
        int headroom = StorageStackBE.SLOTS;
        withPolicy(
                Map.of(
                        ServerConfig.Setting.MAX_PILE_HEIGHT,
                        2,
                        ServerConfig.Setting.ENABLE_STORAGE_STACK_BLOCK,
                        true),
                () -> {
                    GameTestScaffold.Automation access = automation.apply(storage);
                    checkEquals(
                            4,
                            access.insert(headroom, offered, true).getCount(),
                            "Simulated remainder");
                    checkEquals(
                            4,
                            access.insert(headroom, offered, false).getCount(),
                            "Committed remainder");
                });
        checkEquals(4, offered.getCount(), "Automation mutated caller input");
        check(helper.getLevel().getBlockState(above).is(Blocks.STONE), "Obstruction changed");
        helper.succeed();
    }
}
