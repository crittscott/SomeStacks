package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.*;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.StorageStackBlock;
import com.github.crittscott.somestacks.server.AutomationActor;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.StackMode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * World edits and their notifications, observed through the world's native game-event dispatcher,
 * and the rules that keep a gesture or automation from writing where it should not: build height,
 * entity obstruction, the world border, and waterlogging. Automation scenarios take the loader's
 * {@link Automation} view.
 */
public final class WorldEditChecks {
    private WorldEditChecks() {}

    /**
     * To reproduce: have a claim or logging mod report who grows a Storage pile through a hopper.
     * Both loaders report the same [SomeStacks] player.
     */
    public static void automationUsesSharedIdentity(GameTestHelper helper) {
        var actual = WorldEdits.automationActor(helper.getLevel()).getGameProfile();
        checkEquals(AutomationActor.PROFILE.getId(), actual.getId(), "Automation UUID");
        checkEquals(AutomationActor.PROFILE.getName(), actual.getName(), "Automation name");
        helper.succeed();
    }

    /**
     * To reproduce: V-place a temporary Storage Stack into a water source and extract its only
     * item. Settlement removes the empty block and the water source remains.
     */
    public static void cleanupOfWaterloggedStackKeepsTheWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = placeStorage(helper, ORIGIN).getBlockPos();
        level.setBlock(
                pos,
                level.getBlockState(pos).setValue(StorageStackBlock.WATERLOGGED, true),
                Block.UPDATE_ALL);
        StorageStackBE storage = (StorageStackBE) level.getBlockEntity(pos);
        storage.getItems().insertItem(0, new ItemStack(Items.STONE), false);
        StoragePile pile = storage.pile();
        check(pile != null, "Pile did not resolve");
        checkEquals(1, pile.extract(0, 1, false).getCount(), "Extraction count");
        pile.settle();
        helper.assertBlockNotPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN);
        check(
                level.getFluidState(pos).getType() == Fluids.WATER
                        && level.getFluidState(pos).isSource(),
                "Cleanup removed the water source");
        helper.succeed();
    }

    /**
     * To reproduce: in a claim that admits a player but denies [SomeStacks], extract the last item
     * from a temporary Storage, Singles, and Bar Stack, and toggle an empty permanent Storage pile
     * back to temporary. Each empty block is removed on the player's behalf, so the claim allows it
     * and logging mods record the player. Extracting one of two Storage items by hand and the other
     * by automation before settlement attributes the cleanup to [SomeStacks] instead, so that claim
     * keeps the empty block.
     */
    public static void playerCausedCleanupCarriesThePlayer(
            GameTestHelper helper, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        check(!isAutomationActor(player), "Fixture player is the automation actor");

        StorageStackBE storage = placeStorage(helper, ORIGIN);
        storage.getItems().insertItem(0, new ItemStack(Items.STONE), false);
        expectRemovalBy(
                level,
                storage.getBlockPos(),
                player,
                () -> {
                    checkEquals(
                            1,
                            storage.extractAt(0, 1, ItemStack.EMPTY, player).getCount(),
                            "Storage extraction");
                    storage.pile().settle();
                });

        StorageStackBE permanent = placeStorage(helper, ORIGIN);
        permanent.pile().setPermanent(true, null);
        permanent.pile().settle();
        helper.assertBlockPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN);
        expectRemovalBy(
                level,
                permanent.getBlockPos(),
                player,
                () -> {
                    permanent.pile().setPermanent(false, player);
                    permanent.pile().settle();
                });

        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        singles.getItems().insertItem(0, new ItemStack(Items.STONE), false);
        expectRemovalBy(
                level,
                singles.getBlockPos(),
                player,
                () ->
                        checkEquals(
                                1, singles.extractAt(0, player).getCount(), "Singles extraction"));

        BarStackBE bars = placeBar(helper, ORIGIN);
        bars.getItems().insertItem(0, new ItemStack(firstBarItem()), false);
        expectRemovalBy(
                level,
                bars.getBlockPos(),
                player,
                () -> checkEquals(1, bars.extractAt(0, player).getCount(), "Bar extraction"));

        StorageStackBE mixed = placeStorage(helper, ORIGIN);
        mixed.getItems().insertItem(0, new ItemStack(Items.STONE, 2), false);
        expectRemovalBy(
                level,
                mixed.getBlockPos(),
                WorldEdits.automationActor(level),
                () -> {
                    checkEquals(
                            1,
                            mixed.extractAt(0, 1, ItemStack.EMPTY, player).getCount(),
                            "Player extraction");
                    StoragePile pile = mixed.pile();
                    checkEquals(1, pile.extract(0, 1, false).getCount(), "Automation extraction");
                    pile.settle();
                });
        helper.succeed();
    }

    /**
     * To reproduce: in a claim that admits a player but denies [SomeStacks], stack two Bar Stacks
     * with one bar per layer in the lower block holding up one bar in the upper block, and break
     * the lower block by hand. The upper bar falls and its emptied block is removed on the player's
     * behalf, so the claim allows it and logging mods record the player. Then build a temporary
     * Storage pile three blocks high with one stack in each block and break its base: the two
     * remaining stacks settle into the lower block and the emptied top is removed, again under the
     * player. Removing the lower block any other way, such as /setblock or an explosion, charges
     * the same cleanup to [SomeStacks].
     */
    public static void breakingAStackChargesItsCleanupToTheBreaker(
            GameTestHelper helper, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        check(!isAutomationActor(player), "Fixture player is the automation actor");
        BlockPos lowerPos = helper.absolutePos(ORIGIN);

        breakBarColumn(
                helper, player, () -> check(player.gameMode.destroyBlock(lowerPos), "Bar break"));
        breakBarColumn(
                helper,
                WorldEdits.automationActor(level),
                () -> check(level.destroyBlock(lowerPos, false), "Bar removal"));

        breakStoragePile(
                helper,
                player,
                () -> check(player.gameMode.destroyBlock(lowerPos), "Storage break"));
        breakStoragePile(
                helper,
                WorldEdits.automationActor(level),
                () -> check(level.destroyBlock(lowerPos, false), "Storage removal"));
        helper.succeed();
    }

    /**
     * Builds a two-block Bar column whose only upper bar rests on a one-bar-per-layer chain below,
     * removes the lower block with {@code removal}, and expects the emptied upper block removed by
     * {@code actor}.
     */
    private static void breakBarColumn(
            GameTestHelper helper, ServerPlayer actor, Runnable removal) {
        BarStackBE lower = placeBar(helper, ORIGIN);
        BarStackBE upper = placeBar(helper, ORIGIN.above());
        Item barItem = firstBarItem();
        int slot = 0;
        seedSlot(lower.getItems(), slot, new ItemStack(barItem));
        for (int layer = 1; layer < 8; layer++) {
            slot = BarColumnChecks.firstSupportedBy(slot, layer * 8, (layer + 1) * 8);
            seedSlot(lower.getItems(), slot, new ItemStack(barItem));
        }
        seedSlot(
                upper.getItems(),
                BarColumnChecks.firstSeamSupportedBy(slot - 56),
                new ItemStack(barItem));
        expectRemovalBy(helper.getLevel(), upper.getBlockPos(), actor, removal);
    }

    /**
     * Builds a temporary three-block Storage pile holding one stone per block, removes the base
     * with {@code removal}, settles what remains, and expects its emptied top removed by {@code
     * actor}.
     */
    private static void breakStoragePile(
            GameTestHelper helper, ServerPlayer actor, Runnable removal) {
        placeStorage(helper, ORIGIN).getItems().insertItem(0, new ItemStack(Items.STONE), false);
        StorageStackBE middle = placeStorage(helper, ORIGIN.above());
        middle.getItems().insertItem(0, new ItemStack(Items.STONE), false);
        StorageStackBE top = placeStorage(helper, ORIGIN.above(2));
        top.getItems().insertItem(0, new ItemStack(Items.STONE), false);
        expectRemovalBy(
                helper.getLevel(),
                top.getBlockPos(),
                actor,
                () -> {
                    removal.run();
                    StoragePile rest = middle.pile();
                    check(rest != null, "Remaining pile did not resolve");
                    rest.settle();
                });
        checkEquals(2, count(middle.getItems(), Items.STONE), "Settled remainder");
        helper.getLevel().removeBlock(middle.getBlockPos(), false);
    }

    private static boolean isAutomationActor(ServerPlayer player) {
        return AutomationActor.PROFILE.getId().equals(player.getUUID());
    }

    private static void expectRemovalBy(
            ServerLevel level, BlockPos pos, ServerPlayer actor, Runnable edit) {
        BlockState state = level.getBlockState(pos);
        try (EventProbe events = new EventProbe(level, pos)) {
            edit.run();
            check(level.getBlockState(pos).isAir(), "Emptied stack survived at " + pos);
            events.expectOne(GameEvent.BLOCK_DESTROY, actor, state);
        }
    }

    /**
     * To reproduce: put a sculk sensor near a floor, V-place Storage holding dirt, fill its slots,
     * and have a hopper deposit stone to grow it. Extract the stone and let settlement trim the
     * upper block. Each structural edit produces its placement or destruction vibration, subject to
     * ordinary sculk filtering. Inventory edits alone do not produce these structural events.
     */
    public static void placementGrowthAndCleanupNotify(
            GameTestHelper helper, ServerPlayer player, InteractionChecks.LoaderClick click) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(ORIGIN);
        BlockPos above = base.above();
        level.setBlock(base.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        try (EventProbe baseEvents = new EventProbe(level, base);
                EventProbe upperEvents = new EventProbe(level, above);
                InteractionChecks.PlayerScope scope = new InteractionChecks.PlayerScope(player)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            click.click(
                    player,
                    InteractionChecks.HAND,
                    InteractionChecks.aimDown(player, base.below(), 0.5, 0.5));
            BlockState baseState = level.getBlockState(base);
            check(
                    baseState.is(CommonRegistry.STORAGE_STACK_BLOCK.get()),
                    "Gesture did not place Storage");
            baseEvents.expectOne(GameEvent.BLOCK_PLACE, player, baseState);
            upperEvents.expectNone();
            StorageStackBE storage = (StorageStackBE) level.getBlockEntity(base);
            for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
                storage.getItems().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
            }
            checkEquals(
                    1,
                    storage.deposit(new ItemStack(Items.STONE), null),
                    "Automated growth deposit");
            BlockState grownState = level.getBlockState(above);
            check(
                    grownState.is(CommonRegistry.STORAGE_STACK_BLOCK.get()),
                    "Growth did not place Storage");
            upperEvents.expectOne(
                    GameEvent.BLOCK_PLACE, WorldEdits.automationActor(level), grownState);
            baseEvents.expectNone();
            StorageStackBE grown = (StorageStackBE) level.getBlockEntity(above);
            checkEquals(
                    1, grown.extractAt(0, 1, ItemStack.EMPTY, null).getCount(), "Upper extraction");
            storage.pile().settle();
            check(
                    !level.getBlockState(above).is(CommonRegistry.STORAGE_STACK_BLOCK.get()),
                    "Empty top survived");
            upperEvents.expectOne(
                    GameEvent.BLOCK_DESTROY, WorldEdits.automationActor(level), grownState);
            baseEvents.expectNone();
        }
        helper.succeed();
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
    public static void checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(
            GameTestHelper helper, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        BlockPos valid = helper.absolutePos(ORIGIN);
        BlockPos invalid = new BlockPos(valid.getX(), level.getMinBuildHeight() - 1, valid.getZ());

        check(placeStone(player, level, valid), "Valid checked placement was rejected");
        check(
                level.getBlockState(valid).is(Blocks.STONE),
                "Valid checked placement did not change the world");
        check(!placeStone(player, level, invalid), "Out-of-height checked placement was accepted");
        helper.succeed();
    }

    /**
     * The fixture puts a cow inside the proposed full stone shape and calls checked placement;
     * placement fails.
     *
     * <p>Nearest manual check: confine a cow in the destination above a floor, select Storage, and
     * V-right-click the floor holding dirt. The obstructed Storage block must not appear.
     */
    public static void checkedPlacementRejectsAnObstructingEntity(
            GameTestHelper helper, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(ORIGIN);
        putCowIn(helper, ORIGIN);

        try (var events = new EventProbe(level, target)) {
            check(
                    !placeStone(player, level, target),
                    "Entity-obstructed checked placement was accepted");
            events.expectNone();
        }
        helper.assertBlockNotPresent(Blocks.STONE, ORIGIN);
        helper.succeed();
    }

    private static boolean placeStone(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        return WorldEdits.placeChecked(
                player,
                level,
                pos,
                stone,
                Direction.DOWN,
                stone.getCollisionShape(level, pos, CollisionContext.empty()));
    }

    /**
     * To reproduce: enter Creative, hold 64 dirt, select Storage, and V-right-click an existing
     * Storage block. All 64 dirt enter the pile and the hand still holds 64.
     */
    public static void creativeDepositFillsTheStackWithoutSpendingTheHand(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            InteractionChecks.LoaderClick click) {
        BlockPos target = placeStorage(helper, ORIGIN).getBlockPos();
        try (InteractionChecks.PlayerScope scope =
                new InteractionChecks.PlayerScope(factory.apply(new ItemStack(Items.DIRT, 64)))) {
            var player = scope.player;
            player.getAbilities().instabuild = true;
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            click.click(
                    player,
                    InteractionChecks.HAND,
                    InteractionChecks.aim(player, target, 0.5, 0.5));
            checkEquals(
                    64,
                    player.getMainHandItem().getCount(),
                    "A creative deposit spent the held stack");
        }
        checkEquals(
                64, heldAt(helper, target, Items.DIRT), "Creative deposit did not reach the stack");
        helper.succeed();
    }

    /**
     * To reproduce: place source water above a solid floor, select Storage, and V-right-click the
     * floor with dirt. The new Storage block is waterlogged and water remains at that position.
     */
    public static void placementIntoWaterKeepsTheWater(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            InteractionChecks.LoaderClick click) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(ORIGIN);
        level.setBlock(target, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        try (InteractionChecks.PlayerScope scope =
                new InteractionChecks.PlayerScope(factory.apply(new ItemStack(Items.DIRT, 64)))) {
            var player = scope.player;
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            click.click(
                    player,
                    InteractionChecks.HAND,
                    InteractionChecks.aimDown(player, target.below(), 0.5, 0.5));
        }

        helper.assertBlockPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN);
        check(
                level.getBlockState(target).getValue(StorageStackBlock.WATERLOGGED),
                "A stack placed into water was not waterlogged");
        check(
                level.getFluidState(target).getType() == Fluids.WATER,
                "A stack placed into water swallowed the water");
        helper.succeed();
    }

    // Growth under protection
    //
    // An automation insertion aimed at a slot past what the run holds is the one that grows it,
    // and it checks the position above the run before promising the caller anything, so a
    // simulation and the commit that follows agree about a position growth cannot have. The three
    // border tests stage that with the world border. Spawn protection, the other half of the same
    // predicate, cannot be staged here: it is implemented on DedicatedServer, and the server
    // running these tests is not one.

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
    public static void storageGrowthAnswersToProtectionInSimulationAndCommit(
            GameTestHelper helper, Function<BlockEntity, Automation> automation) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        fill(storage, new ItemStack(Items.DIRT, 64), 27);
        growthAnswersToTheBorder(
                helper, automation.apply(storage), new ItemStack(Items.STONE, 4), 27, 0);
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
    public static void singlesGrowthAnswersToProtectionInSimulationAndCommit(
            GameTestHelper helper, Function<BlockEntity, Automation> automation) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        fill(singles, new ItemStack(Items.STONE), 64);
        // A cell takes one item, so a credited growth leaves three of the four behind.
        growthAnswersToTheBorder(
                helper, automation.apply(singles), new ItemStack(Items.STONE, 4), 64, 3);
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
    public static void barGrowthAnswersToProtectionInSimulationAndCommit(
            GameTestHelper helper, Function<BlockEntity, Automation> automation) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item bar = firstBarItem();
        fill(bars, new ItemStack(bar), 64);
        // A position takes one bar, so a credited growth leaves three of the four behind.
        growthAnswersToTheBorder(helper, automation.apply(bars), new ItemStack(bar, 4), 64, 3);
    }

    /**
     * Offers {@code offered} to first headroom slot {@code headroom}: with free space the offer
     * leaves {@code creditedRemainder}; with the space outside the world border, simulation and
     * commit both leave the whole offer, and no block appears above the run.
     */
    private static void growthAnswersToTheBorder(
            GameTestHelper helper,
            Automation capability,
            ItemStack offered,
            int headroom,
            int creditedRemainder) {
        BlockState before = helper.getLevel().getBlockState(helper.absolutePos(ORIGIN));
        withPolicy(
                Map.of(ServerConfig.Setting.MAX_PILE_HEIGHT, 2),
                () -> {
                    checkEquals(
                            creditedRemainder,
                            capability.insert(headroom, offered, true).getCount(),
                            "A full run with free headroom did not credit growth");
                    outsideTheBorder(
                            helper,
                            () -> {
                                checkEquals(
                                        offered.getCount(),
                                        capability.insert(headroom, offered, true).getCount(),
                                        "Simulated remainder");
                                checkEquals(
                                        offered.getCount(),
                                        capability.insert(headroom, offered, false).getCount(),
                                        "Committed remainder");
                            });
                });
        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(before.getBlock(), ORIGIN.above());
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
    public static void storageGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper, Function<BlockEntity, Automation> automation) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        fill(storage, new ItemStack(Items.DIRT, 64), 27);
        growthRefusedByACow(helper, automation.apply(storage), new ItemStack(Items.STONE, 4), 27);
    }

    /**
     * To reproduce: allow height above one, fill one Singles block, and confine a cow in the
     * prospective upper block's first occupied position. Offer four admissible items to first
     * headroom through automation. Commit refuses them all and creates no block. The automated
     * check also asserts dry-run refusal and unchanged caller input; simulation/abort needs an
     * automation probe.
     */
    public static void singlesGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper, Function<BlockEntity, Automation> automation) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        fill(singles, new ItemStack(Items.STONE), 64);
        growthRefusedByACow(helper, automation.apply(singles), new ItemStack(Items.STONE, 4), 64);
    }

    /**
     * To reproduce: allow height above one, fill one Bar block, and confine a cow in the
     * prospective upper block's first occupied position. Offer four admissible items to first
     * headroom through automation. Commit refuses them all and creates no block. The automated
     * check also asserts dry-run refusal and unchanged caller input; simulation/abort needs an
     * automation probe.
     */
    public static void barGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper, Function<BlockEntity, Automation> automation) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item bar = firstBarItem();
        fill(bars, new ItemStack(bar), 64);
        growthRefusedByACow(helper, automation.apply(bars), new ItemStack(bar, 4), 64);
    }

    /**
     * Puts a cow in the space above the run and offers {@code offered} to first headroom slot
     * {@code headroom}; simulation and commit both leave the whole offer and no block appears.
     */
    private static void growthRefusedByACow(
            GameTestHelper helper, Automation capability, ItemStack offered, int headroom) {
        BlockState before = helper.getLevel().getBlockState(helper.absolutePos(ORIGIN));
        putCowIn(helper, ORIGIN.above());
        withPolicy(
                Map.of(ServerConfig.Setting.MAX_PILE_HEIGHT, 2),
                () -> {
                    checkEquals(
                            4,
                            capability.insert(headroom, offered, true).getCount(),
                            "Simulated remainder");
                    checkEquals(
                            4,
                            capability.insert(headroom, offered, false).getCount(),
                            "Committed remainder");
                });
        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(before.getBlock(), ORIGIN.above());
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
    public static void completedShapesDistinguishOccupiedCellsFromEmptySpace(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(ORIGIN);
        Cow cow = EntityType.COW.create(level);
        check(cow != null, "Could not create obstruction cow");
        cow.moveTo(target.getX() + 1.25, target.getY(), target.getZ() + 0.5, 0.0F, 0.0F);
        check(level.addFreshEntity(cow), "Could not add obstruction cow");
        try {
            check(
                    !WorldEdits.isUnobstructed(level, target, Shapes.block()),
                    "Full block missed obstruction");
            check(
                    WorldEdits.isUnobstructed(level, target, Shapes.empty()),
                    "Empty shape was obstructed");
            check(
                    WorldEdits.isUnobstructed(level, target, SinglesCubeIdx.GRID.localShape(0, 0)),
                    "Free Singles cell was obstructed by another cell's occupant");
            check(
                    !WorldEdits.isUnobstructed(
                            level,
                            target,
                            SinglesCubeIdx.GRID.localShape(SinglesCubeIdx.LAYER_SIZE - 1, 0)),
                    "Occupied Singles shape missed obstruction");
            check(
                    WorldEdits.isUnobstructed(level, target, BarCubeIdx.shapeFor(0)),
                    "Free Bar position was obstructed by another position's occupant");
            check(
                    !WorldEdits.isUnobstructed(
                            level, target, BarCubeIdx.shapeFor(BarCubeIdx.LAYER_SIZE - 1)),
                    "Occupied Bar shape missed obstruction");
        } finally {
            cow.discard();
        }
        helper.succeed();
    }

    /** Seeds the first {@code slots} slots of {@code block} with copies of {@code stack}. */
    private static void fill(StackBlockEntity block, ItemStack stack, int slots) {
        for (int slot = 0; slot < slots; slot++) {
            seedSlot(block.getItems(), slot, stack.copy());
        }
    }

    /**
     * Puts a living placement-blocking entity across the bottom north-west cell and bar position of
     * {@code relative}.
     */
    private static void putCowIn(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(relative);
        Cow cow = EntityType.COW.create(level);
        check(cow != null, "Could not create obstruction cow");
        check(cow.blocksBuilding, "Cow does not block building");
        cow.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.0F, 0.0F);
        check(level.addFreshEntity(cow), "Could not add obstruction cow");
    }

    /**
     * Runs {@code action} with the world border moved off the test structure, and restores it
     * before returning. The border is level-wide state, but the move and the restore both happen
     * inside this one synchronous call, so no other test observes it moved.
     */
    public static void outsideTheBorder(GameTestHelper helper, Runnable action) {
        WorldBorder border = helper.getLevel().getWorldBorder();
        BlockPos above = helper.absolutePos(ORIGIN.above());
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();
        double size = border.getSize();
        try {
            border.setCenter(above.getX() + 1000.0, above.getZ());
            border.setSize(16.0);
            check(
                    !border.isWithinBounds(above),
                    "Test setup left the position inside the world border");
            action.run();
        } finally {
            border.setCenter(centerX, centerZ);
            border.setSize(size);
        }
    }

    /**
     * Records placement, destruction, and change events at one position and unregisters at the end
     * of the check.
     */
    public static final class EventProbe implements GameEventListener, AutoCloseable {
        private final BlockPos pos;
        private final PositionSource source;
        private final GameEventListenerRegistry registry;
        private final List<Observed> events = new ArrayList<>();

        public EventProbe(ServerLevel level, BlockPos pos) {
            this.pos = pos.immutable();
            source = new BlockPositionSource(this.pos);
            registry =
                    level.getChunkAt(pos)
                            .getListenerRegistry(SectionPos.blockToSectionCoord(pos.getY()));
            registry.register(this);
        }

        @Override
        public PositionSource getListenerSource() {
            return source;
        }

        @Override
        public int getListenerRadius() {
            return 16;
        }

        @Override
        public boolean handleGameEvent(
                ServerLevel level, GameEvent event, GameEvent.Context context, Vec3 location) {
            if ((event == GameEvent.BLOCK_PLACE
                            || event == GameEvent.BLOCK_DESTROY
                            || event == GameEvent.BLOCK_CHANGE)
                    && location.equals(Vec3.atCenterOf(pos))) {
                events.add(new Observed(event, context));
                return true;
            }
            return false;
        }

        public void expectOne(GameEvent event, ServerPlayer actor, BlockState state) {
            checkEquals(1, events.size(), "Block event count at " + pos);
            Observed observed = events.get(0);
            check(observed.event == event, "Wrong block event at " + pos);
            check(observed.context.sourceEntity() == actor, "Wrong block event actor");
            checkEquals(state, observed.context.affectedState(), "Block event affected state");
            events.clear();
        }

        public void expectNone() {
            checkEquals(0, events.size(), "Unexpected block event at " + pos);
        }

        @Override
        public void close() {
            registry.unregister(this);
        }

        private record Observed(GameEvent event, GameEvent.Context context) {}
    }
}
