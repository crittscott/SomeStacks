package com.github.crittscott.somestacks.gametest;

import net.minecraft.world.phys.shapes.Shapes;
import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.block.StackBlock;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.AutomationActor;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.server.StackInteractions;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.StackMode;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.putCowIn;

/** Loader-neutral placement and player-deposit protection scenarios. */
public final class ProtectionChecks {
    private ProtectionChecks() {}

    /**
     * Every loader attributes automation to the same [SomeStacks] profile. To reproduce in-game:
     * configure a claim or logging mod to report the actor for machine-driven stack growth or
     * cleanup. It reports [SomeStacks] with the shared profile identity.
     */
    public static void automationUsesSharedIdentity(GameTestHelper helper) {
        var actual = WorldEdits.automationActor(helper.getLevel());
        checkEquals(AutomationActor.ID, actual.getUUID(), "Automation UUID");
        checkEquals(AutomationActor.NAME, actual.getScoreboardName(), "Automation name");
        helper.succeed();
    }

    /**
     * Checked world edits place inside build height and reject positions outside it. To reproduce
     * in-game: grow or place a stack in ordinary space, then attempt the same edit at the world's
     * vertical limit. Only the in-bounds placement succeeds.
     */
    public static void checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        BlockPos valid = helper.absolutePos(ORIGIN);
        BlockPos invalid = new BlockPos(valid.getX(), level.getMinY() - 1, valid.getZ());

        check(WorldEdits.placeChecked(
                        player, level, valid, Blocks.STONE.defaultBlockState(), Direction.DOWN, Shapes.block()),
                "Valid checked placement was rejected");
        check(level.getBlockState(valid).is(Blocks.STONE),
                "Valid checked placement did not change the world");
        check(!WorldEdits.placeChecked(
                        player, level, invalid, Blocks.STONE.defaultBlockState(), Direction.DOWN, Shapes.block()),
                "Out-of-height checked placement was accepted");
        helper.succeed();
    }

    /**
     * Stack placement refuses a living entity intersecting its final collision shape. To reproduce
     * in-game: stand or put a mob in the occupied part of a proposed stack and try to place or grow
     * it. No stack block appears.
     */
    public static void checkedPlacementRejectsAnObstructingEntity(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        BlockPos target = helper.absolutePos(ORIGIN);
        putCowIn(helper, ORIGIN);

        check(!WorldEdits.placeChecked(
                        player, level, target, Blocks.STONE.defaultBlockState(), Direction.DOWN, Shapes.block()),
                "Entity-obstructed checked placement was accepted");
        helper.assertBlockNotPresent(Blocks.STONE, ORIGIN);
        helper.succeed();
    }

    /**
     * Creative deposits fill a stack without consuming the held items. To reproduce in-game:
     * switch to Creative, hold the modifier, and deposit a full held stack into Storage. Storage
     * receives the items while the hand retains its original count.
     */
    public static void creativeDepositFillsTheStackWithoutSpendingTheHand(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.DIRT, 64));
        BlockPos target = helper.absolutePos(ORIGIN);
        GameTestScaffold.placeStorage(helper, ORIGIN);

        player.getAbilities().instabuild = true;
        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        try {
            StackInteractions.handleExistingStack(
                    player, InteractionHand.MAIN_HAND, centerHit(target),
                    BlockType.STORAGE_STACK);
            checkEquals(64, player.getMainHandItem().getCount(),
                    "A creative deposit spent the held stack");
        } finally {
            player.getAbilities().instabuild = false;
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }

        checkEquals(64, GameTestScaffold.heldAt(helper, target, Items.DIRT),
                "Creative deposit did not reach the stack");
        helper.succeed();
    }

    /**
     * Placing a stack into water waterlogs it instead of removing the fluid. To reproduce in-game:
     * hold the modifier and place a new stack into a water source. The block appears waterlogged and
     * the water remains present.
     */
    public static void placementIntoWaterKeepsTheWater(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.DIRT, 64));
        BlockPos target = helper.absolutePos(ORIGIN);
        level.setBlock(target, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos support = target.below();

        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        try {
            StackInteractions.handleAdjacentClick(
                    player,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                            new Vec3(support.getX() + 0.5, support.getY() + 1.0,
                                    support.getZ() + 0.5),
                            Direction.UP,
                            support,
                            false),
                    true,
                    true);
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }

        helper.assertBlockPresent(CommonRegistry.storageStackBlock(), ORIGIN);
        check(level.getBlockState(target).getValue(StackBlock.WATERLOGGED),
                "A stack placed into water was not waterlogged");
        check(level.getFluidState(target).getType() == Fluids.WATER,
                "A stack placed into water swallowed the water");
        helper.succeed();
    }

    /** Exercises neighboring and direct deposits under the loader's destination policy. */
    public static void checkAdjacentAndDirectDeposits(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory,
            boolean neighborAllowed) {
        var singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        BlockPos target = singles.getBlockPos();
        BlockPos neighbor = target.west();
        helper.getLevel().setBlock(neighbor, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.STONE, 2));
        player.setPos(neighbor.getX() - 2.0, target.getY() + 0.125 - player.getEyeHeight(),
                target.getZ() + 0.125);
        ServerGestureState.set(player, StackMode.SINGLES_STACK, true);
        try {
            check(StackInteractions.handleAdjacentClick(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(new Vec3(target.getX(), target.getY() + 0.125,
                            target.getZ() + 0.125), Direction.EAST, neighbor, false), true, true),
                    "Neighbor gesture was not consumed");
            checkEquals(neighborAllowed, !singles.isEmpty(), "Neighbor deposit policy");
            checkEquals(neighborAllowed ? 1 : 2, player.getMainHandItem().getCount(), "Neighbor deposit hand");
            if (neighborAllowed) return;
            helper.getLevel().removeBlock(neighbor, false);
            check(StackInteractions.handleExistingStack(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(new Vec3(target.getX() + 0.125, target.getY() + 0.125,
                            target.getZ() + 0.125), Direction.WEST, target, false),
                    BlockType.SINGLES_STACK),
                    "Direct deposit gesture was not consumed");
            check(!singles.isEmpty(), "Direct click failed to deposit");
            checkEquals(1, player.getMainHandItem().getCount(), "Direct deposit hand");
        } finally {
            ServerGestureState.clear(player.getUUID());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    /**
     * Empty grounded cells remain depositable beside occupied cells in both stack types.
     * To reproduce in-game: place a Singles or Bar Stack on stone, put an item in one bottom cell,
     * then hold the modifier and aim down through two other empty bottom cells at the stone.
     * Each deposit fills the aimed cell without changing the first item or adding an upper layer.
     */
    public static void adjacentDepositsFillGroundedCells(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        var singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        var bars = GameTestScaffold.placeBar(helper, ORIGIN.east(3));
        ItemStack singleItem = new ItemStack(Items.STONE);
        ItemStack barItem = new ItemStack(GameTestScaffold.firstBarItem());
        for (var be : List.of(singles, bars)) {
            boolean isSingles = be == singles;
            ItemStack item = isSingles ? singleItem : barItem;
            be.getItems().setStackInSlot(0, item.copy());
            BlockPos target = be.getBlockPos();
            BlockPos support = target.below();
            helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            ServerPlayer player = playerFactory.apply(item.copyWithCount(2));
            ServerGestureState.set(player, StackMode.SINGLES_STACK, true);
            try {
                for (int slot = 1; slot <= 2; slot++) {
                    var box = (isSingles
                            ? SinglesCubeIdx.shapeFor(slot, 0)
                            : BarCubeIdx.shapeFor(slot)).bounds();
                    double x = target.getX() + (box.minX + box.maxX) / 2;
                    double z = target.getZ() + (box.minZ + box.maxZ) / 2;
                    player.setPos(x, target.getY() + 2 - player.getEyeHeight(), z);
                    check(StackInteractions.handleAdjacentClick(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(new Vec3(x, target.getY(), z), Direction.UP,
                                    support, false), true, true), "Grounded deposit was not consumed");
                    check(!be.getItems().getStackInSlot(slot).isEmpty(), "Aimed grounded cell stayed empty");
                    checkEquals(2 - slot, player.getMainHandItem().getCount(), "Grounded deposit hand");
                }
                check(ItemStack.matches(item, be.getItems().getStackInSlot(0)),
                        "Grounded deposits changed the first item");
                checkEquals(3, GameTestScaffold.occupied(be.getItems()),
                        "Grounded deposits filled unintended cells");
            } finally {
                ServerGestureState.clear(player.getUUID());
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }
    }

    /** Native removal observation whose returned action unregisters the hook. */
    @FunctionalInterface
    public interface RemovalObserver {
        Runnable install(ServerLevel level, BlockPos pos, Consumer<Player> observed,
                         Predicate<Player> allowed);
    }

    /**
     * To reproduce in-game: allow a player but deny [SomeStacks] in a claim, then extract the last
     * Bar. Cleanup carries the player and removes the empty block.
     */
    public static void playerBarExtractionUsesPlayerForCleanup(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory,
            RemovalObserver observer) {
        BarStackBE bars = GameTestScaffold.placeBar(helper, ORIGIN);
        bars.getItems().insertItem(0, new ItemStack(GameTestScaffold.firstBarItem()));
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        AtomicReference<Player> actor = new AtomicReference<>();
        Runnable unregister = observer.install(helper.getLevel(), bars.getBlockPos(), actor::set,
                candidate -> !candidate.getUUID().equals(AutomationActor.ID));
        try {
            check(!bars.extractAt(0, player).isEmpty(), "Player extraction returned nothing");
        } finally {
            unregister.run();
        }
        check(actor.get() == player, "Bar cleanup did not carry the player");
        helper.assertBlockNotPresent(CommonRegistry.barStackBlock(), ORIGIN);
        helper.succeed();
    }

    /**
     * To reproduce in-game: allow a player but deny [SomeStacks], extract one of two Storage items
     * by hand and the other by automation before settlement. Mixed cleanup uses [SomeStacks], so
     * the empty block remains until removal is allowed.
     */
    public static void mixedStorageSettlementUsesAutomationForCleanup(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory,
            RemovalObserver observer) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, ORIGIN);
        storage.getItems().insertItem(0, new ItemStack(Items.STONE, 2));
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        AtomicReference<Player> actor = new AtomicReference<>();
        Runnable unregister = observer.install(helper.getLevel(), storage.getBlockPos(), actor::set,
                candidate -> !candidate.getUUID().equals(AutomationActor.ID));
        try {
            checkEquals(1, storage.extractAt(0, 1, ItemStack.EMPTY, player).getCount(),
                    "Player extraction count");
            StoragePile pile = storage.pile();
            check(pile != null, "Pile did not resolve");
            checkEquals(1, pile.extract(0, 1, false).getCount(), "Automation extraction count");
            pile.settle();
        } finally {
            unregister.run();
        }
        check(actor.get() != null, "Cleanup break event did not fire");
        checkEquals(AutomationActor.ID, actor.get().getUUID(), "Mixed cleanup actor");
        helper.assertBlockPresent(CommonRegistry.storageStackBlock(), ORIGIN);
        storage.pile().settle();
        helper.assertBlockNotPresent(CommonRegistry.storageStackBlock(), ORIGIN);
        helper.succeed();
    }

    /**
     * To reproduce in-game: deny stack placement over a modded replaceable container containing
     * items. The refused placement restores the container's contents. This stages the rollback
     * directly with a chest so no test-only replaceable block registration is needed. A second
     * rollback over an empty permanent Storage Stack must restore permanence without scheduling
     * a new placement/settlement tick.
     */
    public static void vetoedPlacementRestoresBlockEntity(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory,
            Runnable installVeto, Runnable removeVeto) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(ORIGIN);
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(pos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        installVeto.run();
        try {
            check(!WorldEdits.placeChecked(playerFactory.apply(ItemStack.EMPTY), level, pos,
                    CommonRegistry.storageStackBlock().defaultBlockState(), Direction.DOWN, Shapes.block()),
                    "Vetoed placement succeeded");
        } finally {
            removeVeto.run();
        }
        helper.assertBlockPresent(Blocks.CHEST, ORIGIN);
        ChestBlockEntity restored = (ChestBlockEntity) level.getBlockEntity(pos);
        checkEquals(Items.DIAMOND, restored.getItem(0).getItem(), "Restored container item");
        checkEquals(3, restored.getItem(0).getCount(), "Restored container count");

        StorageStackBE storage = GameTestScaffold.placeStorage(helper, ORIGIN);
        storage.pile().setPermanent(true, null);
        helper.runAfterDelay(3, () -> {
            Block storageBlock = CommonRegistry.storageStackBlock();
            check(!level.getBlockTicks().hasScheduledTick(pos, storageBlock),
                    "Initial permanent stack settlement is still pending");
            installVeto.run();
            try {
                check(!WorldEdits.placeChecked(playerFactory.apply(ItemStack.EMPTY), level, pos,
                        CommonRegistry.barStackBlock().defaultBlockState(), Direction.DOWN, Shapes.empty()),
                        "Vetoed stack replacement succeeded");
            } finally {
                removeVeto.run();
            }
            helper.assertBlockPresent(storageBlock, ORIGIN);
            check(((StorageStackBE) level.getBlockEntity(pos)).isPermanent(),
                    "Rollback lost stack permanence");
            check(!level.getBlockTicks().hasScheduledTick(pos, storageBlock),
                    "Rollback ran the restored stack's placement hook");
            helper.succeed();
    });
    }

    /**
     * To reproduce in-game: face an observer toward a denied placement position. A rejected
     * placement produces no observer pulse; after permitting the same placement, the observer pulses.
     */
    public static void placementPublicationAnswersToVeto(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory,
            Runnable installVeto, Runnable removeVeto) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(ORIGIN);
        BlockPos observer = target.west();
        level.setBlock(observer, Blocks.OBSERVER.defaultBlockState()
                .setValue(ObserverBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        helper.runAfterDelay(6, () -> {
            installVeto.run();
            try {
                check(!WorldEdits.placeChecked(playerFactory.apply(ItemStack.EMPTY), level, target,
                        CommonRegistry.storageStackBlock().defaultBlockState(), Direction.DOWN, Shapes.block()),
                        "Vetoed placement succeeded");
            } finally {
                removeVeto.run();
            }
            helper.runAfterDelay(3, () -> {
                check(!level.getBlockState(observer).getValue(ObserverBlock.POWERED),
                        "Denied placement pulsed its observer");
                check(WorldEdits.placeChecked(playerFactory.apply(ItemStack.EMPTY), level, target,
                        CommonRegistry.storageStackBlock().defaultBlockState(), Direction.DOWN, Shapes.block()),
                        "Accepted placement failed");
                ((StorageStackBE) level.getBlockEntity(target)).getItems()
                        .insertItem(0, new ItemStack(Items.STONE));
                helper.runAfterDelay(3, () -> {
                    check(level.getBlockState(observer).getValue(ObserverBlock.POWERED),
                            "Accepted placement never published to its observer");
                    helper.succeed();
                });
            });
        });
    }

    private static BlockHitResult centerHit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }
    /** Native per-slot offer returning the accepted count; commit=false rolls back. */
    @FunctionalInterface
    public interface OfferAt {
        int insert(StackBlockEntity blockEntity, int slot, ItemStack stack, boolean commit);
    }

    /**
     * To reproduce in-game: allow a player but deny [SomeStacks] in a claim, then have the player
     * extract the last Single. Player-attributed cleanup removes the empty block.
     */
    public static void playerExtractionUsesPlayerForCleanup(GameTestHelper helper,
            Function<ItemStack, ServerPlayer> playerFactory, RemovalObserver observer) {
        ServerLevel level = helper.getLevel();
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        singles.getItems().insertItem(0, new ItemStack(Items.STONE));
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        BlockPos target = helper.absolutePos(ORIGIN);
        AtomicReference<Player> observedActor = new AtomicReference<>();
        Runnable unregister = observer.install(level, target, observedActor::set, candidate -> candidate == player);
        try {
            check(!singles.extractAt(0, player).isEmpty(), "Player extraction returned nothing");
        } finally {
            unregister.run();
        }

        check(observedActor.get() == player, "Cleanup break event did not carry the player");
        helper.assertBlockNotPresent(CommonRegistry.singlesStackBlock(), ORIGIN);
        helper.succeed();
    }

    /**
     * To reproduce in-game: allow a player but deny [SomeStacks] in a claim, then have the player
     * extract the last Storage item. Deferred cleanup retains the player and removes the block.
     */
    public static void playerStorageSettlementUsesPlayerForCleanup(GameTestHelper helper,
            Function<ItemStack, ServerPlayer> playerFactory, RemovalObserver observer) {
        ServerLevel level = helper.getLevel();
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, ORIGIN);
        storage.getItems().insertItem(0, new ItemStack(Items.STONE));
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        BlockPos target = helper.absolutePos(ORIGIN);
        AtomicReference<Player> observedActor = new AtomicReference<>();
        Runnable unregister = observer.install(level, target, observedActor::set, candidate -> candidate == player);
        try {
            check(!storage.extractAt(0, 64, ItemStack.EMPTY, player).isEmpty(),
                    "Player extraction returned nothing");
            StoragePile pile = storage.pile();
            check(pile != null, "Pile did not resolve before settlement");
            pile.settle();
        } finally {
            unregister.run();
        }

        check(observedActor.get() == player, "Deferred cleanup did not retain the player");
        helper.assertBlockNotPresent(CommonRegistry.storageStackBlock(), ORIGIN);
        helper.succeed();
    }

    /**
     * A refused cleanup leaves the empty top Storage block in both the world and the resolved run.
     * To reproduce in-game: protect the top block of a two-block temporary Storage pile from
     * [SomeStacks], leave one item in the bottom block, and trigger settlement. The protected empty
     * top block remains; after allowing [SomeStacks], the next settlement removes it.
     */
    public static void settleKeepsAnEmptyTopBlockWhoseRemovalIsRefused(GameTestHelper helper, RemovalObserver observer) {
        BlockPos topRelative = ORIGIN.above();
        StorageStackBE base = GameTestScaffold.placeStorage(helper, ORIGIN);
        GameTestScaffold.placeStorage(helper, topRelative);
        base.getItems().insertItem(0, new ItemStack(Items.DIRT));

        ServerLevel level = helper.getLevel();
        Runnable unregister = observer.install(level, helper.absolutePos(topRelative), actor -> {}, actor -> false);
        try {
            StoragePile pile = base.pile();
            check(pile != null, "Pile did not resolve");
            pile.settle();
            helper.assertBlockPresent(CommonRegistry.storageStackBlock(), topRelative);
            checkEquals(2, pile.height(), "The pile dropped a block it never removed");
        } finally {
            unregister.run();
        }

        StoragePile pile = base.pile();
        check(pile != null, "Pile did not resolve after the refusal");
        pile.settle();
        helper.assertBlockNotPresent(CommonRegistry.storageStackBlock(), topRelative);
        helper.succeed();
    }

    /**
     * A refused removal keeps an emptied Bar block without preventing the unsupported block above
     * from collapsing. To reproduce in-game: build a supported two-block Bar column, deny
     * [SomeStacks] permission to remove the lower block, and extract its bottom support. The empty
     * lower block remains, while the unsupported upper block comes down.
     */
    public static void refusedRemovalStillLetsTheBarsAboveComeDown(GameTestHelper helper, RemovalObserver observer) {
        BarStackBE lower = GameTestScaffold.placeBar(helper, ORIGIN);
        BarStackBE upper = GameTestScaffold.placeBar(helper, ORIGIN.above());
        Item barItem = GameTestScaffold.firstBarItem();
        BlockPos upperPos = upper.getBlockPos();

        int slot = 0;
        GameTestScaffold.seedSlot(lower.getItems(), slot, new ItemStack(barItem));
        for (int layer = 1; layer < 8; layer++) {
            slot = BarColumnChecks.firstSupportedBy(slot, layer * 8, (layer + 1) * 8);
            GameTestScaffold.seedSlot(lower.getItems(), slot, new ItemStack(barItem));
        }
        GameTestScaffold.seedSlot(
                upper.getItems(), BarColumnChecks.firstSeamSupportedBy(slot - 56),
                new ItemStack(barItem));

        Runnable unregister = observer.install(helper.getLevel(), lower.getBlockPos(), actor -> {}, actor -> false);
        try {
            lower.extractAt(0, null);
        } finally {
            unregister.run();
        }

        helper.assertBlockPresent(CommonRegistry.barStackBlock(), ORIGIN);
        check(lower.isEmpty(), "The kept block held on to its bars");
        check(helper.getLevel().getBlockEntity(upperPos) == null,
                "The block above a kept block was not brought down");
        helper.succeed();
    }

    /**
     * To reproduce in-game: protect the space above a full Storage pile from SomeStacks and insert
     * through item automation; it accepts nothing and creates no block.
     */
    public static void storageGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper, OfferAt offer) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64));
        }
        ItemStack offered = new ItemStack(Items.STONE, 4);

        int headroom = StorageStackBE.SLOTS;
        checkEquals(4, offer.insert(storage, headroom, offered, false),
                "A full pile with free headroom did not credit growth");

        GameTestScaffold.outsideWorldBorder(helper, ORIGIN.above(), () -> {
            checkEquals(0, offer.insert(storage, headroom, offered, false),
                    "Aborted offer accepted count");
            checkEquals(0, offer.insert(storage, headroom, offered, true),
                    "Committed offer accepted count");
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
    public static void singlesGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper, OfferAt offer) {
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            singles.getItems().insertItem(slot, new ItemStack(Items.STONE, 1));
        }
        ItemStack offered = new ItemStack(Items.STONE, 4);

        // A cell takes one item, so a credited growth leaves three of the four behind.
        int headroom = SinglesStackBE.SLOTS;
        checkEquals(1, offer.insert(singles, headroom, offered, false),
                "A full column with free headroom did not credit growth");

        GameTestScaffold.outsideWorldBorder(helper, ORIGIN.above(), () -> {
            checkEquals(0, offer.insert(singles, headroom, offered, false),
                    "Aborted offer accepted count");
            checkEquals(0, offer.insert(singles, headroom, offered, true),
                    "Committed offer accepted count");
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
    public static void barGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper, OfferAt offer) {
        BarStackBE bars = GameTestScaffold.placeBar(helper, ORIGIN);
        Item bar = GameTestScaffold.firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            bars.getItems().insertItem(slot, new ItemStack(bar, 1));
        }
        ItemStack offered = new ItemStack(bar, 4);

        // A position takes one bar, so a credited growth leaves three of the four behind.
        int headroom = BarStackBE.SLOTS;
        checkEquals(1, offer.insert(bars, headroom, offered, false),
                "A full column with free headroom did not credit growth");

        GameTestScaffold.outsideWorldBorder(helper, ORIGIN.above(), () -> {
            checkEquals(0, offer.insert(bars, headroom, offered, false),
                    "Aborted offer accepted count");
            checkEquals(0, offer.insert(bars, headroom, offered, true),
                    "Committed offer accepted count");
        });

        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.barStackBlock(), ORIGIN.above());
        helper.succeed();
    }

}
