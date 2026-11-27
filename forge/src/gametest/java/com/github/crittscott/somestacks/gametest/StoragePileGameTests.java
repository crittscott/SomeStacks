package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.block.StorageStackBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Storage pile behavior in a live level: deposits filling partial stacks before empty ones, growth
 * when a pile fills, capability insertion addressing the specified slot, obstruction refusing
 * growth, and settling consolidating, packing, and sorting.
 */
@GameTestHolder(SomeStacksCommon.MODID)
@PrefixGameTestTemplate(false)
public final class StoragePileGameTests {
    private StoragePileGameTests() {}

    /**
     * Loader entry point for {@link
     * StoragePileChecks#depositFillsCompatiblePartialBeforeEmptySlot}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void depositFillsCompatiblePartialBeforeEmptySlot(GameTestHelper helper) {
        StoragePileChecks.depositFillsCompatiblePartialBeforeEmptySlot(helper);
    }

    /**
     * Loader entry point for {@link StoragePileChecks#fullPileGrowsByOneBlock}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void fullPileGrowsByOneBlock(GameTestHelper helper) {
        StoragePileChecks.fullPileGrowsByOneBlock(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /** See {@link StoragePileChecks#growthRequiresBuildPermission}. */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void growthRequiresBuildPermission(GameTestHelper helper) {
        StoragePileChecks.growthRequiresBuildPermission(
                helper, FakePlayerFactory.getMinecraft(helper.getLevel()));
    }

    /** See {@link StoragePileChecks#placementChoosesFacingBeforeJoining}. */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void placementChoosesFacingBeforeJoining(GameTestHelper helper) {
        StoragePileChecks.placementChoosesFacingBeforeJoining(helper);
    }

    /**
     * The scenario of {@link StoragePileChecks#grownBlockTakesTheBaseFacing}, which see for the
     * manual check, plus a Forge-only assertion: the growth posts {@code
     * BlockEvent.EntityPlaceEvent} exactly once at the new block, and a listener reading the world
     * during that event already sees the base's facing, not an intermediate one. A protection or
     * logging mod listening to placements therefore records the block as it ends up.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void grownBlockTakesTheBaseFacing(GameTestHelper helper) {
        BlockPos target = helper.absolutePos(ORIGIN.above(2));
        List<Direction> seen = new ArrayList<>();
        Consumer<BlockEvent.EntityPlaceEvent> observe =
                event -> {
                    if (event.getLevel() == helper.getLevel() && event.getPos().equals(target)) {
                        seen.add(
                                helper.getLevel()
                                        .getBlockState(target)
                                        .getValue(StorageStackBlock.HORIZONTAL_FACING));
                    }
                };
        MinecraftForge.EVENT_BUS.addListener(observe);
        try {
            StoragePileChecks.growThreeBlockPileFromRotatedBase(
                    helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(observe);
        }
        checkEquals(List.of(Direction.WEST), seen, "Facing each growth placement event saw");
        helper.succeed();
    }

    /**
     * Loader entry point for {@link
     * StoragePileChecks#capabilityInsertionAnswersForTheSlotItIsGiven}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void capabilityInsertionAnswersForTheSlotItIsGiven(GameTestHelper helper) {
        StoragePileChecks.capabilityInsertionAnswersForTheSlotItIsGiven(
                helper, ForgeGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * StoragePileChecks#obstructionPreventsGrowthAndSimulationReportsNoRoom}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void obstructionPreventsGrowthAndSimulationReportsNoRoom(GameTestHelper helper) {
        StoragePileChecks.obstructionPreventsGrowthAndSimulationReportsNoRoom(
                helper, ForgeGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link StoragePileChecks#settleConsolidatesExactIdentityAndPacksDown};
     * see that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void settleConsolidatesExactIdentityAndPacksDown(GameTestHelper helper) {
        StoragePileChecks.settleConsolidatesExactIdentityAndPacksDown(helper);
    }

    /** Loader entry point for {@link StoragePileChecks#settleMergesOnlyIdenticalNames}. */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void settleMergesOnlyIdenticalNames(GameTestHelper helper) {
        StoragePileChecks.settleMergesOnlyIdenticalNames(helper);
    }

    /**
     * Loader entry point for {@link StoragePileChecks#extractionSettlesOnScheduledBlockTick}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE, timeoutTicks = 20)
    public static void extractionSettlesOnScheduledBlockTick(GameTestHelper helper) {
        StoragePileChecks.extractionSettlesOnScheduledBlockTick(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link
     * StoragePileChecks#emptyTemporaryPileDisappearsButPermanentPileRemains}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void emptyTemporaryPileDisappearsButPermanentPileRemains(GameTestHelper helper) {
        StoragePileChecks.emptyTemporaryPileDisappearsButPermanentPileRemains(helper);
    }

    /**
     * Loader entry point for {@link
     * StoragePileChecks#capabilityFromEveryBlockAddressesTheSamePile}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void capabilityFromEveryBlockAddressesTheSamePile(GameTestHelper helper) {
        StoragePileChecks.capabilityFromEveryBlockAddressesTheSamePile(
                helper, ForgeGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link StoragePileChecks#comparatorReadsTheWholePileFromEveryBlock};
     * see that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void comparatorReadsTheWholePileFromEveryBlock(GameTestHelper helper) {
        StoragePileChecks.comparatorReadsTheWholePileFromEveryBlock(helper);
    }

    /**
     * Loader entry point for {@link
     * StoragePileChecks#ordinaryPlacementBelowPermanentPileRepairsDerivedState}; see that method
     * for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE, timeoutTicks = 20)
    public static void ordinaryPlacementBelowPermanentPileRepairsDerivedState(
            GameTestHelper helper) {
        StoragePileChecks.ordinaryPlacementBelowPermanentPileRepairsDerivedState(helper);
    }

    /**
     * See {@link StoragePileChecks#middleRemovalSettlesAndPublishesBothSurvivingPiles} for the
     * split scenario and manual procedure.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void middleRemovalSettlesAndPublishesBothSurvivingPiles(GameTestHelper helper) {
        StoragePileChecks.middleRemovalSettlesAndPublishesBothSurvivingPiles(helper);
    }
}
