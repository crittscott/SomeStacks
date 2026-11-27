package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.PlatformServices;
import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.StackMode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.function.Consumer;

/**
 * The rules that keep a gesture from writing where it should not: build height, entity obstruction,
 * and native placement and interaction protection.
 *
 * <p>These fire the Forge events themselves, so they cover the order a consult and the commit that
 * follows must agree on.
 */
@GameTestHolder(SomeStacksCommon.MODID)
@PrefixGameTestTemplate(false)
public final class ProtectionGameTests {
    private ProtectionGameTests() {}

    /**
     * Loader entry point for {@link WorldEditChecks#placementGrowthAndCleanupNotify}; see that
     * method for the scenario and manual check.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void placementGrowthAndCleanupNotify(GameTestHelper helper) {
        WorldEditChecks.placementGrowthAndCleanupNotify(
                helper,
                FakePlayerFactory.getMinecraft(helper.getLevel()),
                ForgeGameTestSupport::click);
    }

    /** Loader entry point for {@link WorldEditChecks#automationUsesSharedIdentity}. */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void automationUsesSharedIdentity(GameTestHelper helper) {
        WorldEditChecks.automationUsesSharedIdentity(helper);
    }

    /** Loader entry point for {@link WorldEditChecks#cleanupOfWaterloggedStackKeepsTheWater}. */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void cleanupOfWaterloggedStackKeepsTheWater(GameTestHelper helper) {
        WorldEditChecks.cleanupOfWaterloggedStackKeepsTheWater(helper);
    }

    /** Loader entry point for {@link WorldEditChecks#playerCausedCleanupCarriesThePlayer}. */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void playerCausedCleanupCarriesThePlayer(GameTestHelper helper) {
        WorldEditChecks.playerCausedCleanupCarriesThePlayer(
                helper, FakePlayerFactory.getMinecraft(helper.getLevel()));
    }

    /**
     * Loader entry point for {@link WorldEditChecks#breakingAStackChargesItsCleanupToTheBreaker}.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void breakingAStackChargesItsCleanupToTheBreaker(GameTestHelper helper) {
        WorldEditChecks.breakingAStackChargesItsCleanupToTheBreaker(
                helper, FakePlayerFactory.getMinecraft(helper.getLevel()));
    }

    /**
     * Loader entry point for {@link
     * WorldEditChecks#checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight}.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(
            GameTestHelper helper) {
        WorldEditChecks.checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(
                helper, ForgeGameTestSupport.players(helper).apply(ItemStack.EMPTY));
    }

    /**
     * Loader entry point for {@link WorldEditChecks#checkedPlacementRejectsAnObstructingEntity}.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void checkedPlacementRejectsAnObstructingEntity(GameTestHelper helper) {
        WorldEditChecks.checkedPlacementRejectsAnObstructingEntity(
                helper, ForgeGameTestSupport.players(helper).apply(ItemStack.EMPTY));
    }

    /**
     * Loader entry point for {@link
     * WorldEditChecks#creativeDepositFillsTheStackWithoutSpendingTheHand}.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void creativeDepositFillsTheStackWithoutSpendingTheHand(GameTestHelper helper) {
        WorldEditChecks.creativeDepositFillsTheStackWithoutSpendingTheHand(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    // Mod-driven removal under protection
    //
    // A settle or a collapse takes down the blocks it empties, and that is a world edit with no
    // actor left to ask, so it uses the level's fake player exactly as growth does. A refusal
    // has to leave the block standing without leaving the run's own model out of step with it.

    /**
     * Forge fixture: cancel BlockEvent.BreakEvent at an empty upper Storage block above a base
     * holding one dirt. Settlement keeps the refused block and height two; removing the veto and
     * settling again removes the empty top.
     *
     * <p>To check manually, grow a temporary two-block pile and configure protection to deny
     * removal by the automation actor at the upper position while allowing extraction. Empty the
     * upper block and allow settlement; it remains. Lift the denial and trigger another inventory
     * change; subsequent settlement trims it.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void settleKeepsAnEmptyTopBlockWhoseRemovalIsRefused(GameTestHelper helper) {
        BlockPos topRelative = ORIGIN.above();
        StorageStackBE base = GameTestScaffold.placeStorage(helper, ORIGIN);
        GameTestScaffold.placeStorage(helper, topRelative);
        base.getItems().insertItem(0, new ItemStack(Items.DIRT, 1), false);

        BlockPos top = helper.absolutePos(topRelative);
        Consumer<BlockEvent.BreakEvent> denyTop =
                event -> {
                    if (event.getPos().equals(top)) {
                        event.setCanceled(true);
                    }
                };

        MinecraftForge.EVENT_BUS.addListener(denyTop);
        try (var events = new WorldEditChecks.EventProbe(helper.getLevel(), top)) {
            StoragePile pile = base.pile();
            check(pile != null, "Pile did not resolve");
            pile.settle();
            helper.assertBlockPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), topRelative);
            checkEquals(2, pile.height(), "The pile dropped a block it never removed");
            events.expectNone();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(denyTop);
        }

        // With nothing refusing it, the same settle takes the block down.
        StoragePile pile = base.pile();
        check(pile != null, "Pile did not resolve after the refusal");
        var state = helper.getLevel().getBlockState(top);
        try (var events = new WorldEditChecks.EventProbe(helper.getLevel(), top)) {
            pile.settle();
            events.expectOne(
                    GameEvent.BLOCK_DESTROY, WorldEdits.automationActor(helper.getLevel()), state);
        }
        helper.assertBlockNotPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), topRelative);
        helper.succeed();
    }

    /** Loader entry point for {@link WorldEditChecks#placementIntoWaterKeepsTheWater}. */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void placementIntoWaterKeepsTheWater(GameTestHelper helper) {
        WorldEditChecks.placementIntoWaterKeepsTheWater(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link
     * WorldEditChecks#storageGrowthAnswersToProtectionInSimulationAndCommit}.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void storageGrowthAnswersToProtectionInSimulationAndCommit(
            GameTestHelper helper) {
        WorldEditChecks.storageGrowthAnswersToProtectionInSimulationAndCommit(
                helper, ForgeGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * WorldEditChecks#singlesGrowthAnswersToProtectionInSimulationAndCommit}.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void singlesGrowthAnswersToProtectionInSimulationAndCommit(
            GameTestHelper helper) {
        WorldEditChecks.singlesGrowthAnswersToProtectionInSimulationAndCommit(
                helper, ForgeGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * WorldEditChecks#barGrowthAnswersToProtectionInSimulationAndCommit}.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void barGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        WorldEditChecks.barGrowthAnswersToProtectionInSimulationAndCommit(
                helper, ForgeGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * WorldEditChecks#storageGrowthRejectsAnObstructingEntityInSimulationAndCommit}.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void storageGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        WorldEditChecks.storageGrowthRejectsAnObstructingEntityInSimulationAndCommit(
                helper, ForgeGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * WorldEditChecks#singlesGrowthRejectsAnObstructingEntityInSimulationAndCommit}.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void singlesGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        WorldEditChecks.singlesGrowthRejectsAnObstructingEntityInSimulationAndCommit(
                helper, ForgeGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * WorldEditChecks#barGrowthRejectsAnObstructingEntityInSimulationAndCommit}.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void barGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        WorldEditChecks.barGrowthRejectsAnObstructingEntityInSimulationAndCommit(
                helper, ForgeGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * WorldEditChecks#completedShapesDistinguishOccupiedCellsFromEmptySpace}.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void completedShapesDistinguishOccupiedCellsFromEmptySpace(
            GameTestHelper helper) {
        WorldEditChecks.completedShapesDistinguishOccupiedCellsFromEmptySpace(helper);
    }

    /**
     * Loader entry point for {@link InteractionChecks#blockUseDenialFreezesStackGestures}; see that
     * method for the scenario and manual check. The denial is a {@code RightClickBlock} listener at
     * the default priority that sets {@code setUseBlock(DENY)} for the fixture player at the
     * gesture's block, so it runs before the mod's own {@code LOWEST} listener, as a protection
     * mod's would.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void blockUseDenialFreezesStackGestures(GameTestHelper helper) {
        ServerPlayer[] deniedPlayer = {null};
        BlockPos[] deniedPosition = {null};
        Consumer<PlayerInteractEvent.RightClickBlock> veto =
                event -> {
                    if (event.getEntity() == deniedPlayer[0]
                            && event.getPos().equals(deniedPosition[0])) {
                        event.setUseBlock(Event.Result.DENY);
                    }
                };
        MinecraftForge.EVENT_BUS.addListener(veto);
        try {
            InteractionChecks.blockUseDenialFreezesStackGestures(
                    helper,
                    ForgeGameTestSupport.players(helper),
                    ForgeGameTestSupport::click,
                    (player, pos) -> {
                        deniedPlayer[0] = player;
                        deniedPosition[0] = pos;
                    },
                    () -> {
                        deniedPlayer[0] = null;
                        deniedPosition[0] = null;
                    });
        } finally {
            MinecraftForge.EVENT_BUS.unregister(veto);
        }
    }

    /**
     * Forge permission fixture: a RightClickBlock listener at an existing Storage block denies only
     * item use. Stack gestures are the block's own use, so a dirt deposit and an extraction both
     * still succeed. The block-use half is {@link #blockUseDenialFreezesStackGestures}.
     *
     * <p>To check manually, use a protection rule or test listener that denies item use at the
     * stack while allowing block use: V-deposit dirt, then release V and extract it; both work.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void itemUseDenialLeavesStackGesturesWorking(GameTestHelper helper) {
        var storage = GameTestScaffold.placeStorage(helper, ORIGIN);
        storage.getItems().setStackInSlot(0, new ItemStack(Items.STONE, 3));
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        Consumer<PlayerInteractEvent.RightClickBlock> deny =
                event -> {
                    if (event.getEntity() == player
                            && event.getPos().equals(storage.getBlockPos())) {
                        event.setUseItem(Event.Result.DENY);
                    }
                };
        MinecraftForge.EVENT_BUS.addListener(deny);
        try (InteractionChecks.PlayerScope scope = new InteractionChecks.PlayerScope(player)) {
            var hit = InteractionChecks.aim(player, storage.getBlockPos(), 0.2, 0.2);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            ForgeGameTestSupport.click(player, InteractionHand.MAIN_HAND, hit);
            checkEquals(
                    1,
                    GameTestScaffold.count(storage.getItems(), Items.DIRT),
                    "Item denial blocked deposit");
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            ForgeGameTestSupport.click(player, InteractionHand.MAIN_HAND, hit);
            checkEquals(
                    Items.STONE,
                    player.getMainHandItem().getItem(),
                    "Item denial blocked extraction");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(deny);
        }
        helper.succeed();
    }

    /**
     * Native adjacent-deposit fixture: put stone west of an empty Singles block and aim through the
     * stone's east face toward its bottom cell. A Forge RightClickBlock listener denying block use
     * is applied first at the clicked stone and then at the Singles destination. Either veto
     * prevents deposit; allowing both deposits one stone.
     *
     * <p>To check manually, put an existing Singles block east of a stone block and aim through
     * that face while V-depositing stone. Configure protection to deny interaction at the clicked
     * block, then only at the destination, then at neither. Only the last configuration deposits;
     * loader-exact callback vetoes require a test listener if the protection mod lacks separate
     * rules.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void nativeAdjacentDepositConsultsBothPositions(GameTestHelper helper) {
        var singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        BlockPos destination = singles.getBlockPos();
        BlockPos clicked = destination.west();
        helper.getLevel().setBlock(clicked, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        for (BlockPos denied : new BlockPos[] {clicked, destination}) {
            Consumer<PlayerInteractEvent.RightClickBlock> veto =
                    event -> {
                        if (event.getEntity() == player && event.getPos().equals(denied))
                            event.setUseBlock(Event.Result.DENY);
                    };
            MinecraftForge.EVENT_BUS.addListener(veto);
            try (InteractionChecks.PlayerScope scope = new InteractionChecks.PlayerScope(player)) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
                ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
                var hit = InteractionChecks.aim(player, clicked, 0.125, 0.125);
                ForgeHooks.onRightClickBlock(
                        player,
                        InteractionHand.MAIN_HAND,
                        clicked,
                        new BlockHitResult(hit.getLocation(), Direction.EAST, clicked, false));
                checkEquals(
                        0,
                        GameTestScaffold.count(singles.getItems(), Items.STONE),
                        "Denied adjacent deposit");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(veto);
            }
        }
        try (InteractionChecks.PlayerScope scope = new InteractionChecks.PlayerScope(player)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            var hit = InteractionChecks.aim(player, clicked, 0.125, 0.125);
            ForgeHooks.onRightClickBlock(
                    player,
                    InteractionHand.MAIN_HAND,
                    clicked,
                    new BlockHitResult(hit.getLocation(), Direction.EAST, clicked, false));
            checkEquals(
                    1,
                    GameTestScaffold.count(singles.getItems(), Items.STONE),
                    "Allowed adjacent deposit");
        }
        helper.succeed();
    }

    /**
     * No in-game form: no player-visible stack placement replaces a block that holds data, so there
     * is nothing a player could see restored. The fixture changes the facing of a named chest
     * holding seven diamonds through the Forge edit authority's {@code place}, the capture step
     * behind {@code WorldEdits.placeChecked} (whose gate refuses an occupied position, so it cannot
     * drive this fixture), and its Forge {@code EntityPlaceEvent} listener alters the chest's
     * inventory and then cancels the placement. It guards that a vetoed placement restores the
     * prior block state and block-entity data (items and name) without notifying neighbors or
     * emitting a game event, leaves no snapshot state behind, and that an accepted placement
     * notifies neighbors only after the event permits it.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void placementDefersNeighborPublicationAndRestoresDeniedInventory(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(ORIGIN);
        // Changing the chest's facing captures its inventory without removing the container.
        var originalState = Blocks.CHEST.defaultBlockState();
        var placedState = originalState.setValue(ChestBlock.FACING, Direction.EAST);
        level.setBlock(target, originalState, Block.UPDATE_ALL);
        var chest = (ChestBlockEntity) level.getBlockEntity(target);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
        chest.setCustomName(Component.literal("Snapshot chest"));
        var before = chest.saveWithFullMetadata();
        int[] neighbors = {0};
        boolean[] deny = {true};
        Consumer<BlockEvent.NeighborNotifyEvent> observe =
                event -> {
                    if (event.getLevel() == level && event.getPos().equals(target)) neighbors[0]++;
                };
        Consumer<BlockEvent.EntityPlaceEvent> placement =
                event -> {
                    if (event.getLevel() == level && event.getPos().equals(target)) {
                        checkEquals(0, neighbors[0], "Placement published before acceptance");
                        if (deny[0]) {
                            ((ChestBlockEntity) level.getBlockEntity(target))
                                    .setItem(0, new ItemStack(Items.DIRT));
                            event.setCanceled(true);
                        }
                    }
                };
        MinecraftForge.EVENT_BUS.addListener(observe);
        MinecraftForge.EVENT_BUS.addListener(placement);
        try (var events = new WorldEditChecks.EventProbe(level, target)) {
            var player = FakePlayerFactory.getMinecraft(level);
            check(
                    !PlatformServices.editAuthority()
                            .place(player, level, target, placedState, Direction.DOWN),
                    "Denied placement accepted");
            events.expectNone();
            checkEquals(originalState, level.getBlockState(target), "Denied state not restored");
            var restored = level.getBlockEntity(target);
            checkEquals(
                    before, restored.saveWithFullMetadata(), "Denied inventory/name not restored");
            checkEquals(0, neighbors[0], "Denied placement notified neighbors");
            check(
                    !level.captureBlockSnapshots
                            && !level.restoringBlockSnapshots
                            && level.capturedBlockSnapshots.isEmpty(),
                    "Snapshot state leaked");
            deny[0] = false;
            check(
                    PlatformServices.editAuthority()
                            .place(player, level, target, placedState, Direction.DOWN),
                    "Accepted placement rejected");
            check(neighbors[0] > 0, "Accepted placement never notified neighbors");
            checkEquals(placedState, level.getBlockState(target), "Accepted placement state");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(observe);
            MinecraftForge.EVENT_BUS.unregister(placement);
        }
        helper.succeed();
    }

    /**
     * No in-game form: both halves need another mod acting from inside Forge's placement machinery.
     * It guards that {@code WorldEdits.placeChecked} refuses a nested call made from inside an
     * {@code EntityPlaceEvent} listener while the outer placement still succeeds, and that it
     * refuses to run while another party has {@code captureBlockSnapshots} set, leaving that
     * party's capture flag as it was.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void placementRefusesReentrancyAndForeignCapture(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(ORIGIN);
        var player = FakePlayerFactory.getMinecraft(level);
        Consumer<BlockEvent.EntityPlaceEvent> listener =
                event -> {
                    if (event.getLevel() == level && event.getPos().equals(target)) {
                        check(
                                !WorldEdits.placeChecked(
                                        player,
                                        level,
                                        target.east(),
                                        Blocks.STONE.defaultBlockState(),
                                        Direction.DOWN,
                                        Blocks.STONE
                                                .defaultBlockState()
                                                .getCollisionShape(
                                                        level,
                                                        target.east(),
                                                        CollisionContext.empty())),
                                "Reentrant placement accepted");
                    }
                };
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            check(
                    WorldEdits.placeChecked(
                            player,
                            level,
                            target,
                            Blocks.STONE.defaultBlockState(),
                            Direction.DOWN,
                            Blocks.STONE
                                    .defaultBlockState()
                                    .getCollisionShape(level, target, CollisionContext.empty())),
                    "Outer placement failed");
            level.captureBlockSnapshots = true;
            check(
                    !WorldEdits.placeChecked(
                            player,
                            level,
                            target.east(),
                            Blocks.STONE.defaultBlockState(),
                            Direction.DOWN,
                            Blocks.STONE
                                    .defaultBlockState()
                                    .getCollisionShape(
                                            level, target.east(), CollisionContext.empty())),
                    "Foreign capture accepted");
            check(level.captureBlockSnapshots, "Foreign capture changed");
        } finally {
            level.captureBlockSnapshots = false;
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }
}
