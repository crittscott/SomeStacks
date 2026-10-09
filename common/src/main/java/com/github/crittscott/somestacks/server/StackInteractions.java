package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.block.BarColumn;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesColumn;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.QuarterTurns;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.StackMode;
import com.github.crittscott.somestacks.util.StackPlacement;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import com.github.crittscott.somestacks.util.ViewRay;
import com.github.crittscott.somestacks.util.ViewRays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Applies stack gestures carried by the vanilla right-click interaction. */
public final class StackInteractions {
    private StackInteractions() {}

    /** Handles a vanilla use call on an existing stack block. */
    public static boolean handleExistingStack(
            ServerPlayer player, InteractionHand hand, BlockHitResult hit, BlockType clickedType) {
        if (AdjacentEdits.isConsulting() || hand != InteractionHand.MAIN_HAND) {
            return false;
        }

        BlockPos clickedPos = hit.getBlockPos();

        ServerGestureState.State gesture = ServerGestureState.get(player);
        ViewRay ray = ViewRays.through(player, hit.getLocation());
        ItemStack held = player.getMainHandItem();
        return applyExistingStack(
                player, clickedPos, clickedType, hit.getDirection(), ray, held, gesture);
    }

    /**
     * Handles the sneaking torch gestures before vanilla bypasses the block's use methods.
     * Loader callbacks call this only after earlier listeners have allowed both block and item use.
     */
    public static boolean handleSneakingRotation(
            ServerPlayer player, InteractionHand hand, BlockHitResult hit,
            boolean blockAllowed, boolean itemAllowed) {
        if (AdjacentEdits.isConsulting()
                || hand != InteractionHand.MAIN_HAND
                || !blockAllowed
                || !itemAllowed
                || !player.isShiftKeyDown()
                || player.isSpectator()) {
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        BlockType type = BlockType.of(player.level().getBlockState(pos).getBlock());
        if (type == null || WorldEdits.isProtected(player, pos)) {
            return false;
        }

        return applySneakingRotation(
                player, pos, type, ViewRays.through(player, hit.getLocation()),
                player.getMainHandItem());
    }

    /**
     * Handles a loader-native server right-click against a non-stack block when the selected
     * stack belongs in the adjacent position.
     */
    public static boolean handleAdjacentClick(
            ServerPlayer player, InteractionHand hand, BlockHitResult hit,
            boolean blockAllowed, boolean itemAllowed) {
        if (AdjacentEdits.isConsulting() || hand != InteractionHand.MAIN_HAND) {
            return false;
        }

        Level level = player.level();
        BlockPos clickedPos = hit.getBlockPos();
        if (BlockType.of(level.getBlockState(clickedPos).getBlock()) != null) {
            return false;
        }

        ServerGestureState.State gesture = ServerGestureState.get(player);
        ViewRay ray = ViewRays.through(player, hit.getLocation());
        ItemStack held = player.getMainHandItem();
        if (!gesture.modifierDown()
                || gesture.mode() == StackMode.TOGGLE_PERMANENT
                || player.isShiftKeyDown()
                || held.isEmpty()
                || !blockAllowed
                || !itemAllowed
                || player.isSpectator()
                || WorldEdits.isProtected(player, clickedPos)) {
            return false;
        }

        BlockPos destination = clickedPos.relative(hit.getDirection());
        BlockType destinationType = BlockType.of(level.getBlockState(destination).getBlock());
        if (destinationType == BlockType.SINGLES_STACK || destinationType == BlockType.BAR_STACK) {
            if (!AdjacentEdits.mayUseItemAt(player, destination)) {
                return false;
            }
            deposit(player, destination, ray);
            return true;
        }

        placeAndDeposit(player, gesture.mode().toBlockType(), destination,
                hit.getDirection(), ray);
        return true;
    }

    private static boolean applyExistingStack(
            ServerPlayer player, BlockPos pos, BlockType type, Direction face, ViewRay ray,
            ItemStack held, ServerGestureState.State gesture) {
        if (player.isSpectator() || WorldEdits.isProtected(player, pos)) {
            return true;
        }

        if (gesture.modifierDown()
                && held.isEmpty()
                && gesture.mode() == StackMode.TOGGLE_PERMANENT
                && type == BlockType.STORAGE_STACK) {
            togglePermanent(player, pos);
            return true;
        }

        if (gesture.modifierDown()
                && gesture.mode() != StackMode.TOGGLE_PERMANENT
                && !player.isShiftKeyDown()
                && !held.isEmpty()) {
            if (depositsIntoClickedBlock(player.level(), pos, type, face, ray)) {
                deposit(player, pos, ray);
            } else {
                BlockPos destination = pos.relative(face);
                placeAndDeposit(player, gesture.mode().toBlockType(), destination, face, ray);
            }
            return true;
        }

        if (!gesture.modifierDown() && !player.isShiftKeyDown()) {
            extract(player, pos, ray);
            return true;
        }

        return false;
    }

    private static boolean applySneakingRotation(
            ServerPlayer player, BlockPos pos, BlockType type, ViewRay ray, ItemStack held) {
        if (held.is(Items.REDSTONE_TORCH)
                && (type == BlockType.STORAGE_STACK || type == BlockType.SINGLES_STACK)) {
            rotateBlock(player, pos, type);
            return true;
        }
        if (held.is(Items.SOUL_TORCH) && type == BlockType.SINGLES_STACK) {
            rotateItem(player, pos, ray);
            return true;
        }
        return false;
    }

    private static boolean depositsIntoClickedBlock(Level level, BlockPos pos, BlockType type,
                                                     Direction face, ViewRay ray) {
        if (face != Direction.UP || type == BlockType.STORAGE_STACK) {
            return true;
        }

        if (level.getBlockEntity(pos) instanceof SinglesStackBE singles) {
            int index = SinglesCubeIdx.traceAllPositions(
                    ray, pos, singles.getItems(), singles.getRotation());
            return singles.canDepositAt(index);
        }

        if (level.getBlockEntity(pos) instanceof BarStackBE bars) {
            int index = BarCubeIdx.traceAllPositions(ray, pos, bars.getItems());
            return bars.canDepositAt(index);
        }

        return true;
    }

    private static void deposit(ServerPlayer player, BlockPos pos, ViewRay ray) {
        Level level = player.level();
        boolean creative = player.getAbilities().instabuild;
        ItemStack held = player.getMainHandItem();
        ItemStack handStack = creative ? held.copy() : held;
        if (handStack.isEmpty()
                || ItemOps.checkDisabledModAndNotify(handStack, player)
                || ItemOps.checkDisabledItemAndNotify(handStack, player)) {
            return;
        }

        BlockType type = BlockType.of(level.getBlockState(pos).getBlock());
        if (type == BlockType.STORAGE_STACK
                && level.getBlockEntity(pos) instanceof StorageStackBE storage) {
            int deposited = storage.deposit(handStack, player);
            returnToHand(player, creative, handStack);
            if (deposited > 0) {
                level.playSound(null, pos, CommonRegistry.storageDepositSound(), SoundSource.BLOCKS,
                        StackSounds.VOLUME, 1.0f);
                emitBlockChange(player, pos, level.getBlockState(pos));
            }
            return;
        }

        if (type == BlockType.SINGLES_STACK
                && level.getBlockEntity(pos) instanceof SinglesStackBE singles) {
            int index = SinglesCubeIdx.traceAllPositions(
                    ray, pos, singles.getItems(), singles.getRotation());
            if (index >= 0 && singles.getItems().getStackInSlot(index).isEmpty()
                    && singles.depositAt(index, handStack)) {
                returnToHand(player, creative, handStack);
                level.playSound(null, pos, CommonRegistry.singlesDepositSound(), SoundSource.BLOCKS,
                        StackSounds.VOLUME, 1.0f);
                emitBlockChange(player, pos, level.getBlockState(pos));
            }
            return;
        }

        if (type == BlockType.BAR_STACK
                && level.getBlockEntity(pos) instanceof BarStackBE bars) {
            int index = BarCubeIdx.traceAllPositions(ray, pos, bars.getItems());
            if (index >= 0 && bars.getItems().getStackInSlot(index).isEmpty()
                    && bars.depositAt(index, handStack)) {
                returnToHand(player, creative, handStack);
                level.playSound(null, pos, CommonRegistry.barDepositSound(), SoundSource.BLOCKS,
                        StackSounds.VOLUME, 1.0f);
                emitBlockChange(player, pos, level.getBlockState(pos));
            }
        }
    }

    private static void placeAndDeposit(ServerPlayer player, BlockType type,
                                        BlockPos pos, Direction face,
                                        ViewRay ray) {
        Level level = player.level();
        boolean creative = player.getAbilities().instabuild;
        ItemStack held = player.getMainHandItem();
        ItemStack handStack = creative ? held.copy() : held;
        if (handStack.isEmpty()
                || !level.getBlockState(pos).canBeReplaced()
                || WorldEdits.isProtected(player, pos)
                || !player.mayUseItemAt(pos, face, handStack)
                || ItemOps.checkDisabledModAndNotify(handStack, player)
                || ItemOps.checkDisabledItemAndNotify(handStack, player)
                || !type.isEnabled()) {
            return;
        }

        boolean columnFull = switch (type) {
            case STORAGE_STACK -> !StoragePile.columnHasRoomFor(level, pos);
            case SINGLES_STACK -> !SinglesColumn.columnHasRoomFor(level, pos);
            case BAR_STACK -> !BarColumn.columnHasRoomFor(level, pos);
        };
        if (columnFull) {
            player.displayClientMessage(Component.translatable(
                    "somestacks.message.maximum_height", ServerConfig.maxPileHeight()), true);
            return;
        }

        int depositIndex = switch (type) {
            case STORAGE_STACK -> -1;
            case SINGLES_STACK -> SinglesCubeIdx.traceAllPositions(ray, pos);
            case BAR_STACK -> BarCubeIdx.traceAllPositions(ray, pos);
        };
        if (type != BlockType.STORAGE_STACK && depositIndex < 0) {
            return;
        }
        if (!firstDepositWouldSucceed(type, level, pos, handStack, depositIndex)) {
            return;
        }

        BlockState state = StackPlacement.stateFor(type.getBlock(), level, pos);
        VoxelShape finalCollision = switch (type) {
            case STORAGE_STACK -> state.getCollisionShape(level, pos, CollisionContext.empty());
            case SINGLES_STACK -> SinglesCubeIdx.shapeFor(depositIndex, 0);
            case BAR_STACK -> BarCubeIdx.shapeFor(depositIndex);
        };
        if (!WorldEdits.placeChecked(
                player, player.serverLevel(), pos, state, face.getOpposite(), finalCollision)) {
            return;
        }

        switch (type) {
            case STORAGE_STACK -> {
                StorageStackBE storage = (StorageStackBE) level.getBlockEntity(pos);
                storage.deposit(handStack, player);
                level.playSound(null, pos, CommonRegistry.storageDepositSound(), SoundSource.BLOCKS,
                        StackSounds.VOLUME, 1.0f);
            }
            case SINGLES_STACK -> {
                SinglesStackBE singles = (SinglesStackBE) level.getBlockEntity(pos);
                singles.depositAt(depositIndex, handStack);
                level.playSound(null, pos, CommonRegistry.singlesDepositSound(), SoundSource.BLOCKS,
                        StackSounds.VOLUME, 1.0f);
            }
            case BAR_STACK -> {
                BarStackBE bars = (BarStackBE) level.getBlockEntity(pos);
                bars.depositAt(depositIndex, handStack);
                level.playSound(null, pos, CommonRegistry.barDepositSound(), SoundSource.BLOCKS,
                        StackSounds.VOLUME, 1.0f);
            }
        }
        returnToHand(player, creative, handStack);
    }

    private static boolean firstDepositWouldSucceed(BlockType type, Level level, BlockPos pos,
                                                    ItemStack handStack, int depositIndex) {
        return switch (type) {
            case STORAGE_STACK -> StorageStackBE.isValidStorageItem(handStack);
            case SINGLES_STACK -> SinglesStackBE.isValidSinglesItem(handStack)
                    && SinglesStackBE.supportsFreshDeposit(level, pos, depositIndex);
            case BAR_STACK -> BarStackBE.isValidBarItem(handStack)
                    && BarStackBE.supportsFreshDeposit(level, pos, depositIndex);
        };
    }

    private static void extract(ServerPlayer player, BlockPos pos, ViewRay ray) {
        Level level = player.level();
        BlockState changedState = level.getBlockState(pos);
        if (level.getBlockEntity(pos) instanceof StorageStackBE storage) {
            int index = StorageCubeIdx.traceCubes(ray, pos, storage, storage.getRotation());
            if (index < 0) {
                return;
            }
            ItemStack handStack = player.getMainHandItem();
            int maxCanTake = handStack.isEmpty()
                    ? Integer.MAX_VALUE
                    : handStack.getMaxStackSize() - handStack.getCount();
            ItemStack taken = storage.extractAt(
                    index, maxCanTake, handStack.isEmpty() ? ItemStack.EMPTY : handStack, player);
            if (!taken.isEmpty()) {
                level.playSound(null, pos, CommonRegistry.storageExtractSound(), SoundSource.BLOCKS,
                        StackSounds.VOLUME, 1.0f);
                ItemOps.giveToPlayerOrDrop(player, InteractionHand.MAIN_HAND, taken);
                emitBlockChange(player, pos, changedState);
            }
            return;
        }

        if (level.getBlockEntity(pos) instanceof SinglesStackBE singles) {
            int index = SinglesCubeIdx.traceCubes(ray, pos, singles);
            if (index < 0) {
                return;
            }
            ItemStack stored = singles.getItems().getStackInSlot(index);
            if (stored.isEmpty() || !ItemOps.canTakeIntoHand(player.getMainHandItem(), stored)) {
                return;
            }
            ItemStack taken = singles.extractAt(index, player);
            if (!taken.isEmpty()) {
                level.playSound(null, pos, CommonRegistry.singlesExtractSound(), SoundSource.BLOCKS,
                        StackSounds.VOLUME, 1.0f);
                ItemOps.giveToPlayerOrDrop(player, InteractionHand.MAIN_HAND, taken);
                emitBlockChange(player, pos, changedState);
            }
            return;
        }

        if (level.getBlockEntity(pos) instanceof BarStackBE bars) {
            int index = BarCubeIdx.traceCubes(ray, pos, bars);
            if (index < 0) {
                return;
            }
            ItemStack stored = bars.getItems().getStackInSlot(index);
            if (stored.isEmpty() || !ItemOps.canTakeIntoHand(player.getMainHandItem(), stored)) {
                return;
            }
            ItemStack taken = bars.extractAt(index, player);
            if (!taken.isEmpty()) {
                level.playSound(null, pos, CommonRegistry.barExtractSound(), SoundSource.BLOCKS,
                        StackSounds.VOLUME, 1.0f);
                ItemOps.giveToPlayerOrDrop(player, InteractionHand.MAIN_HAND, taken);
                emitBlockChange(player, pos, changedState);
            }
        }
    }

    private static void rotateBlock(ServerPlayer player, BlockPos pos, BlockType type) {
        int rotation;
        if (type == BlockType.SINGLES_STACK
                && player.level().getBlockEntity(pos) instanceof SinglesStackBE singles) {
            rotation = QuarterTurns.next(singles.getRotation());
            singles.setRotation(rotation);
            StackSounds.playRotation(player, pos, CommonRegistry.singlesRotateSound());
        } else if (type == BlockType.STORAGE_STACK
                && player.level().getBlockEntity(pos) instanceof StorageStackBE storage) {
            rotation = QuarterTurns.next(storage.getRotation());
            storage.setRotation(rotation);
            StackSounds.playRotation(player, pos, CommonRegistry.storageRotateSound());
        } else {
            return;
        }
        emitBlockChange(player, pos, player.level().getBlockState(pos));
        player.displayClientMessage(Component.translatable(
                "somestacks.message.rotation", QuarterTurns.degrees(rotation)), true);
    }

    private static void rotateItem(ServerPlayer player, BlockPos pos, ViewRay ray) {
        if (!(player.level().getBlockEntity(pos) instanceof SinglesStackBE singles)) {
            return;
        }
        int index = SinglesCubeIdx.traceCubes(ray, pos, singles);
        if (index < 0 || singles.getItems().getStackInSlot(index).isEmpty()) {
            return;
        }
        int rotation = QuarterTurns.next(singles.getCubeRotation(index));
        singles.setCubeRotation(index, rotation);
        emitBlockChange(player, pos, player.level().getBlockState(pos));
        StackSounds.playRotation(player, pos, CommonRegistry.singlesRotateItemSound());
        player.displayClientMessage(Component.translatable(
                "somestacks.message.item_rotation", QuarterTurns.degrees(rotation)), true);
    }

    private static void togglePermanent(ServerPlayer player, BlockPos pos) {
        StoragePile pile = StoragePile.at(player.level(), pos);
        if (pile == null) {
            return;
        }
        boolean permanent = !pile.isPermanent();
        pile.setPermanent(permanent, player);
        emitBlockChange(player, pos, player.level().getBlockState(pos));
        player.displayClientMessage(Component.translatable(
                "somestacks.message.pile_state",
                Component.translatable(permanent
                        ? "somestacks.state.permanent"
                        : "somestacks.state.temporary")), true);
    }

    private static void returnToHand(ServerPlayer player, boolean creative, ItemStack handStack) {
        if (!creative) {
            player.setItemInHand(InteractionHand.MAIN_HAND, handStack);
        }
    }

    /** Emits one sensor-visible event for one successful player mutation inside a stack block. */
    private static void emitBlockChange(ServerPlayer player, BlockPos pos, BlockState state) {
        player.serverLevel().gameEvent(
                GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, state));
    }
}
