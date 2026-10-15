package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.ServerLifecycle;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.network.GestureStatePkt;
import com.github.crittscott.somestacks.server.RotationSoundThrottle;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.util.StackMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.*;

/** Shared regression checks driven through vanilla's server block-use path on each loader. */
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
            callback.click(player, HAND, aim(player, storage.getBlockPos(), 0.2, 0.2));
            checkEquals(4, player.getMainHandItem().getCount(), "Extraction count");
            BlockPos support = helper.absolutePos(TARGET.east(3));
            helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            player.setItemInHand(HAND, new ItemStack(Items.DIRT, 2));
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            aim(player, support, 0.5, 0.5);
            check(callback.click(player, HAND, new BlockHitResult(
                    Vec3.atCenterOf(support).add(0, 0.5, 0), Direction.UP, support, false)), "Placement consumed");
            check(helper.getLevel().getBlockState(support.above()).is(CommonRegistry.STORAGE_STACK_BLOCK.get()), "Placed type");
            checkEquals(2, heldAt(helper, support.above(), Items.DIRT), "First deposit");
            check(player.getMainHandItem().isEmpty(), "Placement did not spend hand");
        }
        helper.succeed();
    }

    /**
     * To reproduce: on a laggy connection, plain-click one Storage cell while turning toward
     * another. The cell under the crosshair when the client clicked is extracted, not the one the
     * server last saw the player facing.
     */
    public static void extractionFollowsHitPoint(GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory, LoaderClick callback) {
        var storage = placeStorage(helper, TARGET);
        storage.getItems().setStackInSlot(0, new ItemStack(Items.STONE, 8));
        storage.getItems().setStackInSlot(3, new ItemStack(Items.DIRT, 3));
        try (PlayerScope scope = new PlayerScope(factory.apply(ItemStack.EMPTY))) {
            var player = scope.player;
            BlockPos pos = storage.getBlockPos();
            // The server view still faces slot 0; the client's hit point lies on slot 3.
            aim(player, pos, 0.2, 0.2);
            callback.click(player, HAND, new BlockHitResult(
                    new Vec3(pos.getX(), pos.getY() + 0.2, pos.getZ() + 0.5), Direction.WEST, pos, false));
            checkEquals(Items.DIRT, player.getMainHandItem().getItem(), "Hit-point item");
            checkEquals(3, player.getMainHandItem().getCount(), "Hit-point count");
            checkEquals(8, storage.getItems().getStackInSlot(0).getCount(), "Viewed cell changed");
        }
        helper.succeed();
    }

    /** To reproduce: Shift-click one of two Singles items with a soul torch, then an empty row. Only the aimed occupied item rotates. */
    public static void rotationTargetsAimedItemAndConsumesMiss(GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory, LoaderClick callback) {
        var singles = placeSingles(helper, TARGET);
        singles.getItems().setStackInSlot(0, new ItemStack(Items.STICK));
        singles.getItems().setStackInSlot(1, new ItemStack(Items.STONE));
        try (PlayerScope scope = new PlayerScope(factory.apply(new ItemStack(Items.SOUL_TORCH)))) {
            var player = scope.player;
            player.setShiftKeyDown(true);
            check(callback.click(player, HAND, aim(player, singles.getBlockPos(), 0.125, 0.125)),
                    "Rotation consumed");
            checkEquals(1, singles.getCubeRotation(0), "Aimed rotation");
            checkEquals(0, singles.getCubeRotation(1), "Other item rotation");
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

    /**
     * To reproduce: hold dirt only in the off hand and modifier-click Storage; nothing is deposited.
     * Shift-click it with a redstone torch: the block rotates and the torch is neither deposited
     * nor spent. Shift-click it with dirt: the dirt is placed as an ordinary block, not deposited.
     */
    public static void handsSneakingAndRotationPrecedence(GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory, LoaderClick callback) {
        var storage = placeStorage(helper, TARGET);
        try (PlayerScope scope = new PlayerScope(factory.apply(ItemStack.EMPTY))) {
            var player = scope.player;
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT));
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            var hit = aim(player, storage.getBlockPos(), 0.5, 0.5);
            callback.click(player, HAND, hit);
            checkEquals(0, count(storage.getItems(), Items.DIRT), "Off-hand item deposited");
            checkEquals(1, player.getOffhandItem().getCount(), "Off-hand item spent");
            player.setShiftKeyDown(true);
            player.setItemInHand(HAND, new ItemStack(Items.REDSTONE_TORCH));
            check(callback.click(player, HAND, hit), "Torch gesture not handled");
            checkEquals(1, storage.getRotation(), "Block rotation");
            checkEquals(0, count(storage.getItems(), Items.REDSTONE_TORCH), "Torch deposited");
            checkEquals(1, player.getMainHandItem().getCount(), "Torch spent");
            player.setItemInHand(HAND, new ItemStack(Items.DIRT));
            callback.click(player, HAND, hit);
            checkEquals(0, count(storage.getItems(), Items.DIRT), "Sneaking deposited");
            checkEquals(1, storage.getRotation(), "Sneaking rotated");
        }
        helper.succeed();
    }

    /** To reproduce: select permanence, sneak, and modifier-click Storage empty-handed twice. Each click toggles it. */
    public static void permanenceTogglesWhileSneaking(GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory, LoaderClick callback) {
        var storage = placeStorage(helper, TARGET);
        try (PlayerScope scope = new PlayerScope(factory.apply(ItemStack.EMPTY))) {
            var player = scope.player;
            player.setShiftKeyDown(true);
            ServerGestureState.set(player, StackMode.TOGGLE_PERMANENT, true);
            var hit = aim(player, storage.getBlockPos(), 0.5, 0.5);
            check(callback.click(player, HAND, hit), "Toggle not consumed");
            check(storage.pile().isPermanent(), "First click did not toggle");
            callback.click(player, HAND, hit);
            check(!storage.pile().isPermanent(), "Second click did not toggle");
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
            callback.click(player, HAND, new BlockHitResult(hit.getLocation(), Direction.EAST, support, false));
            check(helper.getLevel().getBlockState(destination).isAir(), "Unsupported first cell left block");
            checkEquals(1, player.getMainHandItem().getCount(), "Rejected deposit spent hand");
        }
        helper.succeed();
    }

    /**
     * To reproduce: hold snowballs, hold the Stack Modifier, and right-click open air. The mode
     * cycles and no snowball is thrown; release the modifier and right-click again to throw one.
     */
    public static void modifierAirUseLeavesItemUnused(GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory) {
        try (PlayerScope scope = new PlayerScope(factory.apply(new ItemStack(Items.SNOWBALL, 4)))) {
            var player = scope.player;
            Vec3 at = helper.absoluteVec(Vec3.atCenterOf(TARGET));
            player.setPos(at.x, at.y, at.z);
            var area = new AABB(player.blockPosition()).inflate(4);
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            check(!useItem(player).consumesAction(), "Modifier item use consumed");
            checkEquals(4, player.getMainHandItem().getCount(), "Modifier item use spent item");
            checkEquals(0, helper.getLevel().getEntitiesOfClass(Snowball.class, area).size(), "Modifier item use threw");
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            check(useItem(player).consumesAction(), "Plain item use refused");
            checkEquals(3, player.getMainHandItem().getCount(), "Plain item use did not spend item");
            helper.getLevel().getEntitiesOfClass(Snowball.class, area).forEach(Entity::discard);
        }
        helper.succeed();
    }

    /** Runs vanilla's server item use, which posts the loader's item-use event before the item acts. */
    private static InteractionResult useItem(ServerPlayer player) {
        return player.gameMode.useItem(player, player.serverLevel(), player.getItemInHand(HAND), HAND);
    }

    /** To reproduce: select another mode, disconnect, and reconnect. Logout clears mode/modifier state. */
    public static void stateAndLogout(GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory, LoaderClick callback) {
        try (PlayerScope scope = new PlayerScope(factory.apply(ItemStack.EMPTY))) {
            var player = scope.player;
            GestureStatePkt.handleServer(new GestureStatePkt(StackMode.BAR_STACK, true), player);
            checkEquals(new ServerGestureState.State(StackMode.BAR_STACK, true), ServerGestureState.get(player), "Synced state");
            ServerLifecycle.logout(player.getUUID());
            checkEquals(new ServerGestureState.State(StackMode.STORAGE_STACK, false), ServerGestureState.get(player), "Logout state");
        }
        helper.succeed();
    }

    /**
     * To reproduce: put a sculk sensor beside a Storage, a Singles, and a Bar Stack. Deposit into
     * and extract from Storage, toggle its permanence, and rotate it with a redstone torch; rotate a
     * Singles item with a soul torch. Each successful use vibrates. Extract one of two Singles items
     * or bars, then the last: the first vibrates as a change, the last only as the block's
     * destruction.
     */
    public static void successfulUseEmitsBlockChange(GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory, LoaderClick callback) {
        var storage = placeStorage(helper, TARGET);
        var singles = placeSingles(helper, TARGET.east(3));
        var bars = placeBar(helper, TARGET.east(6));
        var barItem = firstBarItem();
        singles.getItems().setStackInSlot(0, new ItemStack(Items.STONE));
        singles.getItems().setStackInSlot(1, new ItemStack(Items.STONE));
        bars.getItems().setStackInSlot(0, new ItemStack(barItem));
        bars.getItems().setStackInSlot(1, new ItemStack(barItem));
        try (PlayerScope scope = new PlayerScope(factory.apply(new ItemStack(Items.DIRT)))) {
            var player = scope.player;
            BlockPos pile = storage.getBlockPos();
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            expectUseEvent(helper, player, pile, GameEvent.BLOCK_CHANGE, () -> callback.click(player, HAND, aim(player, pile, 0.2, 0.2)));
            checkEquals(1, count(storage.getItems(), Items.DIRT), "Deposit contents");
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            expectUseEvent(helper, player, pile, GameEvent.BLOCK_CHANGE, () -> callback.click(player, HAND, aim(player, pile, 0.2, 0.2)));
            checkEquals(0, count(storage.getItems(), Items.DIRT), "Extraction contents");
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.set(player, StackMode.TOGGLE_PERMANENT, true);
            expectUseEvent(helper, player, pile, GameEvent.BLOCK_CHANGE, () -> callback.click(player, HAND, aim(player, pile, 0.5, 0.5)));
            check(storage.pile().isPermanent(), "Toggle did not apply");
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            player.setShiftKeyDown(true);
            player.setItemInHand(HAND, new ItemStack(Items.REDSTONE_TORCH));
            expectUseEvent(helper, player, pile, GameEvent.BLOCK_CHANGE, () -> callback.click(player, HAND, aim(player, pile, 0.5, 0.5)));
            checkEquals(1, storage.getRotation(), "Block rotation");
            player.setItemInHand(HAND, new ItemStack(Items.SOUL_TORCH));
            expectUseEvent(helper, player, singles.getBlockPos(), GameEvent.BLOCK_CHANGE,
                    () -> callback.click(player, HAND, aim(player, singles.getBlockPos(), 0.125, 0.125)));
            checkEquals(1, singles.getCubeRotation(0), "Item rotation");
            player.setShiftKeyDown(false);
            for (var be : new StackBlockEntity[] {singles, bars}) {
                BlockPos pos = be.getBlockPos();
                double y = be == bars ? 0.0625 : 0.125;
                player.setItemInHand(HAND, ItemStack.EMPTY);
                expectUseEvent(helper, player, pos, GameEvent.BLOCK_CHANGE, () -> callback.click(player, HAND, aim(player, pos, y, 0.125)));
                checkEquals(1, player.getMainHandItem().getCount(), "First extraction");
                expectUseEvent(helper, player, pos, GameEvent.BLOCK_DESTROY, () -> callback.click(player, HAND, aim(player, pos, y, 0.125)));
                checkEquals(2, player.getMainHandItem().getCount(), "Last extraction");
            }
        }
        helper.succeed();
    }

    /** Runs one gesture and requires exactly {@code event} at {@code pos}, attributed to the player. */
    private static void expectUseEvent(GameTestHelper helper, ServerPlayer player, BlockPos pos,
                                       GameEvent event, Runnable gesture) {
        var level = helper.getLevel();
        var before = level.getBlockState(pos);
        RotationSoundThrottle.clear(player.getUUID());
        try (var events = new WorldEditChecks.EventProbe(level, pos)) {
            gesture.run();
            events.expectOne(event, player, event == GameEvent.BLOCK_DESTROY ? before : level.getBlockState(pos));
        }
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
            RotationSoundThrottle.clear(player.getUUID());
        }
        @Override public void close() {
            player.setPos(position.x, position.y, position.z);
            player.setXRot(xRot); player.setYRot(yRot);
            player.setShiftKeyDown(shift);
            player.getAbilities().instabuild = creative;
            player.setItemInHand(HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, offhand);
            ServerGestureState.clear(player.getUUID());
            RotationSoundThrottle.clear(player.getUUID());
        }
    }
}
