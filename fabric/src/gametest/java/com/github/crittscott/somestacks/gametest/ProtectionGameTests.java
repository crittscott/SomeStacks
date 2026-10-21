package com.github.crittscott.somestacks.gametest;

import net.minecraft.world.phys.shapes.Shapes;
import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.WorldEdits;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/**
 * The rules that keep a gesture from writing where it should not: build height, entity obstruction,
 * destination consultation, actor-preserving cleanup, and growth checks in simulation and commit.
 */
public final class ProtectionGameTests {
    static final Map<TestTarget, PlacementProbe> PLACEMENT_PROBES =
            new ConcurrentHashMap<>();
    private static final Map<TestTarget, RemovalProbe> REMOVAL_PROBES =
            new ConcurrentHashMap<>();

    static {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            PlacementProbe probe = PLACEMENT_PROBES.get(new TestTarget(level, hit.getBlockPos()));
            if (probe != null) probe.syntheticClick().set(true);
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
     * insert into a full Storage pile through automation. Growth accepts nothing and keeps the input.
     */
    public void automationGrowthConsultsProtectionWithoutSyntheticClick(GameTestHelper helper) {
        StorageStackBE base = GameTestScaffold.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            base.getItems().insertItem(slot, new ItemStack(Items.STONE, 64));
        }
        BlockPos target = base.getBlockPos().above();
        TestTarget key = new TestTarget(helper.getLevel(), target);
        PlacementProbe probe = new PlacementProbe(InteractionResult.FAIL,
                new AtomicBoolean(), new AtomicBoolean(), new AtomicReference<>(), new AtomicBoolean());
        PLACEMENT_PROBES.put(key, probe);
        try {
            ItemStack offered = new ItemStack(Items.STONE, 4);
            checkEquals(4, FabricGameTestSupport.insertAt(FabricGameTestSupport.storage(base),
                    StorageStackBE.SLOTS, offered, false).getCount(), "Denied growth remainder");
            check(probe.invoked().get(), "Automation skipped the placement query");
            check(probe.actor().get() == WorldEdits.automationActor(helper.getLevel()),
                    "Protection query lost the automation actor");
            check(probe.sawAir().get(), "Automation queried protection after placement");
            check(!probe.syntheticClick().get(), "Automation fabricated a block-use callback");
            helper.assertBlockNotPresent(CommonRegistry.storageStackBlock(), ORIGIN.above());
        } finally {
            PLACEMENT_PROBES.remove(key);
        }
        helper.succeed();
    }

    /** See {@link ProtectionChecks#adventureExtractionKeepsEmptyBlock}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void adventureExtractionKeepsEmptyBlock(GameTestHelper helper) {
        ProtectionChecks.adventureExtractionKeepsEmptyBlock(helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ProtectionChecks#placementReplacesClickedVegetation}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void placementReplacesClickedVegetation(GameTestHelper helper) {
        ProtectionChecks.placementReplacesClickedVegetation(helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ProtectionChecks#automationUsesSharedIdentity}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void automationUsesSharedIdentity(GameTestHelper helper) {
        ProtectionChecks.automationUsesSharedIdentity(helper);
    }

    /**
     * No in-game reproduction applies: this verifies that Fabric backs the shared SomeStacks
     * automation identity with its FakePlayer implementation.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void automationUsesFabricFakePlayer(GameTestHelper helper) {
        check(WorldEdits.automationActor(helper.getLevel()) instanceof FakePlayer,
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
        PlacementProbe probe = new PlacementProbe(
                InteractionResult.FAIL, new AtomicBoolean(), new AtomicBoolean(),
                new AtomicReference<>(), new AtomicBoolean());
        PLACEMENT_PROBES.put(key, probe);
        try {
            check(!WorldEdits.placeChecked(
                            player, level, target, Blocks.STONE.defaultBlockState(), Direction.DOWN, net.minecraft.world.phys.shapes.Shapes.block()),
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
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void playerExtractionUsesPlayerForCleanup(GameTestHelper helper) {
        ProtectionChecks.playerExtractionUsesPlayerForCleanup(helper, FabricGameTestSupport.playerFactory(helper), ProtectionGameTests::observeRemoval);
    }

    /** See {@link ProtectionChecks#playerStorageSettlementUsesPlayerForCleanup}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void playerStorageSettlementUsesPlayerForCleanup(GameTestHelper helper) {
        ProtectionChecks.playerStorageSettlementUsesPlayerForCleanup(helper, FabricGameTestSupport.playerFactory(helper), ProtectionGameTests::observeRemoval);
    }

    /** See {@link ProtectionChecks#settleKeepsAnEmptyTopBlockWhoseRemovalIsRefused}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void settleKeepsAnEmptyTopBlockWhoseRemovalIsRefused(GameTestHelper helper) {
        ProtectionChecks.settleKeepsAnEmptyTopBlockWhoseRemovalIsRefused(helper, ProtectionGameTests::observeRemoval);
    }

    /**
     * A Common Protection API denial vetoes an adjacent-stack consultation. To reproduce in-game: deny
     * item use at a claimed destination, then hold the modifier and right-click the neighboring
     * block toward it. No item is deposited and no stack is placed in the denied position.
     */
    public void adjacentConsultationHonorsProtectionQuery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayer.get(level);
        BlockPos target = helper.absolutePos(ORIGIN);
        TestTarget key = new TestTarget(level, target);
        PlacementProbe probe = new PlacementProbe(
                InteractionResult.FAIL, new AtomicBoolean(), new AtomicBoolean(),
                new AtomicReference<>(), new AtomicBoolean());
        PLACEMENT_PROBES.put(key, probe);
        try {
            check(!WorldEdits.mayUseAdjacent(player, target),
                    "Protection query did not veto the adjacent stack consultation");
        } finally {
            PLACEMENT_PROBES.remove(key);
        }
        check(probe.invoked().get(), "Fabric did not consult the adjacent destination");
        check(!probe.syntheticClick().get(), "Adjacent query fabricated a block-use callback");
        helper.succeed();
    }

    /** See {@link ProtectionChecks#refusedRemovalStillLetsTheBarsAboveComeDown}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void refusedRemovalStillLetsTheBarsAboveComeDown(GameTestHelper helper) {
        ProtectionChecks.refusedRemovalStillLetsTheBarsAboveComeDown(helper, ProtectionGameTests::observeRemoval);
    }

    /** See {@link ProtectionChecks#checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(GameTestHelper helper) {
        ProtectionChecks.checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ProtectionChecks#checkedPlacementRejectsAnObstructingEntity}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void checkedPlacementRejectsAnObstructingEntity(GameTestHelper helper) {
        ProtectionChecks.checkedPlacementRejectsAnObstructingEntity(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ProtectionChecks#creativeDepositFillsTheStackWithoutSpendingTheHand}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void creativeDepositFillsTheStackWithoutSpendingTheHand(GameTestHelper helper) {
        ProtectionChecks.creativeDepositFillsTheStackWithoutSpendingTheHand(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ProtectionChecks#placementIntoWaterKeepsTheWater}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void placementIntoWaterKeepsTheWater(GameTestHelper helper) {
        ProtectionChecks.placementIntoWaterKeepsTheWater(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ProtectionChecks#adjacentDepositsFillGroundedCells}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void adjacentDepositsFillGroundedCells(GameTestHelper helper) {
        ProtectionChecks.adjacentDepositsFillGroundedCells(helper, FabricGameTestSupport.playerFactory(helper));
        helper.succeed();
    }

    /**
     * Destination protection queries can refuse neighboring deposits. To reproduce in-game: deny
     * block use at a stack with a Common Protection API claim, then modifier-click its support
     * through an empty cell. No item moves.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void adjacentDepositsHonorProtectionQueries(GameTestHelper helper) {
        TestTarget key = new TestTarget(helper.getLevel(), helper.absolutePos(ORIGIN));
        PlacementProbe probe = new PlacementProbe(InteractionResult.FAIL,
                new AtomicBoolean(), new AtomicBoolean(), new AtomicReference<>(), new AtomicBoolean());
        PLACEMENT_PROBES.put(key, probe);
        try {
            ProtectionChecks.checkAdjacentAndDirectDeposits(
                    helper, FabricGameTestSupport.playerFactory(helper), false);
            check(!WorldEdits.isConsultingAdjacent(), "Destination consultation guard leaked");
            check(!probe.syntheticClick().get(), "Neighbor deposit fabricated a block-use callback");
        } finally {
            PLACEMENT_PROBES.remove(key);
        }
        helper.succeed();
    }

    record TestTarget(Level level, BlockPos pos) {}

    record PlacementProbe(
            InteractionResult result, AtomicBoolean invoked, AtomicBoolean sawAir,
            AtomicReference<Player> actor, AtomicBoolean syntheticClick) {}

    private record RemovalProbe(Predicate<Player> allowed, Consumer<Player> actor) {}

    /** See {@link ProtectionChecks#playerBarExtractionUsesPlayerForCleanup}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void playerBarExtractionUsesPlayerForCleanup(GameTestHelper helper) {
        ProtectionChecks.playerBarExtractionUsesPlayerForCleanup(
                helper, FabricGameTestSupport.playerFactory(helper), ProtectionGameTests::observeRemoval);
    }

    /** See {@link ProtectionChecks#mixedStorageSettlementUsesAutomationForCleanup}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void mixedStorageSettlementUsesAutomationForCleanup(GameTestHelper helper) {
        ProtectionChecks.mixedStorageSettlementUsesAutomationForCleanup(
                helper, FabricGameTestSupport.playerFactory(helper), ProtectionGameTests::observeRemoval);
    }

    private static Runnable observeRemoval(ServerLevel level, BlockPos pos,
            Consumer<Player> observed, Predicate<Player> allowed) {
        TestTarget key = new TestTarget(level, pos);
        REMOVAL_PROBES.put(key, new RemovalProbe(allowed, observed));
        return () -> REMOVAL_PROBES.remove(key);
    }

    // Growth under protection
    //
    // A storage insertion aimed at a slot past what the run holds is the one that grows it, and it
    // checks the position above the run before promising the caller anything, so a simulation and the
    // commit that follows agree about a position growth cannot have. The three tests below stage that
    // with the world border. Spawn protection, the other half of the same predicate, cannot be staged
    // here: it is implemented on DedicatedServer, and the server running these tests is not one.

    /** See {@link ProtectionChecks#storageGrowthAnswersToProtectionInSimulationAndCommit}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void storageGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        ProtectionChecks.storageGrowthAnswersToProtectionInSimulationAndCommit(helper, FabricGameTestSupport::offerAt);
    }

    /** See {@link ProtectionChecks#singlesGrowthAnswersToProtectionInSimulationAndCommit}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void singlesGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        ProtectionChecks.singlesGrowthAnswersToProtectionInSimulationAndCommit(helper, FabricGameTestSupport::offerAt);
    }

    /** See {@link ProtectionChecks#barGrowthAnswersToProtectionInSimulationAndCommit}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void barGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        ProtectionChecks.barGrowthAnswersToProtectionInSimulationAndCommit(helper, FabricGameTestSupport::offerAt);
    }

    // Growth through entities

    /**
     * To reproduce in-game: stand in the growth space above a full Storage pile and insert through
     * item automation; it accepts nothing and does not grow into the entity.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void storageGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        StorageStackBE blockEntity = GameTestScaffold.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            blockEntity.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64));
        }
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(blockEntity);
        ItemStack offered = new ItemStack(Items.STONE, 4);
        GameTestScaffold.putCowIn(helper, ORIGIN.above());

        int headroom = StorageStackBE.SLOTS;
        checkEquals(4, FabricGameTestSupport.insertAt(storage, headroom, offered, true).getCount(),
                "Simulated remainder");
        checkEquals(4, FabricGameTestSupport.insertAt(storage, headroom, offered, false).getCount(),
                "Committed remainder");
        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.storageStackBlock(), ORIGIN.above());
        helper.succeed();
    }

    /**
     * To reproduce in-game: stand in the growth space above a full Singles column and insert through
     * item automation; it accepts nothing and does not grow into the entity.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void singlesGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            singles.getItems().insertItem(slot, new ItemStack(Items.STONE));
        }
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(singles);
        ItemStack offered = new ItemStack(Items.STONE, 4);
        GameTestScaffold.putCowIn(helper, ORIGIN.above());

        int headroom = SinglesStackBE.SLOTS;
        checkEquals(4, FabricGameTestSupport.insertAt(storage, headroom, offered, true).getCount(),
                "Simulated remainder");
        checkEquals(4, FabricGameTestSupport.insertAt(storage, headroom, offered, false).getCount(),
                "Committed remainder");
        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.singlesStackBlock(), ORIGIN.above());
        helper.succeed();
    }

    /**
     * To reproduce in-game: stand in the growth space above a full Bar column and insert through
     * item automation; it accepts nothing and does not grow into the entity.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void barGrowthRejectsAnObstructingEntityInSimulationAndCommit(GameTestHelper helper) {
        BarStackBE bars = GameTestScaffold.placeBar(helper, ORIGIN);
        Item bar = GameTestScaffold.firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            bars.getItems().insertItem(slot, new ItemStack(bar));
        }
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(bars);
        ItemStack offered = new ItemStack(bar, 4);
        GameTestScaffold.putCowIn(helper, ORIGIN.above());

        int headroom = BarStackBE.SLOTS;
        checkEquals(4, FabricGameTestSupport.insertAt(storage, headroom, offered, true).getCount(),
                "Simulated remainder");
        checkEquals(4, FabricGameTestSupport.insertAt(storage, headroom, offered, false).getCount(),
                "Committed remainder");
        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.barStackBlock(), ORIGIN.above());
        helper.succeed();
    }
    /** See {@link InteractionChecks#deniedCleanupEmitsOnlyBlockChange}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void deniedCleanupEmitsOnlyBlockChange(GameTestHelper helper) {
        InteractionChecks.deniedCleanupEmitsOnlyBlockChange(helper,
                FabricGameTestSupport.playerFactory(helper), ProtectionGameTests::observeRemoval);
    }

    /**
     * To reproduce in-game: hold the modifier and click a neighboring face toward a Singles Stack
     * allowed by Common Protection API. One item is deposited.
     */
    public void allowedAdjacentDepositUsesProtectionQuery(GameTestHelper helper) {
        TestTarget key = new TestTarget(helper.getLevel(), helper.absolutePos(ORIGIN));
        PlacementProbe probe = new PlacementProbe(InteractionResult.PASS,
                new AtomicBoolean(), new AtomicBoolean(), new AtomicReference<>(), new AtomicBoolean());
        PLACEMENT_PROBES.put(key, probe);
        try {
            ProtectionChecks.checkAdjacentAndDirectDeposits(helper, FabricGameTestSupport.playerFactory(helper), true);
            check(probe.invoked().get(), "Neighbor deposit skipped protection");
            check(!probe.syntheticClick().get(), "Neighbor deposit fabricated a block-use callback");
        } finally {
            PLACEMENT_PROBES.remove(key);
        }
        helper.succeed();
    }

}
