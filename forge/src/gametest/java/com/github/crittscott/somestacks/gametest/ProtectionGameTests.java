package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.ForgeEditAuthority;
import com.github.crittscott.somestacks.server.WorldEdits;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.items.IItemHandler;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
@GameTestHolder(SomeStacks.MODID)
public final class ProtectionGameTests {
    private ProtectionGameTests() {}

    /** See {@link ProtectionChecks#automationUsesSharedIdentity}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void automationUsesSharedIdentity(GameTestHelper helper) {
        ProtectionChecks.automationUsesSharedIdentity(helper);
    }

    /**
     * No in-game reproduction applies: a protection listener may message the Forge automation
     * actor even though it has no client connection, and that message must be discarded safely.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void automationActorDiscardsClientMessages(GameTestHelper helper) {
        ForgeEditAuthority authority = new ForgeEditAuthority();
        ServerPlayer actor = authority.automationActor(helper.getLevel());
        actor.sendSystemMessage(Component.literal("ignored system message"));
        actor.displayClientMessage(Component.literal("ignored client message"), true);
        authority.clear();
        helper.succeed();
    }

    /** See {@link ProtectionChecks#checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(
            GameTestHelper helper) {
        ProtectionChecks.checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link ProtectionChecks#checkedPlacementRejectsAnObstructingEntity}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void checkedPlacementRejectsAnObstructingEntity(GameTestHelper helper) {
        ProtectionChecks.checkedPlacementRejectsAnObstructingEntity(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link ProtectionChecks#creativeDepositFillsTheStackWithoutSpendingTheHand}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void creativeDepositFillsTheStackWithoutSpendingTheHand(GameTestHelper helper) {
        ProtectionChecks.creativeDepositFillsTheStackWithoutSpendingTheHand(
                helper, GameTestSupport.playerFactory(helper));
    }

    // Mod-driven removal under protection
    //
    // An unattended settle has no player actor, so it uses the level's automation player exactly
    // as growth does. A refusal must leave the block and the run model in step.

    /**
     * To reproduce in-game: have a claim deny SomeStacks permission to remove an empty top Storage
     * block, trigger settling, and verify the protected block remains.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void settleKeepsAnEmptyTopBlockWhoseRemovalIsRefused(GameTestHelper helper) {
        BlockPos topRelative = ORIGIN.above();
        StorageStackBE base = GameTestScaffold.placeStorage(helper, ORIGIN);
        GameTestScaffold.placeStorage(helper, topRelative);
        base.getItems().insertItem(0, new ItemStack(Items.DIRT, 1), false);

        BlockPos top = helper.absolutePos(topRelative);
        Consumer<BlockEvent.BreakEvent> denyTop = event -> {
            if (event.getPos().equals(top)) {
                event.setCanceled(true);
            }
        };

        MinecraftForge.EVENT_BUS.addListener(denyTop);
        try {
            StoragePile pile = base.pile();
            check(pile != null, "Pile did not resolve");
            pile.settle();
            helper.assertBlockPresent(CommonRegistry.storageStackBlock(), topRelative);
            checkEquals(2, pile.height(), "The pile dropped a block it never removed");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(denyTop);
        }

        // With nothing refusing it, the same settle takes the block down.
        StoragePile pile = base.pile();
        check(pile != null, "Pile did not resolve after the refusal");
        pile.settle();
        helper.assertBlockNotPresent(CommonRegistry.storageStackBlock(), topRelative);
        helper.succeed();
    }

    /**
     * To reproduce in-game: allow a player but deny [SomeStacks] in a claim, then have the player
     * extract the last Single. Player-attributed cleanup removes the empty block.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void playerExtractionUsesPlayerForCleanup(GameTestHelper helper) {
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        singles.getItems().insertItem(0, new ItemStack(Items.STONE), false);
        ServerPlayer player = GameTestSupport.fakePlayer(helper.getLevel());
        BlockPos target = helper.absolutePos(ORIGIN);
        AtomicReference<Player> observedActor = new AtomicReference<>();
        Consumer<BlockEvent.BreakEvent> captureActor = event -> {
            if (event.getPos().equals(target)) {
                observedActor.set(event.getPlayer());
            }
        };

        MinecraftForge.EVENT_BUS.addListener(captureActor);
        try {
            check(!singles.extractAt(0, player).isEmpty(), "Player extraction returned nothing");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(captureActor);
        }

        check(observedActor.get() == player, "Cleanup break event did not carry the player");
        helper.assertBlockNotPresent(CommonRegistry.singlesStackBlock(), ORIGIN);
        helper.succeed();
    }

    /**
     * To reproduce in-game: allow a player but deny [SomeStacks] in a claim, then have the player
     * extract the last Storage item. Deferred cleanup retains the player and removes the block.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void playerStorageSettlementUsesPlayerForCleanup(GameTestHelper helper) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, ORIGIN);
        storage.getItems().insertItem(0, new ItemStack(Items.STONE), false);
        ServerPlayer player = GameTestSupport.fakePlayer(helper.getLevel());
        BlockPos target = helper.absolutePos(ORIGIN);
        AtomicReference<Player> observedActor = new AtomicReference<>();
        Consumer<BlockEvent.BreakEvent> captureActor = event -> {
            if (event.getPos().equals(target)) {
                observedActor.set(event.getPlayer());
            }
        };

        MinecraftForge.EVENT_BUS.addListener(captureActor);
        try {
            check(!storage.extractAt(0, 64, ItemStack.EMPTY, player).isEmpty(),
                    "Player extraction returned nothing");
            StoragePile pile = storage.pile();
            check(pile != null, "Pile did not resolve before settlement");
            pile.settle();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(captureActor);
        }

        check(observedActor.get() == player, "Deferred cleanup did not retain the player");
        helper.assertBlockNotPresent(CommonRegistry.storageStackBlock(), ORIGIN);
        helper.succeed();
    }

    // Waterlogging

    /** See {@link ProtectionChecks#placementIntoWaterKeepsTheWater}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void placementIntoWaterKeepsTheWater(GameTestHelper helper) {
        ProtectionChecks.placementIntoWaterKeepsTheWater(
                helper, GameTestSupport.playerFactory(helper));
    }

    /**
     * Neighbor deposits consult protection at the destination and honor cancellation or either
     * use denial. To reproduce in-game: hold the modifier and click the support beneath an empty
     * Singles cell; it fills when allowed and preserves the held item when a claim denies use.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void adjacentStackConsultsDestinationProtection(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.fakePlayer(helper.getLevel());
        BlockPos target = helper.absolutePos(ORIGIN);
        java.util.concurrent.atomic.AtomicInteger clicks = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger denial = new java.util.concurrent.atomic.AtomicInteger();
        Consumer<PlayerInteractEvent.RightClickBlock> observe = event -> {
            if (event.getLevel() != helper.getLevel() || !event.getPos().equals(target)) return;
            clicks.incrementAndGet();
            check(WorldEdits.isConsultingAdjacent(), "Destination consultation lacks its gesture guard");
            switch (denial.get()) {
                case 1 -> event.setCanceled(true);
                case 2 -> event.setUseBlock(Event.Result.DENY);
                case 3 -> event.setUseItem(Event.Result.DENY);
                default -> { }
            }
        };
        MinecraftForge.EVENT_BUS.addListener(observe);
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
            MinecraftForge.EVENT_BUS.unregister(observe);
        }
        helper.succeed();
    }

    /** See {@link ProtectionChecks#adjacentDepositsFillGroundedCells}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void adjacentDepositsFillGroundedCells(GameTestHelper helper) {
        ProtectionChecks.adjacentDepositsFillGroundedCells(helper, GameTestSupport.playerFactory(helper));
        helper.succeed();
    }

    /** See {@link ProtectionChecks#placementPublicationAnswersToVeto}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = 30)
    public static void placementPublicationAnswersToVeto(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(ORIGIN);
        Consumer<BlockEvent.EntityPlaceEvent> deny = event -> {
            if (event.getPos().equals(pos)) event.setCanceled(true);
        };
        ProtectionChecks.placementPublicationAnswersToVeto(helper,
                GameTestSupport.playerFactory(helper),
                () -> MinecraftForge.EVENT_BUS.addListener(deny),
                () -> MinecraftForge.EVENT_BUS.unregister(deny));
    }

    /** See {@link ProtectionChecks#playerBarExtractionUsesPlayerForCleanup}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void playerBarExtractionUsesPlayerForCleanup(GameTestHelper helper) {
        ProtectionChecks.playerBarExtractionUsesPlayerForCleanup(
                helper, GameTestSupport.playerFactory(helper), ProtectionGameTests::observeRemoval);
    }

    /** See {@link ProtectionChecks#mixedStorageSettlementUsesAutomationForCleanup}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void mixedStorageSettlementUsesAutomationForCleanup(GameTestHelper helper) {
        ProtectionChecks.mixedStorageSettlementUsesAutomationForCleanup(
                helper, GameTestSupport.playerFactory(helper), ProtectionGameTests::observeRemoval);
    }

    private static Runnable observeRemoval(ServerLevel level, BlockPos pos,
            java.util.function.Consumer<Player> observed, java.util.function.Predicate<Player> allowed) {
        Consumer<BlockEvent.BreakEvent> listener = event -> {
            if (event.getLevel() == level && event.getPos().equals(pos)) {
                observed.accept(event.getPlayer());
                if (!allowed.test(event.getPlayer())) {
                    event.setCanceled(true);
                }
            }
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        return () -> MinecraftForge.EVENT_BUS.unregister(listener);
    }

    /** See {@link ProtectionChecks#vetoedPlacementRestoresBlockEntity}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void vetoedPlacementRestoresBlockEntity(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(ORIGIN);
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getPos().equals(pos)) {
                event.setCanceled(true);
            }
        };
        ProtectionChecks.vetoedPlacementRestoresBlockEntity(helper,
                GameTestSupport.playerFactory(helper),
                () -> MinecraftForge.EVENT_BUS.addListener(listener),
                () -> MinecraftForge.EVENT_BUS.unregister(listener));
    }

    // Growth under protection
    //
    // A capability insertion aimed at a slot past what the run holds is the one that grows it, and it
    // checks the position above the run before promising the caller anything, so a simulation and the
    // commit that follows agree about a position growth cannot have. The three tests below stage that
    // with the world border. Spawn protection, the other half of the same predicate, cannot be staged
    // here: it is implemented on DedicatedServer, and the server running these tests is not one.

    /**
     * To reproduce in-game: protect the space above a full Storage pile from SomeStacks and insert
     * through item automation; it accepts nothing and creates no block.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void storageGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
        }
        IItemHandler capability = GameTestSupport.capability(storage);
        ItemStack offered = new ItemStack(Items.STONE, 4);

        int headroom = StorageStackBE.SLOTS;
        check(capability.insertItem(headroom, offered, true).isEmpty(),
                "A full pile with free headroom did not credit growth");

        GameTestScaffold.outsideWorldBorder(helper, ORIGIN.above(), () -> {
            checkEquals(4, capability.insertItem(headroom, offered, true).getCount(),
                    "Simulated remainder");
            checkEquals(4, capability.insertItem(headroom, offered, false).getCount(),
                    "Committed remainder");
        });

        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.storageStackBlock(), ORIGIN.above());
        helper.succeed();
    }

    /**
     * To reproduce in-game: protect the space above a full Singles column from SomeStacks and insert
     * through item automation; it accepts nothing and creates no block.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void singlesGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            singles.getItems().insertItem(slot, new ItemStack(Items.STONE, 1), false);
        }
        IItemHandler capability = GameTestSupport.capability(singles);
        ItemStack offered = new ItemStack(Items.STONE, 4);

        // A cell takes one item, so a credited growth leaves three of the four behind.
        int headroom = SinglesStackBE.SLOTS;
        checkEquals(3, capability.insertItem(headroom, offered, true).getCount(),
                "A full column with free headroom did not credit growth");

        GameTestScaffold.outsideWorldBorder(helper, ORIGIN.above(), () -> {
            checkEquals(4, capability.insertItem(headroom, offered, true).getCount(),
                    "Simulated remainder");
            checkEquals(4, capability.insertItem(headroom, offered, false).getCount(),
                    "Committed remainder");
        });

        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.singlesStackBlock(), ORIGIN.above());
        helper.succeed();
    }

    /**
     * To reproduce in-game: protect the space above a full Bar column from SomeStacks and insert
     * through item automation; it accepts nothing and creates no block.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void barGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        BarStackBE bars = GameTestScaffold.placeBar(helper, ORIGIN);
        Item bar = GameTestScaffold.firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            bars.getItems().insertItem(slot, new ItemStack(bar, 1), false);
        }
        IItemHandler capability = GameTestSupport.capability(bars);
        ItemStack offered = new ItemStack(bar, 4);

        // A position takes one bar, so a credited growth leaves three of the four behind.
        int headroom = BarStackBE.SLOTS;
        checkEquals(3, capability.insertItem(headroom, offered, true).getCount(),
                "A full column with free headroom did not credit growth");

        GameTestScaffold.outsideWorldBorder(helper, ORIGIN.above(), () -> {
            checkEquals(4, capability.insertItem(headroom, offered, true).getCount(),
                    "Simulated remainder");
            checkEquals(4, capability.insertItem(headroom, offered, false).getCount(),
                    "Committed remainder");
        });

        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.barStackBlock(), ORIGIN.above());
        helper.succeed();
    }

    // Growth through entities

    /**
     * To reproduce in-game: stand in the growth space above a full Storage pile and insert through
     * item automation; it accepts nothing and does not grow into the entity.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void storageGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
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
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void singlesGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            singles.getItems().insertItem(slot, new ItemStack(Items.STONE), false);
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
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void barGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        BarStackBE bars = GameTestScaffold.placeBar(helper, ORIGIN);
        Item bar = GameTestScaffold.firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            bars.getItems().insertItem(slot, new ItemStack(bar), false);
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
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void deniedCleanupEmitsOnlyBlockChange(GameTestHelper helper) {
        InteractionChecks.deniedCleanupEmitsOnlyBlockChange(helper,
                GameTestSupport.playerFactory(helper), ProtectionGameTests::observeRemoval);
    }

}
