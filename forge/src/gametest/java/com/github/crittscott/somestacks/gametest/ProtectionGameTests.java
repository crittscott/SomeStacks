package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.ForgeEditAuthority;
import com.github.crittscott.somestacks.server.WorldEdits;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.eventbus.api.listener.EventListener;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.items.IItemHandler;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/**
 * The rules that keep a gesture from writing where it should not: build height, entity obstruction,
 * loader-native interaction denial, and world protection.
 *
 * <p>These fire the Forge events themselves, so they cover the order a consult and the commit that
 * follows must agree on.
 */
public final class ProtectionGameTests {
    public ProtectionGameTests() {}

    /** See {@link ProtectionChecks#placementReplacesClickedVegetation}. */
    public static void placementReplacesClickedVegetation(GameTestHelper helper) {
        ProtectionChecks.placementReplacesClickedVegetation(helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link ProtectionChecks#automationUsesSharedIdentity}. */
    public static void automationUsesSharedIdentity(GameTestHelper helper) {
        ProtectionChecks.automationUsesSharedIdentity(helper);
    }

    /**
     * No in-game reproduction applies: a protection listener may message the Forge automation
     * actor even though it has no client connection, and that message must be discarded safely.
     */
    public static void automationActorDiscardsClientMessages(GameTestHelper helper) {
        ForgeEditAuthority authority = new ForgeEditAuthority();
        ServerPlayer actor = authority.automationActor(helper.getLevel());
        actor.sendSystemMessage(Component.literal("ignored system message"));
        actor.displayClientMessage(Component.literal("ignored client message"), true);
        authority.clear();
        helper.succeed();
    }

    /** See {@link ProtectionChecks#checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight}. */
    public static void checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(
            GameTestHelper helper) {
        ProtectionChecks.checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link ProtectionChecks#checkedPlacementRejectsAnObstructingEntity}. */
    public static void checkedPlacementRejectsAnObstructingEntity(GameTestHelper helper) {
        ProtectionChecks.checkedPlacementRejectsAnObstructingEntity(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link ProtectionChecks#creativeDepositFillsTheStackWithoutSpendingTheHand}. */
    public static void creativeDepositFillsTheStackWithoutSpendingTheHand(GameTestHelper helper) {
        ProtectionChecks.creativeDepositFillsTheStackWithoutSpendingTheHand(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link ProtectionChecks#playerPlacementAndGrowthAwardPlacedBlock}. */
    public static void playerPlacementAndGrowthAwardPlacedBlock(GameTestHelper helper) {
        ProtectionChecks.playerPlacementAndGrowthAwardPlacedBlock(
                helper, GameTestSupport.playerFactory(helper));
    }

    // Mod-driven removal under protection
    //
    // An unattended settle has no player actor, so it uses the level's automation player exactly
    // as growth does. A refusal must leave the block and the run model in step.

    /** See {@link ProtectionChecks#settleKeepsAnEmptyTopBlockWhoseRemovalIsRefused}. */
    public static void settleKeepsAnEmptyTopBlockWhoseRemovalIsRefused(GameTestHelper helper) {
        ProtectionChecks.settleKeepsAnEmptyTopBlockWhoseRemovalIsRefused(helper, ProtectionGameTests::observeRemoval);
    }

    /** See {@link ProtectionChecks#playerExtractionUsesPlayerForCleanup}. */
    public static void playerExtractionUsesPlayerForCleanup(GameTestHelper helper) {
        ProtectionChecks.playerExtractionUsesPlayerForCleanup(helper, GameTestSupport.playerFactory(helper), ProtectionGameTests::observeRemoval);
    }

    /** See {@link ProtectionChecks#playerStorageSettlementUsesPlayerForCleanup}. */
    public static void playerStorageSettlementUsesPlayerForCleanup(GameTestHelper helper) {
        ProtectionChecks.playerStorageSettlementUsesPlayerForCleanup(helper, GameTestSupport.playerFactory(helper), ProtectionGameTests::observeRemoval);
    }

    // Waterlogging

    /** See {@link ProtectionChecks#placementIntoWaterKeepsTheWater}. */
    public static void placementIntoWaterKeepsTheWater(GameTestHelper helper) {
        ProtectionChecks.placementIntoWaterKeepsTheWater(
                helper, GameTestSupport.playerFactory(helper));
    }

    /**
     * Neighbor deposits consult protection at the destination and honor cancellation or either
     * use denial. To reproduce in-game: hold the modifier and click the support beneath an empty
     * Singles cell; it fills when allowed and preserves the held item when a claim denies use.
     */
    public static void adjacentStackConsultsDestinationProtection(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.fakePlayer(helper.getLevel());
        BlockPos target = helper.absolutePos(ORIGIN);
        AtomicInteger clicks = new AtomicInteger();
        AtomicInteger denial = new AtomicInteger();
        Predicate<PlayerInteractEvent.RightClickBlock> observe = event -> {
            if (event.getLevel() != helper.getLevel() || !event.getPos().equals(target)) return false;
            clicks.incrementAndGet();
            check(WorldEdits.isConsultingAdjacent(), "Destination consultation lacks its gesture guard");
            switch (denial.get()) {
                case 1 -> { return true; }
                case 2 -> event.setUseBlock(Result.DENY);
                case 3 -> event.setUseItem(Result.DENY);
                default -> { }
            }
            return false;
        };
        var observeListener = PlayerInteractEvent.RightClickBlock.BUS.addListener(observe);
        try {
            check(WorldEdits.mayUseAdjacent(player, target), "Allowed destination was refused");
            checkEquals(1, clicks.get(), "Destination consultation count");
            helper.getLevel().removeBlock(target, false);
            ProtectionChecks.checkAdjacentAndDirectDeposits(helper, GameTestSupport.playerFactory(helper), true);
            for (int reason = 1; reason <= 3; reason++) {
                denial.set(reason);
                check(!WorldEdits.mayUseAdjacent(player, target), "Destination denial was ignored");
                helper.getLevel().removeBlock(target, false);
                ProtectionChecks.checkAdjacentAndDirectDeposits(helper, GameTestSupport.playerFactory(helper), false);
            }
            check(!WorldEdits.isConsultingAdjacent(), "Destination consultation guard leaked");
        } finally {
            PlayerInteractEvent.RightClickBlock.BUS.removeListener(observeListener);
        }
        helper.succeed();
    }

    /** See {@link ProtectionChecks#adjacentDepositsFillGroundedCells}. */
    public static void adjacentDepositsFillGroundedCells(GameTestHelper helper) {
        ProtectionChecks.adjacentDepositsFillGroundedCells(helper, GameTestSupport.playerFactory(helper));
        helper.succeed();
    }

    /** See {@link ProtectionChecks#placementPublicationAnswersToVeto}. */
    public static void placementPublicationAnswersToVeto(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(ORIGIN);
        Predicate<BlockEvent.EntityPlaceEvent> deny = event -> event.getPos().equals(pos);
        AtomicReference<EventListener> listener = new AtomicReference<>();
        ProtectionChecks.placementPublicationAnswersToVeto(helper,
                GameTestSupport.playerFactory(helper),
                () -> listener.set(BlockEvent.EntityPlaceEvent.BUS.addListener(deny)),
                () -> BlockEvent.EntityPlaceEvent.BUS.removeListener(listener.get()));
    }

    /** See {@link ProtectionChecks#playerBarExtractionUsesPlayerForCleanup}. */
    public static void playerBarExtractionUsesPlayerForCleanup(GameTestHelper helper) {
        ProtectionChecks.playerBarExtractionUsesPlayerForCleanup(
                helper, GameTestSupport.playerFactory(helper), ProtectionGameTests::observeRemoval);
    }

    /** See {@link ProtectionChecks#mixedStorageSettlementUsesAutomationForCleanup}. */
    public static void mixedStorageSettlementUsesAutomationForCleanup(GameTestHelper helper) {
        ProtectionChecks.mixedStorageSettlementUsesAutomationForCleanup(
                helper, GameTestSupport.playerFactory(helper), ProtectionGameTests::observeRemoval);
    }

    private static Runnable observeRemoval(ServerLevel level, BlockPos pos,
            Consumer<Player> observed, Predicate<Player> allowed) {
        Consumer<BlockEvent.BreakEvent> listener = event -> {
            if (event.getLevel() == level && event.getPos().equals(pos)) {
                observed.accept(event.getPlayer());
                if (!allowed.test(event.getPlayer())) {
                    event.setResult(Result.DENY);
                }
            }
        };
        var registration = BlockEvent.BreakEvent.BUS.addListener(listener);
        return () -> BlockEvent.BreakEvent.BUS.removeListener(registration);
    }

    /** See {@link ProtectionChecks#refusedRemovalStillLetsTheBarsAboveComeDown}. */
    public static void refusedRemovalStillLetsTheBarsAboveComeDown(GameTestHelper helper) {
        ProtectionChecks.refusedRemovalStillLetsTheBarsAboveComeDown(helper, ProtectionGameTests::observeRemoval);
    }

    /** See {@link ProtectionChecks#vetoedPlacementRestoresBlockEntity}. */
    public static void vetoedPlacementRestoresBlockEntity(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(ORIGIN);
        Predicate<BlockEvent.EntityPlaceEvent> listener = event -> event.getPos().equals(pos);
        AtomicReference<EventListener> registration = new AtomicReference<>();
        ProtectionChecks.vetoedPlacementRestoresBlockEntity(helper,
                GameTestSupport.playerFactory(helper),
                () -> registration.set(BlockEvent.EntityPlaceEvent.BUS.addListener(listener)),
                () -> BlockEvent.EntityPlaceEvent.BUS.removeListener(registration.get()));
    }

    // Growth under protection
    //
    // A capability insertion aimed at a slot past what the run holds is the one that grows it, and it
    // checks the position above the run before promising the caller anything, so a simulation and the
    // commit that follows agree about a position growth cannot have. The three tests below stage that
    // with the world border. Spawn protection, the other half of the same predicate, cannot be staged
    // here: it is implemented on DedicatedServer, and the server running these tests is not one.

    /** See {@link ProtectionChecks#storageGrowthAnswersToProtectionInSimulationAndCommit}. */
    public static void storageGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        ProtectionChecks.storageGrowthAnswersToProtectionInSimulationAndCommit(helper, GameTestSupport::offerAt);
    }

    /** See {@link ProtectionChecks#singlesGrowthAnswersToProtectionInSimulationAndCommit}. */
    public static void singlesGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        ProtectionChecks.singlesGrowthAnswersToProtectionInSimulationAndCommit(helper, GameTestSupport::offerAt);
    }

    /** See {@link ProtectionChecks#barGrowthAnswersToProtectionInSimulationAndCommit}. */
    public static void barGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        ProtectionChecks.barGrowthAnswersToProtectionInSimulationAndCommit(helper, GameTestSupport::offerAt);
    }

    // Growth through entities

    /**
     * To reproduce in-game: stand in the growth space above a full Storage pile and insert through
     * item automation; it accepts nothing and does not grow into the entity.
     */
    public static void storageGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64));
        }
        IItemHandler capability = GameTestSupport.capability(storage);
        ItemStack offered = new ItemStack(Items.STONE, 4);
        GameTestScaffold.putCowIn(helper, ORIGIN.above());

        int headroom = StorageStackBE.SLOTS;
        checkEquals(4, capability.insertItem(headroom, offered, true).getCount(),
                "Simulated remainder");
        checkEquals(4, capability.insertItem(headroom, offered, false).getCount(),
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
    public static void singlesGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            singles.getItems().insertItem(slot, new ItemStack(Items.STONE));
        }
        IItemHandler capability = GameTestSupport.capability(singles);
        ItemStack offered = new ItemStack(Items.STONE, 4);
        GameTestScaffold.putCowIn(helper, ORIGIN.above());

        int headroom = SinglesStackBE.SLOTS;
        checkEquals(4, capability.insertItem(headroom, offered, true).getCount(),
                "Simulated remainder");
        checkEquals(4, capability.insertItem(headroom, offered, false).getCount(),
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
    public static void barGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        BarStackBE bars = GameTestScaffold.placeBar(helper, ORIGIN);
        Item bar = GameTestScaffold.firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            bars.getItems().insertItem(slot, new ItemStack(bar));
        }
        IItemHandler capability = GameTestSupport.capability(bars);
        ItemStack offered = new ItemStack(bar, 4);
        GameTestScaffold.putCowIn(helper, ORIGIN.above());

        int headroom = BarStackBE.SLOTS;
        checkEquals(4, capability.insertItem(headroom, offered, true).getCount(),
                "Simulated remainder");
        checkEquals(4, capability.insertItem(headroom, offered, false).getCount(),
                "Committed remainder");
        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.barStackBlock(), ORIGIN.above());
        helper.succeed();
    }
    /** See {@link InteractionChecks#deniedCleanupEmitsOnlyBlockChange}. */
    public static void deniedCleanupEmitsOnlyBlockChange(GameTestHelper helper) {
        InteractionChecks.deniedCleanupEmitsOnlyBlockChange(helper,
                GameTestSupport.playerFactory(helper), ProtectionGameTests::observeRemoval);
    }

}
