package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.ServerLifecycle;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.network.GestureStatePkt;
import com.github.crittscott.somestacks.server.GestureThrottle;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.server.StackInteractions;
import com.github.crittscott.somestacks.util.StackMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.*;

/** Shared regression checks driven through each loader's actual block-use callback. */
public final class InteractionChecks {
    public static final BlockPos TARGET = new BlockPos(2, 1, 2);
    public static final InteractionHand HAND = InteractionHand.MAIN_HAND;
    private InteractionChecks() {}

    @FunctionalInterface
    public interface LoaderClick {
        boolean click(ServerPlayer player, InteractionHand hand, BlockHitResult hit);
    }

    /** To reproduce: modifier-deposit into Storage, release the modifier and extract, then place against an ordinary block. */
    public static void depositExtractAndPlacement(GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory, LoaderClick callback) {
        var storage = placeStorage(helper, TARGET);
        try (PlayerScope scope = new PlayerScope(factory.apply(new ItemStack(Items.DIRT, 4)))) {
            var player = scope.player;
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            callback.click(player, HAND, aim(player, storage.getBlockPos(), 0.2, 0.2));
            checkEquals(4, count(storage.getItems(), Items.DIRT), "Deposit contents");
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            GestureThrottle.clear(player.getUUID());
            callback.click(player, HAND, aim(player, storage.getBlockPos(), 0.2, 0.2));
            checkEquals(4, player.getMainHandItem().getCount(), "Extraction count");
            BlockPos support = helper.absolutePos(TARGET.east(3));
            helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            player.setItemInHand(HAND, new ItemStack(Items.DIRT, 2));
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            GestureThrottle.clear(player.getUUID());
            aim(player, support, 0.5, 0.5);
            check(callback.click(player, HAND, new BlockHitResult(
                    Vec3.atCenterOf(support).add(0, 0.5, 0), Direction.UP, support, false)), "Placement consumed");
            check(helper.getLevel().getBlockState(support.above()).is(CommonRegistry.STORAGE_STACK_BLOCK.get()), "Placed type");
            checkEquals(2, heldAt(helper, support.above(), Items.DIRT), "First deposit");
            check(player.getMainHandItem().isEmpty(), "Placement did not spend hand");
        }
        helper.succeed();
    }

    /** To reproduce: plain-click the near of two Storage cells; stale client hit coordinates cannot select the cell behind it. */
    public static void extractionUsesServerRay(GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory, LoaderClick callback) {
        var storage = placeStorage(helper, TARGET);
        storage.getItems().setStackInSlot(0, new ItemStack(Items.STONE, 8));
        storage.getItems().setStackInSlot(1, new ItemStack(Items.DIRT, 3));
        try (PlayerScope scope = new PlayerScope(factory.apply(ItemStack.EMPTY))) {
            var player = scope.player;
            aim(player, storage.getBlockPos(), 0.2, 0.2);
            // The hit location names another row; only the server view ray selects a rendered cell.
            callback.click(player, HAND, new BlockHitResult(
                    Vec3.atCenterOf(storage.getBlockPos()), Direction.WEST, storage.getBlockPos(), false));
            checkEquals(Items.STONE, player.getMainHandItem().getItem(), "Nearest ray item");
            checkEquals(8, player.getMainHandItem().getCount(), "Nearest ray count");
            checkEquals(3, storage.getItems().getStackInSlot(1).getCount(), "Other cell changed");
        }
        helper.succeed();
    }

    /** To reproduce: Shift-click two Singles items with a soul torch, then an empty row. Only the aimed occupied item rotates. */
    public static void rotationUsesServerRayAndConsumesMiss(GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory, LoaderClick callback) {
        var singles = placeSingles(helper, TARGET);
        singles.getItems().setStackInSlot(0, new ItemStack(Items.STICK));
        singles.getItems().setStackInSlot(1, new ItemStack(Items.STONE));
        try (PlayerScope scope = new PlayerScope(factory.apply(new ItemStack(Items.SOUL_TORCH)))) {
            var player = scope.player;
            player.setShiftKeyDown(true);
            aim(player, singles.getBlockPos(), 0.125, 0.125);
            check(callback.click(player, HAND, new BlockHitResult(Vec3.atCenterOf(singles.getBlockPos()),
                    Direction.WEST, singles.getBlockPos(), false)), "Rotation consumed");
            checkEquals(1, singles.getCubeRotation(0), "Aimed rotation");
            checkEquals(0, singles.getCubeRotation(1), "Other item rotation");
            GestureThrottle.clear(player.getUUID());
            check(callback.click(player, HAND, aim(player, singles.getBlockPos(), 0.625, 0.625)), "Miss consumed");
            checkEquals(1, singles.getCubeRotation(0), "Miss rotated occupied cell");
            checkEquals(1, player.getMainHandItem().getCount(), "Torch spent");
        }
        helper.succeed();
    }

    /** To reproduce: hold dirt, then a full matching stack, and plain-click each type. No stored items or unsupported bars move. */
    public static void incompatibleAndFullHandsLeaveAllTypesUntouched(GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory, LoaderClick callback) {
        StackBlockEntity[] blocks = {placeStorage(helper, TARGET), placeSingles(helper, TARGET.east(3)), placeBar(helper, TARGET.east(6))};
        var barItem = firstBarItem();
        for (int i = 0; i < blocks.length; i++) {
            var item = i == 2 ? barItem : Items.STONE;
            int count = i == 0 ? 8 : 1;
            blocks[i].getItems().setStackInSlot(0, new ItemStack(item, count));
            try (PlayerScope scope = new PlayerScope(factory.apply(new ItemStack(Items.DIRT)))) {
                var player = scope.player;
                callback.click(player, HAND, aim(player, blocks[i].getBlockPos(), i == 2 ? 0.0625 : 0.2, 0.125));
                checkEquals(Items.DIRT, player.getMainHandItem().getItem(), "Wrong hand replaced");
                player.setItemInHand(HAND, new ItemStack(item, new ItemStack(item).getMaxStackSize()));
                GestureThrottle.clear(player.getUUID());
                callback.click(player, HAND, aim(player, blocks[i].getBlockPos(), i == 2 ? 0.0625 : 0.2, 0.125));
                checkEquals(new ItemStack(item).getMaxStackSize(), player.getMainHandItem().getCount(), "Full hand grew");
                checkEquals(count, blocks[i].getItems().getStackInSlot(0).getCount(), "Stored contents changed");
                check(helper.getLevel().getBlockEntity(blocks[i].getBlockPos()) == blocks[i], "Block removed");
                checkEquals(0, droppedNear(helper, blocks[i].getBlockPos(), item), "Refusal dropped items");
            }
        }
        helper.succeed();
    }

    /** To reproduce: plain-click empty space in any stack. The gesture consumes use and yields nothing. */
    public static void extractionMissConsumesWithoutMutation(GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory, LoaderClick callback) {
        StackBlockEntity[] blocks = {placeStorage(helper, TARGET), placeSingles(helper, TARGET.east(3)), placeBar(helper, TARGET.east(6))};
        for (var block : blocks) {
            block.getItems().setStackInSlot(0, new ItemStack(Items.STONE));
            try (PlayerScope scope = new PlayerScope(factory.apply(ItemStack.EMPTY))) {
                check(callback.click(scope.player, HAND, aim(scope.player, block.getBlockPos(), 0.9, 0.9)), "Miss not consumed");
                check(scope.player.getMainHandItem().isEmpty(), "Miss extracted item");
                checkEquals(1, block.getItems().getStackInSlot(0).getCount(), "Miss changed contents");
            }
        }
        helper.succeed();
    }

    /** To reproduce: use an off-hand item, ordinary sneaking, and Shift with a redstone torch on Storage. Only the torch rotates it. */
    public static void handsSneakingAndRotationPrecedence(GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory, LoaderClick callback) {
        var storage = placeStorage(helper, TARGET);
        try (PlayerScope scope = new PlayerScope(factory.apply(new ItemStack(Items.REDSTONE_TORCH)))) {
            var player = scope.player;
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT));
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            player.setShiftKeyDown(true);
            var hit = aim(player, storage.getBlockPos(), 0.5, 0.5);
            check(!callback.click(player, InteractionHand.OFF_HAND, hit), "Offhand gesture handled");
            check(callback.click(player, HAND, hit), "Torch gesture not handled");
            checkEquals(1, storage.getRotation(), "Block rotation");
            checkEquals(0, count(storage.getItems(), Items.REDSTONE_TORCH), "Torch deposited");
            checkEquals(1, player.getMainHandItem().getCount(), "Torch spent");
            player.setItemInHand(HAND, new ItemStack(Items.DIRT));
            GestureThrottle.clear(player.getUUID());
            check(!callback.click(player, HAND, hit), "Ordinary sneaking consumed");
            checkEquals(0, count(storage.getItems(), Items.DIRT), "Sneaking deposited");
        }
        helper.succeed();
    }

    /** To reproduce: select permanence, modifier-click Storage empty-handed while sneaking, and repeat in one tick. It toggles once. */
    public static void permanenceAndSameTickPacing(GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory, LoaderClick callback) {
        var storage = placeStorage(helper, TARGET);
        try (PlayerScope scope = new PlayerScope(factory.apply(ItemStack.EMPTY))) {
            var player = scope.player;
            player.setShiftKeyDown(true);
            ServerGestureState.set(player, StackMode.TOGGLE_PERMANENT, true);
            var hit = aim(player, storage.getBlockPos(), 0.5, 0.5);
            callback.click(player, HAND, hit);
            check(storage.pile().isPermanent(), "Empty hand did not toggle");
            callback.click(player, HAND, hit);
            check(storage.pile().isPermanent(), "Same-tick duplicate toggled");
        }
        helper.succeed();
    }

    /** To reproduce: fill Singles, select Storage mode, and modifier-click its top. The new block uses the selected type. */
    public static void fullTopFaceUsesSelectedType(GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory, LoaderClick callback) {
        var singles = placeSingles(helper, TARGET);
        for (int slot = 0; slot < singles.getItems().getSlots(); slot++) singles.getItems().setStackInSlot(slot, new ItemStack(Items.STONE));
        try (PlayerScope scope = new PlayerScope(factory.apply(new ItemStack(Items.DIRT, 2)))) {
            var player = scope.player;
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            BlockPos pos = singles.getBlockPos();
            player.setPos(pos.getX() + 0.125, pos.getY() + 2, pos.getZ() + 0.125);
            player.setXRot(90);
            player.setYRot(0);
            callback.click(player, HAND, new BlockHitResult(new Vec3(pos.getX() + 0.125, pos.getY() + 1, pos.getZ() + 0.125), Direction.UP, pos, false));
            check(helper.getLevel().getBlockState(pos.above()).is(CommonRegistry.STORAGE_STACK_BLOCK.get()), "Selected placement type");
            checkEquals(2, heldAt(helper, pos.above(), Items.DIRT), "Top placement contents");
        }
        helper.succeed();
    }

    /** To reproduce: try placing an ingot in Singles, or an unsupported upper cell. Rejected first deposits leave no block. */
    public static void firstDepositFailureLeavesNoBlock(GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory, LoaderClick callback) {
        BlockPos support = helper.absolutePos(TARGET);
        helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        BlockPos destination = support.east();
        try (PlayerScope scope = new PlayerScope(factory.apply(new ItemStack(firstBarItem())))) {
            var player = scope.player;
            ServerGestureState.set(player, StackMode.SINGLES_STACK, true);
            var hit = aim(player, support, 0.625, 0.125);
            callback.click(player, HAND, new BlockHitResult(hit.getLocation(), Direction.EAST, support, false));
            check(helper.getLevel().getBlockState(destination).isAir(), "Invalid item left block");
            player.setItemInHand(HAND, new ItemStack(Items.STONE));
            GestureThrottle.clear(player.getUUID());
            callback.click(player, HAND, new BlockHitResult(hit.getLocation(), Direction.EAST, support, false));
            check(helper.getLevel().getBlockState(destination).isAir(), "Unsupported first cell left block");
            checkEquals(1, player.getMainHandItem().getCount(), "Rejected deposit spent hand");
        }
        helper.succeed();
    }

    /** To reproduce: select another mode, disconnect, and reconnect. Logout clears mode/modifier state and gesture pacing. */
    public static void stateAndLogout(GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory, LoaderClick callback) {
        try (PlayerScope scope = new PlayerScope(factory.apply(ItemStack.EMPTY))) {
            var player = scope.player;
            GestureStatePkt.handleServer(new GestureStatePkt(StackMode.BAR_STACK, true), player);
            checkEquals(new ServerGestureState.State(StackMode.BAR_STACK, true), ServerGestureState.get(player), "Synced state");
            GestureThrottle.claimTick(player);
            ServerLifecycle.logout(player.getUUID());
            checkEquals(new ServerGestureState.State(StackMode.STORAGE_STACK, false), ServerGestureState.get(player), "Logout state");
            check(GestureThrottle.claimTick(player), "Logout pacing");
            BlockPos near = helper.absolutePos(TARGET);
            aim(player, near, 0.5, 0.5);
            check(StackInteractions.withinReach(player, near), "Near reach");
            check(!StackInteractions.withinReach(player, near.east(20)), "Far reach");
        }
        helper.succeed();
    }

    /** Positions the eye west of the named row and aims east, independently of client hit coordinates. */
    public static BlockHitResult aim(ServerPlayer player, BlockPos pos, double y, double z) {
        player.setPos(pos.getX() - 2, pos.getY() + y - player.getEyeHeight(), pos.getZ() + z);
        player.setXRot(0);
        player.setYRot(-90);
        return new BlockHitResult(new Vec3(pos.getX(), pos.getY() + y, pos.getZ() + z), Direction.WEST, pos, false);
    }

    /** Restores the loader's possibly shared fake player and clears session state after a scenario. */
    public static final class PlayerScope implements AutoCloseable {
        public final ServerPlayer player;
        private final Vec3 position;
        private final float xRot, yRot;
        private final boolean shift, creative;
        private final ItemStack offhand;
        public PlayerScope(ServerPlayer player) {
            this.player = player;
            position = player.position();
            xRot = player.getXRot(); yRot = player.getYRot();
            shift = player.isShiftKeyDown(); creative = player.getAbilities().instabuild;
            offhand = player.getOffhandItem().copy();
            player.setShiftKeyDown(false);
            player.getAbilities().instabuild = false;
            ServerGestureState.clear(player.getUUID());
            GestureThrottle.clear(player.getUUID());
        }
        @Override public void close() {
            player.setPos(position.x, position.y, position.z);
            player.setXRot(xRot); player.setYRot(yRot);
            player.setShiftKeyDown(shift);
            player.getAbilities().instabuild = creative;
            player.setItemInHand(HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, offhand);
            ServerGestureState.clear(player.getUUID());
            GestureThrottle.clear(player.getUUID());
        }
    }
}
