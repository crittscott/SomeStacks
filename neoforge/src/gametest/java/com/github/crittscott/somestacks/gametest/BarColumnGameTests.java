package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.SomeStacksNeoForge;
import com.github.crittscott.somestacks.block.BarStackBE;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.function.Consumer;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.count;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.droppedNear;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.firstBarItem;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.heldAt;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.occupied;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.seedSlot;

/**
 * Bar support and collapse in a live level: item validity, grounding at the bottom of a column,
 * footprint overlap deciding support across the alternating layer orientations, and the difference
 * between player extraction, which drops what it unsupports, and automated extraction, which
 * backfills from the top instead.
 */
@GameTestHolder(SomeStacksNeoForge.MODID)
@PrefixGameTestTemplate(false)
public final class BarColumnGameTests {
    private BarColumnGameTests() {}

    /** See {@link BarColumnChecks#validityAndBottomGroundingAreEnforced}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void validityAndBottomGroundingAreEnforced(GameTestHelper helper) {
        BarColumnChecks.validityAndBottomGroundingAreEnforced(helper);
    }

    /** See {@link BarColumnChecks#upperBarRequiresOverlappingSupport}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void upperBarRequiresOverlappingSupport(GameTestHelper helper) {
        BarColumnChecks.upperBarRequiresOverlappingSupport(helper);
    }

    /** See {@link BarColumnChecks#playerExtractionDropsUnsupportedBars}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void playerExtractionDropsUnsupportedBars(GameTestHelper helper) {
        BarColumnChecks.playerExtractionDropsUnsupportedBars(helper);
    }

    /** See {@link BarColumnChecks#supportedNeighborSurvivesPlayerExtraction}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void supportedNeighborSurvivesPlayerExtraction(GameTestHelper helper) {
        BarColumnChecks.supportedNeighborSurvivesPlayerExtraction(helper);
    }

    /** See {@link BarColumnChecks#differentlyOrientedSeamUsesFootprintOverlap}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void differentlyOrientedSeamUsesFootprintOverlap(GameTestHelper helper) {
        BarColumnChecks.differentlyOrientedSeamUsesFootprintOverlap(helper);
    }

    /** See {@link BarColumnChecks#depositTraceStopsAtLastEmptyBeforeOccupiedBar}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void depositTraceStopsAtLastEmptyBeforeOccupiedBar(GameTestHelper helper) {
        BarColumnChecks.depositTraceStopsAtLastEmptyBeforeOccupiedBar(helper);
    }

    /**
     * To reproduce in-game: fill a supported Bar column, let item automation extract a lower
     * position, and verify the top bar fills the hole without dropping an item.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void automationBackfillsWithoutDropping(GameTestHelper helper) {
        AutomationChecks.barAutomationBackfillsWithoutDropping(helper);
    }

    /**
     * To reproduce in-game: attach item automation to a Bar Stack and target individual advertised
     * positions; unsupported positions refuse insertion while a supported empty position accepts it.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void capabilityInsertionAnswersForThePositionItIsGiven(GameTestHelper helper) {
        AutomationChecks.barInsertionAnswersForThePositionItIsGiven(helper);
    }

    /**
     * Validity gates insertion only, so a bar stored before an {@code ss ingot} edit or a data pack
     * reload narrowed the rule has to survive the backfill an automated extraction moves it through.
     * A stick stands in for such a bar: a Bar Stack refuses one from a deposit, but may be holding
     * contents the current ingot set no longer covers.
     *
     * <p>To reproduce in-game: fill a Bar Stack, narrow the ingot rule so one stored bar is no
     * longer accepted, then automate extraction below it. Backfill retains or drops that legacy
     * bar without deleting or changing it.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void backfillKeepsABarItWouldNoLongerAccept(GameTestHelper helper) {
        AutomationChecks.barBackfillKeepsAStoredItemNoLongerAccepted(helper);
    }

    /**
     * No in-game reproduction applies: this verifies the loader capability simulation contract; a
     * simulated Bar extraction reports the result without changing the stored bar.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void capabilitySimulationDoesNotMutateExtraction(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        bars.depositAt(0, new ItemStack(barItem));
        IItemHandler capability = GameTestSupport.capability(bars);

        ItemStack simulated = capability.extractItem(0, 64, true);

        checkEquals(barItem, simulated.getItem(), "Simulated item");
        checkEquals(1, simulated.getCount(), "Simulated count");
        checkEquals(barItem, bars.getItems().getStackInSlot(0).getItem(),
                "Simulation mutated contents");
        helper.succeed();
    }

    /** See {@link BarColumnChecks#breakingLowerBlockCollapsesDependentUpperBlock}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void breakingLowerBlockCollapsesDependentUpperBlock(GameTestHelper helper) {
        BarColumnChecks.breakingLowerBlockCollapsesDependentUpperBlock(helper);
    }

    /** See {@link BarColumnChecks#breakingFullColumnConsolidatesDrops}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void breakingFullColumnConsolidatesDrops(GameTestHelper helper) {
        BarColumnChecks.breakingFullColumnConsolidatesDrops(helper);
    }

    /** See {@link BarColumnChecks#playerCascadeConsolidatesAcrossBlocks}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void playerCascadeConsolidatesAcrossBlocks(GameTestHelper helper) {
        BarColumnChecks.playerCascadeConsolidatesAcrossBlocks(helper);
    }

    /** See {@link BarColumnChecks#collapseDoesNotMergeDifferentComponents}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void collapseDoesNotMergeDifferentComponents(GameTestHelper helper) {
        BarColumnChecks.collapseDoesNotMergeDifferentComponents(helper);
    }

    /** See {@link BarColumnChecks#comparatorReservesZeroForAnEmptyColumn}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorReservesZeroForAnEmptyColumn(GameTestHelper helper) {
        BarColumnChecks.comparatorReservesZeroForAnEmptyColumn(helper);
    }

    /** See {@link BarColumnChecks#comparatorReadsTheWholeColumnFromEveryBlock}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        BarColumnChecks.comparatorReadsTheWholeColumnFromEveryBlock(helper);
    }

    /** See {@link BarColumnChecks#comparatorFollowsAColumnLosingABlock}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorFollowsAColumnLosingABlock(GameTestHelper helper) {
        BarColumnChecks.comparatorFollowsAColumnLosingABlock(helper);
    }

    /**
     * A cascade still settles the column above an empty block that protection refuses to remove.
     * Support depends on the seam's occupancy, which is empty whether or not the block remains;
     * protection controls only the world edit.
     *
     * <p>To reproduce in-game: build a supported two-block Bar column, deny [SomeStacks]
     * permission to remove the lower block, and extract its bottom support. The empty lower block
     * remains, while the unsupported upper block comes down.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void refusedRemovalStillLetsTheBarsAboveComeDown(GameTestHelper helper) {
        BarStackBE lower = placeBar(helper, ORIGIN);
        BarStackBE upper = placeBar(helper, ORIGIN.above());
        Item barItem = firstBarItem();
        BlockPos lowerPos = lower.getBlockPos();
        BlockPos upperPos = upper.getBlockPos();

        // One bar per layer, each resting on the one below, so slot 0 carries the whole column and
        // the seam the upper block stands on is the top of that chain.
        int slot = 0;
        seedSlot(lower.getItems(), slot, new ItemStack(barItem));
        for (int layer = 1; layer < 8; layer++) {
            slot = BarColumnChecks.firstSupportedBy(slot, layer * 8, (layer + 1) * 8);
            seedSlot(lower.getItems(), slot, new ItemStack(barItem));
        }
        seedSlot(upper.getItems(), BarColumnChecks.firstSeamSupportedBy(slot - 56),
                new ItemStack(barItem));

        Consumer<BlockEvent.BreakEvent> denyLower = event -> {
            if (event.getPos().equals(lowerPos)) {
                event.setCanceled(true);
            }
        };

        NeoForge.EVENT_BUS.addListener(denyLower);
        try {
            lower.extractAt(0);
        } finally {
            NeoForge.EVENT_BUS.unregister(denyLower);
        }

        helper.assertBlockPresent(ModRegistry.BAR_STACK_BLOCK.get(), ORIGIN);
        check(lower.isEmpty(), "The kept block held on to its bars");
        check(helper.getLevel().getBlockEntity(upperPos) == null,
                "The block above a kept block was not brought down");
        helper.succeed();
    }
}
