package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.WorldEdits;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The rules that keep a gesture from writing where it should not: build height, entity obstruction,
 * destination consultation, actor-preserving cleanup, and growth checks in simulation and commit.
 */
public final class ProtectionGameTests implements FabricGameTest {
    static final Map<TestTarget, PlacementProbe> PLACEMENT_PROBES = new ConcurrentHashMap<>();
    private static final Map<TestTarget, RemovalProbe> REMOVAL_PROBES = new ConcurrentHashMap<>();
    private static final Map<TestTarget, InteractionResult> ADJACENT_USE_RESULTS =
            new ConcurrentHashMap<>();
    private static final Set<TestTarget> BLOCK_USE_DENIALS = ConcurrentHashMap.newKeySet();

    static {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (BLOCK_USE_DENIALS.contains(new TestTarget(level, hit.getBlockPos()))) {
                return InteractionResult.FAIL;
            }
            PlacementProbe probe = PLACEMENT_PROBES.get(new TestTarget(level, hit.getBlockPos()));
            if (probe != null) probe.syntheticClick().set(true);
            InteractionResult result =
                    ADJACENT_USE_RESULTS.get(new TestTarget(level, hit.getBlockPos()));
            if (result != null) {
                check(
                        WorldEdits.isConsultingAdjacent(),
                        "Destination callback lacks its gesture guard");
                return result;
            }
            return InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            RemovalProbe probe = REMOVAL_PROBES.get(new TestTarget(level, pos));
            if (probe != null) {
                probe.actor().accept(player);
                return probe.allowed().test(player);
            }
            return true;
        });
    }

    static boolean consult(Level level, BlockPos pos, Player player) {
        PlacementProbe probe = PLACEMENT_PROBES.get(new TestTarget(level, pos));
        if (probe == null) return true;
        probe.invoked().set(true);
        probe.sawAir().set(level.getBlockState(pos).isAir());
        probe.actor().set(player);
        return probe.result() == InteractionResult.PASS;
    }

    /**
     * To reproduce in-game: deny [SomeStacks] placement in a Common Protection API claim, then
     * insert into a full Storage pile through automation. Growth accepts nothing and keeps the
     * input.
     */
    public void automationGrowthConsultsProtectionWithoutSyntheticClick(GameTestHelper helper) {
        StorageStackBE base = GameTestScaffold.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            base.getItems().insertItem(slot, new ItemStack(Items.STONE, 64), false);
        }
        BlockPos target = base.getBlockPos().above();
        TestTarget key = new TestTarget(helper.getLevel(), target);
        PlacementProbe probe =
                new PlacementProbe(
                        InteractionResult.FAIL,
                        new AtomicBoolean(),
                        new AtomicBoolean(),
                        new AtomicReference<>(),
                        new AtomicBoolean());
        PLACEMENT_PROBES.put(key, probe);
        try {
            ItemStack offered = new ItemStack(Items.STONE, 4);
            checkEquals(
                    4,
                    FabricGameTestSupport.insertAt(
                                    FabricGameTestSupport.storage(base),
                                    StorageStackBE.SLOTS,
                                    offered,
                                    false)
                            .getCount(),
                    "Denied growth remainder");
            check(probe.invoked().get(), "Automation skipped the placement query");
            check(
                    probe.actor().get() == WorldEdits.automationActor(helper.getLevel()),
                    "Protection query lost the automation actor");
            check(probe.sawAir().get(), "Automation queried protection after placement");
            check(!probe.syntheticClick().get(), "Automation fabricated a block-use callback");
            helper.assertBlockNotPresent(CommonRegistry.storageStackBlock(), ORIGIN.above());
        } finally {
            PLACEMENT_PROBES.remove(key);
        }
        helper.succeed();
    }

    /** See {@link ProtectionChecks#automationUsesSharedIdentity}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void automationUsesSharedIdentity(GameTestHelper helper) {
        ProtectionChecks.automationUsesSharedIdentity(helper);
    }

    /**
     * No in-game reproduction applies: this verifies that Fabric backs the shared SomeStacks
     * automation identity with its FakePlayer implementation.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void automationUsesFabricFakePlayer(GameTestHelper helper) {
        check(
                WorldEdits.automationActor(helper.getLevel()) instanceof FakePlayer,
                "Fabric automation actor was not a Fabric API FakePlayer");
        helper.succeed();
    }

    /**
     * To reproduce in-game: stand outside a claim and click toward a denied destination inside it.
     * Fabric consults that destination while it is still air and places nothing there.
     */
    public void destinationConsultationRunsBeforePlacement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayer.get(level);
        BlockPos target = helper.absolutePos(ORIGIN);
        TestTarget key = new TestTarget(level, target);
        PlacementProbe probe =
                new PlacementProbe(
                        InteractionResult.FAIL,
                        new AtomicBoolean(),
                        new AtomicBoolean(),
                        new AtomicReference<>(),
                        new AtomicBoolean());
        PLACEMENT_PROBES.put(key, probe);
        try {
            check(
                    !WorldEdits.placeChecked(
                            player,
                            level,
                            target,
                            Blocks.STONE.defaultBlockState(),
                            Direction.DOWN),
                    "A handled destination consultation permitted placement");
        } finally {
            PLACEMENT_PROBES.remove(key);
        }

        check(probe.invoked().get(), "Fabric did not consult the placement destination");
        check(probe.sawAir().get(), "Fabric consulted protection only after placement");
        check(!probe.syntheticClick().get(), "Placement fabricated a block-use callback");
        helper.assertBlockNotPresent(Blocks.STONE, ORIGIN);
        helper.succeed();
    }

    /** See {@link ProtectionChecks#playerExtractionUsesPlayerForCleanup}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerExtractionUsesPlayerForCleanup(GameTestHelper helper) {
        ProtectionChecks.playerExtractionUsesPlayerForCleanup(
                helper,
                FabricGameTestSupport.playerFactory(helper),
                ProtectionGameTests::observeRemoval);
    }

    /** See {@link ProtectionChecks#playerStorageSettlementUsesPlayerForCleanup}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE, timeoutTicks = 20)
    public void playerStorageSettlementUsesPlayerForCleanup(GameTestHelper helper) {
        ProtectionChecks.playerStorageSettlementUsesPlayerForCleanup(
                helper,
                FabricGameTestSupport.playerFactory(helper),
                ProtectionGameTests::observeRemoval);
    }

    /** See {@link ProtectionChecks#settleKeepsAnEmptyTopBlockWhoseRemovalIsRefused}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void settleKeepsAnEmptyTopBlockWhoseRemovalIsRefused(GameTestHelper helper) {
        ProtectionChecks.settleKeepsAnEmptyTopBlockWhoseRemovalIsRefused(
                helper, ProtectionGameTests::observeRemoval);
    }

    /**
     * A Common Protection API denial vetoes an adjacent-stack consultation. To reproduce in-game:
     * deny item use at a claimed destination, then hold the modifier and right-click the
     * neighboring block toward it. No item is deposited and no stack is placed in the denied
     * position.
     */
    public void adjacentConsultationHonorsProtectionQuery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayer.get(level);
        BlockPos target = helper.absolutePos(ORIGIN);
        TestTarget key = new TestTarget(level, target);
        PlacementProbe probe =
                new PlacementProbe(
                        InteractionResult.FAIL,
                        new AtomicBoolean(),
                        new AtomicBoolean(),
                        new AtomicReference<>(),
                        new AtomicBoolean());
        PLACEMENT_PROBES.put(key, probe);
        try {
            check(
                    !WorldEdits.mayUseAdjacent(player, target),
                    "Protection query did not veto the adjacent stack consultation");
        } finally {
            PLACEMENT_PROBES.remove(key);
        }
        check(probe.invoked().get(), "Fabric did not consult the adjacent destination");
        check(!probe.syntheticClick().get(), "Adjacent query fabricated a block-use callback");
        helper.succeed();
    }

    /** See {@link ProtectionChecks#refusedRemovalStillLetsTheBarsAboveComeDown}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void refusedRemovalStillLetsTheBarsAboveComeDown(GameTestHelper helper) {
        ProtectionChecks.refusedRemovalStillLetsTheBarsAboveComeDown(
                helper,
                FabricGameTestSupport.playerFactory(helper),
                ProtectionGameTests::observeRemoval);
    }

    /** See {@link ProtectionChecks#checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(GameTestHelper helper) {
        ProtectionChecks.checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ProtectionChecks#checkedPlacementRejectsAnObstructingEntity}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void checkedPlacementRejectsAnObstructingEntity(GameTestHelper helper) {
        ProtectionChecks.checkedPlacementRejectsAnObstructingEntity(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ProtectionChecks#creativeDepositFillsTheStackWithoutSpendingTheHand}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void creativeDepositFillsTheStackWithoutSpendingTheHand(GameTestHelper helper) {
        ProtectionChecks.creativeDepositFillsTheStackWithoutSpendingTheHand(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ProtectionChecks#placementIntoWaterKeepsTheWater}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void placementIntoWaterKeepsTheWater(GameTestHelper helper) {
        ProtectionChecks.placementIntoWaterKeepsTheWater(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ProtectionChecks#adjacentDepositsFillGroundedCells}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void adjacentDepositsFillGroundedCells(GameTestHelper helper) {
        ProtectionChecks.adjacentDepositsFillGroundedCells(
                helper, FabricGameTestSupport.playerFactory(helper));
        helper.succeed();
    }

    /**
     * Destination block-use callbacks can refuse neighboring deposits with or without Common
     * Protection API. To reproduce in-game: deny block use at a stack with a protection mod, then
     * modifier-click its support through an empty cell. No item moves.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void adjacentDepositsHonorBlockUseCallbacks(GameTestHelper helper) {
        TestTarget key = new TestTarget(helper.getLevel(), helper.absolutePos(ORIGIN));
        try {
            for (InteractionResult result :
                    List.of(InteractionResult.FAIL, InteractionResult.SUCCESS)) {
                ADJACENT_USE_RESULTS.put(key, result);
                helper.getLevel().removeBlock(key.pos(), false);
                ProtectionChecks.checkAdjacentAndDirectDeposits(
                        helper, FabricGameTestSupport.playerFactory(helper), false);
                check(!WorldEdits.isConsultingAdjacent(), "Destination consultation guard leaked");
            }
        } finally {
            ADJACENT_USE_RESULTS.remove(key);
        }
        helper.succeed();
    }

    record TestTarget(Level level, BlockPos pos) {}

    record PlacementProbe(
            InteractionResult result,
            AtomicBoolean invoked,
            AtomicBoolean sawAir,
            AtomicReference<Player> actor,
            AtomicBoolean syntheticClick) {}

    private record RemovalProbe(Predicate<Player> allowed, Consumer<Player> actor) {}

    /** See {@link ProtectionChecks#playerBarExtractionUsesPlayerForCleanup}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerBarExtractionUsesPlayerForCleanup(GameTestHelper helper) {
        ProtectionChecks.playerBarExtractionUsesPlayerForCleanup(
                helper,
                FabricGameTestSupport.playerFactory(helper),
                ProtectionGameTests::observeRemoval);
    }

    /** See {@link ProtectionChecks#mixedStorageSettlementUsesAutomationForCleanup}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void mixedStorageSettlementUsesAutomationForCleanup(GameTestHelper helper) {
        ProtectionChecks.mixedStorageSettlementUsesAutomationForCleanup(
                helper,
                FabricGameTestSupport.playerFactory(helper),
                FabricGameTestSupport::automation,
                ProtectionGameTests::observeRemoval);
    }

    /** See {@link ProtectionChecks#breakingAStackChargesItsCleanupToTheBreaker}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void breakingAStackChargesItsCleanupToTheBreaker(GameTestHelper helper) {
        ProtectionChecks.breakingAStackChargesItsCleanupToTheBreaker(
                helper,
                FabricGameTestSupport.playerFactory(helper),
                ProtectionGameTests::observeRemoval);
    }

    /**
     * Denies block use at {@code pos} by failing the default-phase {@code UseBlockCallback}, which
     * runs before the mod's gesture phase, as a claim mod does.
     */
    private static Runnable denyBlockUse(ServerLevel level, BlockPos pos) {
        TestTarget key = new TestTarget(level, pos);
        BLOCK_USE_DENIALS.add(key);
        return () -> BLOCK_USE_DENIALS.remove(key);
    }

    private static Runnable observeRemoval(
            ServerLevel level, BlockPos pos, Consumer<Player> observed, Predicate<Player> allowed) {
        TestTarget key = new TestTarget(level, pos);
        REMOVAL_PROBES.put(key, new RemovalProbe(allowed, observed));
        return () -> REMOVAL_PROBES.remove(key);
    }

    /** See {@link ProtectionChecks#storageGrowthAnswersToProtectionInSimulationAndCommit}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void storageGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        ProtectionChecks.storageGrowthAnswersToProtectionInSimulationAndCommit(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link ProtectionChecks#singlesGrowthAnswersToProtectionInSimulationAndCommit}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void singlesGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        ProtectionChecks.singlesGrowthAnswersToProtectionInSimulationAndCommit(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link ProtectionChecks#barGrowthAnswersToProtectionInSimulationAndCommit}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void barGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        ProtectionChecks.barGrowthAnswersToProtectionInSimulationAndCommit(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * See {@link ProtectionChecks#storageGrowthRejectsAnObstructingEntityInSimulationAndCommit}.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void storageGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        ProtectionChecks.storageGrowthRejectsAnObstructingEntityInSimulationAndCommit(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * See {@link ProtectionChecks#singlesGrowthRejectsAnObstructingEntityInSimulationAndCommit}.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void singlesGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        ProtectionChecks.singlesGrowthRejectsAnObstructingEntityInSimulationAndCommit(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link ProtectionChecks#barGrowthRejectsAnObstructingEntityInSimulationAndCommit}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void barGrowthRejectsAnObstructingEntityInSimulationAndCommit(GameTestHelper helper) {
        ProtectionChecks.barGrowthRejectsAnObstructingEntityInSimulationAndCommit(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link InteractionChecks#deniedCleanupEmitsOnlyBlockChange}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void deniedCleanupEmitsOnlyBlockChange(GameTestHelper helper) {
        InteractionChecks.deniedCleanupEmitsOnlyBlockChange(
                helper,
                FabricGameTestSupport.playerFactory(helper),
                ProtectionGameTests::observeRemoval);
    }

    /**
     * To reproduce in-game: hold the modifier and click a neighboring face toward a Singles Stack
     * allowed by Common Protection API and destination block-use callbacks. One item is deposited.
     */
    public void allowedAdjacentDepositUsesProtectionQuery(GameTestHelper helper) {
        TestTarget key = new TestTarget(helper.getLevel(), helper.absolutePos(ORIGIN));
        PlacementProbe probe =
                new PlacementProbe(
                        InteractionResult.PASS,
                        new AtomicBoolean(),
                        new AtomicBoolean(),
                        new AtomicReference<>(),
                        new AtomicBoolean());
        PLACEMENT_PROBES.put(key, probe);
        try {
            ProtectionChecks.checkAdjacentAndDirectDeposits(
                    helper, FabricGameTestSupport.playerFactory(helper), true);
            check(probe.invoked().get(), "Neighbor deposit skipped protection");
            check(
                    probe.syntheticClick().get(),
                    "Neighbor deposit skipped destination block-use callbacks");
        } finally {
            PLACEMENT_PROBES.remove(key);
        }
        helper.succeed();
    }

    /** See {@link InteractionChecks#blockUseDenialFreezesStackGestures}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void blockUseDenialFreezesStackGestures(GameTestHelper helper) {
        InteractionChecks.blockUseDenialFreezesStackGestures(
                helper,
                FabricGameTestSupport.playerFactory(helper),
                ProtectionGameTests::denyBlockUse);
    }
}
