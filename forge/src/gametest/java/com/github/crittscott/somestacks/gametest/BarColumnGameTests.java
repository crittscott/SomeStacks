package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.block.BarStackBE;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

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
@GameTestHolder(SomeStacks.MODID)
@PrefixGameTestTemplate(false)
public final class BarColumnGameTests {
    private BarColumnGameTests() {}

    /**
     * Loader entry point for {@link BarColumnChecks#validityAndBottomGroundingAreEnforced}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void validityAndBottomGroundingAreEnforced(GameTestHelper helper) {
        BarColumnChecks.validityAndBottomGroundingAreEnforced(helper);
    }

    /** See {@link BarColumnChecks#barAdmissionFollowsTheIngotTag}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void barAdmissionFollowsTheIngotTag(GameTestHelper helper) {
        BarColumnChecks.barAdmissionFollowsTheIngotTag(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#upperBarRequiresOverlappingSupport}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void upperBarRequiresOverlappingSupport(GameTestHelper helper) {
        BarColumnChecks.upperBarRequiresOverlappingSupport(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#playerExtractionDropsUnsupportedBars}; see that
     * method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void playerExtractionDropsUnsupportedBars(GameTestHelper helper) {
        BarColumnChecks.playerExtractionDropsUnsupportedBars(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#supportedNeighborSurvivesPlayerExtraction}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void supportedNeighborSurvivesPlayerExtraction(GameTestHelper helper) {
        BarColumnChecks.supportedNeighborSurvivesPlayerExtraction(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#differentlyOrientedSeamUsesFootprintOverlap};
     * see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void differentlyOrientedSeamUsesFootprintOverlap(GameTestHelper helper) {
        BarColumnChecks.differentlyOrientedSeamUsesFootprintOverlap(helper);
    }

    /**
     * To reproduce: allow the chosen ingot for Bar, then use a machine that walks positions
     * bottom-up to insert ten identical ingots. Extract one from bottom position 0 through
     * automation. Nine remain, position 0 is backfilled from the topmost bar, and no ingot item
     * entity is dropped.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void automationBackfillsWithoutDropping(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        IItemHandler capability = GameTestSupport.capability(bars);
        ItemStack offered = new ItemStack(barItem, 10);

        // A walk of the advertised positions fills the block from the bottom, because each bar
        // placed supports the layer above it before the walk reaches it.
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(capability, offered, false);
        check(remainder.isEmpty(), "A walk of the positions left a remainder");
        checkEquals(10, count(bars.getItems(), barItem),
                "Inserted bar count");

        ItemStack extracted = capability.extractItem(0, 64, false);

        checkEquals(1, extracted.getCount(), "Automated extracted count");
        checkEquals(9, count(bars.getItems(), barItem),
                "Count after automated extraction");
        checkEquals(barItem, bars.getItems().getStackInSlot(0).getItem(),
                "Hole was not backfilled");
        checkEquals(0, droppedNear(helper, bars.getBlockPos(), barItem),
                "Automation dropped a bar");
        helper.succeed();
    }

    /**
     * Internal positional insertion check: offer four accepted ingots to unsupported slot 8, then
     * grounded slot 0. Slot 8 refuses all four; slot 0 accepts exactly one, leaving the caller's
     * offer unchanged. Simulation reports the same capacity without storing.
     *
     * <p>To check manually, use slot-addressing automation on an empty Bar fixture. An unsupported
     * target must not redirect to another position; a bottom target stores one bar. Simulation
     * requires a machine or probe exposing that operation.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void capabilityInsertionAnswersForThePositionItIsGiven(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        IItemHandler capability = GameTestSupport.capability(bars);
        ItemStack offered = new ItemStack(barItem, 4);

        // Position 8 is the first of layer 1, with an empty layer 0 beneath it, so it is refused
        // rather than redirected to a position that would take the bar.
        checkEquals(4, capability.insertItem(8, offered, true).getCount(),
                "Simulated insertion into an unsupported position");
        checkEquals(4, capability.insertItem(8, offered, false).getCount(),
                "Committed insertion into an unsupported position");
        checkEquals(0, occupied(bars.getItems()),
                "An unsupported position stored something");

        // Position 0 is in the bottom layer of a column standing on the world, so it is grounded
        // outright and takes exactly the one bar a position holds.
        checkEquals(3, capability.insertItem(0, offered, true).getCount(),
                "Simulated insertion into a grounded position");
        checkEquals(3, capability.insertItem(0, offered, false).getCount(),
                "Committed insertion into a grounded position");
        checkEquals(4, offered.getCount(), "Capability mutated caller input");
        checkEquals(1, occupied(bars.getItems()),
                "One call should store one bar");
        checkEquals(1, capability.getSlotLimit(0),
                "A position's slot limit should be what one call takes");
        helper.succeed();
    }

    /**
     * Synthetic admission fixture: directly seed a refused stick at Bar slot 1 beside an accepted
     * ingot at slot 0, then automate extraction from slot 0. The ingot is extracted and the stick
     * is conserved in storage plus nearby drops. The test does not change policy.
     *
     * <p>To check manually, enable a data pack adding minecraft:oak_planks to the
     * somestacks:ingots item tag and run /reload. Store an ordinary ingot at bottom position 0 and
     * oak planks at position 1, then disable the data pack and /reload. Automate extraction from
     * position 0; the stored plank remains conserved despite its new refusal.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void backfillKeepsABarItWouldNoLongerAccept(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        BlockPos pos = bars.getBlockPos();
        check(!BlockType.BAR_STACK.admits(new ItemStack(Items.STICK), null),
                "Test needs an item a Bar Stack refuses");
        bars.getItems().insertItem(0, new ItemStack(barItem), false);
        seedSlot(bars.getItems(), 1, new ItemStack(Items.STICK));
        IItemHandler capability = GameTestSupport.capability(bars);

        ItemStack extracted = capability.extractItem(0, 64, false);

        checkEquals(barItem, extracted.getItem(), "Automated extracted item");
        checkEquals(1, extracted.getCount(), "Automated extraction took more than one bar");
        int accounted = heldAt(helper, pos, Items.STICK) + droppedNear(helper, pos, Items.STICK);
        checkEquals(1, accounted, "Refused bar after being backfilled into the hole");
        helper.succeed();
    }

    /**
     * Internal extraction simulation: put one accepted ingot at Bar slot 0 and simulate extracting
     * up to 64. The result is one ingot and the stored bar remains unchanged.
     *
     * <p>No player gesture performs this dry run. Use slot-addressing automation or a probe that
     * simulates extraction without committing; the bar should remain visible.
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

    /**
     * Loader entry point for {@link
     * BarColumnChecks#breakingLowerBlockCollapsesDependentUpperBlock}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void breakingLowerBlockCollapsesDependentUpperBlock(GameTestHelper helper) {
        BarColumnChecks.breakingLowerBlockCollapsesDependentUpperBlock(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#breakingFullColumnConsolidatesDrops}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void breakingFullColumnConsolidatesDrops(GameTestHelper helper) {
        BarColumnChecks.breakingFullColumnConsolidatesDrops(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#breakingFullColumnDropsWithBlockDropsDisabled};
     * see that method for the scenario and manual check.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void breakingFullColumnDropsWithBlockDropsDisabled(GameTestHelper helper) {
        BarColumnChecks.breakingFullColumnDropsWithBlockDropsDisabled(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#playerCascadeDropsWithBlockDropsDisabled};
     * see that method for the scenario and manual check.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void playerCascadeDropsWithBlockDropsDisabled(GameTestHelper helper) {
        BarColumnChecks.playerCascadeDropsWithBlockDropsDisabled(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#playerCascadeConsolidatesAcrossBlocks}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void playerCascadeConsolidatesAcrossBlocks(GameTestHelper helper) {
        BarColumnChecks.playerCascadeConsolidatesAcrossBlocks(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#collapseDoesNotMergeDifferentTags}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void collapseDoesNotMergeDifferentTags(GameTestHelper helper) {
        BarColumnChecks.collapseDoesNotMergeDifferentTags(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#comparatorReservesZeroForAnEmptyColumn}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorReservesZeroForAnEmptyColumn(GameTestHelper helper) {
        BarColumnChecks.comparatorReservesZeroForAnEmptyColumn(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#comparatorReadsTheWholeColumnFromEveryBlock};
     * see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        BarColumnChecks.comparatorReadsTheWholeColumnFromEveryBlock(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#comparatorFollowsAColumnLosingABlock}; see that
     * method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorFollowsAColumnLosingABlock(GameTestHelper helper) {
        BarColumnChecks.comparatorFollowsAColumnLosingABlock(helper);
    }

    /**
     * Forge event fixture: cancel BlockEvent.BreakEvent only at the lower Bar block, seed a chain
     * of eight supported bars below one dependent upper bar, then extract the bottom support. The
     * lower block remains empty while the dependent upper block disappears.
     *
     * <p>To check manually, configure a protection mod to deny the automation actor's removal at
     * the lower position while allowing the player's extraction and upper removal. Build the same
     * one-bar-per-layer support chain and plain-extract its bottom bar. Exact native veto setup
     * requires an event listener when the protection mod cannot express those permissions.
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

        MinecraftForge.EVENT_BUS.addListener(denyLower);
        try {
            lower.extractAt(0, null);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(denyLower);
        }

        helper.assertBlockPresent(ModRegistry.BAR_STACK_BLOCK.get(), ORIGIN);
        check(lower.isEmpty(), "The kept block held on to its bars");
        check(helper.getLevel().getBlockEntity(upperPos) == null,
                "The block above a kept block was not brought down");
        helper.succeed();
    }
}
