package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.network.GestureStatePkt;
import com.github.crittscott.somestacks.server.RotationSoundThrottle;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.server.StackInteractions;
import com.github.crittscott.somestacks.util.StackMode;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/** Loader-neutral checks for the state packet and vanilla-interaction gesture service. */
public final class InteractionChecks {
    public static final BlockPos TARGET = new BlockPos(2, 1, 2);
    public static final InteractionHand HAND = InteractionHand.MAIN_HAND;

    private InteractionChecks() {}

    @FunctionalInterface
    public interface LoaderBlockClick {
        boolean click(ServerPlayer player, BlockHitResult hit);
    }

    /**
     * A gesture-state packet with an invalid mode ordinal fails during decoding. No in-game
     * reproduction applies: this is a malformed-wire boundary that the normal client cannot send.
     */
    public static void malformedGestureStateFailsDuringDecoding(GameTestHelper helper) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(StackMode.values().length);
        buffer.writeBoolean(false);
        try {
            check(decodeGestureFails(buffer),
                    "Invalid stack-mode ordinal reached the state handler");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static boolean decodeGestureFails(ByteBuf buffer) {
        try {
            GestureStatePkt.STREAM_CODEC.decode(buffer);
            return false;
        } catch (RuntimeException expected) {
            return true;
        }
    }

    /**
     * The server records the client's selected mode and current modifier state. To reproduce
     * in-game: cycle to Bar mode, hold the modifier, and right-click an eligible item against a
     * block. The server performs the Bar placement rather than another mode or an ordinary use.
     */
    public static void gestureStateTracksModeAndModifier(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        try {
            GestureStatePkt.handleServer(
                    new GestureStatePkt(StackMode.BAR_STACK, true), player);
            ServerGestureState.State state = ServerGestureState.get(player);
            checkEquals(StackMode.BAR_STACK, state.mode(), "Server gesture mode");
            check(state.modifierDown(), "Server lost the gesture modifier state");
        } finally {
            ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * Stack gestures act only from the main hand. To reproduce in-game: leave the main hand empty,
     * hold a depositable item in the off hand, hold the modifier, and right-click a stack. The
     * off-hand item is not deposited.
     */
    public static void interactionReadsOnlyTheMainHand(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, TARGET);
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 4));
        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        try {
            check(!StackInteractions.handleExistingStack(
                            player, InteractionHand.OFF_HAND,
                            hit(player, storage.getBlockPos(), 0.5, 0.5, 0.5),
                    com.github.crittscott.somestacks.util.BlockType.STORAGE_STACK),
                    "Off-hand interaction was consumed");
            checkEquals(0, GameTestScaffold.count(storage.getItems(), Items.DIRT),
                    "Off-hand item was deposited");
        } finally {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * Redstone torches rotate Storage and Singles blocks, soul torches rotate Singles items, and
     * Bar blocks do not rotate. To reproduce in-game: Shift-right-click each type with those torches
     * and observe that only the matching gestures change orientation.
     */
    public static void rotationValidatesHeldItemAndBlockType(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, TARGET);
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, TARGET.east(3));
        var bars = GameTestScaffold.placeBar(helper, TARGET.east(6));
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.REDSTONE_TORCH));
        player.setShiftKeyDown(true);
        try {
            rotationClick(player, storage.getBlockPos(), 0.5, 0.5, 0.5);
            checkEquals(1, storage.getRotation(), "Storage rotation");

            player.setItemInHand(HAND, new ItemStack(Items.SOUL_TORCH));
            rotationClick(player, singles.getBlockPos(), 0.125, 0.125, 0.125);
            checkEquals(0, singles.getRotation(), "Wrong torch rotated Singles");

            player.setItemInHand(HAND, new ItemStack(Items.REDSTONE_TORCH));
            rotationClick(player, singles.getBlockPos(), 0.125, 0.125, 0.125);
            checkEquals(1, singles.getRotation(), "Singles rotation");

            rotationClick(player, bars.getBlockPos(), 0.25, 0.0625, 0.125);
            check(helper.getLevel().getBlockEntity(bars.getBlockPos()) == bars,
                    "Rotate-block gesture changed a Bar Stack");
        } finally {
            player.setShiftKeyDown(false);
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
            RotationSoundThrottle.clear(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * Sneaking right-clicks reach the loader's real server interaction hook for both block and item
     * rotation. To reproduce in-game: Shift-right-click Storage with a redstone torch and an
     * occupied Singles cell with a soul torch. The block and item rotate respectively.
     */
    public static void sneakingRotationsReachTheLoaderHook(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory,
            LoaderBlockClick loaderClick) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, TARGET);
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, TARGET.east(3));
        check(singles.depositAt(0, new ItemStack(Items.STICK)),
                "Singles fixture deposit failed");
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.REDSTONE_TORCH));
        player.setShiftKeyDown(true);
        try {
            check(loaderClick.click(
                            player, hit(player, storage.getBlockPos(), 0.5, 0.5, 0.5)),
                    "Loader hook declined redstone-torch rotation");
            checkEquals(1, storage.getRotation(), "Loader-hook block rotation");

            player.setItemInHand(HAND, new ItemStack(Items.SOUL_TORCH));
            check(loaderClick.click(
                            player, hit(player, singles.getBlockPos(), 0.125, 0.125, 0.125)),
                    "Loader hook declined soul-torch rotation");
            checkEquals(1, singles.getCubeRotation(0), "Loader-hook item rotation");
        } finally {
            player.setShiftKeyDown(false);
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
            RotationSoundThrottle.clear(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * Vanilla block-use interactions reach deposit, extraction, and adjacent placement. To
     * reproduce in-game: modifier-click an existing Storage pile with an item, plain-click it with
     * an empty hand, then modifier-click the top of an ordinary block. The item deposits, extracts,
     * and creates a new Storage stack in the three cases.
     */
    public static void vanillaInteractionReachesDepositExtractAndPlacement(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        StorageStackBE depositTarget = GameTestScaffold.placeStorage(helper, TARGET);
        StorageStackBE extractTarget = GameTestScaffold.placeStorage(helper, TARGET.east(3));
        int center = StorageCubeIdx.GRID_EDGE * StorageCubeIdx.GRID_EDGE
                + StorageCubeIdx.GRID_EDGE + 1;
        extractTarget.getItems().insertItem(center, new ItemStack(Items.STICK, 3), false);
        BlockPos support = helper.absolutePos(TARGET.east(6));
        helper.getLevel().setBlock(support, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.DIRT, 4));

        try {
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            click(player, depositTarget.getBlockPos(), 0.5, 0.5, 0.5);
            checkEquals(4, GameTestScaffold.count(depositTarget.getItems(), Items.DIRT),
                    "Deposit contents");

            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            click(player, extractTarget.getBlockPos(), 0.5, 0.5, 0.5);
            checkEquals(Items.STICK, player.getMainHandItem().getItem(), "Extracted item");
            checkEquals(3, player.getMainHandItem().getCount(), "Extracted count");

            player.setItemInHand(HAND, new ItemStack(Items.DIRT, 2));
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            BlockPos destination = support.above();
            StackInteractions.handleAdjacentClick(
                    player, HAND,
                    new BlockHitResult(
                            new Vec3(support.getX() + 0.5, support.getY() + 1.0,
                                    support.getZ() + 0.5),
                            Direction.UP, support, false),
                    true, true);
            check(helper.getLevel().getBlockState(destination)
                            .is(CommonRegistry.storageStackBlock()),
                    "Vanilla interaction did not place Storage");
            checkEquals(2, GameTestScaffold.heldAt(helper, destination, Items.DIRT),
                    "Placed stack contents");
        } finally {
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * Item rotation affects only the occupied Singles cell on the view ray. To reproduce in-game:
     * Shift-right-click an occupied cell with a soul torch, then an empty cell beside it. The first
     * item rotates once and the empty-cell click changes nothing.
     */
    public static void itemRotationTargetsOnlyAnOccupiedCell(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, TARGET);
        int occupied = 0;
        check(singles.depositAt(occupied, new ItemStack(Items.STICK)),
                "Singles fixture deposit failed");
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.SOUL_TORCH));
        player.setShiftKeyDown(true);
        try {
            rotationClick(player, singles.getBlockPos(), 0.125, 0.125, 0.125);
            checkEquals(1, singles.getCubeRotation(occupied), "Occupied item rotation");

            int unobstructedEmpty = 4;
            rotationClick(player, singles.getBlockPos(), 0.125, 0.125, 0.375);
            checkEquals(0, singles.getCubeRotation(unobstructedEmpty),
                    "Empty cell retained rotation");
            checkEquals(1, singles.getCubeRotation(occupied),
                    "Clicking an empty cell rotated another item");
        } finally {
            player.setShiftKeyDown(false);
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
            RotationSoundThrottle.clear(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * Permanence requires an empty main hand and obeys world protection. To reproduce in-game:
     * select permanence mode and modifier-click Storage first with an item, then empty-handed, then
     * inside a claim that denies the player. Only the unprotected empty-hand click toggles it.
     */
    public static void permanenceRequiresEmptyHandAndHonorsProtection(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, TARGET);
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.STONE));
        ServerGestureState.set(player, StackMode.TOGGLE_PERMANENT, true);
        try {
            click(player, storage.getBlockPos(), 0.5, 0.5, 0.5);
            check(!storage.pile().isPermanent(), "Occupied hand toggled permanence");

            player.setItemInHand(HAND, ItemStack.EMPTY);
            click(player, storage.getBlockPos(), 0.5, 0.5, 0.5);
            check(storage.pile().isPermanent(), "Empty hand did not toggle permanence");

            storage.pile().setPermanent(false);
            GameTestScaffold.outsideWorldBorder(helper, TARGET, () ->
                    click(player, storage.getBlockPos(), 0.5, 0.5, 0.5));
            check(!storage.pile().isPermanent(), "Protected pile changed permanence");
        } finally {
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * Extraction refuses an incompatible held item or a compatible stack with no room. To
     * reproduce in-game: plain-click stored stone while holding dirt, then while holding a full
     * stack of matching stone. The Storage contents and held stacks remain unchanged.
     */
    public static void extractionRefusesIncompatibleOrFullHands(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, TARGET);
        int center = 13;
        storage.getItems().insertItem(center, new ItemStack(Items.STONE, 8), false);
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.DIRT));
        try {
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            click(player, storage.getBlockPos(), 0.5, 0.5, 0.5);
            checkEquals(Items.DIRT, player.getMainHandItem().getItem(), "Held item was replaced");
            checkEquals(8, storage.getItems().getStackInSlot(center).getCount(),
                    "Incompatible hand extracted items");

            ItemStack full = new ItemStack(Items.STONE);
            full.setCount(full.getMaxStackSize());
            player.setItemInHand(HAND, full);
            click(player, storage.getBlockPos(), 0.5, 0.5, 0.5);
            checkEquals(full.getMaxStackSize(), player.getMainHandItem().getCount(),
                    "Full hand grew past its maximum");
            checkEquals(8, storage.getItems().getStackInSlot(center).getCount(),
                    "Full hand extracted items");
        } finally {
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * To reproduce in-game: fill a Singles block, select Storage mode, hold an item, and
     * modifier-click its top face. A Storage Stack appears above and receives the held items.
     */
    public static void fullTopFacePlacesTheSelectedStackType(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, TARGET);
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            singles.getItems().insertItem(slot, new ItemStack(Items.DIRT), false);
        }
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.STONE, 2));
        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        BlockPos destination = singles.getBlockPos().above();
        try {
            StackInteractions.handleExistingStack(
                    player,
                    HAND,
                    new BlockHitResult(
                            new Vec3(
                                    singles.getBlockPos().getX() + 0.5,
                                    singles.getBlockPos().getY() + 1.0,
                                    singles.getBlockPos().getZ() + 0.5),
                            Direction.UP,
                            singles.getBlockPos(),
                            false),
                    com.github.crittscott.somestacks.util.BlockType.SINGLES_STACK);
            check(helper.getLevel().getBlockState(destination)
                            .is(CommonRegistry.storageStackBlock()),
                    "Top-face placement ignored the selected type");
            checkEquals(2, GameTestScaffold.heldAt(helper, destination, Items.STONE),
                    "Placed Storage contents");
            check(player.getMainHandItem().isEmpty(), "Successful placement did not spend hand");
        } finally {
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * To reproduce in-game: extend a piston into each SomeStacks block type. Storage, Singles, and
     * Bar blocks remain in place and the piston cannot push them.
     */
    public static void everyStackTypeBlocksPistons(GameTestHelper helper) {
        checkEquals(PushReaction.BLOCK,
                CommonRegistry.storageStackBlock().defaultBlockState()
                        .getPistonPushReaction(),
                "Storage piston reaction");
        checkEquals(PushReaction.BLOCK,
                CommonRegistry.singlesStackBlock().defaultBlockState()
                        .getPistonPushReaction(),
                "Singles piston reaction");
        checkEquals(PushReaction.BLOCK,
                CommonRegistry.barStackBlock().defaultBlockState()
                        .getPistonPushReaction(),
                "Bar piston reaction");
        helper.succeed();
    }

    /**
     * Rotation sound pacing permits one sound per player within its throttle window and resets when
     * player state clears. To reproduce in-game: perform rotation gestures faster than the throttle
     * interval, then pause and rotate again. The rapid repeats do not each play a sound; the later
     * rotation does.
     */
    public static void rotationSoundThrottleSuppressesSameTickAndClears(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        RotationSoundThrottle.clear(player.getUUID());
        try {
            check(RotationSoundThrottle.claim(player), "First rotation sound was refused");
            check(!RotationSoundThrottle.claim(player),
                    "Second same-tick rotation sound succeeded");
            RotationSoundThrottle.clear(player.getUUID());
            check(RotationSoundThrottle.claim(player), "Clear did not reset sound pacing");
        } finally {
            RotationSoundThrottle.clear(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * A successful mutation inside a stack emits one block-change game event. To reproduce
     * in-game: put a sculk sensor beside a Storage Stack and deposit an item; the sensor activates.
     */
    public static void playerMutationEmitsBlockChange(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, TARGET);
        BlockPos sensorPos = helper.absolutePos(TARGET.east(2));
        check(helper.getLevel().setBlock(
                        sensorPos, Blocks.SCULK_SENSOR.defaultBlockState(), Block.UPDATE_ALL),
                "Could not place sculk sensor");
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.DIRT));
        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        try {
            check(click(player, storage.getBlockPos(), 0.5, 0.5, 0.5),
                    "Storage deposit interaction was not consumed");
        } finally {
            ServerGestureState.clear(player.getUUID());
        }

        helper.runAfterDelay(5, () -> {
            checkEquals(
                    SculkSensorPhase.ACTIVE,
                    helper.getLevel().getBlockState(sensorPos).getValue(SculkSensorBlock.PHASE),
                    "Sculk sensor phase after stack mutation");
            helper.succeed();
        });
    }

    /**
     * To reproduce in-game: extract one of two Singles or Bars beside a sculk sensor, then the last.
     * The surviving block emits only change; removing the last item emits only destroy.
     */
    public static void extractionEventsFollowBlockSurvival(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        for (boolean bar : new boolean[] {false, true}) {
            var be = bar ? GameTestScaffold.placeBar(helper, TARGET)
                    : GameTestScaffold.placeSingles(helper, TARGET);
            var item = bar ? GameTestScaffold.firstBarItem() : Items.STONE;
            be.getItems().insertItem(0, new ItemStack(item), false);
            be.getItems().insertItem(1, new ItemStack(item), false);
            ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            try {
                double y = bar ? 0.0625 : 0.125;
                List<Holder<GameEvent>> first = observeGameEvents(helper.getLevel(), be.getBlockPos(),
                        () -> click(player, be.getBlockPos(), 0.125, y, 0.125));
                checkEquals(List.of(GameEvent.BLOCK_CHANGE), first, "Surviving extraction events");
                List<Holder<GameEvent>> last = observeGameEvents(helper.getLevel(), be.getBlockPos(),
                        () -> click(player, be.getBlockPos(), bar ? 0.75 : 0.375, y, 0.125));
                checkEquals(List.of(GameEvent.BLOCK_DESTROY), last, "Last extraction events");
                check(helper.getLevel().getBlockEntity(be.getBlockPos()) == null,
                        "Last extraction did not remove the block");
            } finally {
                ServerGestureState.clear(player.getUUID());
                player.setItemInHand(HAND, ItemStack.EMPTY);
            }
        }
        helper.succeed();
    }

    /**
     * To reproduce in-game: deny cleanup of a Singles or Bar block, then extract its final item.
     * The empty block survives and emits one change vibration, with no destroy vibration.
     */
    public static void deniedCleanupEmitsOnlyBlockChange(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory,
            ProtectionChecks.RemovalObserver observer) {
        for (boolean bar : new boolean[] {false, true}) {
            var be = bar ? GameTestScaffold.placeBar(helper, TARGET)
                    : GameTestScaffold.placeSingles(helper, TARGET);
            be.getItems().insertItem(0,
                    new ItemStack(bar ? GameTestScaffold.firstBarItem() : Items.STONE), false);
            ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
            Runnable unregister = observer.install(helper.getLevel(), be.getBlockPos(),
                    actor -> {}, actor -> false);
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            try {
                List<Holder<GameEvent>> events = observeGameEvents(helper.getLevel(), be.getBlockPos(),
                        () -> click(player, be.getBlockPos(), 0.125, bar ? 0.0625 : 0.125, 0.125));
                checkEquals(List.of(GameEvent.BLOCK_CHANGE), events, "Denied-cleanup events");
                check(helper.getLevel().getBlockEntity(be.getBlockPos()) == be && be.isEmpty(),
                        "Denied cleanup did not leave the empty block");
            } finally {
                unregister.run();
                ServerGestureState.clear(player.getUUID());
                player.setItemInHand(HAND, ItemStack.EMPTY);
            }
        }
        helper.succeed();
    }

    private static List<Holder<GameEvent>> observeGameEvents(ServerLevel level, BlockPos pos,
                                                            Runnable mutation) {
        var events = new ArrayList<Holder<GameEvent>>();
        GameEventListener listener = new GameEventListener() {
            @Override
            public PositionSource getListenerSource() { return new BlockPositionSource(pos); }

            @Override
            public int getListenerRadius() { return 16; }

            @Override
            public boolean handleGameEvent(ServerLevel sourceLevel, Holder<GameEvent> event,
                                           GameEvent.Context context, Vec3 source) {
                if (BlockPos.containing(source).equals(pos)
                        && (event.equals(GameEvent.BLOCK_CHANGE) || event.equals(GameEvent.BLOCK_DESTROY))) {
                    events.add(event);
                }
                return true;
            }
        };
        var registry = level.getChunkAt(pos).getListenerRegistry(SectionPos.blockToSectionCoord(pos.getY()));
        registry.register(listener);
        try {
            mutation.run();
        } finally {
            registry.unregister(listener);
        }
        return events;
    }

    private static boolean click(ServerPlayer player, BlockPos pos,
                                 double localX, double localY, double localZ) {
        return StackInteractions.handleExistingStack(
                player, HAND, hit(player, pos, localX, localY, localZ),
                    com.github.crittscott.somestacks.util.BlockType.of(player.level().getBlockState(pos).getBlock()));
    }

    private static boolean rotationClick(ServerPlayer player, BlockPos pos,
                                         double localX, double localY, double localZ) {
        return StackInteractions.handleSneakingRotation(
                player, HAND, hit(player, pos, localX, localY, localZ), true, true);
    }

    private static BlockHitResult hit(ServerPlayer player, BlockPos pos,
                                      double localX, double localY, double localZ) {
        Vec3 point = new Vec3(pos.getX() + localX, pos.getY() + localY, pos.getZ() + localZ);
        player.setPos(pos.getX() - 2.0, point.y - player.getEyeHeight(), point.z);
        return new BlockHitResult(point, Direction.WEST, pos, false);
    }
}
