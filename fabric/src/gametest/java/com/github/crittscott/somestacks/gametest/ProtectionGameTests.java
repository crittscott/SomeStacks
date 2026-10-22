package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.StorageStackBlock;
import com.github.crittscott.somestacks.server.AutomationActor;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.StackMode;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.ArrayList;
import java.util.List;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/**
 * The rules that keep a gesture from writing where it should not: build height, entity obstruction,
 * and waterlogging, plus growth answering to the same checks in both simulation and commit.
 *
 */
public final class ProtectionGameTests implements FabricGameTest {
    /** To reproduce: place Storage near a sculk sensor, grow it with a full-pile deposit, then empty the top; each structural edit emits a vibration. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void placementGrowthAndCleanupNotify(GameTestHelper helper) {
        WorldEditChecks.placementGrowthAndCleanupNotify(helper, FakePlayer.get(helper.getLevel()),
                player -> nativeGesture(player, helper.absolutePos(ORIGIN.below()), Direction.UP));
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
        WorldEditChecks.playerCausedCleanupCarriesThePlayer(helper, FakePlayer.get(helper.getLevel()));
    }

    /**
     * Internal checked-placement test: place stone at an in-bounds position, then try a position
     * one below the minimum build height. Only the in-bounds call succeeds. The out-of-world call
     * has no direct player reproduction.
     *
     * <p>Nearest manual check: select Storage and V-place an admissible item against a floor in
     * bounds, then try to grow a stack beyond the world's upper build limit. No out-of-bounds block
     * should appear.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayer.get(helper.getLevel());
        BlockPos valid = helper.absolutePos(ORIGIN);
        BlockPos invalid = new BlockPos(
                valid.getX(), level.getMinBuildHeight() - 1, valid.getZ());

        check(WorldEdits.placeChecked(
                player, level, valid, Blocks.STONE.defaultBlockState(), Direction.DOWN,
                Blocks.STONE.defaultBlockState().getCollisionShape(level, valid, CollisionContext.empty())),
                "Valid checked placement was rejected");
        check(level.getBlockState(valid).is(Blocks.STONE),
                "Valid checked placement did not change the world");
        check(!WorldEdits.placeChecked(
                player, level, invalid, Blocks.STONE.defaultBlockState(), Direction.DOWN,
                Blocks.STONE.defaultBlockState().getCollisionShape(level, invalid, CollisionContext.empty())),
                "Out-of-height checked placement was accepted");
        helper.succeed();
    }

    /**
     * The fixture puts a cow inside the proposed full stone shape and calls checked placement;
     * placement fails.
     *
     * <p>Nearest manual check: confine a cow in the destination above a floor, select Storage, and
     * V-right-click the floor holding dirt. The obstructed Storage block must not appear.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void checkedPlacementRejectsAnObstructingEntity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayer.get(helper.getLevel());
        BlockPos target = helper.absolutePos(ORIGIN);
        putCowIn(helper, ORIGIN);

        try (var events = new WorldEditChecks.EventProbe(level, target)) {
            check(!WorldEdits.placeChecked(
                    player, level, target, Blocks.STONE.defaultBlockState(), Direction.DOWN,
                    Blocks.STONE.defaultBlockState().getCollisionShape(level, target, CollisionContext.empty())),
                    "Entity-obstructed checked placement was accepted");
            events.expectNone();
        }
        helper.assertBlockNotPresent(Blocks.STONE, ORIGIN);
        helper.succeed();
    }

    /**
     * To reproduce: enter Creative, hold 64 dirt, and V-right-click an existing Storage block. All
     * 64 dirt enter the pile and the hand still holds 64. The test exercises the loader's native
     * block-use callback.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void creativeDepositFillsTheStackWithoutSpendingTheHand(GameTestHelper helper) {
        ServerPlayer player = FakePlayer.get(helper.getLevel());
        BlockPos target = helper.absolutePos(ORIGIN);
        GameTestScaffold.placeStorage(helper, ORIGIN);

        player.getAbilities().instabuild = true;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 64));
        nativeGesture(player, target, Direction.WEST);
        checkEquals(64, player.getMainHandItem().getCount(),
                "A creative deposit spent the held stack");

        checkEquals(64, GameTestScaffold.heldAt(helper, target, Items.DIRT),
                "Creative deposit did not reach the stack");
        helper.succeed();
    }

    /**
     * To reproduce: place source water above a solid floor, select Storage, and V-right-click the
     * floor with dirt. The new Storage block is waterlogged and water remains at that position. The
     * test exercises native placement and inspects the fluid state.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void placementIntoWaterKeepsTheWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayer.get(helper.getLevel());
        BlockPos target = helper.absolutePos(ORIGIN);
        level.setBlock(target, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 64));

        nativeGesture(player, target.below(), Direction.UP);

        helper.assertBlockPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN);
        check(level.getBlockState(target).getValue(StorageStackBlock.WATERLOGGED),
                "A stack placed into water was not waterlogged");
        check(level.getFluidState(target).getType() == Fluids.WATER,
                "A stack placed into water swallowed the water");
        helper.succeed();
    }

    // Growth under protection
    //
    // A storage insertion aimed at a slot past what the run holds is the one that grows it, and it
    // checks the position above the run before promising the caller anything, so a simulation and the
    // commit that follows agree about a position growth cannot have. The three tests below stage that
    // with the world border. Spawn protection, the other half of the same predicate, cannot be staged
    // here: it is implemented on DedicatedServer, and the server running these tests is not one.

    /**
     * World-border fixture: fill one Storage block, first confirm a free headroom offer has
     * capacity, then move the border to exclude the target and try simulation and commit. Both
     * refuse the entire four-item offer without growth or input mutation.
     *
     * <p>To reproduce, allow height above one, fill a Storage pile, and use /worldborder center and
     * /worldborder set to exclude its prospective growth position. Offer admissible items to first
     * headroom through slot-addressing automation. No upper block appears. Restore the original
     * border after the check; dry-run results require simulation/abort support.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void storageGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
        }
        SlottedStorage<ItemVariant> capability = FabricGameTestSupport.capability(storage);
        ItemStack offered = new ItemStack(Items.STONE, 4);

        int headroom = StorageStackBE.SLOTS;
        check(FabricGameTestSupport.insertAt(capability, headroom, offered, true).isEmpty(),
                "A full pile with free headroom did not credit growth");

        outsideTheBorder(helper, () -> {
            checkEquals(4, FabricGameTestSupport.insertAt(capability, headroom, offered, true).getCount(),
                    "Simulated remainder");
            checkEquals(4, FabricGameTestSupport.insertAt(capability, headroom, offered, false).getCount(),
                    "Committed remainder");
        });

        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    /**
     * World-border fixture: fill one Singles block, first confirm a free headroom offer has
     * capacity, then move the border to exclude the target and try simulation and commit. Both
     * refuse the entire four-item offer without growth or input mutation.
     *
     * <p>To reproduce, allow height above one, fill a Singles column, and use /worldborder center
     * and /worldborder set to exclude its prospective growth position. Offer admissible items to
     * first headroom through slot-addressing automation. No upper block appears. Restore the
     * original border after the check; dry-run results require simulation/abort support.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void singlesGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            singles.getItems().insertItem(slot, new ItemStack(Items.STONE, 1), false);
        }
        SlottedStorage<ItemVariant> capability = FabricGameTestSupport.capability(singles);
        ItemStack offered = new ItemStack(Items.STONE, 4);

        // A cell takes one item, so a credited growth leaves three of the four behind.
        int headroom = SinglesStackBE.SLOTS;
        checkEquals(3, FabricGameTestSupport.insertAt(capability, headroom, offered, true).getCount(),
                "A full column with free headroom did not credit growth");

        outsideTheBorder(helper, () -> {
            checkEquals(4, FabricGameTestSupport.insertAt(capability, headroom, offered, true).getCount(),
                    "Simulated remainder");
            checkEquals(4, FabricGameTestSupport.insertAt(capability, headroom, offered, false).getCount(),
                    "Committed remainder");
        });

        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.SINGLES_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    /**
     * World-border fixture: fill one Bar block, first confirm a free headroom offer has capacity,
     * then move the border to exclude the target and try simulation and commit. Both refuse the
     * entire four-item offer without growth or input mutation.
     *
     * <p>To reproduce, allow height above one, fill a Bar column, and use /worldborder center and
     * /worldborder set to exclude its prospective growth position. Offer admissible items to first
     * headroom through slot-addressing automation. No upper block appears. Restore the original
     * border after the check; dry-run results require simulation/abort support.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void barGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        BarStackBE bars = GameTestScaffold.placeBar(helper, ORIGIN);
        Item bar = GameTestScaffold.firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            bars.getItems().insertItem(slot, new ItemStack(bar, 1), false);
        }
        SlottedStorage<ItemVariant> capability = FabricGameTestSupport.capability(bars);
        ItemStack offered = new ItemStack(bar, 4);

        // A position takes one bar, so a credited growth leaves three of the four behind.
        int headroom = BarStackBE.SLOTS;
        checkEquals(3, FabricGameTestSupport.insertAt(capability, headroom, offered, true).getCount(),
                "A full column with free headroom did not credit growth");

        outsideTheBorder(helper, () -> {
            checkEquals(4, FabricGameTestSupport.insertAt(capability, headroom, offered, true).getCount(),
                    "Simulated remainder");
            checkEquals(4, FabricGameTestSupport.insertAt(capability, headroom, offered, false).getCount(),
                    "Committed remainder");
        });

        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.BAR_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    // Growth through entities

    /**
     * To reproduce: allow height above one, fill one Storage block, and confine a cow in the
     * prospective upper block's first occupied position. Offer four admissible items to first
     * headroom through automation. Commit refuses them all and creates no block. The automated
     * check also asserts dry-run refusal and unchanged caller input; simulation/abort needs an
     * automation probe.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void storageGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
        }
        SlottedStorage<ItemVariant> capability = FabricGameTestSupport.capability(storage);
        ItemStack offered = new ItemStack(Items.STONE, 4);
        putCowIn(helper, ORIGIN.above());

        int headroom = StorageStackBE.SLOTS;
        checkEquals(4, FabricGameTestSupport.insertAt(capability, headroom, offered, true).getCount(),
                "Simulated remainder");
        checkEquals(4, FabricGameTestSupport.insertAt(capability, headroom, offered, false).getCount(),
                "Committed remainder");
        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    /**
     * To reproduce: allow height above one, fill one Singles block, and confine a cow in the
     * prospective upper block's first occupied position. Offer four admissible items to first
     * headroom through automation. Commit refuses them all and creates no block. The automated
     * check also asserts dry-run refusal and unchanged caller input; simulation/abort needs an
     * automation probe.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void singlesGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            singles.getItems().insertItem(slot, new ItemStack(Items.STONE), false);
        }
        SlottedStorage<ItemVariant> capability = FabricGameTestSupport.capability(singles);
        ItemStack offered = new ItemStack(Items.STONE, 4);
        putCowIn(helper, ORIGIN.above());

        int headroom = SinglesStackBE.SLOTS;
        checkEquals(4, FabricGameTestSupport.insertAt(capability, headroom, offered, true).getCount(),
                "Simulated remainder");
        checkEquals(4, FabricGameTestSupport.insertAt(capability, headroom, offered, false).getCount(),
                "Committed remainder");
        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.SINGLES_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    /**
     * To reproduce: allow height above one, fill one Bar block, and confine a cow in the
     * prospective upper block's first occupied position. Offer four admissible items to first
     * headroom through automation. Commit refuses them all and creates no block. The automated
     * check also asserts dry-run refusal and unchanged caller input; simulation/abort needs an
     * automation probe.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void barGrowthRejectsAnObstructingEntityInSimulationAndCommit(GameTestHelper helper) {
        BarStackBE bars = GameTestScaffold.placeBar(helper, ORIGIN);
        Item bar = GameTestScaffold.firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            bars.getItems().insertItem(slot, new ItemStack(bar), false);
        }
        SlottedStorage<ItemVariant> capability = FabricGameTestSupport.capability(bars);
        ItemStack offered = new ItemStack(bar, 4);
        putCowIn(helper, ORIGIN.above());

        int headroom = BarStackBE.SLOTS;
        checkEquals(4, FabricGameTestSupport.insertAt(capability, headroom, offered, true).getCount(),
                "Simulated remainder");
        checkEquals(4, FabricGameTestSupport.insertAt(capability, headroom, offered, false).getCount(),
                "Committed remainder");
        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.BAR_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    /**
     * Puts a living placement-blocking entity across the bottom north-west cell and bar position
     * of {@code relative}.
     */
    private static void putCowIn(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(relative);
        Cow cow = EntityType.COW.create(level);
        check(cow != null, "Could not create obstruction cow");
        check(cow.blocksBuilding, "Cow does not block building");
        cow.moveTo(
                target.getX() + 0.5,
                target.getY(),
                target.getZ() + 0.5,
                0.0F,
                0.0F);
        check(level.addFreshEntity(cow), "Could not add obstruction cow");
    }

    /**
     * Runs {@code action} with the world border moved off the test structure, and restores it
     * before returning. The border is level-wide state, but the move and the restore both happen
     * inside this one synchronous call, so no other test observes it moved.
     */
    private static void outsideTheBorder(GameTestHelper helper, Runnable action) {
        WorldBorder border = helper.getLevel().getWorldBorder();
        BlockPos above = helper.absolutePos(ORIGIN.above());
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();
        double size = border.getSize();
        try {
            border.setCenter(above.getX() + 1000.0, above.getZ());
            border.setSize(16.0);
            check(!border.isWithinBounds(above),
                    "Test setup left the position inside the world border");
            action.run();
        } finally {
            border.setCenter(centerX, centerZ);
            border.setSize(size);
        }
    }
    private static ServerPlayer deniedPlayer;
    private static BlockPos deniedPosition;
    private static BlockPos automatedRemovalPosition;
    private static ServerLevel removalLevel;
    private static boolean denyRemoval;
    private static final List<RemovalNotice> removalNotices = new ArrayList<>();
    private record RemovalNotice(String phase, Player actor, BlockState state, BlockEntity entity) {}
    static {
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                player == deniedPlayer && hit.getBlockPos().equals(deniedPosition)
                        ? InteractionResult.FAIL : InteractionResult.PASS);
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
                check(level.getBlockEntity(pos) == blockEntity, "CANCELED lost the original entity");
            }
        });
    }

    /** To reproduce: deny automated cleanup through a protection mod, then allow it. The empty stack stays on veto; Fabric listeners receive BEFORE/CANCELED or BEFORE/AFTER with the [SomeStacks] fake player and original block entity. A nearby sculk sensor receives only the accepted destruction vibration. */
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
            check(WorldEdits.automationActor(helper.getLevel()) == actor,
                    "Automation does not use Fabric's native actor with the shared profile");
            outsideTheBorder(helper, () -> {
                check(!WorldEdits.removeChecked(null, helper.getLevel(), target), "Protected removal was accepted");
                checkEquals(0, removalNotices.size(), "Protected removal invoked break callbacks");
                events.expectNone();
            });
            check(!WorldEdits.removeChecked(null, helper.getLevel(), target), "Vetoed removal was accepted");
            expectRemovalNotices(actor, state, stack, "before", "canceled");
            events.expectNone();
            helper.assertBlockPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN);
            denyRemoval = false;
            check(WorldEdits.removeChecked(null, helper.getLevel(), target), "Allowed removal was rejected");
            expectRemovalNotices(actor, state, stack, "before", "after");
            events.expectOne(GameEvent.BLOCK_DESTROY, actor, state);
            helper.assertBlockNotPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN);
            check(!WorldEdits.removeChecked(null, helper.getLevel(), target), "Unchanged air reported successful removal");
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
     * last bar from a Bar Stack. The removal reaches BEFORE and AFTER as the extracting player, with
     * the original block state and block entity.
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

    private static void expectRemovalNotices(Player actor, BlockState state, BlockEntity entity,
                                             String... phases) {
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
                deniedPlayer = player; deniedPosition = denied;
                try (InteractionChecks.PlayerScope scope = new InteractionChecks.PlayerScope(player)) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
                    ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
                    var hit = InteractionChecks.aim(player, clicked, 0.125, 0.125);
                    UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND,
                            new BlockHitResult(hit.getLocation(), Direction.EAST, clicked, false));
                    checkEquals(0, GameTestScaffold.count(singles.getItems(), Items.STONE), "Denied adjacent deposit");
                }
            }
        } finally {
            deniedPlayer = null; deniedPosition = null;
        }
        try (InteractionChecks.PlayerScope scope = new InteractionChecks.PlayerScope(player)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            var hit = InteractionChecks.aim(player, clicked, 0.125, 0.125);
            UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(hit.getLocation(), Direction.EAST, clicked, false));
            checkEquals(1, GameTestScaffold.count(singles.getItems(), Items.STONE), "Allowed adjacent deposit");
        }
        helper.succeed();
    }

    /**
     * Internal obstruction-shape check: a cow overlaps one edge of the target position. Full and
     * occupied-edge shapes are obstructed; empty shapes and distant Singles/Bar positions are
     * clear. No block placement or deposit is performed.
     *
     * <p>Nearest manual check: hold a cow at one edge of a destination and attempt first placement
     * of Singles or Bar into occupied-edge and clear positions. Only shapes overlapping the cow
     * should prevent placement.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void completedShapesDistinguishOccupiedCellsFromEmptySpace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(ORIGIN);
        Cow cow = EntityType.COW.create(level);
        check(cow != null, "Could not create obstruction cow");
        cow.moveTo(target.getX() + 1.25, target.getY(), target.getZ() + 0.5, 0.0F, 0.0F);
        check(level.addFreshEntity(cow), "Could not add obstruction cow");
        try {
            check(!WorldEdits.isUnobstructed(level, target, Shapes.block()), "Full block missed obstruction");
            check(WorldEdits.isUnobstructed(level, target, Shapes.empty()), "Empty shape was obstructed");
            check(WorldEdits.isUnobstructed(level, target, SinglesCubeIdx.GRID.localShape(0, 0)),
                    "Free Singles cell was obstructed by another cell's occupant");
            check(!WorldEdits.isUnobstructed(level, target,
                            SinglesCubeIdx.GRID.localShape(SinglesCubeIdx.LAYER_SIZE - 1, 0)),
                    "Occupied Singles shape missed obstruction");
            check(WorldEdits.isUnobstructed(level, target, BarCubeIdx.shapeFor(0)),
                    "Free Bar position was obstructed by another position's occupant");
            check(!WorldEdits.isUnobstructed(level, target, BarCubeIdx.shapeFor(BarCubeIdx.LAYER_SIZE - 1)),
                    "Occupied Bar shape missed obstruction");
        } finally {
            cow.discard();
        }
        helper.succeed();
    }

    private static void nativeGesture(ServerPlayer player, BlockPos clicked, Direction face) {
        boolean creative = player.getAbilities().instabuild;
        ItemStack result;
        try (InteractionChecks.PlayerScope scope = new InteractionChecks.PlayerScope(player)) {
            player.getAbilities().instabuild = creative;
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            var hit = InteractionChecks.aim(player, clicked, 0.5, 0.5);
            // Vanilla's server block use invokes Fabric's callback and then calls the block.
            player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(),
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(hit.getLocation(), face, clicked, false));
            result = player.getMainHandItem().copy();
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, result);
    }
}
