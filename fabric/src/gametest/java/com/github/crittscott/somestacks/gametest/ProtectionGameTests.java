package com.github.crittscott.somestacks.gametest;

import net.minecraft.world.phys.shapes.Shapes;
import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.WorldEdits;
import net.fabricmc.fabric.api.entity.FakePlayer;
import eu.pb4.common.protection.api.CommonProtection;
import eu.pb4.common.protection.api.ProtectionProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.phys.AABB;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final Map<TestTarget, RemovalProbe> REMOVAL_PROBES =
            new ConcurrentHashMap<>();
    private static final Map<TestTarget, InteractionResult> ADJACENT_USE_RESULTS =
            new ConcurrentHashMap<>();

    private static final Set<TestTarget> PLACEMENT_REFUSALS = ConcurrentHashMap.newKeySet();

    private static final Set<TestTarget> GROWTH_REFUSALS = ConcurrentHashMap.newKeySet();

    static {
        CommonProtection.register(Identifier.fromNamespaceAndPath("somestacks_gametest", "protection"),
                new ProtectionProvider() {
                    @Override public boolean isProtected(Level level, BlockPos pos) { return false; }
                    @Override public boolean isAreaProtected(Level level, AABB area) { return false; }

                    @Override
                    public boolean canPlaceBlock(Level level, BlockPos pos, NameAndId identity, Player player) {
                        if (PLACEMENT_REFUSALS.contains(new TestTarget(level, pos))) return false;
                        if (!GROWTH_REFUSALS.contains(new TestTarget(level, pos))) return true;
                        check(player == WorldEdits.automationActor((ServerLevel) level),
                                "Growth query used someone other than the automation actor");
                        return false;
                    }

                    @Override
                    public boolean canInteractBlock(Level level, BlockPos pos, NameAndId identity, Player player) {
                        return !ADJACENT_USE_RESULTS.containsKey(new TestTarget(level, pos));
                    }
                });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            check(!GROWTH_REFUSALS.contains(new TestTarget(level, hit.getBlockPos()))
                            && !ADJACENT_USE_RESULTS.containsKey(new TestTarget(level, hit.getBlockPos())),
                    "A protection query invoked a gameplay block-use callback");
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
     * block use at a stack with a protection mod, then modifier-click its support through an empty
     * cell. No item moves.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void adjacentDepositsHonorProtectionQueries(GameTestHelper helper) {
        TestTarget key = new TestTarget(helper.getLevel(), helper.absolutePos(ORIGIN));
        try {
            for (InteractionResult result : List.of(InteractionResult.FAIL, InteractionResult.SUCCESS)) {
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

    /**
     * To reproduce in-game: place a stack against an ordinary block, then modifier-click through
     * empty grounded cells at their support. Placement uses destination queries and vanilla checks, and neighboring deposits
     * work when destination protection queries permit them.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void placementAndAdjacentDepositsUseVanillaAuthority(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(ORIGIN);
        ServerPlayer player = FakePlayer.get(helper.getLevel());
        check(WorldEdits.mayPlace(player, helper.getLevel(), pos), "Unprotected placement was denied");
        check(WorldEdits.mayUseAdjacent(player, pos), "Unprotected neighboring deposit was denied");
        check(WorldEdits.placeChecked(player, helper.getLevel(), pos,
                        Blocks.STONE.defaultBlockState(), Direction.DOWN, Shapes.block()),
                "Unprotected placement failed");
        helper.assertBlockPresent(Blocks.STONE, ORIGIN);
        helper.getLevel().removeBlock(pos, false);
        ProtectionChecks.checkAdjacentAndDirectDeposits(
                helper, FabricGameTestSupport.playerFactory(helper), true);
        helper.succeed();
    }

    /**
     * Player placement checks the destination independently of the clicked block. To reproduce
     * in-game: allow use on the origin side of a claim border and deny placement across the border.
     * A modified Storage placement preserves both the destination and the held item.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void playerPlacementConsultsDestination(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos destination = helper.absolutePos(ORIGIN);
        BlockPos origin = destination.below();
        level.setBlock(origin, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        ServerPlayer player = FabricGameTestSupport.playerFactory(helper).apply(new ItemStack(Items.DIRT));
        var key = new TestTarget(level, destination);
        PLACEMENT_REFUSALS.add(key);
        com.github.crittscott.somestacks.server.ServerGestureState.set(player,
                com.github.crittscott.somestacks.util.StackMode.STORAGE_STACK, true);
        try {
            check(!WorldEdits.mayPlace(player, level, destination), "Player destination query ignored denial");
            check(com.github.crittscott.somestacks.server.StackInteractions.handleAdjacentClick(player,
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(origin),
                            Direction.UP, origin, false), true, true), "Placement click was not consumed");
            check(level.getBlockState(destination).isAir(), "Denied placement changed the destination");
            checkEquals(1, player.getMainHandItem().getCount(), "Denied placement spent the held item");
        } finally {
            PLACEMENT_REFUSALS.remove(key);
            com.github.crittscott.somestacks.server.ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

    record TestTarget(Level level, BlockPos pos) {}

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

    /**
     * To reproduce in-game: with a protection mod that denies placement to [SomeStacks] above a full
     * Storage pile, insert through item automation; nothing is accepted and no block is created.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void storageGrowthAnswersToProtectionQueries(GameTestHelper helper) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64));
        }
        checkRefusedGrowth(helper, FabricGameTestSupport.storage(storage), StorageStackBE.SLOTS,
                new ItemStack(Items.STONE, 4), CommonRegistry.storageStackBlock());
    }

    /**
     * To reproduce in-game: with a protection mod that denies placement to [SomeStacks] above a full
     * Singles column, insert through item automation; nothing is accepted and no block is created.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void singlesGrowthAnswersToProtectionQueries(GameTestHelper helper) {
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            singles.getItems().insertItem(slot, new ItemStack(Items.STONE));
        }
        checkRefusedGrowth(helper, FabricGameTestSupport.storage(singles), SinglesStackBE.SLOTS,
                new ItemStack(Items.STONE, 4), CommonRegistry.singlesStackBlock());
    }

    /**
     * To reproduce in-game: with a protection mod that denies placement to [SomeStacks] above a full
     * Bar column, insert through item automation; nothing is accepted and no block is created.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void barGrowthAnswersToProtectionQueries(GameTestHelper helper) {
        BarStackBE bars = GameTestScaffold.placeBar(helper, ORIGIN);
        Item bar = GameTestScaffold.firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            bars.getItems().insertItem(slot, new ItemStack(bar));
        }
        checkRefusedGrowth(helper, FabricGameTestSupport.storage(bars), BarStackBE.SLOTS,
                new ItemStack(bar, 4), CommonRegistry.barStackBlock());
    }

    /** Offers {@code offered} to the headroom slot while a provider refuses automation placement. */
    private static void checkRefusedGrowth(GameTestHelper helper, SlottedStorage<ItemVariant> storage,
                                           int headroom, ItemStack offered, Block stackBlock) {
        TestTarget above = new TestTarget(helper.getLevel(), helper.absolutePos(ORIGIN.above()));
        GROWTH_REFUSALS.add(above);
        try {
            checkEquals(4, FabricGameTestSupport.insertAt(storage, headroom, offered, false).getCount(),
                    "Committed remainder");
        } finally {
            GROWTH_REFUSALS.remove(above);
        }
        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        check(!WorldEdits.isConsultingAdjacent(), "Growth consultation guard leaked");
        helper.assertBlockNotPresent(stackBlock, ORIGIN.above());
        helper.succeed();
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
}
