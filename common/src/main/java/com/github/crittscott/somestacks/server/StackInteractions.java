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
import com.github.crittscott.somestacks.util.StackGesture;
import com.github.crittscott.somestacks.block.StackBlock;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import com.github.crittscott.somestacks.util.ViewRay;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import javax.annotation.Nullable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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
        if (WorldEdits.isConsultingAdjacent() || hand != InteractionHand.MAIN_HAND) {
            return false;
        }

        BlockPos clickedPos = hit.getBlockPos();

        ServerGestureState.State gesture = ServerGestureState.get(player);
        ViewRay ray = ViewRay.through(player, hit.getLocation());
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
        if (WorldEdits.isConsultingAdjacent() || hand != InteractionHand.MAIN_HAND
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
                player, pos, type, ViewRay.through(player, hit.getLocation()),
                player.getMainHandItem());
    }

    /**
     * Handles a loader-native server right-click against a non-stack block when the selected
     * stack belongs in the adjacent position.
     */
    public static boolean handleAdjacentClick(
            ServerPlayer player, InteractionHand hand, BlockHitResult hit,
            boolean blockAllowed, boolean itemAllowed) {
        if (WorldEdits.isConsultingAdjacent() || hand != InteractionHand.MAIN_HAND) {
            return false;
        }

        Level level = player.level();
        BlockPos clickedPos = hit.getBlockPos();
        if (BlockType.of(level.getBlockState(clickedPos).getBlock()) != null) {
            return false;
        }

        ServerGestureState.State gesture = ServerGestureState.get(player);
        ViewRay ray = ViewRay.through(player, hit.getLocation());
        ItemStack held = player.getMainHandItem();
        if (StackGesture.classify(null, gesture.mode(), gesture.modifierDown(),
                player.isShiftKeyDown(), held, true, hand) != StackGesture.DEPOSIT
                || !blockAllowed
                || !itemAllowed
                || player.isSpectator()
                || WorldEdits.isProtected(player, clickedPos)) {
            return false;
        }

        BlockPos destination = clickedPos.relative(hit.getDirection());
        BlockType destinationType = BlockType.of(level.getBlockState(destination).getBlock());
        if (destinationType == BlockType.SINGLES_STACK || destinationType == BlockType.BAR_STACK) {
            if (!WorldEdits.mayUseAdjacent(player, destination)) {
                return true;
            }
            deposit(player, destination, ray);
            return true;
        }

        BlockPlaceContext context = new BlockPlaceContext(player, hand, held, hit);
        if (context.canPlace()) {
            placeAndDeposit(player, gesture.mode().toBlockType(), context.getClickedPos(),
                    hit.getDirection(), ray, context);
        }
        return true;
    }

    private static boolean applyExistingStack(
            ServerPlayer player, BlockPos pos, BlockType type, Direction face, ViewRay ray,
            ItemStack held, ServerGestureState.State gesture) {
        if (player.isSpectator() || WorldEdits.isProtected(player, pos)) {
            return true;
        }

        StackGesture intent = StackGesture.classify(type, gesture.mode(), gesture.modifierDown(),
                player.isShiftKeyDown(), held, true, InteractionHand.MAIN_HAND);
        if (intent == StackGesture.TOGGLE_PERMANENT) {
            togglePermanent(player, pos);
            return true;
        }

        if (intent == StackGesture.DEPOSIT) {
            if (depositsIntoClickedBlock(player.level(), pos, type, face, ray)) {
                deposit(player, pos, ray);
            } else {
                BlockPos destination = pos.relative(face);
                placeAndDeposit(player, gesture.mode().toBlockType(), destination, face, ray, null);
            }
            return true;
        }

        if (intent == StackGesture.EXTRACT) {
            extract(player, pos, ray);
            return true;
        }

        return false;
    }

    private static boolean applySneakingRotation(
            ServerPlayer player, BlockPos pos, BlockType type, ViewRay ray, ItemStack held) {
        StackGesture intent = StackGesture.classify(type, StackMode.STORAGE_STACK, false,
                true, held, true, InteractionHand.MAIN_HAND);
        if (intent == StackGesture.ROTATE_BLOCK) {
            rotateBlock(player, pos);
            return true;
        }
        if (intent == StackGesture.ROTATE_ITEM) {
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

        BlockEntity target = level.getBlockEntity(pos);
        if (target instanceof StorageStackBE storage) {
            int deposited = storage.deposit(handStack, player);
            returnToHand(player, creative, handStack);
            if (deposited > 0) {
                StackSounds.play(level, pos, CommonRegistry.storageDepositSound());
                emitBlockChange(player, pos, level.getBlockState(pos));
            }
            return;
        }

        if (target instanceof SinglesStackBE singles) {
            int index = SinglesCubeIdx.traceAllPositions(
                    ray, pos, singles.getItems(), singles.getRotation());
            if (singles.depositAt(index, handStack)) {
                returnToHand(player, creative, handStack);
                StackSounds.play(level, pos, CommonRegistry.singlesDepositSound());
                emitBlockChange(player, pos, level.getBlockState(pos));
            }
            return;
        }

        if (target instanceof BarStackBE bars) {
            int index = BarCubeIdx.traceAllPositions(ray, pos, bars.getItems());
            if (bars.depositAt(index, handStack)) {
                returnToHand(player, creative, handStack);
                StackSounds.play(level, pos, CommonRegistry.barDepositSound());
                emitBlockChange(player, pos, level.getBlockState(pos));
            }
        }
    }

    private static void placeAndDeposit(ServerPlayer player, BlockType type,
                                        BlockPos pos, Direction face,
                                        ViewRay ray, @Nullable BlockPlaceContext context) {
        Level level = player.level();
        boolean creative = player.getAbilities().instabuild;
        ItemStack held = player.getMainHandItem();
        ItemStack handStack = creative ? held.copy() : held;
        if (handStack.isEmpty()
                || (context == null ? !level.getBlockState(pos).canBeReplaced() : !context.canPlace())
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
            player.sendOverlayMessage(Component.translatable(
                    "somestacks.message.maximum_height", ServerConfig.maxPileHeight()));
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

        BlockState state = context == null
                ? StackBlock.placementState(type.getBlock(), level, pos)
                : type.getBlock().getStateForPlacement(context);
        if (state == null) return;
        VoxelShape finalCollision = switch (type) {
            case STORAGE_STACK -> state.getCollisionShape(level, pos, CollisionContext.empty());
            case SINGLES_STACK -> SinglesCubeIdx.shapeFor(depositIndex, 0);
            case BAR_STACK -> BarCubeIdx.shapeFor(depositIndex);
        };
        if (!WorldEdits.placeChecked(
                player, player.level(), pos, state, face.getOpposite(), finalCollision)) {
            return;
        }

        ItemStack placedWith = handStack.copy();
        switch (type) {
            case STORAGE_STACK -> {
                StorageStackBE storage = (StorageStackBE) level.getBlockEntity(pos);
                storage.deposit(handStack, player);
                StackSounds.play(level, pos, CommonRegistry.storageDepositSound());
            }
            case SINGLES_STACK -> {
                SinglesStackBE singles = (SinglesStackBE) level.getBlockEntity(pos);
                singles.depositAt(depositIndex, handStack);
                StackSounds.play(level, pos, CommonRegistry.singlesDepositSound());
            }
            case BAR_STACK -> {
                BarStackBE bars = (BarStackBE) level.getBlockEntity(pos);
                bars.depositAt(depositIndex, handStack);
                StackSounds.play(level, pos, CommonRegistry.barDepositSound());
            }
        }
        returnToHand(player, creative, handStack);
        CriteriaTriggers.PLACED_BLOCK.trigger(player, pos, placedWith);
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
        var originalStack = level.getBlockEntity(pos);
        if (originalStack instanceof StorageStackBE storage) {
            int index = StorageCubeIdx.traceCubes(ray, pos, storage, storage.getRotation());
            if (index < 0) {
                return;
            }
            ItemStack handStack = player.getMainHandItem();
            int maxCanTake = handStack.isEmpty()
                    ? Integer.MAX_VALUE
                    : handStack.getMaxStackSize() - handStack.getCount();
            ItemStack taken = storage.extractAt(
                    index, maxCanTake, handStack, player);
            if (!taken.isEmpty()) {
                finishExtraction(player, pos, originalStack, taken, CommonRegistry.storageExtractSound());
            }
            return;
        }

        if (originalStack instanceof SinglesStackBE singles) {
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
                finishExtraction(player, pos, originalStack, taken, CommonRegistry.singlesExtractSound());
            }
            return;
        }

        if (originalStack instanceof BarStackBE bars) {
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
                finishExtraction(player, pos, originalStack, taken, CommonRegistry.barExtractSound());
            }
        }
    }

    private static void finishExtraction(ServerPlayer player, BlockPos pos, BlockEntity original,
                                         ItemStack taken, SoundEvent sound) {
        StackSounds.play(player.level(), pos, sound);
        ItemOps.giveToPlayerOrDrop(player, InteractionHand.MAIN_HAND, taken);
        emitSurvivingBlockChange(player, pos, original);
    }

    private static void rotateBlock(ServerPlayer player, BlockPos pos) {
        int rotation;
        BlockEntity target = player.level().getBlockEntity(pos);
        if (target instanceof SinglesStackBE singles) {
            rotation = QuarterTurns.next(singles.getRotation());
            singles.setRotation(rotation);
            StackSounds.playRotation(player, pos, CommonRegistry.singlesRotateSound());
        } else if (target instanceof StorageStackBE storage) {
            rotation = QuarterTurns.next(storage.getRotation());
            storage.setRotation(rotation);
            StackSounds.playRotation(player, pos, CommonRegistry.storageRotateSound());
        } else {
            return;
        }
        emitBlockChange(player, pos, player.level().getBlockState(pos));
        player.sendOverlayMessage(Component.translatable(
                "somestacks.message.rotation", QuarterTurns.degrees(rotation)));
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
        player.sendOverlayMessage(Component.translatable(
                "somestacks.message.item_rotation", QuarterTurns.degrees(rotation)));
    }

    private static void togglePermanent(ServerPlayer player, BlockPos pos) {
        StoragePile pile = StoragePile.at(player.level(), pos);
        if (pile == null) {
            return;
        }
        boolean permanent = !pile.isPermanent();
        pile.setPermanent(permanent, player);
        emitBlockChange(player, pos, player.level().getBlockState(pos));
        player.sendOverlayMessage(Component.translatable(
                "somestacks.message.pile_state",
                Component.translatable(permanent
                        ? "somestacks.state.permanent"
                        : "somestacks.state.temporary")));
    }

    private static void returnToHand(ServerPlayer player, boolean creative, ItemStack handStack) {
        if (!creative) {
            player.setItemInHand(InteractionHand.MAIN_HAND, handStack);
        }
    }

    /** Emits one sensor-visible event for one successful player mutation inside a stack block. */
    private static void emitSurvivingBlockChange(ServerPlayer player, BlockPos pos, BlockEntity edited) {
        Level level = player.level();
        if (level.getBlockEntity(pos) == edited) {
            emitBlockChange(player, pos, level.getBlockState(pos));
        }
    }

    private static void emitBlockChange(ServerPlayer player, BlockPos pos, BlockState state) {
        player.level().gameEvent(
                GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, state));
    }
}
