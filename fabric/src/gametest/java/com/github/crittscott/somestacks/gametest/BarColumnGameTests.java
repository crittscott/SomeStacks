package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.BarStackBE;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
public final class BarColumnGameTests implements FabricGameTest {
    /**
     * Loader entry point for {@link BarColumnChecks#validityAndBottomGroundingAreEnforced}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void validityAndBottomGroundingAreEnforced(GameTestHelper helper) {
        BarColumnChecks.validityAndBottomGroundingAreEnforced(helper);
    }

    /** See {@link BarColumnChecks#barAdmissionFollowsTheIngotTag}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void barAdmissionFollowsTheIngotTag(GameTestHelper helper) {
        BarColumnChecks.barAdmissionFollowsTheIngotTag(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#upperBarRequiresOverlappingSupport}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void upperBarRequiresOverlappingSupport(GameTestHelper helper) {
        BarColumnChecks.upperBarRequiresOverlappingSupport(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#playerExtractionDropsUnsupportedBars}; see that
     * method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerExtractionDropsUnsupportedBars(GameTestHelper helper) {
        BarColumnChecks.playerExtractionDropsUnsupportedBars(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#supportedNeighborSurvivesPlayerExtraction}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void supportedNeighborSurvivesPlayerExtraction(GameTestHelper helper) {
        BarColumnChecks.supportedNeighborSurvivesPlayerExtraction(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#differentlyOrientedSeamUsesFootprintOverlap};
     * see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void differentlyOrientedSeamUsesFootprintOverlap(GameTestHelper helper) {
        BarColumnChecks.differentlyOrientedSeamUsesFootprintOverlap(helper);
    }

    /**
     * To reproduce: allow the chosen ingot for Bar, then use a machine that walks positions
     * bottom-up to insert ten identical ingots. Extract one from bottom position 0 through
     * automation. Nine remain, position 0 is backfilled from the topmost bar, and no ingot item
     * entity is dropped.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void automationBackfillsWithoutDropping(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        SlottedStorage<ItemVariant> capability = FabricGameTestSupport.capability(bars);
        ItemStack offered = new ItemStack(barItem, 10);

        // A walk of the advertised positions fills the block from the bottom, because each bar
        // placed supports the layer above it before the walk reaches it.
        ItemStack remainder = FabricGameTestSupport.insertWalkingSlots(capability, offered, false);
        check(remainder.isEmpty(), "A walk of the positions left a remainder");
        checkEquals(10, count(bars.getItems(), barItem),
                "Inserted bar count");

        ItemStack extracted = FabricGameTestSupport.extractAt(capability, 0, 64, false);

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
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void capabilityInsertionAnswersForThePositionItIsGiven(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        SlottedStorage<ItemVariant> capability = FabricGameTestSupport.capability(bars);
        ItemStack offered = new ItemStack(barItem, 4);

        // Position 8 is the first of layer 1, with an empty layer 0 beneath it, so it is refused
        // rather than redirected to a position that would take the bar.
        checkEquals(4, FabricGameTestSupport.insertAt(capability, 8, offered, true).getCount(),
                "Simulated insertion into an unsupported position");
        checkEquals(4, FabricGameTestSupport.insertAt(capability, 8, offered, false).getCount(),
                "Committed insertion into an unsupported position");
        checkEquals(0, occupied(bars.getItems()),
                "An unsupported position stored something");

        // Position 0 is in the bottom layer of a column standing on the world, so it is grounded
        // outright and takes exactly the one bar a position holds.
        checkEquals(3, FabricGameTestSupport.insertAt(capability, 0, offered, true).getCount(),
                "Simulated insertion into a grounded position");
        checkEquals(3, FabricGameTestSupport.insertAt(capability, 0, offered, false).getCount(),
                "Committed insertion into a grounded position");
        checkEquals(4, offered.getCount(), "Capability mutated caller input");
        checkEquals(1, occupied(bars.getItems()),
                "One call should store one bar");
        checkEquals(1, FabricGameTestSupport.slotLimit(capability, 0),
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
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void backfillKeepsABarItWouldNoLongerAccept(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        BlockPos pos = bars.getBlockPos();
        check(!BarStackBE.isValidBarItem(new ItemStack(Items.STICK)),
                "Test needs an item a Bar Stack refuses");
        bars.getItems().insertItem(0, new ItemStack(barItem), false);
        seedSlot(bars.getItems(), 1, new ItemStack(Items.STICK));
        SlottedStorage<ItemVariant> capability = FabricGameTestSupport.capability(bars);

        ItemStack extracted = FabricGameTestSupport.extractAt(capability, 0, 64, false);

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
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void capabilitySimulationDoesNotMutateExtraction(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        bars.depositAt(0, new ItemStack(barItem));
        SlottedStorage<ItemVariant> capability = FabricGameTestSupport.capability(bars);

        ItemStack simulated = FabricGameTestSupport.extractAt(capability, 0, 64, true);

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
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void breakingLowerBlockCollapsesDependentUpperBlock(GameTestHelper helper) {
        BarColumnChecks.breakingLowerBlockCollapsesDependentUpperBlock(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#breakingFullColumnConsolidatesDrops}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void breakingFullColumnConsolidatesDrops(GameTestHelper helper) {
        BarColumnChecks.breakingFullColumnConsolidatesDrops(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#breakingFullColumnDropsWithBlockDropsDisabled};
     * see that method for the scenario and manual check.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void breakingFullColumnDropsWithBlockDropsDisabled(GameTestHelper helper) {
        BarColumnChecks.breakingFullColumnDropsWithBlockDropsDisabled(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#playerCascadeDropsWithBlockDropsDisabled};
     * see that method for the scenario and manual check.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerCascadeDropsWithBlockDropsDisabled(GameTestHelper helper) {
        BarColumnChecks.playerCascadeDropsWithBlockDropsDisabled(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#playerCascadeConsolidatesAcrossBlocks}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerCascadeConsolidatesAcrossBlocks(GameTestHelper helper) {
        BarColumnChecks.playerCascadeConsolidatesAcrossBlocks(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#collapseDoesNotMergeDifferentTags}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void collapseDoesNotMergeDifferentTags(GameTestHelper helper) {
        BarColumnChecks.collapseDoesNotMergeDifferentTags(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#comparatorReservesZeroForAnEmptyColumn}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorReservesZeroForAnEmptyColumn(GameTestHelper helper) {
        BarColumnChecks.comparatorReservesZeroForAnEmptyColumn(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#comparatorReadsTheWholeColumnFromEveryBlock};
     * see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        BarColumnChecks.comparatorReadsTheWholeColumnFromEveryBlock(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#comparatorFollowsAColumnLosingABlock}; see that
     * method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorFollowsAColumnLosingABlock(GameTestHelper helper) {
        BarColumnChecks.comparatorFollowsAColumnLosingABlock(helper);
    }
}
