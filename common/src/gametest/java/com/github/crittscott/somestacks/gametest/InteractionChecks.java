package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.network.GestureStatePkt;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.server.StackInteractions;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.StackMode;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Loader-neutral checks for the state packet and vanilla-interaction gesture service. */
public final class InteractionChecks {
    public static final InteractionHand HAND = InteractionHand.MAIN_HAND;
    public static final BlockPos TARGET = new BlockPos(2, 1, 2);

    /** The middle Storage cell, which a click through the center of the block reaches. */
    private static final int STORAGE_CENTER =
            StorageCubeIdx.GRID_EDGE * StorageCubeIdx.GRID_EDGE + StorageCubeIdx.GRID_EDGE + 1;

    private InteractionChecks() {}

    /**
     * Makes the loader deny block use at a position, the way a claim or protection mod does; the
     * returned action lifts the denial.
     */
    @FunctionalInterface
    public interface BlockUseDenial {
        Runnable denyAt(ServerLevel level, BlockPos pos);
    }

    /**
     * Every mode round-trips through the gesture-state packet under its fixed wire id, and an
     * unknown mode id or a missing modifier flag fails during decoding. No in-game reproduction
     * applies: this guards the one client-to-server input and the stability of its wire ids, which
     * a normal client cannot violate.
     */
    public static void malformedGestureStateFailsDuringDecoding(GameTestHelper helper) {
        Map<StackMode, Integer> wireIds =
                Map.of(
                        StackMode.STORAGE_STACK, 0,
                        StackMode.SINGLES_STACK, 1,
                        StackMode.BAR_STACK, 2,
                        StackMode.TOGGLE_PERMANENT, 3);
        checkEquals(StackMode.values().length, wireIds.size(), "Wire id table is incomplete");
        for (StackMode mode : StackMode.values()) {
            for (boolean modifier : new boolean[] {false, true}) {
                ByteBuf buffer = Unpooled.buffer();
                try {
                    GestureStatePkt packet = new GestureStatePkt(mode, modifier);
                    GestureStatePkt.STREAM_CODEC.encode(buffer, packet);
                    checkEquals(
                            (int) wireIds.get(mode),
                            (int) buffer.getUnsignedByte(0),
                            "Wire id of " + mode);
                    checkEquals(
                            packet, GestureStatePkt.STREAM_CODEC.decode(buffer), "Round trip");
                    checkEquals(0, buffer.readableBytes(), "Trailing gesture bytes");
                } finally {
                    buffer.release();
                }
            }
        }
        for (int invalid : new int[] {StackMode.values().length, 255}) {
            ByteBuf buffer = Unpooled.buffer();
            buffer.writeByte(invalid);
            buffer.writeBoolean(false);
            try {
                check(
                        decodeGestureFails(buffer),
                        "Invalid stack-mode id " + invalid + " reached the state handler");
            } finally {
                buffer.release();
            }
        }
        ByteBuf truncated = Unpooled.buffer();
        truncated.writeByte(StackMode.STORAGE_STACK.id());
        try {
            check(decodeGestureFails(truncated), "Gesture state without a modifier flag decoded");
        } finally {
            truncated.release();
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
            GestureStatePkt.handleServer(new GestureStatePkt(StackMode.BAR_STACK, true), player);
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
            check(
                    !StackInteractions.handleExistingStack(
                            player,
                            InteractionHand.OFF_HAND,
                            aim(player, storage.getBlockPos(), 0.5, 0.5, 0.5),
                            BlockType.STORAGE_STACK),
                    "Off-hand interaction was consumed");
            checkEquals(
                    0,
                    GameTestScaffold.count(storage.getItems(), Items.DIRT),
                    "Off-hand item was deposited");
        } finally {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * Redstone torches rotate Storage and Singles blocks, soul torches rotate Singles items, and
     * Bar blocks do not rotate. To reproduce in-game: Shift-right-click each type with those
     * torches and observe that only the matching gestures change orientation.
     */
    public static void rotationValidatesHeldItemAndBlockType(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, TARGET);
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, TARGET.east(3));
        var bars = GameTestScaffold.placeBar(helper, TARGET.east(6));
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.REDSTONE_TORCH));
        player.setShiftKeyDown(true);
        try {
            click(player, storage.getBlockPos(), 0.5, 0.5, 0.5);
            checkEquals(1, storage.getRotation(), "Storage rotation");

            player.setItemInHand(HAND, new ItemStack(Items.SOUL_TORCH));
            click(player, singles.getBlockPos(), 0.125, 0.125, 0.125);
            checkEquals(0, singles.getRotation(), "Wrong torch rotated Singles");

            player.setItemInHand(HAND, new ItemStack(Items.REDSTONE_TORCH));
            click(player, singles.getBlockPos(), 0.125, 0.125, 0.125);
            checkEquals(1, singles.getRotation(), "Singles rotation");

            click(player, bars.getBlockPos(), 0.25, 0.0625, 0.125);
            check(
                    helper.getLevel().getBlockEntity(bars.getBlockPos()) == bars,
                    "Rotate-block gesture changed a Bar Stack");
        } finally {
            player.setShiftKeyDown(false);
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
            StackInteractions.clearRotationSoundPacing(player.getUUID());
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
        extractTarget.getItems().insertItem(STORAGE_CENTER, new ItemStack(Items.STICK, 3), false);
        BlockPos support = helper.absolutePos(TARGET.east(6));
        helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.DIRT, 4));

        try {
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            click(player, depositTarget.getBlockPos(), 0.5, 0.5, 0.5);
            checkEquals(
                    4,
                    GameTestScaffold.count(depositTarget.getItems(), Items.DIRT),
                    "Deposit contents");

            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            click(player, extractTarget.getBlockPos(), 0.5, 0.5, 0.5);
            checkEquals(Items.STICK, player.getMainHandItem().getItem(), "Extracted item");
            checkEquals(3, player.getMainHandItem().getCount(), "Extracted count");

            player.setItemInHand(HAND, new ItemStack(Items.DIRT, 2));
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            BlockPos destination = support.above();
            use(
                    player,
                    new BlockHitResult(
                            new Vec3(
                                    support.getX() + 0.5,
                                    support.getY() + 1.0,
                                    support.getZ() + 0.5),
                            Direction.UP,
                            support,
                            false));
            check(
                    helper.getLevel()
                            .getBlockState(destination)
                            .is(CommonRegistry.storageStackBlock()),
                    "Vanilla interaction did not place Storage");
            checkEquals(
                    2,
                    GameTestScaffold.heldAt(helper, destination, Items.DIRT),
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
        check(
                singles.depositAt(occupied, new ItemStack(Items.STICK)),
                "Singles fixture deposit failed");
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.SOUL_TORCH));
        player.setShiftKeyDown(true);
        try {
            click(player, singles.getBlockPos(), 0.125, 0.125, 0.125);
            checkEquals(1, singles.getCubeRotation(occupied), "Occupied item rotation");

            int unobstructedEmpty = 4;
            click(player, singles.getBlockPos(), 0.125, 0.125, 0.375);
            checkEquals(
                    0, singles.getCubeRotation(unobstructedEmpty), "Empty cell retained rotation");
            checkEquals(
                    1,
                    singles.getCubeRotation(occupied),
                    "Clicking an empty cell rotated another item");
        } finally {
            player.setShiftKeyDown(false);
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
            StackInteractions.clearRotationSoundPacing(player.getUUID());
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

            storage.pile().setPermanent(false, null);
            GameTestScaffold.outsideWorldBorder(
                    helper, TARGET, () -> click(player, storage.getBlockPos(), 0.5, 0.5, 0.5));
            check(!storage.pile().isPermanent(), "Protected pile changed permanence");
        } finally {
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * Extraction refuses an incompatible held item, the same item with different data components,
     * or a compatible stack with no room, and a compatible partial stack takes only what fits. To
     * reproduce in-game: store 8 stone, then plain-click it while holding dirt, while holding
     * renamed stone, and while holding a full stack of plain stone; the Storage contents and held
     * stacks remain unchanged. Then plain-click it holding 60 plain stone: the hand reaches 64 and 4
     * stone stay stored, so none were taken out only to be dropped.
     */
    public static void extractionRefusesIncompatibleOrFullHands(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, TARGET);
        int center = STORAGE_CENTER;
        storage.getItems().insertItem(center, new ItemStack(Items.STONE, 8), false);
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.DIRT));
        try {
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            click(player, storage.getBlockPos(), 0.5, 0.5, 0.5);
            checkEquals(Items.DIRT, player.getMainHandItem().getItem(), "Held item was replaced");
            checkEquals(
                    8,
                    storage.getItems().getStackInSlot(center).getCount(),
                    "Incompatible hand extracted items");

            ItemStack full = new ItemStack(Items.STONE);
            full.setCount(full.getMaxStackSize());
            player.setItemInHand(HAND, full);
            click(player, storage.getBlockPos(), 0.5, 0.5, 0.5);
            checkEquals(
                    full.getMaxStackSize(),
                    player.getMainHandItem().getCount(),
                    "Full hand grew past its maximum");
            checkEquals(
                    8,
                    storage.getItems().getStackInSlot(center).getCount(),
                    "Full hand extracted items");

            CompoundTag variant = new CompoundTag();
            variant.putInt("variant", 1);
            ItemStack componentHand = new ItemStack(Items.STONE);
            componentHand.set(DataComponents.CUSTOM_DATA, CustomData.of(variant));
            player.setItemInHand(HAND, componentHand);
            click(player, storage.getBlockPos(), 0.5, 0.5, 0.5);
            checkEquals(
                    1, player.getMainHandItem().getCount(), "Component-distinct hand changed");
            checkEquals(
                    CustomData.of(variant),
                    player.getMainHandItem().get(DataComponents.CUSTOM_DATA),
                    "Component-distinct hand lost its data");
            checkEquals(
                    8,
                    storage.getItems().getStackInSlot(center).getCount(),
                    "Component-distinct hand extracted items");

            player.setItemInHand(HAND, new ItemStack(Items.STONE, 60));
            click(player, storage.getBlockPos(), 0.5, 0.5, 0.5);
            checkEquals(64, player.getMainHandItem().getCount(), "Partial hand did not fill");
            checkEquals(
                    4,
                    GameTestScaffold.count(storage.getItems(), Items.STONE),
                    "Partial hand took more than fits");
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
            use(
                    player,
                    new BlockHitResult(
                            new Vec3(
                                    singles.getBlockPos().getX() + 0.5,
                                    singles.getBlockPos().getY() + 1.0,
                                    singles.getBlockPos().getZ() + 0.5),
                            Direction.UP,
                            singles.getBlockPos(),
                            false));
            check(
                    helper.getLevel()
                            .getBlockState(destination)
                            .is(CommonRegistry.storageStackBlock()),
                    "Top-face placement ignored the selected type");
            checkEquals(
                    2,
                    GameTestScaffold.heldAt(helper, destination, Items.STONE),
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
        checkEquals(
                PushReaction.BLOCK,
                CommonRegistry.storageStackBlock().defaultBlockState().getPistonPushReaction(),
                "Storage piston reaction");
        checkEquals(
                PushReaction.BLOCK,
                CommonRegistry.singlesStackBlock().defaultBlockState().getPistonPushReaction(),
                "Singles piston reaction");
        checkEquals(
                PushReaction.BLOCK,
                CommonRegistry.barStackBlock().defaultBlockState().getPistonPushReaction(),
                "Bar piston reaction");
        helper.succeed();
    }

    /**
     * Rotation sound pacing permits one sound per player within its throttle window and resets when
     * player state clears. To reproduce in-game: perform rotation gestures faster than the throttle
     * interval, disconnect and reconnect, then rotate immediately. Rapid repeats are suppressed;
     * the reconnect clears the old player's pacing state so the first new rotation can sound.
     */
    public static void rotationSoundThrottleSuppressesSameTickAndClears(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        StackInteractions.clearRotationSoundPacing(player.getUUID());
        try {
            check(StackInteractions.claimRotationSound(player), "First rotation sound was refused");
            check(
                    !StackInteractions.claimRotationSound(player),
                    "Second same-tick rotation sound succeeded");
            StackInteractions.clearRotationSoundPacing(player.getUUID());
            check(StackInteractions.claimRotationSound(player), "Clear did not reset sound pacing");
        } finally {
            StackInteractions.clearRotationSoundPacing(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * A successful mutation inside a stack emits one block-change game event. To reproduce in-game:
     * put a sculk sensor beside a Storage Stack and deposit an item; the sensor activates.
     */
    public static void playerMutationEmitsBlockChange(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, TARGET);
        BlockPos sensorPos = helper.absolutePos(TARGET.east(2));
        check(
                helper.getLevel()
                        .setBlock(
                                sensorPos,
                                Blocks.SCULK_SENSOR.defaultBlockState(),
                                Block.UPDATE_ALL),
                "Could not place sculk sensor");
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.DIRT));
        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        try {
            check(
                    click(player, storage.getBlockPos(), 0.5, 0.5, 0.5),
                    "Storage deposit interaction was not consumed");
        } finally {
            ServerGestureState.clear(player.getUUID());
        }

        helper.runAfterDelay(
                5,
                () -> {
                    checkEquals(
                            SculkSensorPhase.ACTIVE,
                            helper.getLevel()
                                    .getBlockState(sensorPos)
                                    .getValue(SculkSensorBlock.PHASE),
                            "Sculk sensor phase after stack mutation");
                    helper.succeed();
                });
    }

    /**
     * To reproduce in-game: extract one of two Singles or Bars beside a sculk sensor, then the
     * last. The surviving block emits only change; removing the last item emits only destroy.
     */
    public static void extractionEventsFollowBlockSurvival(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        for (boolean bar : new boolean[] {false, true}) {
            var be =
                    bar
                            ? GameTestScaffold.placeBar(helper, TARGET)
                            : GameTestScaffold.placeSingles(helper, TARGET);
            var item = bar ? GameTestScaffold.firstBarItem() : Items.STONE;
            be.getItems().insertItem(0, new ItemStack(item), false);
            be.getItems().insertItem(1, new ItemStack(item), false);
            ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            try {
                double y = bar ? 0.0625 : 0.125;
                List<Holder<GameEvent>> first =
                        observeGameEvents(
                                helper.getLevel(),
                                be.getBlockPos(),
                                () -> click(player, be.getBlockPos(), 0.125, y, 0.125));
                checkEquals(List.of(GameEvent.BLOCK_CHANGE), first, "Surviving extraction events");
                List<Holder<GameEvent>> last =
                        observeGameEvents(
                                helper.getLevel(),
                                be.getBlockPos(),
                                () ->
                                        click(
                                                player,
                                                be.getBlockPos(),
                                                bar ? 0.75 : 0.375,
                                                y,
                                                0.125));
                checkEquals(List.of(GameEvent.BLOCK_DESTROY), last, "Last extraction events");
                check(
                        helper.getLevel().getBlockEntity(be.getBlockPos()) == null,
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
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> playerFactory,
            ProtectionChecks.RemovalObserver observer) {
        for (boolean bar : new boolean[] {false, true}) {
            var be =
                    bar
                            ? GameTestScaffold.placeBar(helper, TARGET)
                            : GameTestScaffold.placeSingles(helper, TARGET);
            be.getItems()
                    .insertItem(
                            0,
                            new ItemStack(bar ? GameTestScaffold.firstBarItem() : Items.STONE),
                            false);
            ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
            Runnable unregister =
                    observer.install(
                            helper.getLevel(), be.getBlockPos(), actor -> {}, actor -> false);
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            try {
                List<Holder<GameEvent>> events =
                        observeGameEvents(
                                helper.getLevel(),
                                be.getBlockPos(),
                                () ->
                                        click(
                                                player,
                                                be.getBlockPos(),
                                                0.125,
                                                bar ? 0.0625 : 0.125,
                                                0.125));
                checkEquals(List.of(GameEvent.BLOCK_CHANGE), events, "Denied-cleanup events");
                check(
                        helper.getLevel().getBlockEntity(be.getBlockPos()) == be && be.isEmpty(),
                        "Denied cleanup did not leave the empty block");
            } finally {
                unregister.run();
                ServerGestureState.clear(player.getUUID());
                player.setItemInHand(HAND, ItemStack.EMPTY);
            }
        }
        helper.succeed();
    }

    private static List<Holder<GameEvent>> observeGameEvents(
            ServerLevel level, BlockPos pos, Runnable mutation) {
        var events = new ArrayList<Holder<GameEvent>>();
        GameEventListener listener =
                new GameEventListener() {
                    @Override
                    public PositionSource getListenerSource() {
                        return new BlockPositionSource(pos);
                    }

                    @Override
                    public int getListenerRadius() {
                        return 16;
                    }

                    @Override
                    public boolean handleGameEvent(
                            ServerLevel sourceLevel,
                            Holder<GameEvent> event,
                            GameEvent.Context context,
                            Vec3 source) {
                        if (BlockPos.containing(source).equals(pos)
                                && (event.equals(GameEvent.BLOCK_CHANGE)
                                        || event.equals(GameEvent.BLOCK_DESTROY))) {
                            events.add(event);
                        }
                        return true;
                    }
                };
        var registry =
                level.getChunkAt(pos)
                        .getListenerRegistry(SectionPos.blockToSectionCoord(pos.getY()));
        registry.register(listener);
        try {
            mutation.run();
        } finally {
            registry.unregister(listener);
        }
        return events;
    }

    /**
     * To reproduce in-game with the test mod: revoke somestacks:gametest_place_storage, hold
     * exactly two Dirt, and create a Storage Stack. Its placed-block criterion is awarded using the
     * original two-item offer even though the deposit empties the hand. A blocked destination
     * awards nothing. Revoke it again and deposit two Dirt into a full pile: growth awards nothing.
     */
    public static void playerPlacementAwardsCriterionAfterDeposit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Automation fake players can suppress advancement tracking. This check needs a regular
        // player's tracker, with no connection because the fixture has no display or rewards.
        ServerPlayer player =
                new ServerPlayer(
                        level.getServer(),
                        level,
                        new GameProfile(UUID.randomUUID(), "[SSCriterion]"),
                        ClientInformation.createDefault()) {
                    @Override
                    public void displayClientMessage(Component message, boolean actionBar) {
                        // Action-bar output is not observed by this server-side check.
                    }
                };
        player.setItemInHand(HAND, new ItemStack(Items.DIRT, 2));
        var advancement =
                helper.getLevel()
                        .getServer()
                        .getAdvancements()
                        .get(
                                ResourceLocation.fromNamespaceAndPath(
                                        "somestacks", "gametest_place_storage"));
        check(advancement != null, "Missing placement advancement fixture");
        var progress = player.getAdvancements();
        BlockPos support = helper.absolutePos(TARGET.east());
        BlockPos destination = support.west();
        helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        try {
            helper.getLevel()
                    .setBlock(
                            destination,
                            CommonRegistry.storageStackBlock().defaultBlockState(),
                            Block.UPDATE_ALL);
            CriteriaTriggers.PLACED_BLOCK.trigger(
                    player, destination, new ItemStack(Items.DIRT, 2));
            check(
                    progress.getOrStartProgress(advancement).isDone(),
                    "Placement advancement fixture did not respond to the control trigger");
            progress.revoke(advancement, "placed");

            helper.getLevel()
                    .setBlock(destination, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            BlockHitResult hit = aim(player, support, 0.0, 0.5, 0.5);
            use(player, hit);
            check(
                    !progress.getOrStartProgress(advancement).isDone(),
                    "Denied placement awarded criterion");
            checkEquals(2, player.getMainHandItem().getCount(), "Denied placement spent the offer");

            helper.getLevel()
                    .setBlock(destination, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            use(player, hit);
            check(
                    helper.getLevel().getBlockEntity(destination) instanceof StorageStackBE,
                    "Player placement failed");
            check(player.getMainHandItem().isEmpty(), "First deposit did not empty the hand");
            check(
                    progress.getOrStartProgress(advancement).isDone(),
                    "Player placement did not award the original-offer criterion");

            progress.revoke(advancement, "placed");
            StorageStackBE storage = (StorageStackBE) helper.getLevel().getBlockEntity(destination);
            for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
                storage.getItems().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
            }
            player.setItemInHand(HAND, new ItemStack(Items.DIRT, 2));
            GameTestScaffold.withPolicy(
                    Map.of(
                            ServerConfig.Setting.MAX_PILE_HEIGHT,
                            2,
                            ServerConfig.Setting.ENABLE_STORAGE_STACK_BLOCK,
                            true),
                    () -> click(player, destination, 0.5, 0.5, 0.5));
            check(player.getMainHandItem().isEmpty(), "Growth deposit did not take the hand");
            check(
                    helper.getLevel()
                            .getBlockState(destination.above())
                            .is(CommonRegistry.storageStackBlock()),
                    "Full pile did not grow");
            check(
                    !progress.getOrStartProgress(advancement).isDone(),
                    "Pile growth awarded criterion");
        } finally {
            progress.stopListening();
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * A type the server has disabled starts no new block and never grows, while blocks of it that
     * already exist keep taking deposits and giving them back. To reproduce in-game: build an empty
     * Storage, Singles, and Bar Stack and fill two more of each completely; set every {@code
     * enable_*_stack_block} option to false and run /ss reload. Selecting each type and
     * modifier-clicking the top of a stone block places nothing, keeps the held items, and the
     * action bar says that type's placement is disabled. Modifier-clicking the top of the empty
     * block deposits one item and a plain click takes it back. Modifier-clicking the top of a full
     * block, or a hopper feeding the other full block, adds no block above it. Set the options back
     * to true and reload: the stone click places a block of the type, the modifier-click grows the
     * first full block, and the hopper grows the second.
     */
    public static void disabledTypesRefuseNewBlocksButKeepExistingOnes(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        ServerLevel level = helper.getLevel();
        List<Component> messages = new ArrayList<>();
        ServerPlayer player = messageRecorder(helper, messages);
        try {
            for (BlockType type : BlockType.values()) {
                BlockPos column = TARGET.east(3 * type.ordinal());
                Item item = GameTestScaffold.sampleItem(type);
                Vec3 cell = GameTestScaffold.cellBox(type, 0).getCenter();
                StackMode mode = StackMode.valueOf(type.name());
                BlockPos support = helper.absolutePos(column.north(2));
                level.setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                BlockPos existing = placeType(helper, type, column).getBlockPos();
                StackBlockEntity clicked = placeType(helper, type, column.south());
                StackBlockEntity automated = placeType(helper, type, column.south(3));
                GameTestScaffold.fill(clicked, item);
                GameTestScaffold.fill(automated, item);
                BlockPos clickedTop = clicked.getBlockPos();
                BlockPos automatedTop = automated.getBlockPos();
                int headroom = automated.getItems().getSlots();
                ServerGestureState.set(player, mode, true);
                messages.clear();
                GameTestScaffold.withPolicy(
                        Map.of(enableSetting(type), false, ServerConfig.Setting.MAX_PILE_HEIGHT, 2),
                        () -> {
                            player.setItemInHand(HAND, new ItemStack(item, 2));
                            use(player, aimDown(player, support, cell.x, cell.z));
                            check(level.getBlockState(support.above()).isAir(), type + " placed");
                            checkEquals(
                                    2,
                                    player.getMainHandItem().getCount(),
                                    type + ": refused placement spent the hand");
                            checkEquals(
                                    List.of("somestacks.message.type_disabled"),
                                    translationKeys(messages),
                                    type + ": placement refusal message");

                            player.setItemInHand(HAND, new ItemStack(item));
                            use(player, aimDown(player, existing, cell.x, cell.z));
                            check(
                                    player.getMainHandItem().isEmpty(),
                                    type + ": existing block refused a deposit");
                            ServerGestureState.set(player, mode, false);
                            use(player, aimDown(player, existing, cell.x, cell.z));
                            checkEquals(
                                    item,
                                    player.getMainHandItem().getItem(),
                                    type + ": existing block kept its item");
                            ServerGestureState.set(player, mode, true);

                            player.setItemInHand(HAND, new ItemStack(item));
                            use(player, aimDown(player, clickedTop, cell.x, cell.z));
                            check(
                                    level.getBlockState(clickedTop.above()).isAir(),
                                    type + ": modifier-click grew a disabled type");
                            checkEquals(
                                    1,
                                    player.getMainHandItem().getCount(),
                                    type + ": refused growth spent the hand");

                            GameTestScaffold.Automation access = automation.apply(automated);
                            ItemStack offered = new ItemStack(item);
                            checkEquals(
                                    1,
                                    access.insert(headroom, offered, true).getCount(),
                                    type + ": simulated automation growth remainder");
                            checkEquals(
                                    1,
                                    access.insert(headroom, offered, false).getCount(),
                                    type + ": automation growth remainder");
                            check(
                                    level.getBlockState(automatedTop.above()).isAir(),
                                    type + ": automation grew a disabled type");
                        });
                GameTestScaffold.withPolicy(
                        Map.of(enableSetting(type), true, ServerConfig.Setting.MAX_PILE_HEIGHT, 2),
                        () -> {
                            player.setItemInHand(HAND, new ItemStack(item, 2));
                            use(player, aimDown(player, support, cell.x, cell.z));
                            check(
                                    level.getBlockState(support.above()).is(type.getBlock()),
                                    type + ": enabled type was not placed");

                            player.setItemInHand(HAND, new ItemStack(item));
                            use(player, aimDown(player, clickedTop, cell.x, cell.z));
                            check(
                                    level.getBlockState(clickedTop.above()).is(type.getBlock()),
                                    type + ": enabled type did not grow by modifier-click");

                            checkEquals(
                                    0,
                                    automation
                                            .apply(automated)
                                            .insert(headroom, new ItemStack(item), false)
                                            .getCount(),
                                    type + ": enabled automation growth remainder");
                            check(
                                    level.getBlockState(automatedTop.above()).is(type.getBlock()),
                                    type + ": enabled type did not grow by automation");
                        });
            }
        } finally {
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * At the configured maximum height a full run takes nothing more and offers automation no
     * headroom, while one more block of allowed height lets automation grow it. To reproduce
     * in-game: set {@code max_pile_height} to 1, run /ss reload, and fill a one-block Storage,
     * Singles, and Bar Stack. A hopper feeding each adds nothing, a modifier-click on its top adds
     * no block above, and the action bar says the stack is at its maximum height. With 2 and a
     * full two-block run of each, the same holds. Raising the limit by one lets the hopper grow
     * each run by one block.
     */
    public static void maximumHeightRefusesGrowthAndHidesHeadroom(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        ServerLevel level = helper.getLevel();
        List<Component> messages = new ArrayList<>();
        ServerPlayer player = messageRecorder(helper, messages);
        try {
            for (BlockType type : BlockType.values()) {
                Item item = GameTestScaffold.sampleItem(type);
                Vec3 cell = GameTestScaffold.cellBox(type, 0).getCenter();
                // A Storage block has 3 x 3 x 3 cells; Singles has 4 x 4 x 4 and Bar 8 layers of 8.
                int perBlock = type == BlockType.STORAGE_STACK ? 27 : 64;
                ServerGestureState.set(player, StackMode.valueOf(type.name()), true);
                for (int height = 1; height <= 2; height++) {
                    BlockPos base = TARGET.east(3 * type.ordinal()).south(3 * (height - 1));
                    StackBlockEntity bottom = placeType(helper, type, base);
                    GameTestScaffold.fill(bottom, item);
                    BlockPos top = bottom.getBlockPos();
                    for (int above = 1; above < height; above++) {
                        StackBlockEntity block = placeType(helper, type, base.above(above));
                        GameTestScaffold.fill(block, item);
                        top = block.getBlockPos();
                    }
                    BlockPos topPos = top;
                    int pinned = height;
                    String label = type + " at height " + pinned;
                    GameTestScaffold.Automation access = automation.apply(bottom);
                    GameTestScaffold.withPolicy(
                            Map.of(
                                    ServerConfig.Setting.MAX_PILE_HEIGHT,
                                    pinned,
                                    enableSetting(type),
                                    true),
                            () -> {
                                checkEquals(
                                        perBlock * pinned,
                                        access.slots(),
                                        label + ": advertised slots");
                                checkEquals(
                                        1,
                                        access.insertWalking(new ItemStack(item)).getCount(),
                                        label + ": full run took an automation insertion");
                                messages.clear();
                                player.setItemInHand(HAND, new ItemStack(item));
                                use(player, aimDown(player, topPos, cell.x, cell.z));
                                checkEquals(
                                        1,
                                        player.getMainHandItem().getCount(),
                                        label + ": full run took a deposit");
                                checkEquals(
                                        List.of("somestacks.message.maximum_height"),
                                        translationKeys(messages),
                                        label + ": refusal message");
                                check(
                                        level.getBlockState(topPos.above()).isAir(),
                                        label + ": run grew past its maximum");
                            });
                    GameTestScaffold.withPolicy(
                            Map.of(
                                    ServerConfig.Setting.MAX_PILE_HEIGHT,
                                    pinned + 1,
                                    enableSetting(type),
                                    true),
                            () -> {
                                checkEquals(
                                        perBlock * (pinned + 1),
                                        access.slots(),
                                        label + ": advertised slots one below the maximum");
                                checkEquals(
                                        0,
                                        access.insert(perBlock * pinned, new ItemStack(item), false)
                                                .getCount(),
                                        label + ": automation growth remainder");
                                check(
                                        level.getBlockState(topPos.above()).is(type.getBlock()),
                                        label + ": run did not grow below its maximum");
                            });
                }
            }
        } finally {
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * An item on {@code disable_items} is refused from players only, while a namespace on {@code
     * disable_mods} is refused from players and automation, and neither stops a player taking
     * stored contents out. To reproduce in-game: add {@code minecraft:dirt} to {@code
     * disable_items} and run /ss reload. Modifier-clicking a Storage Stack holding dirt, or the top
     * of a stone block, deposits and places nothing, keeps the dirt, and the action bar says dirt
     * is disabled; stone still deposits, and a hopper still feeds dirt in. Replace the entry with
     * {@code minecraft} on {@code disable_mods} and reload: the dirt click is refused with a
     * message naming the mod, the hopper stops, and a plain click still takes stone out. Clear both
     * lists and reload: the dirt deposits.
     */
    public static void deniedItemsRefuseDepositsAndOnlyModDenialStopsAutomation(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        ServerLevel level = helper.getLevel();
        List<Component> messages = new ArrayList<>();
        ServerPlayer player = messageRecorder(helper, messages);
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, TARGET);
        BlockPos pos = storage.getBlockPos();
        storage.getItems().insertItem(STORAGE_CENTER, new ItemStack(Items.STONE, 8), false);
        BlockPos support = helper.absolutePos(TARGET.east(3));
        level.setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        GameTestScaffold.Automation access = automation.apply(storage);
        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        try {
            GameTestScaffold.withPolicy(
                    Map.of(
                            ServerConfig.Setting.DISABLE_ITEMS,
                            List.of("minecraft:dirt"),
                            ServerConfig.Setting.DISABLE_MODS,
                            List.of()),
                    () -> {
                        player.setItemInHand(HAND, new ItemStack(Items.DIRT, 2));
                        click(player, pos, 0.5, 0.5, 0.5);
                        checkEquals(
                                0,
                                GameTestScaffold.count(storage.getItems(), Items.DIRT),
                                "Denied item was deposited");
                        use(player, aimDown(player, support, 0.5, 0.5));
                        check(
                                level.getBlockState(support.above()).isAir(),
                                "Denied item started a stack");
                        checkEquals(
                                2, player.getMainHandItem().getCount(), "Denial spent the hand");
                        checkEquals(
                                List.of(
                                        "somestacks.message.disabled_item",
                                        "somestacks.message.disabled_item"),
                                translationKeys(messages),
                                "Denied item messages");

                        player.setItemInHand(HAND, new ItemStack(Items.STONE, 2));
                        click(player, pos, 0.5, 0.5, 0.5);
                        check(
                                player.getMainHandItem().isEmpty(),
                                "Item denial refused a different item");
                        check(
                                access.insertWalking(new ItemStack(Items.DIRT, 3)).isEmpty(),
                                "Item denial reached automation");
                    });
            messages.clear();
            GameTestScaffold.withPolicy(
                    Map.of(
                            ServerConfig.Setting.DISABLE_ITEMS,
                            List.of(),
                            ServerConfig.Setting.DISABLE_MODS,
                            List.of("minecraft")),
                    () -> {
                        player.setItemInHand(HAND, new ItemStack(Items.DIRT, 2));
                        click(player, pos, 0.5, 0.5, 0.5);
                        checkEquals(
                                2,
                                player.getMainHandItem().getCount(),
                                "Mod denial spent the hand");
                        checkEquals(
                                List.of("somestacks.message.disabled_mod"),
                                translationKeys(messages),
                                "Denied mod message");
                        checkEquals(
                                1,
                                access.insertWalking(new ItemStack(Items.DIRT)).getCount(),
                                "Mod denial did not reach automation");

                        player.setItemInHand(HAND, ItemStack.EMPTY);
                        ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
                        click(player, pos, 0.5, 0.5, 0.5);
                        checkEquals(
                                Items.STONE,
                                player.getMainHandItem().getItem(),
                                "Mod denial stopped extraction");
                        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
                    });
            GameTestScaffold.withPolicy(
                    Map.of(
                            ServerConfig.Setting.DISABLE_ITEMS,
                            List.of(),
                            ServerConfig.Setting.DISABLE_MODS,
                            List.of()),
                    () -> {
                        int before = GameTestScaffold.count(storage.getItems(), Items.DIRT);
                        player.setItemInHand(HAND, new ItemStack(Items.DIRT, 2));
                        click(player, pos, 0.5, 0.5, 0.5);
                        checkEquals(
                                before + 2,
                                GameTestScaffold.count(storage.getItems(), Items.DIRT),
                                "Allowed dirt was refused");
                    });
        } finally {
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * A loader's block-use denial, and Spectator mode, stop every gesture on an existing stack;
     * with neither, every gesture applies. To reproduce in-game: inside a claim that denies you
     * block use, modifier-click a Storage Stack holding dirt, plain-click it empty-handed, toggle
     * its permanence in Toggle Permanent mode, Shift-right-click it with a redstone torch, and
     * Shift-right-click a stored Singles item with a soul torch. Nothing changes and you keep the
     * dirt. Repeat in Spectator mode outside the claim: nothing changes. In Survival outside the
     * claim, the dirt goes in, the plain click takes stone out, the pile becomes permanent, the
     * block turns, and the item turns.
     */
    public static void blockUseDenialFreezesStackGestures(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> playerFactory,
            BlockUseDenial denial) {
        ServerLevel level = helper.getLevel();
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, TARGET);
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, TARGET.east(3));
        storage.getItems().insertItem(STORAGE_CENTER, new ItemStack(Items.STONE, 8), false);
        check(singles.depositAt(0, new ItemStack(Items.STICK)), "Singles fixture deposit failed");
        // Every click lands on a west face. Stone there leaves a held block or torch nowhere to be
        // placed, so a loader that still lets vanilla use the held item after denying block use
        // cannot change the hand either.
        level.setBlock(storage.getBlockPos().west(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(singles.getBlockPos().west(), Blocks.STONE.defaultBlockState(), 3);
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        try {
            Runnable liftStorage = denial.denyAt(level, storage.getBlockPos());
            Runnable liftSingles = denial.denyAt(level, singles.getBlockPos());
            try {
                checkGesturesRefused(player, storage, singles, "Denied block use");
            } finally {
                liftStorage.run();
                liftSingles.run();
            }

            GameType before = player.gameMode.getGameModeForPlayer();
            player.setGameMode(GameType.SPECTATOR);
            try {
                checkGesturesRefused(player, storage, singles, "Spectator");
            } finally {
                player.setGameMode(before);
            }

            BlockPos pos = storage.getBlockPos();
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            player.setItemInHand(HAND, new ItemStack(Items.DIRT, 4));
            click(player, pos, 0.5, 0.5, 0.5);
            checkEquals(
                    4, GameTestScaffold.count(storage.getItems(), Items.DIRT), "Allowed deposit");

            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            player.setItemInHand(HAND, ItemStack.EMPTY);
            click(player, pos, 0.5, 0.5, 0.5);
            checkEquals(8, player.getMainHandItem().getCount(), "Allowed extraction");

            ServerGestureState.set(player, StackMode.TOGGLE_PERMANENT, true);
            player.setItemInHand(HAND, ItemStack.EMPTY);
            click(player, pos, 0.5, 0.5, 0.5);
            check(storage.pile().isPermanent(), "Allowed permanence toggle");

            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            player.setShiftKeyDown(true);
            player.setItemInHand(HAND, new ItemStack(Items.REDSTONE_TORCH));
            click(player, pos, 0.5, 0.5, 0.5);
            checkEquals(1, storage.getRotation(), "Allowed block rotation");
            player.setItemInHand(HAND, new ItemStack(Items.SOUL_TORCH));
            click(player, singles.getBlockPos(), 0.125, 0.125, 0.125);
            checkEquals(1, singles.getCubeRotation(0), "Allowed item rotation");
        } finally {
            player.setShiftKeyDown(false);
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
            StackInteractions.clearRotationSoundPacing(player.getUUID());
        }
        helper.succeed();
    }

    /** Drives the five stack gestures and checks that none of them changed anything. */
    private static void checkGesturesRefused(
            ServerPlayer player, StorageStackBE storage, SinglesStackBE singles, String label) {
        BlockPos pos = storage.getBlockPos();
        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        player.setItemInHand(HAND, new ItemStack(Items.DIRT, 4));
        click(player, pos, 0.5, 0.5, 0.5);
        checkEquals(4, player.getMainHandItem().getCount(), label + ": deposit spent the hand");
        checkEquals(
                0,
                GameTestScaffold.count(storage.getItems(), Items.DIRT),
                label + ": deposit reached the stack");

        ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
        player.setItemInHand(HAND, ItemStack.EMPTY);
        click(player, pos, 0.5, 0.5, 0.5);
        check(player.getMainHandItem().isEmpty(), label + ": extraction filled the hand");
        checkEquals(
                8,
                GameTestScaffold.count(storage.getItems(), Items.STONE),
                label + ": extraction took stored items");

        ServerGestureState.set(player, StackMode.TOGGLE_PERMANENT, true);
        click(player, pos, 0.5, 0.5, 0.5);
        check(!storage.pile().isPermanent(), label + ": permanence changed");

        ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
        player.setShiftKeyDown(true);
        try {
            player.setItemInHand(HAND, new ItemStack(Items.REDSTONE_TORCH));
            click(player, pos, 0.5, 0.5, 0.5);
            checkEquals(0, storage.getRotation(), label + ": block rotated");
            player.setItemInHand(HAND, new ItemStack(Items.SOUL_TORCH));
            click(player, singles.getBlockPos(), 0.125, 0.125, 0.125);
            checkEquals(0, singles.getCubeRotation(0), label + ": item rotated");
        } finally {
            player.setShiftKeyDown(false);
            player.setItemInHand(HAND, ItemStack.EMPTY);
        }
    }

    private static ServerConfig.Setting enableSetting(BlockType type) {
        return switch (type) {
            case STORAGE_STACK -> ServerConfig.Setting.ENABLE_STORAGE_STACK_BLOCK;
            case SINGLES_STACK -> ServerConfig.Setting.ENABLE_SINGLES_STACK_BLOCK;
            case BAR_STACK -> ServerConfig.Setting.ENABLE_BAR_STACK_BLOCK;
        };
    }

    private static StackBlockEntity placeType(
            GameTestHelper helper, BlockType type, BlockPos relative) {
        return GameTestScaffold.place(helper, relative, type.getBlock(), StackBlockEntity.class);
    }

    /**
     * A deposit into a full Storage pile that cannot grow because it is at the configured height is
     * refused with a message. To reproduce in-game: set {@code max_pile_height = 1}, run /ss
     * reload, fill a one-block Storage pile, and modifier-click it with another item. Nothing moves
     * and the action bar says the stack is at its maximum height. After taking one stack out, the
     * same click deposits.
     */
    public static void fullMaxHeightDepositExplainsRefusal(GameTestHelper helper) {
        List<Component> messages = new ArrayList<>();
        ServerPlayer player = messageRecorder(helper, messages);
        ServerConfig.Settings original = ServerConfig.settings();
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, TARGET);
        player.setItemInHand(HAND, new ItemStack(Items.STONE, 2));
        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        try {
            ServerConfig.apply(original.with(ServerConfig.Setting.MAX_PILE_HEIGHT, 1));
            for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
                storage.getItems().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
            }
            click(player, storage.getBlockPos(), 0.5, 0.5, 0.5);
            checkEquals(2, player.getMainHandItem().getCount(), "Full pile took items");
            check(
                    !helper.getLevel()
                            .getBlockState(storage.getBlockPos().above())
                            .is(CommonRegistry.storageStackBlock()),
                    "Pile grew past its maximum height");
            checkEquals(
                    List.of("somestacks.message.maximum_height"),
                    translationKeys(messages),
                    "Maximum-height refusal message");

            storage.getItems().setStackInSlot(0, ItemStack.EMPTY);
            click(player, storage.getBlockPos(), 0.5, 0.5, 0.5);
            check(player.getMainHandItem().isEmpty(), "Pile with room refused the deposit");
            checkEquals(1, messages.size(), "Successful deposit sent a refusal message");
        } finally {
            ServerConfig.apply(original);
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * In Adventure mode, placing a stack and growing a Storage pile answer to the same build check.
     * To reproduce in-game: in Adventure mode, select Storage and modifier-click the top of a stone
     * block holding plain dirt: nothing is placed. Hold dirt whose {@code can_place_on} names stone
     * and Storage Stack, and the same click places a Storage Stack holding it. Fill that stack with
     * full stacks of stone and modifier-click it holding plain dirt: it does not grow. Holding the
     * {@code can_place_on} dirt, the same click grows a block above holding that dirt.
     */
    public static void adventurePlacementAndGrowthShareTheBuildCheck(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = messageRecorder(helper, new ArrayList<>());
        player.getAbilities().mayBuild = false;
        BlockPos support = helper.absolutePos(TARGET.east());
        BlockPos base = support.above();
        BlockPos grown = base.above();
        level.setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockHitResult top =
                new BlockHitResult(
                        Vec3.atCenterOf(support).add(0, 0.5, 0), Direction.UP, support, false);
        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        try {
            GameTestScaffold.withPolicy(
                    Map.of(
                            ServerConfig.Setting.MAX_PILE_HEIGHT,
                            2,
                            ServerConfig.Setting.ENABLE_STORAGE_STACK_BLOCK,
                            true),
                    () -> {
                        player.setItemInHand(HAND, new ItemStack(Items.DIRT, 2));
                        use(player, top);
                        check(level.getBlockState(base).isAir(), "Placed without can_place_on");
                        checkEquals(
                                2, player.getMainHandItem().getCount(), "Refusal spent the hand");

                        player.setItemInHand(HAND, placeableDirt(2));
                        use(player, top);
                        check(
                                level.getBlockEntity(base) instanceof StorageStackBE,
                                "can_place_on placement was refused");

                        StorageStackBE storage = (StorageStackBE) level.getBlockEntity(base);
                        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
                            storage.getItems().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
                        }
                        player.setItemInHand(HAND, new ItemStack(Items.DIRT, 2));
                        click(player, base, 0.5, 0.5, 0.5);
                        check(level.getBlockState(grown).isAir(), "Grew without can_place_on");
                        checkEquals(
                                2,
                                player.getMainHandItem().getCount(),
                                "Refused growth spent the hand");

                        player.setItemInHand(HAND, placeableDirt(2));
                        click(player, base, 0.5, 0.5, 0.5);
                        check(
                                level.getBlockEntity(grown) instanceof StorageStackBE,
                                "can_place_on growth was refused");
                        checkEquals(
                                2,
                                GameTestScaffold.heldAt(helper, grown, Items.DIRT),
                                "Grown block contents");
                    });
        } finally {
            ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

    /** Dirt an Adventure mode player may place against stone and against a Storage Stack. */
    private static ItemStack placeableDirt(int count) {
        ItemStack stack = new ItemStack(Items.DIRT, count);
        stack.set(
                DataComponents.CAN_PLACE_ON,
                new AdventureModePredicate(
                        List.of(
                                BlockPredicate.Builder.block()
                                        .of(Blocks.STONE, CommonRegistry.storageStackBlock())
                                        .build()),
                        true));
        return stack;
    }

    /**
     * Bar admits only ingots through a gesture deposit. To reproduce in-game: select Bar and
     * modifier-click the side of an empty Bar Stack holding dirt. Nothing moves and the hand keeps
     * the dirt; the same click holding an ingot deposits one.
     */
    public static void barGestureDepositRefusesNonIngots(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        BarStackBE bars = GameTestScaffold.placeBar(helper, TARGET);
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.DIRT, 2));
        ServerGestureState.set(player, StackMode.BAR_STACK, true);
        try {
            click(player, bars.getBlockPos(), 0.0625, 0.0625, 0.125);
            checkEquals(0, GameTestScaffold.occupied(bars.getItems()), "Bar took a non-ingot");
            checkEquals(2, player.getMainHandItem().getCount(), "Refused deposit spent the hand");

            player.setItemInHand(HAND, new ItemStack(GameTestScaffold.firstBarItem(), 2));
            click(player, bars.getBlockPos(), 0.0625, 0.0625, 0.125);
            checkEquals(1, GameTestScaffold.occupied(bars.getItems()), "Bar refused an ingot");
            checkEquals(1, player.getMainHandItem().getCount(), "Deposit did not spend one ingot");
        } finally {
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

    /** A player outside the level that records its action-bar and chat messages. */
    private static ServerPlayer messageRecorder(GameTestHelper helper, List<Component> messages) {
        ServerLevel level = helper.getLevel();
        return new ServerPlayer(
                level.getServer(),
                level,
                new GameProfile(UUID.randomUUID(), "[SSMessages]"),
                ClientInformation.createDefault()) {
            @Override
            public void displayClientMessage(Component message, boolean actionBar) {
                messages.add(message);
            }
        };
    }

    private static List<String> translationKeys(List<Component> messages) {
        List<String> keys = new ArrayList<>();
        for (Component message : messages) {
            keys.add(
                    message.getContents() instanceof TranslatableContents translatable
                            ? translatable.getKey()
                            : message.getString());
        }
        return keys;
    }

    /**
     * A click on the west side of {@code pos} through the block-local point, with the player's eye
     * two blocks west at the point's height and depth, so the view ray runs east through the point.
     */
    public static BlockHitResult aim(
            ServerPlayer player, BlockPos pos, double localX, double localY, double localZ) {
        Vec3 point = new Vec3(pos.getX() + localX, pos.getY() + localY, pos.getZ() + localZ);
        player.setPos(pos.getX() - 2.0, point.y - player.getEyeHeight(), point.z);
        return new BlockHitResult(point, Direction.WEST, pos, false);
    }

    /** {@link #aim} through the center of a block-local box, such as one stack cell. */
    public static BlockHitResult aimAt(ServerPlayer player, BlockPos pos, AABB localBox) {
        Vec3 center = localBox.getCenter();
        return aim(player, pos, center.x, center.y, center.z);
    }

    /**
     * A click on the top face of {@code pos} at the block-local column, with the player's eye one
     * block above that face, so the view ray runs straight down through the column.
     */
    public static BlockHitResult aimDown(
            ServerPlayer player, BlockPos pos, double localX, double localZ) {
        Vec3 point = new Vec3(pos.getX() + localX, pos.getY() + 1.0, pos.getZ() + localZ);
        player.setPos(point.x, point.y + 1.0 - player.getEyeHeight(), point.z);
        return new BlockHitResult(point, Direction.UP, pos, false);
    }

    private static boolean click(
            ServerPlayer player, BlockPos pos, double localX, double localY, double localZ) {
        return use(player, aim(player, pos, localX, localY, localZ));
    }

    /**
     * Runs vanilla's server block use with the main hand, the path a player's right-click on a
     * block takes: Forge and NeoForge post {@code RightClickBlock} and Fabric invokes {@code
     * UseBlockCallback} first, then the clicked block's own use runs unless a listener stopped it.
     * Returns whether the click was consumed.
     */
    public static boolean use(ServerPlayer player, BlockHitResult hit) {
        return player.gameMode
                .useItemOn(player, player.serverLevel(), player.getMainHandItem(), HAND, hit)
                .consumesAction();
    }
}
