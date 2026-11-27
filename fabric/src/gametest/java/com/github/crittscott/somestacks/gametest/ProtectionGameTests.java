package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.AutomationActor;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.StackMode;

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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * The rules that keep a gesture from writing where it should not: build height, entity obstruction,
 * and waterlogging, plus growth answering to the same checks in both simulation and commit.
 */
public final class ProtectionGameTests implements FabricGameTest {
    /**
     * Loader entry point for {@link WorldEditChecks#placementGrowthAndCleanupNotify}; see that
     * method for the scenario and manual check.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void placementGrowthAndCleanupNotify(GameTestHelper helper) {
        WorldEditChecks.placementGrowthAndCleanupNotify(
                helper, FakePlayer.get(helper.getLevel()), FabricGameTestSupport::click);
    }

    /** Loader entry point for {@link WorldEditChecks#automationUsesSharedIdentity}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void automationUsesSharedIdentity(GameTestHelper helper) {
        WorldEditChecks.automationUsesSharedIdentity(helper);
    }

    /** Loader entry point for {@link WorldEditChecks#cleanupOfWaterloggedStackKeepsTheWater}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void cleanupOfWaterloggedStackKeepsTheWater(GameTestHelper helper) {
        WorldEditChecks.cleanupOfWaterloggedStackKeepsTheWater(helper);
    }

    /** Loader entry point for {@link WorldEditChecks#playerCausedCleanupCarriesThePlayer}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerCausedCleanupCarriesThePlayer(GameTestHelper helper) {
        WorldEditChecks.playerCausedCleanupCarriesThePlayer(
                helper, FakePlayer.get(helper.getLevel()));
    }

    /**
     * Loader entry point for {@link WorldEditChecks#breakingAStackChargesItsCleanupToTheBreaker}.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void breakingAStackChargesItsCleanupToTheBreaker(GameTestHelper helper) {
        WorldEditChecks.breakingAStackChargesItsCleanupToTheBreaker(
                helper, FakePlayer.get(helper.getLevel()));
    }

    /**
     * Loader entry point for {@link
     * WorldEditChecks#checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight}.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(GameTestHelper helper) {
        WorldEditChecks.checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(
                helper, FabricGameTestSupport.players(helper).apply(ItemStack.EMPTY));
    }

    /**
     * Loader entry point for {@link WorldEditChecks#checkedPlacementRejectsAnObstructingEntity}.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void checkedPlacementRejectsAnObstructingEntity(GameTestHelper helper) {
        WorldEditChecks.checkedPlacementRejectsAnObstructingEntity(
                helper, FabricGameTestSupport.players(helper).apply(ItemStack.EMPTY));
    }

    /**
     * Loader entry point for {@link
     * WorldEditChecks#creativeDepositFillsTheStackWithoutSpendingTheHand}.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void creativeDepositFillsTheStackWithoutSpendingTheHand(GameTestHelper helper) {
        WorldEditChecks.creativeDepositFillsTheStackWithoutSpendingTheHand(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /** Loader entry point for {@link WorldEditChecks#placementIntoWaterKeepsTheWater}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void placementIntoWaterKeepsTheWater(GameTestHelper helper) {
        WorldEditChecks.placementIntoWaterKeepsTheWater(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link
     * WorldEditChecks#storageGrowthAnswersToProtectionInSimulationAndCommit}.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void storageGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        WorldEditChecks.storageGrowthAnswersToProtectionInSimulationAndCommit(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * WorldEditChecks#singlesGrowthAnswersToProtectionInSimulationAndCommit}.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void singlesGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        WorldEditChecks.singlesGrowthAnswersToProtectionInSimulationAndCommit(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * WorldEditChecks#barGrowthAnswersToProtectionInSimulationAndCommit}.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void barGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        WorldEditChecks.barGrowthAnswersToProtectionInSimulationAndCommit(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * WorldEditChecks#storageGrowthRejectsAnObstructingEntityInSimulationAndCommit}.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void storageGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        WorldEditChecks.storageGrowthRejectsAnObstructingEntityInSimulationAndCommit(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * WorldEditChecks#singlesGrowthRejectsAnObstructingEntityInSimulationAndCommit}.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void singlesGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        WorldEditChecks.singlesGrowthRejectsAnObstructingEntityInSimulationAndCommit(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * WorldEditChecks#barGrowthRejectsAnObstructingEntityInSimulationAndCommit}.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void barGrowthRejectsAnObstructingEntityInSimulationAndCommit(GameTestHelper helper) {
        WorldEditChecks.barGrowthRejectsAnObstructingEntityInSimulationAndCommit(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * WorldEditChecks#completedShapesDistinguishOccupiedCellsFromEmptySpace}.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void completedShapesDistinguishOccupiedCellsFromEmptySpace(GameTestHelper helper) {
        WorldEditChecks.completedShapesDistinguishOccupiedCellsFromEmptySpace(helper);
    }

    private static ServerPlayer deniedPlayer;
    private static BlockPos deniedPosition;
    private static BlockPos automatedRemovalPosition;
    private static ServerLevel removalLevel;
    private static boolean denyRemoval;
    private static final List<RemovalNotice> removalNotices = new ArrayList<>();

    private record RemovalNotice(
            String phase, Player actor, BlockState state, BlockEntity entity) {}

    static {
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                player == deniedPlayer && hit.getBlockPos().equals(deniedPosition)
                        ? InteractionResult.FAIL
                        : InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (level != removalLevel || !pos.equals(automatedRemovalPosition)) return true;
            removalNotices.add(new RemovalNotice("before", player, state, blockEntity));
            return !denyRemoval;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level == removalLevel && pos.equals(automatedRemovalPosition)) {
                removalNotices.add(new RemovalNotice("after", player, state, blockEntity));
                check(level.getBlockEntity(pos) != blockEntity, "AFTER ran before entity removal");
            }
        });
        PlayerBlockBreakEvents.CANCELED.register((level, player, pos, state, blockEntity) -> {
            if (level == removalLevel && pos.equals(automatedRemovalPosition)) {
                removalNotices.add(new RemovalNotice("canceled", player, state, blockEntity));
                check(
                        level.getBlockEntity(pos) == blockEntity,
                        "CANCELED lost the original entity");
            }
        });
    }

    /**
     * Loader entry point for {@link InteractionChecks#blockUseDenialFreezesStackGestures}; see that
     * method for the scenario and manual check. The denial is this class's default-phase {@code
     * UseBlockCallback} veto, which returns {@code FAIL} for the fixture player at the gesture's
     * block, so it runs before the mod's own late-phase callback, as a protection mod's would.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void blockUseDenialFreezesStackGestures(GameTestHelper helper) {
        try {
            InteractionChecks.blockUseDenialFreezesStackGestures(
                    helper,
                    FabricGameTestSupport.players(helper),
                    FabricGameTestSupport::click,
                    (player, pos) -> {
                        deniedPlayer = player;
                        deniedPosition = pos;
                    },
                    () -> {
                        deniedPlayer = null;
                        deniedPosition = null;
                    });
        } finally {
            deniedPlayer = null;
            deniedPosition = null;
        }
    }

    /**
     * To reproduce: deny automated cleanup through a protection mod, then allow it. The empty stack
     * stays on veto; Fabric listeners receive BEFORE/CANCELED or BEFORE/AFTER with the [SomeStacks]
     * fake player and original block entity. A nearby sculk sensor receives only the accepted
     * destruction vibration.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void automatedRemovalUsesNativeActorAndHonorsVeto(GameTestHelper helper) {
        StorageStackBE stack = GameTestScaffold.placeStorage(helper, ORIGIN);
        BlockPos target = stack.getBlockPos();
        BlockState state = helper.getLevel().getBlockState(target);
        ServerPlayer actor = FakePlayer.get(helper.getLevel(), AutomationActor.PROFILE);
        automatedRemovalPosition = target;
        removalLevel = helper.getLevel();
        denyRemoval = true;
        removalNotices.clear();
        try (var events = new WorldEditChecks.EventProbe(helper.getLevel(), target)) {
            check(
                    WorldEdits.automationActor(helper.getLevel()) == actor,
                    "Automation does not use Fabric's native actor with the shared profile");
            WorldEditChecks.outsideTheBorder(
                    helper,
                    () -> {
                        check(
                                !WorldEdits.removeChecked(null, helper.getLevel(), target),
                                "Protected removal was accepted");
                        checkEquals(
                                0,
                                removalNotices.size(),
                                "Protected removal invoked break callbacks");
                        events.expectNone();
                    });
            check(
                    !WorldEdits.removeChecked(null, helper.getLevel(), target),
                    "Vetoed removal was accepted");
            expectRemovalNotices(actor, state, stack, "before", "canceled");
            events.expectNone();
            helper.assertBlockPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN);
            denyRemoval = false;
            check(
                    WorldEdits.removeChecked(null, helper.getLevel(), target),
                    "Allowed removal was rejected");
            expectRemovalNotices(actor, state, stack, "before", "after");
            events.expectOne(GameEvent.BLOCK_DESTROY, actor, state);
            helper.assertBlockNotPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN);
            check(
                    !WorldEdits.removeChecked(null, helper.getLevel(), target),
                    "Unchanged air reported successful removal");
            expectRemovalNotices(actor, Blocks.AIR.defaultBlockState(), null, "before");
            events.expectNone();
        } finally {
            automatedRemovalPosition = null;
            removalLevel = null;
            denyRemoval = false;
            removalNotices.clear();
        }
        helper.succeed();
    }

    /**
     * To reproduce: with a protection or logging mod listening to Fabric block breaks, extract the
     * last bar from a Bar Stack. The removal reaches BEFORE and AFTER as the extracting player,
     * with the original block state and block entity.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerCleanupReachesBreakCallbacksAsThePlayer(GameTestHelper helper) {
        BarStackBE bars = GameTestScaffold.placeBar(helper, ORIGIN);
        bars.getItems().insertItem(0, new ItemStack(GameTestScaffold.firstBarItem()), false);
        BlockPos target = bars.getBlockPos();
        BlockState state = helper.getLevel().getBlockState(target);
        ServerPlayer player = FakePlayer.get(helper.getLevel());
        automatedRemovalPosition = target;
        removalLevel = helper.getLevel();
        removalNotices.clear();
        try {
            checkEquals(1, bars.extractAt(0, player).getCount(), "Bar extraction");
            expectRemovalNotices(player, state, bars, "before", "after");
            helper.assertBlockNotPresent(CommonRegistry.BAR_STACK_BLOCK.get(), ORIGIN);
        } finally {
            automatedRemovalPosition = null;
            removalLevel = null;
            removalNotices.clear();
        }
        helper.succeed();
    }

    private static void expectRemovalNotices(
            Player actor, BlockState state, BlockEntity entity, String... phases) {
        checkEquals(phases.length, removalNotices.size(), "Removal callback count");
        for (int i = 0; i < phases.length; i++) {
            RemovalNotice notice = removalNotices.get(i);
            checkEquals(phases[i], notice.phase(), "Removal callback order");
            check(notice.actor() == actor, "Removal callback actor changed");
            checkEquals(state, notice.state(), "Removal callback lost original state");
            check(notice.entity() == entity, "Removal callback lost original block entity");
        }
        removalNotices.clear();
    }

    /**
     * Native adjacent-deposit fixture: put stone west of an empty Singles block and aim through the
     * stone's east face toward its bottom cell. A Fabric UseBlockCallback veto is applied first at
     * the clicked stone and then at the Singles destination. Either veto prevents deposit; allowing
     * both deposits one stone.
     *
     * <p>To check manually, put an existing Singles block east of a stone block and aim through
     * that face while V-depositing stone. Configure protection to deny interaction at the clicked
     * block, then only at the destination, then at neither. Only the last configuration deposits;
     * loader-exact callback vetoes require a test listener if the protection mod lacks separate
     * rules.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void nativeAdjacentDepositConsultsBothPositions(GameTestHelper helper) {
        var singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        BlockPos destination = singles.getBlockPos();
        BlockPos clicked = destination.west();
        helper.getLevel().setBlock(clicked, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        var player = FakePlayer.get(helper.getLevel());
        try {
            for (BlockPos denied : new BlockPos[] {clicked, destination}) {
                deniedPlayer = player;
                deniedPosition = denied;
                try (InteractionChecks.PlayerScope scope =
                        new InteractionChecks.PlayerScope(player)) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
                    ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
                    var hit = InteractionChecks.aim(player, clicked, 0.125, 0.125);
                    UseBlockCallback.EVENT
                            .invoker()
                            .interact(
                                    player,
                                    player.level(),
                                    InteractionHand.MAIN_HAND,
                                    new BlockHitResult(
                                            hit.getLocation(), Direction.EAST, clicked, false));
                    checkEquals(
                            0,
                            GameTestScaffold.count(singles.getItems(), Items.STONE),
                            "Denied adjacent deposit");
                }
            }
        } finally {
            deniedPlayer = null;
            deniedPosition = null;
        }
        try (InteractionChecks.PlayerScope scope = new InteractionChecks.PlayerScope(player)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            var hit = InteractionChecks.aim(player, clicked, 0.125, 0.125);
            UseBlockCallback.EVENT
                    .invoker()
                    .interact(
                            player,
                            player.level(),
                            InteractionHand.MAIN_HAND,
                            new BlockHitResult(hit.getLocation(), Direction.EAST, clicked, false));
            checkEquals(
                    1,
                    GameTestScaffold.count(singles.getItems(), Items.STONE),
                    "Allowed adjacent deposit");
        }
        helper.succeed();
    }
}
