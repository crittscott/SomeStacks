package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.AutomationActor;
import com.github.crittscott.somestacks.server.ForgeEditAuthority;
import com.github.crittscott.somestacks.server.Protection;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.items.IItemHandler;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

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
    private static final Protection PROTECTION = new Protection();

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
            helper.assertBlockPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), topRelative);
            checkEquals(2, pile.height(), "The pile dropped a block it never removed");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(denyTop);
        }

        // With nothing refusing it, the same settle takes the block down.
        StoragePile pile = base.pile();
        check(pile != null, "Pile did not resolve after the refusal");
        pile.settle();
        helper.assertBlockNotPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), topRelative);
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
        helper.assertBlockNotPresent(CommonRegistry.SINGLES_STACK_BLOCK.get(), ORIGIN);
        helper.succeed();
    }

    /**
     * To reproduce in-game: allow a player but deny [SomeStacks] in a claim, then have the player
     * extract the last Bar. Player-attributed cleanup removes the empty block.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void playerBarExtractionUsesPlayerForCleanup(GameTestHelper helper) {
        BarStackBE bars = GameTestScaffold.placeBar(helper, ORIGIN);
        bars.getItems().insertItem(0, new ItemStack(GameTestScaffold.firstBarItem()), false);
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
            check(!bars.extractAt(0, player).isEmpty(), "Player extraction returned nothing");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(captureActor);
        }

        check(observedActor.get() == player, "Bar cleanup event did not carry the player");
        helper.assertBlockNotPresent(CommonRegistry.BAR_STACK_BLOCK.get(), ORIGIN);
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
        helper.assertBlockNotPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN);
        helper.succeed();
    }

    /**
     * To reproduce in-game: allow a player but deny [SomeStacks] in a claim, extract one of two
     * Storage items by hand and the other by automation before settling, and verify the empty block
     * remains because mixed cleanup is attributed to [SomeStacks].
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void mixedStorageSettlementUsesAutomationForCleanup(GameTestHelper helper) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, ORIGIN);
        storage.getItems().insertItem(0, new ItemStack(Items.STONE, 2), false);
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
            checkEquals(1, storage.extractAt(0, 1, ItemStack.EMPTY, player).getCount(),
                    "Player extraction count");
            StoragePile pile = storage.pile();
            check(pile != null, "Pile did not resolve before automation extraction");
            checkEquals(1, pile.extract(0, 1, false).getCount(), "Automation extraction count");
            pile.settle();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(captureActor);
        }

        check(observedActor.get() != null, "Cleanup break event did not fire");
        checkEquals(AutomationActor.PROFILE.getId(), observedActor.get().getUUID(),
                "Mixed cleanup actor UUID");
        helper.assertBlockNotPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN);
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
     * To reproduce in-game: deny SomeStacks item use at the destination beside a stack, then
     * right-click the neighboring face and verify no block or item is placed there.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void adjacentConsultationHonorsUseItemDeny(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = GameTestSupport.fakePlayer(level);
        BlockPos clicked = helper.absolutePos(ORIGIN);
        Consumer<PlayerInteractEvent.RightClickBlock> denyItem = event -> {
            if (event.getEntity() == player && event.getPos().equals(clicked)) {
                event.setUseItem(Event.Result.DENY);
            }
        };

        MinecraftForge.EVENT_BUS.addListener(denyItem);
        try {
            check(!PROTECTION.mayUseItemAt(player, clicked),
                    "Item-use denial did not veto the adjacent stack consultation");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(denyItem);
        }
        helper.succeed();
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
                CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN.above());
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
                CommonRegistry.SINGLES_STACK_BLOCK.get(), ORIGIN.above());
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
                CommonRegistry.BAR_STACK_BLOCK.get(), ORIGIN.above());
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
                CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN.above());
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
                CommonRegistry.SINGLES_STACK_BLOCK.get(), ORIGIN.above());
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
                CommonRegistry.BAR_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

}
