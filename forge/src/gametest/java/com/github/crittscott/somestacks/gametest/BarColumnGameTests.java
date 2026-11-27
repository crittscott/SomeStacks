package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.firstBarItem;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.seedSlot;

import com.github.crittscott.somestacks.ForgeRegistry;
import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.block.BarStackBE;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.function.Consumer;

/**
 * Bar support and collapse in a live level: item validity, grounding at the bottom of a column,
 * footprint overlap deciding support across the alternating layer orientations, and the difference
 * between player extraction, which drops what it unsupports, and automated extraction, which
 * backfills from the top instead.
 */
@GameTestHolder(SomeStacksCommon.MODID)
@PrefixGameTestTemplate(false)
public final class BarColumnGameTests {
    private BarColumnGameTests() {}

    /**
     * Loader entry point for {@link BarColumnChecks#validityAndBottomGroundingAreEnforced}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void validityAndBottomGroundingAreEnforced(GameTestHelper helper) {
        BarColumnChecks.validityAndBottomGroundingAreEnforced(helper);
    }

    /** See {@link BarColumnChecks#barAdmissionFollowsTheIngotTag}. */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void barAdmissionFollowsTheIngotTag(GameTestHelper helper) {
        BarColumnChecks.barAdmissionFollowsTheIngotTag(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#upperBarRequiresOverlappingSupport}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void upperBarRequiresOverlappingSupport(GameTestHelper helper) {
        BarColumnChecks.upperBarRequiresOverlappingSupport(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#playerExtractionDropsUnsupportedBars}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void playerExtractionDropsUnsupportedBars(GameTestHelper helper) {
        BarColumnChecks.playerExtractionDropsUnsupportedBars(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#supportedNeighborSurvivesPlayerExtraction}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void supportedNeighborSurvivesPlayerExtraction(GameTestHelper helper) {
        BarColumnChecks.supportedNeighborSurvivesPlayerExtraction(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#differentlyOrientedSeamUsesFootprintOverlap};
     * see that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void differentlyOrientedSeamUsesFootprintOverlap(GameTestHelper helper) {
        BarColumnChecks.differentlyOrientedSeamUsesFootprintOverlap(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#automationBackfillsWithoutDropping}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void automationBackfillsWithoutDropping(GameTestHelper helper) {
        BarColumnChecks.automationBackfillsWithoutDropping(
                helper, ForgeGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * BarColumnChecks#capabilityInsertionAnswersForThePositionItIsGiven}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void capabilityInsertionAnswersForThePositionItIsGiven(GameTestHelper helper) {
        BarColumnChecks.capabilityInsertionAnswersForThePositionItIsGiven(
                helper, ForgeGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#backfillKeepsABarItWouldNoLongerAccept}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void backfillKeepsABarItWouldNoLongerAccept(GameTestHelper helper) {
        BarColumnChecks.backfillKeepsABarItWouldNoLongerAccept(
                helper, ForgeGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#capabilitySimulationDoesNotMutateExtraction};
     * see that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void capabilitySimulationDoesNotMutateExtraction(GameTestHelper helper) {
        BarColumnChecks.capabilitySimulationDoesNotMutateExtraction(
                helper, ForgeGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * BarColumnChecks#breakingLowerBlockCollapsesDependentUpperBlock}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void breakingLowerBlockCollapsesDependentUpperBlock(GameTestHelper helper) {
        BarColumnChecks.breakingLowerBlockCollapsesDependentUpperBlock(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#breakingFullColumnConsolidatesDrops}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void breakingFullColumnConsolidatesDrops(GameTestHelper helper) {
        BarColumnChecks.breakingFullColumnConsolidatesDrops(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#breakingFullColumnDropsWithBlockDropsDisabled};
     * see that method for the scenario and manual check.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void breakingFullColumnDropsWithBlockDropsDisabled(GameTestHelper helper) {
        BarColumnChecks.breakingFullColumnDropsWithBlockDropsDisabled(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#playerCascadeDropsWithBlockDropsDisabled}; see
     * that method for the scenario and manual check.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void playerCascadeDropsWithBlockDropsDisabled(GameTestHelper helper) {
        BarColumnChecks.playerCascadeDropsWithBlockDropsDisabled(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#playerCascadeConsolidatesAcrossBlocks}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void playerCascadeConsolidatesAcrossBlocks(GameTestHelper helper) {
        BarColumnChecks.playerCascadeConsolidatesAcrossBlocks(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#collapseDoesNotMergeDifferentTags}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void collapseDoesNotMergeDifferentTags(GameTestHelper helper) {
        BarColumnChecks.collapseDoesNotMergeDifferentTags(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#comparatorReadsTheWholeColumnFromEveryBlock};
     * see that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        BarColumnChecks.comparatorReadsTheWholeColumnFromEveryBlock(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#comparatorFollowsAColumnLosingABlock}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
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
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
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
        seedSlot(
                upper.getItems(),
                BarColumnChecks.firstSeamSupportedBy(slot - 56),
                new ItemStack(barItem));

        Consumer<BlockEvent.BreakEvent> denyLower =
                event -> {
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

        helper.assertBlockPresent(ForgeRegistry.BAR_STACK_BLOCK.get(), ORIGIN);
        check(lower.isEmpty(), "The kept block held on to its bars");
        check(
                helper.getLevel().getBlockEntity(upperPos) == null,
                "The block above a kept block was not brought down");
        helper.succeed();
    }
}
