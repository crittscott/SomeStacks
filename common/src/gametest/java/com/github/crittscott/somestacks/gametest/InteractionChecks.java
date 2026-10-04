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
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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

    public static void interactionReadsOnlyTheMainHand(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, TARGET);
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 4));
        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        try {
            check(!StackInteractions.handleExistingStack(
                            player, InteractionHand.OFF_HAND,
                            hit(player, storage.getBlockPos(), 0.5, 0.5, 0.5)),
                    "Off-hand interaction was consumed");
            checkEquals(0, GameTestScaffold.count(storage.getItems(), Items.DIRT),
                    "Off-hand item was deposited");
        } finally {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

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
        }
        helper.succeed();
    }

    /**
     * Reproduces sneaking right-clicks with redstone and soul torches and verifies that the
     * loader's real server interaction hook rotates the block and targeted item.
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
                            .is(CommonRegistry.STORAGE_STACK_BLOCK.get()),
                    "Vanilla interaction did not place Storage");
            checkEquals(2, GameTestScaffold.heldAt(helper, destination, Items.DIRT),
                    "Placed stack contents");
        } finally {
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

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
        }
        helper.succeed();
    }

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
            outsideWorldBorder(helper, storage.getBlockPos(), () ->
                    click(player, storage.getBlockPos(), 0.5, 0.5, 0.5));
            check(!storage.pile().isPermanent(), "Protected pile changed permanence");
        } finally {
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }
        helper.succeed();
    }

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

    public static void rotationSoundThrottleSuppressesSameTickAndClears(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
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

    private static boolean click(ServerPlayer player, BlockPos pos,
                                 double localX, double localY, double localZ) {
        return StackInteractions.handleExistingStack(
                player, HAND, hit(player, pos, localX, localY, localZ));
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

    private static void outsideWorldBorder(
            GameTestHelper helper, BlockPos target, Runnable action) {
        WorldBorder border = helper.getLevel().getWorldBorder();
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();
        double size = border.getSize();
        try {
            border.setCenter(target.getX() + 1000.0, target.getZ());
            border.setSize(16.0);
            check(!border.isWithinBounds(target),
                    "Test setup left protected target inside the world border");
            action.run();
        } finally {
            border.setCenter(centerX, centerZ);
            border.setSize(size);
        }
    }
}
