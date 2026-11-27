package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.putCowIn;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StackBlock;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.AutomationActor;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.server.StackInteractions;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.StackMode;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;

/** Loader-neutral placement and player-deposit protection scenarios. */
public final class ProtectionChecks {
    private ProtectionChecks() {}

    /**
     * Adventure players may extract contents but cannot remove the emptied block. To reproduce
     * in-game: enter Adventure mode and empty a Singles Stack; the item is returned and its block
     * remains.
     */
    public static void adventureExtractionKeepsEmptyBlock(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        var previous = player.gameMode.getGameModeForPlayer();
        var singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        singles.getItems().setStackInSlot(0, new ItemStack(Items.APPLE));
        try {
            player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
            check(!singles.extractAt(0, player).isEmpty(), "Adventure extraction lost the item");
            check(
                    helper.getLevel().getBlockEntity(singles.getBlockPos()) == singles,
                    "Adventure cleanup removed the block");
            check(singles.isEmpty(), "Adventure extraction left the item behind");
        } finally {
            player.setGameMode(previous);
        }
        helper.succeed();
    }

    /**
     * Ordinary new placement replaces clicked vegetation instead of floating above it. To reproduce
     * in-game: hold dirt and the modifier in Storage mode and click short grass, thin snow, then
     * full snow from above. Grass and thin snow are replaced; full snow stays below the new stack.
     */
    public static void placementReplacesClickedVegetation(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        var level = helper.getLevel();
        BlockPos target = helper.absolutePos(ORIGIN);
        level.setBlock(target.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(target, Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_ALL);
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.DIRT));
        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        try {
            check(
                    StackInteractions.handleAdjacentClick(
                            player,
                            InteractionHand.MAIN_HAND,
                            new BlockHitResult(
                                    Vec3.atCenterOf(target), Direction.UP, target, false),
                            true,
                            true),
                    "Placement gesture was not consumed");
            check(
                    level.getBlockState(target).is(CommonRegistry.storageStackBlock()),
                    "Clicked grass was not replaced");
            check(level.getBlockState(target.above()).isAir(), "Placement floated above the grass");
            check(player.getMainHandItem().isEmpty(), "Successful placement did not consume dirt");
            for (int layers : new int[] {1, 8}) {
                BlockPos snow = target.east(layers == 1 ? 3 : 6);
                level.setBlock(snow.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                var snowState =
                        Blocks.SNOW
                                .defaultBlockState()
                                .setValue(
                                        net.minecraft.world.level.block.SnowLayerBlock.LAYERS,
                                        layers);
                level.setBlock(snow, snowState, Block.UPDATE_ALL);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
                check(
                        StackInteractions.handleAdjacentClick(
                                player,
                                InteractionHand.MAIN_HAND,
                                new BlockHitResult(
                                        Vec3.atCenterOf(snow), Direction.UP, snow, false),
                                true,
                                true),
                        "Snow placement gesture was not consumed");
                BlockPos placed = layers == 1 ? snow : snow.above();
                check(
                        level.getBlockState(placed).is(CommonRegistry.storageStackBlock()),
                        "Snow placement chose the wrong destination at " + layers + " layers");
                if (layers == 8)
                    check(level.getBlockState(snow).equals(snowState), "Full snow was overwritten");
            }
        } finally {
            ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

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

        check(
                WorldEdits.placeChecked(
                        player,
                        level,
                        valid,
                        Blocks.STONE.defaultBlockState(),
                        Direction.DOWN,
                        Shapes.block()),
                "Valid checked placement was rejected");
        check(
                level.getBlockState(valid).is(Blocks.STONE),
                "Valid checked placement did not change the world");
        check(
                !WorldEdits.placeChecked(
                        player,
                        level,
                        invalid,
                        Blocks.STONE.defaultBlockState(),
                        Direction.DOWN,
                        Shapes.block()),
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

        check(
                !WorldEdits.placeChecked(
                        player,
                        level,
                        target,
                        Blocks.STONE.defaultBlockState(),
                        Direction.DOWN,
                        Shapes.block()),
                "Entity-obstructed checked placement was accepted");
        helper.assertBlockNotPresent(Blocks.STONE, ORIGIN);
        helper.succeed();
    }

    /**
     * Creative deposits fill a stack without consuming the held items. To reproduce in-game: switch
     * to Creative, hold the modifier, and deposit a full held stack into Storage. Storage receives
     * the items while the hand retains its original count.
     */
    public static void creativeDepositFillsTheStackWithoutSpendingTheHand(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.DIRT, 64));
        BlockPos target = helper.absolutePos(ORIGIN);
        GameTestScaffold.placeStorage(helper, ORIGIN);

        player.getAbilities().instabuild = true;
        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        try {
            InteractionChecks.use(player, centerHit(target));
            checkEquals(
                    64,
                    player.getMainHandItem().getCount(),
                    "A creative deposit spent the held stack");
        } finally {
            player.getAbilities().instabuild = false;
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }

        checkEquals(
                64,
                GameTestScaffold.heldAt(helper, target, Items.DIRT),
                "Creative deposit did not reach the stack");
        helper.succeed();
    }

    /**
     * Placing a stack into water waterlogs it instead of removing the fluid. To reproduce in-game:
     * hold the modifier and place a new stack into a water source. The block appears waterlogged
     * and the water remains present.
     */
    public static void placementIntoWaterKeepsTheWater(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.DIRT, 64));
        BlockPos target = helper.absolutePos(ORIGIN);
        level.setBlock(target, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos support = target.below();
        level.setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        try {
            InteractionChecks.use(player, InteractionChecks.aimDown(player, support, 0.5, 0.5));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }

        helper.assertBlockPresent(CommonRegistry.storageStackBlock(), ORIGIN);
        check(
                level.getBlockState(target).getValue(StackBlock.WATERLOGGED),
                "A stack placed into water was not waterlogged");
        check(
                level.getFluidState(target).getType() == Fluids.WATER,
                "A stack placed into water swallowed the water");
        helper.succeed();
    }

    /**
     * Placing a stack and growing a Storage pile by hand count as placing a block for advancements;
     * automated growth does not. To reproduce in-game: install a data pack with a {@code
     * minecraft:placed_block} advancement for {@code somestacks:storage_stack_block}, then hold the
     * modifier and place a Storage Stack. The advancement is awarded; revoke it, fill the pile and
     * deposit once more, and growing the pile awards it again. A hopper feeding a full pile grows
     * it without awarding anything.
     */
    public static void playerPlacementAndGrowthAwardPlacedBlock(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerLevel level = helper.getLevel();
        AdvancementHolder advancement =
                level.getServer()
                        .getAdvancements()
                        .get(
                                Identifier.fromNamespaceAndPath(
                                        "somestacks_gametest", "placed_storage_stack"));
        check(advancement != null, "The placed-block test advancement is not loaded");
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.DIRT, 64));
        PlayerAdvancements advancements = player.getAdvancements();
        BlockPos target = helper.absolutePos(ORIGIN);
        BlockPos support = target.below();
        level.setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        advancements.revoke(advancement, "placed");
        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        try {
            StackInteractions.handleAdjacentClick(
                    player,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                            new Vec3(
                                    support.getX() + 0.5,
                                    support.getY() + 1.0,
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
        check(
                advancements.getOrStartProgress(advancement).isDone(),
                "Placing a stack did not trigger the placed-block criterion");

        StorageStackBE base = (StorageStackBE) level.getBlockEntity(target);
        fillStorage(base);
        advancements.revoke(advancement, "placed");
        base.deposit(new ItemStack(Items.DIRT), player);
        helper.assertBlockPresent(CommonRegistry.storageStackBlock(), ORIGIN.above());
        check(
                advancements.getOrStartProgress(advancement).isDone(),
                "Player-driven growth did not trigger the placed-block criterion");

        fillStorage((StorageStackBE) level.getBlockEntity(target.above()));
        advancements.revoke(advancement, "placed");
        base.deposit(new ItemStack(Items.DIRT), null);
        helper.assertBlockPresent(CommonRegistry.storageStackBlock(), ORIGIN.above(2));
        check(
                !advancements.getOrStartProgress(advancement).isDone(),
                "Automated growth triggered the player's placed-block criterion");

        advancements.revoke(advancement, "placed");
        helper.succeed();
    }

    private static void fillStorage(StorageStackBE storage) {
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64));
        }
    }

    /** Exercises neighboring and direct deposits under the loader's destination policy. */
    public static void checkAdjacentAndDirectDeposits(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> playerFactory,
            boolean neighborAllowed) {
        var singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        BlockPos target = singles.getBlockPos();
        BlockPos neighbor = target.west();
        helper.getLevel().setBlock(neighbor, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.STONE, 2));
        player.setPos(
                neighbor.getX() - 2.0,
                target.getY() + 0.125 - player.getEyeHeight(),
                target.getZ() + 0.125);
        ServerGestureState.set(player, StackMode.SINGLES_STACK, true);
        try {
            check(
                    InteractionChecks.use(
                            player,
                            new BlockHitResult(
                                    new Vec3(
                                            target.getX(),
                                            target.getY() + 0.125,
                                            target.getZ() + 0.125),
                                    Direction.EAST,
                                    neighbor,
                                    false)),
                    "Neighbor gesture was not consumed");
            checkEquals(neighborAllowed, !singles.isEmpty(), "Neighbor deposit policy");
            checkEquals(
                    neighborAllowed ? 1 : 2,
                    player.getMainHandItem().getCount(),
                    "Neighbor deposit hand");
            if (neighborAllowed) return;
            helper.getLevel().removeBlock(neighbor, false);
            // The service, not a real click: the loaders' destination observers would see a real
            // click on the stack as a consultation without its guard.
            check(
                    StackInteractions.handleExistingStack(
                            player,
                            InteractionHand.MAIN_HAND,
                            new BlockHitResult(
                                    new Vec3(
                                            target.getX() + 0.125,
                                            target.getY() + 0.125,
                                            target.getZ() + 0.125),
                                    Direction.WEST,
                                    target,
                                    false),
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
     * Empty grounded cells remain depositable beside occupied cells in both stack types. To
     * reproduce in-game: place a Singles or Bar Stack on stone, put an item in one bottom cell,
     * then hold the modifier and aim down through two other empty bottom cells at the stone. Each
     * deposit fills the aimed cell without changing the first item or adding an upper layer.
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
                    var box =
                            (isSingles
                                            ? SinglesCubeIdx.GRID.localShape(slot, 0)
                                            : BarCubeIdx.shapeFor(slot))
                                    .bounds();
                    double x = target.getX() + (box.minX + box.maxX) / 2;
                    double z = target.getZ() + (box.minZ + box.maxZ) / 2;
                    player.setPos(x, target.getY() + 2 - player.getEyeHeight(), z);
                    check(
                            InteractionChecks.use(
                                    player,
                                    new BlockHitResult(
                                            new Vec3(x, target.getY(), z),
                                            Direction.UP,
                                            support,
                                            false)),
                            "Grounded deposit was not consumed");
                    check(
                            !be.getItems().getStackInSlot(slot).isEmpty(),
                            "Aimed grounded cell stayed empty");
                    checkEquals(
                            2 - slot, player.getMainHandItem().getCount(), "Grounded deposit hand");
                }
                check(
                        ItemStack.matches(item, be.getItems().getStackInSlot(0)),
                        "Grounded deposits changed the first item");
                checkEquals(
                        3,
                        GameTestScaffold.occupied(be.getItems()),
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
        Runnable install(
                ServerLevel level,
                BlockPos pos,
                Consumer<Player> observed,
                Predicate<Player> allowed);
    }

    /**
     * To reproduce in-game: in a claim that allows you but denies [SomeStacks], plain-click the
     * last bar out of a Bar Stack. Cleanup carries you as the actor and removes the empty block.
     */
    public static void playerBarExtractionUsesPlayerForCleanup(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> playerFactory,
            RemovalObserver observer) {
        BarStackBE bars = GameTestScaffold.placeBar(helper, ORIGIN);
        bars.getItems().insertItem(0, new ItemStack(GameTestScaffold.firstBarItem()));
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        AtomicReference<Player> actor = new AtomicReference<>();
        Runnable unregister =
                observer.install(
                        helper.getLevel(),
                        bars.getBlockPos(),
                        actor::set,
                        ProtectionChecks::notAutomation);
        ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
        try {
            InteractionChecks.use(
                    player,
                    InteractionChecks.aimAt(
                            player,
                            bars.getBlockPos(),
                            GameTestScaffold.cellBox(BlockType.BAR_STACK, 0)));
        } finally {
            unregister.run();
            ServerGestureState.clear(player.getUUID());
        }
        check(!player.getMainHandItem().isEmpty(), "Player extraction returned nothing");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(actor.get() == player, "Bar cleanup did not carry the player");
        helper.assertBlockNotPresent(CommonRegistry.barStackBlock(), ORIGIN);
        helper.succeed();
    }

    /**
     * To reproduce in-game: in a claim that allows you but denies [SomeStacks], store two stone in
     * a Storage Stack, plain-click one out holding 63 stone, and let a hopper take the other in the
     * same tick. Mixed cleanup uses [SomeStacks], so the empty block remains until removal is
     * allowed.
     */
    public static void mixedStorageSettlementUsesAutomationForCleanup(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> playerFactory,
            Function<BlockEntity, GameTestScaffold.Automation> automation,
            RemovalObserver observer) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, ORIGIN);
        storage.getItems().insertItem(0, new ItemStack(Items.STONE, 2));
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.STONE, 63));
        AtomicReference<Player> actor = new AtomicReference<>();
        Runnable unregister =
                observer.install(
                        helper.getLevel(),
                        storage.getBlockPos(),
                        actor::set,
                        ProtectionChecks::notAutomation);
        ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
        try {
            InteractionChecks.use(
                    player,
                    InteractionChecks.aimAt(
                            player,
                            storage.getBlockPos(),
                            GameTestScaffold.cellBox(BlockType.STORAGE_STACK, 0)));
            checkEquals(64, player.getMainHandItem().getCount(), "Player extraction count");
            checkEquals(
                    1,
                    automation.apply(storage).extract(0, 1, false).getCount(),
                    "Automation extraction count");
            // Settle now, as the tick the two extractions scheduled would, so the observer is still
            // listening when the cleanup runs.
            StoragePile pile = storage.pile();
            check(pile != null, "Pile did not resolve");
            pile.settle();
        } finally {
            unregister.run();
            ServerGestureState.clear(player.getUUID());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        check(actor.get() != null, "Cleanup break event did not fire");
        checkEquals(AutomationActor.ID, actor.get().getUUID(), "Mixed cleanup actor");
        helper.assertBlockPresent(CommonRegistry.storageStackBlock(), ORIGIN);
        storage.pile().settle();
        helper.assertBlockNotPresent(CommonRegistry.storageStackBlock(), ORIGIN);
        helper.succeed();
    }

    /**
     * A player's break is charged to that player for the cleanup it causes, and a removal no
     * player made keeps the automation actor. To reproduce in-game: in a claim that allows you but
     * denies [SomeStacks], build a two-block Bar column whose upper bars rest on the lower block
     * and break the lower block. The upper block's bars drop and the emptied block is removed; an
     * explosion doing the same leaves the empty upper block standing. The Storage half has no
     * in-game form, because a settled pile never has an empty top block to trim; it stages a
     * three-block pile with an unsettled empty top, breaks the bottom, and settles the rest.
     */
    public static void breakingAStackChargesItsCleanupToTheBreaker(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> playerFactory,
            RemovalObserver observer) {
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        ServerLevel level = helper.getLevel();
        Consumer<BlockPos> playerBreak = pos -> player.gameMode.destroyBlock(pos);
        Consumer<BlockPos> plainBreak = pos -> level.destroyBlock(pos, true);

        checkBreakCleanup(
                helper, observer, barScene(helper, ORIGIN), playerBreak, player, "Player Bar");
        checkBreakCleanup(
                helper, observer, barScene(helper, ORIGIN.east(3)), plainBreak, null, "Plain Bar");
        checkBreakCleanup(
                helper,
                observer,
                storageScene(helper, ORIGIN.south(3)),
                playerBreak,
                player,
                "Player Storage");
        checkBreakCleanup(
                helper,
                observer,
                storageScene(helper, ORIGIN.east(3).south(3)),
                plainBreak,
                null,
                "Plain Storage");
        helper.succeed();
    }

    /** A block to break, the block whose cleanup the break causes, and what completes it. */
    private record BreakScene(BlockPos broken, BlockPos cleaned, Runnable afterBreak) {}

    /** A two-block Bar column whose upper bar rests only on the lower block's top layer. */
    private static BreakScene barScene(GameTestHelper helper, BlockPos relative) {
        BarStackBE lower = GameTestScaffold.placeBar(helper, relative);
        BarStackBE upper = GameTestScaffold.placeBar(helper, relative.above());
        lower.getItems()
                .insertItem(
                        BarStackBE.SLOTS - BarCubeIdx.LAYER_SIZE,
                        new ItemStack(GameTestScaffold.firstBarItem()));
        check(
                upper.depositAt(
                        BarColumnChecks.firstSeamSupportedBy(0),
                        new ItemStack(GameTestScaffold.firstBarItem())),
                "Upper Bar setup deposit failed");
        return new BreakScene(lower.getBlockPos(), upper.getBlockPos(), () -> {});
    }

    /** A three-block Storage pile whose empty top block has not yet been trimmed. */
    private static BreakScene storageScene(GameTestHelper helper, BlockPos relative) {
        StorageStackBE bottom = GameTestScaffold.placeStorage(helper, relative);
        StorageStackBE middle = GameTestScaffold.placeStorage(helper, relative.above());
        StorageStackBE top = GameTestScaffold.placeStorage(helper, relative.above(2));
        bottom.getItems().insertItem(0, new ItemStack(Items.STONE, 2));
        middle.getItems().insertItem(0, new ItemStack(Items.DIRT));
        BlockPos middlePos = middle.getBlockPos();
        return new BreakScene(
                bottom.getBlockPos(),
                top.getBlockPos(),
                () -> {
                    StoragePile upperPile = StoragePile.at(helper.getLevel(), middlePos);
                    check(upperPile != null, "Upper pile did not resolve");
                    upperPile.settle();
                });
    }

    /**
     * Breaks the scene's block while a removal observer records who removes the cleaned block and
     * refuses [SomeStacks]. A null expected actor means automation, whose removal is refused.
     */
    private static void checkBreakCleanup(
            GameTestHelper helper,
            RemovalObserver observer,
            BreakScene scene,
            Consumer<BlockPos> breaker,
            @Nullable ServerPlayer expected,
            String label) {
        ServerLevel level = helper.getLevel();
        AtomicReference<Player> actor = new AtomicReference<>();
        Runnable unregister =
                observer.install(
                        level,
                        scene.cleaned(),
                        actor::set,
                        ProtectionChecks::notAutomation);
        try {
            breaker.accept(scene.broken());
            check(level.getBlockEntity(scene.broken()) == null, label + ": break did not happen");
            scene.afterBreak().run();
        } finally {
            unregister.run();
        }
        check(actor.get() != null, label + ": cleanup removal was not attempted");
        if (expected != null) {
            check(actor.get() == expected, label + ": cleanup was not charged to the breaker");
            check(level.getBlockEntity(scene.cleaned()) == null, label + ": cleanup was refused");
        } else {
            checkEquals(
                    AutomationActor.ID,
                    actor.get().getUUID(),
                    label + ": cleanup actor");
            check(
                    level.getBlockEntity(scene.cleaned()) != null,
                    label + ": refused cleanup removed the block");
        }
    }

    /**
     * To reproduce in-game: deny stack placement over a modded replaceable container containing
     * items. The refused placement restores the container's contents. This stages the rollback
     * directly with a chest so no test-only replaceable block registration is needed. A second
     * rollback over an empty permanent Storage Stack must restore permanence without scheduling a
     * new placement/settlement tick.
     */
    public static void vetoedPlacementRestoresBlockEntity(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> playerFactory,
            Runnable installVeto,
            Runnable removeVeto) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(ORIGIN);
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(pos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        installVeto.run();
        try {
            check(
                    !WorldEdits.placeChecked(
                            playerFactory.apply(ItemStack.EMPTY),
                            level,
                            pos,
                            CommonRegistry.storageStackBlock().defaultBlockState(),
                            Direction.DOWN,
                            Shapes.block()),
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
        helper.runAfterDelay(
                3,
                () -> {
                    Block storageBlock = CommonRegistry.storageStackBlock();
                    check(
                            !level.getBlockTicks().hasScheduledTick(pos, storageBlock),
                            "Initial permanent stack settlement is still pending");
                    installVeto.run();
                    try {
                        check(
                                !WorldEdits.placeChecked(
                                        playerFactory.apply(ItemStack.EMPTY),
                                        level,
                                        pos,
                                        CommonRegistry.barStackBlock().defaultBlockState(),
                                        Direction.DOWN,
                                        Shapes.empty()),
                                "Vetoed stack replacement succeeded");
                    } finally {
                        removeVeto.run();
                    }
                    helper.assertBlockPresent(storageBlock, ORIGIN);
                    check(
                            ((StorageStackBE) level.getBlockEntity(pos)).isPermanent(),
                            "Rollback lost stack permanence");
                    check(
                            !level.getBlockTicks().hasScheduledTick(pos, storageBlock),
                            "Rollback ran the restored stack's placement hook");
                    helper.succeed();
                });
    }

    /**
     * To reproduce in-game: face an observer toward a denied placement position. A rejected
     * placement produces no observer pulse; after permitting the same placement, the observer
     * pulses.
     */
    public static void placementPublicationAnswersToVeto(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> playerFactory,
            Runnable installVeto,
            Runnable removeVeto) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(ORIGIN);
        BlockPos observer = target.west();
        level.setBlock(
                observer,
                Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.EAST),
                Block.UPDATE_ALL);
        helper.runAfterDelay(
                6,
                () -> {
                    installVeto.run();
                    try {
                        check(
                                !WorldEdits.placeChecked(
                                        playerFactory.apply(ItemStack.EMPTY),
                                        level,
                                        target,
                                        CommonRegistry.storageStackBlock().defaultBlockState(),
                                        Direction.DOWN,
                                        Shapes.block()),
                                "Vetoed placement succeeded");
                    } finally {
                        removeVeto.run();
                    }
                    helper.runAfterDelay(
                            3,
                            () -> {
                                check(
                                        !level.getBlockState(observer)
                                                .getValue(ObserverBlock.POWERED),
                                        "Denied placement pulsed its observer");
                                check(
                                        WorldEdits.placeChecked(
                                                playerFactory.apply(ItemStack.EMPTY),
                                                level,
                                                target,
                                                CommonRegistry.storageStackBlock()
                                                        .defaultBlockState(),
                                                Direction.DOWN,
                                                Shapes.block()),
                                        "Accepted placement failed");
                                ((StorageStackBE) level.getBlockEntity(target))
                                        .getItems()
                                        .insertItem(0, new ItemStack(Items.STONE));
                                helper.runAfterDelay(
                                        3,
                                        () -> {
                                            check(
                                                    level.getBlockState(observer)
                                                            .getValue(ObserverBlock.POWERED),
                                                    "Accepted placement never published to its observer");
                                            helper.succeed();
                                        });
                            });
                });
    }

    /**
     * A settle whose removal of an empty top block is refused keeps that block in both the world
     * and the run. To reproduce in-game: protect the top block of a two-block temporary Storage
     * pile from [SomeStacks], leave one item in the bottom block, and let it settle. The protected
     * empty top block remains; after allowing [SomeStacks], the next settlement removes it.
     */
    public static void settleKeepsAnEmptyTopBlockWhoseRemovalIsRefused(
            GameTestHelper helper, RemovalObserver observer) {
        BlockPos topRelative = ORIGIN.above();
        StorageStackBE base = GameTestScaffold.placeStorage(helper, ORIGIN);
        GameTestScaffold.placeStorage(helper, topRelative);
        base.getItems().insertItem(0, new ItemStack(Items.DIRT));

        Runnable unregister =
                observer.install(
                        helper.getLevel(),
                        helper.absolutePos(topRelative),
                        actor -> {},
                        actor -> false);
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
     * To reproduce in-game: in a claim that allows you but denies [SomeStacks], plain-click the
     * last item out of a Singles Stack. Its cleanup is charged to you and the empty block is
     * removed.
     */
    public static void playerExtractionUsesPlayerForCleanup(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> playerFactory,
            RemovalObserver observer) {
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        singles.getItems().insertItem(0, new ItemStack(Items.STONE));
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        AtomicReference<Player> actor = new AtomicReference<>();
        Runnable unregister =
                observer.install(
                        helper.getLevel(),
                        singles.getBlockPos(),
                        actor::set,
                        ProtectionChecks::notAutomation);
        ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
        try {
            InteractionChecks.use(
                    player,
                    InteractionChecks.aimAt(
                            player,
                            singles.getBlockPos(),
                            GameTestScaffold.cellBox(BlockType.SINGLES_STACK, 0)));
        } finally {
            unregister.run();
            ServerGestureState.clear(player.getUUID());
        }
        checkEquals(Items.STONE, player.getMainHandItem().getItem(), "Extracted item");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(actor.get() == player, "Cleanup break event did not carry the player");
        helper.assertBlockNotPresent(CommonRegistry.singlesStackBlock(), ORIGIN);
        helper.succeed();
    }

    /**
     * To reproduce in-game: in a claim that allows you but denies [SomeStacks], plain-click the
     * last item out of a Storage Stack and wait a moment. The settlement that follows keeps you as
     * the actor and removes the empty block.
     */
    public static void playerStorageSettlementUsesPlayerForCleanup(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> playerFactory,
            RemovalObserver observer) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, ORIGIN);
        storage.getItems().insertItem(0, new ItemStack(Items.STONE));
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        AtomicReference<Player> actor = new AtomicReference<>();
        Runnable unregister =
                observer.install(
                        helper.getLevel(),
                        storage.getBlockPos(),
                        actor::set,
                        ProtectionChecks::notAutomation);
        ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
        try {
            InteractionChecks.use(
                    player,
                    InteractionChecks.aimAt(
                            player,
                            storage.getBlockPos(),
                            GameTestScaffold.cellBox(BlockType.STORAGE_STACK, 0)));
            checkEquals(Items.STONE, player.getMainHandItem().getItem(), "Extracted item");
        } catch (RuntimeException failure) {
            unregister.run();
            throw failure;
        } finally {
            ServerGestureState.clear(player.getUUID());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        helper.runAfterDelay(
                2,
                () -> {
                    unregister.run();
                    check(actor.get() == player, "Deferred cleanup did not retain the player");
                    helper.assertBlockNotPresent(CommonRegistry.storageStackBlock(), ORIGIN);
                    helper.succeed();
                });
    }

    /**
     * A cascade still brings down the column above an empty Bar block whose removal protection
     * refuses: support depends on the seam's occupancy, which is empty whether or not the block
     * remains, and protection controls only the world edit. To reproduce in-game: build a
     * supported two-block Bar column inside a claim that protects the lower block from removal by
     * anyone, and plain-click its bottom support out. The empty lower block remains, while the
     * unsupported upper block comes down.
     */
    public static void refusedRemovalStillLetsTheBarsAboveComeDown(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> playerFactory,
            RemovalObserver observer) {
        BarStackBE lower = GameTestScaffold.placeBar(helper, ORIGIN);
        BarStackBE upper = GameTestScaffold.placeBar(helper, ORIGIN.above());
        Item barItem = GameTestScaffold.firstBarItem();
        BlockPos upperPos = upper.getBlockPos();

        // One bar per layer, each resting on the one below, so slot 0 carries the whole column and
        // the seam the upper block stands on is the top of that chain.
        int slot = 0;
        GameTestScaffold.seedSlot(lower.getItems(), slot, new ItemStack(barItem));
        for (int layer = 1; layer < 8; layer++) {
            slot = BarColumnChecks.firstSupportedBy(slot, layer * 8, (layer + 1) * 8);
            GameTestScaffold.seedSlot(lower.getItems(), slot, new ItemStack(barItem));
        }
        GameTestScaffold.seedSlot(
                upper.getItems(),
                BarColumnChecks.firstSeamSupportedBy(slot - 56),
                new ItemStack(barItem));

        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        Runnable unregister =
                observer.install(
                        helper.getLevel(), lower.getBlockPos(), actor -> {}, actor -> false);
        ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
        try {
            InteractionChecks.use(
                    player,
                    InteractionChecks.aimAt(
                            player,
                            lower.getBlockPos(),
                            GameTestScaffold.cellBox(BlockType.BAR_STACK, 0)));
        } finally {
            unregister.run();
            ServerGestureState.clear(player.getUUID());
        }
        checkEquals(barItem, player.getMainHandItem().getItem(), "Extracted support");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        helper.assertBlockPresent(CommonRegistry.barStackBlock(), ORIGIN);
        check(lower.isEmpty(), "The kept block held on to its bars");
        check(
                helper.getLevel().getBlockEntity(upperPos) == null,
                "The block above a kept block was not brought down");
        helper.succeed();
    }

    // Growth under protection
    //
    // An automation insertion aimed at a slot past what the run holds is the one that grows it,
    // and it checks the position above the run before promising the caller anything, so a
    // simulation and the commit that follows agree about a position growth cannot have. These
    // stage that with the world border. Spawn protection, the other half of the same predicate,
    // cannot be staged here: it is implemented on DedicatedServer, and the server running these
    // tests is not one.

    /**
     * To reproduce in-game: protect the space above a full Storage pile from SomeStacks and insert
     * through item automation; it accepts nothing and creates no block.
     */
    public static void storageGrowthAnswersToProtectionInSimulationAndCommit(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        checkProtectedGrowth(helper, automation, BlockType.STORAGE_STACK, 0);
    }

    /**
     * To reproduce in-game: protect the space above a full Singles column from SomeStacks and
     * insert through item automation; it accepts nothing and creates no block.
     */
    public static void singlesGrowthAnswersToProtectionInSimulationAndCommit(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        // A cell takes one item, so a credited growth leaves three of the four behind.
        checkProtectedGrowth(helper, automation, BlockType.SINGLES_STACK, 3);
    }

    /**
     * To reproduce in-game: protect the space above a full Bar column from SomeStacks and insert
     * through item automation; it accepts nothing and creates no block.
     */
    public static void barGrowthAnswersToProtectionInSimulationAndCommit(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        // A position takes one bar, so a credited growth leaves three of the four behind.
        checkProtectedGrowth(helper, automation, BlockType.BAR_STACK, 3);
    }

    /**
     * To reproduce in-game: stand in the growth space above a full Storage pile and insert through
     * item automation; it accepts nothing and does not grow into the entity.
     */
    public static void storageGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        checkObstructedGrowth(helper, automation, BlockType.STORAGE_STACK);
    }

    /**
     * To reproduce in-game: stand in the growth space above a full Singles column and insert
     * through item automation; it accepts nothing and does not grow into the entity.
     */
    public static void singlesGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        checkObstructedGrowth(helper, automation, BlockType.SINGLES_STACK);
    }

    /**
     * To reproduce in-game: stand in the growth space above a full Bar column and insert through
     * item automation; it accepts nothing and does not grow into the entity.
     */
    public static void barGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        checkObstructedGrowth(helper, automation, BlockType.BAR_STACK);
    }

    /**
     * Fills a one-block run of {@code type}, checks that four items aimed at its headroom are
     * credited growth while the space above is open, then refused in simulation and commit while
     * the space lies outside the world border.
     */
    private static void checkProtectedGrowth(
            GameTestHelper helper,
            Function<BlockEntity, GameTestScaffold.Automation> automation,
            BlockType type,
            int creditedRemainder) {
        GameTestScaffold.withPolicy(
                Map.of(ServerConfig.Setting.MAX_PILE_HEIGHT, 2, enableSetting(type), true),
                () -> {
                    StackBlockEntity full = fullBlock(helper, type);
                    GameTestScaffold.Automation access = automation.apply(full);
                    ItemStack offered = new ItemStack(GameTestScaffold.sampleItem(type), 4);
                    int headroom = full.getItems().getSlots();
                    checkEquals(
                            creditedRemainder,
                            access.insert(headroom, offered, true).getCount(),
                            "A full run with free headroom did not credit growth");
                    GameTestScaffold.outsideWorldBorder(
                            helper,
                            ORIGIN.above(),
                            () -> {
                                checkEquals(
                                        4,
                                        access.insert(headroom, offered, true).getCount(),
                                        "Simulated remainder");
                                checkEquals(
                                        4,
                                        access.insert(headroom, offered, false).getCount(),
                                        "Committed remainder");
                            });
                    checkEquals(4, offered.getCount(), "Input stack must not be mutated");
                    helper.assertBlockNotPresent(type.getBlock(), ORIGIN.above());
                });
        helper.succeed();
    }

    /**
     * Fills a one-block run of {@code type}, puts a cow in the space above, and checks that four
     * items aimed at its headroom are refused in simulation and commit.
     */
    private static void checkObstructedGrowth(
            GameTestHelper helper,
            Function<BlockEntity, GameTestScaffold.Automation> automation,
            BlockType type) {
        GameTestScaffold.withPolicy(
                Map.of(ServerConfig.Setting.MAX_PILE_HEIGHT, 2, enableSetting(type), true),
                () -> {
                    StackBlockEntity full = fullBlock(helper, type);
                    GameTestScaffold.Automation access = automation.apply(full);
                    ItemStack offered = new ItemStack(GameTestScaffold.sampleItem(type), 4);
                    int headroom = full.getItems().getSlots();
                    putCowIn(helper, ORIGIN.above());
                    checkEquals(
                            4,
                            access.insert(headroom, offered, true).getCount(),
                            "Simulated remainder");
                    checkEquals(
                            4,
                            access.insert(headroom, offered, false).getCount(),
                            "Committed remainder");
                    checkEquals(4, offered.getCount(), "Input stack must not be mutated");
                    helper.assertBlockNotPresent(type.getBlock(), ORIGIN.above());
                });
        helper.succeed();
    }

    private static ServerConfig.Setting enableSetting(BlockType type) {
        return switch (type) {
            case STORAGE_STACK -> ServerConfig.Setting.ENABLE_STORAGE_STACK_BLOCK;
            case SINGLES_STACK -> ServerConfig.Setting.ENABLE_SINGLES_STACK_BLOCK;
            case BAR_STACK -> ServerConfig.Setting.ENABLE_BAR_STACK_BLOCK;
        };
    }

    private static StackBlockEntity fullBlock(GameTestHelper helper, BlockType type) {
        StackBlockEntity block =
                GameTestScaffold.place(helper, ORIGIN, type.getBlock(), StackBlockEntity.class);
        GameTestScaffold.fill(block, GameTestScaffold.sampleItem(type));
        return block;
    }

    /** Lets every actor but [SomeStacks] remove a block, as a claim trusting only players does. */
    private static boolean notAutomation(Player actor) {
        return !actor.getUUID().equals(AutomationActor.ID);
    }

    private static BlockHitResult centerHit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }
}
