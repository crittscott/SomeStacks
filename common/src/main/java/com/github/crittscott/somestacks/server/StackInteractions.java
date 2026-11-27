package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StackBlock;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.block.StackRun;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.QuarterTurns;
import com.github.crittscott.somestacks.util.StackGesture;
import com.github.crittscott.somestacks.util.StackMode;
import com.github.crittscott.somestacks.util.ViewRay;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Applies stack gestures carried by the vanilla right-click interaction: one deposit, one
 * extraction, and one placement path for every stack type, the sounds they play, and the pacing of
 * rotation sounds.
 */
public final class StackInteractions {
    private static final float ROTATION_PITCH_BASE = 0.9f;
    private static final float ROTATION_PITCH_SPREAD = 0.2f;

    /** Minimum ticks between one player's rotation sounds; rotations themselves are not paced. */
    private static final long ROTATION_SOUND_INTERVAL_TICKS = 4L;

    /** Volume of every stack action sound. */
    private static final float SOUND_VOLUME = 0.5f;

    /** Game tick of each player's last rotation sound, on the server clock. */
    private static final Map<UUID, Long> lastRotationSoundTick = new HashMap<>();

    private StackInteractions() {}

    /**
     * Interprets a click on an existing stack: permanence toggle, deposit into it or placement
     * against it, or extraction. Recognized gestures consume the click even when refused.
     */
    private static boolean applyExistingStack(
            ServerPlayer player,
            BlockPos pos,
            BlockType type,
            Direction face,
            ViewRay ray,
            ItemStack held,
            ServerGestureState.State gesture) {
        if (player.isSpectator() || WorldEdits.isProtected(player, pos)) {
            return true;
        }

        StackGesture intent =
                StackGesture.classify(
                        type,
                        gesture.mode(),
                        gesture.modifierDown(),
                        player.isShiftKeyDown(),
                        held,
                        true,
                        InteractionHand.MAIN_HAND);
        if (intent == StackGesture.TOGGLE_PERMANENT) {
            togglePermanent(player, pos);
            return true;
        }

        if (intent == StackGesture.DEPOSIT) {
            if (face != Direction.UP
                    || !(player.level().getBlockEntity(pos) instanceof StackBlockEntity stack)
                    || stack.acceptsDepositAlong(ray)) {
                deposit(player, pos, type, ray);
            } else {
                BlockPos destination = pos.relative(face);
                placeAndDeposit(player, gesture.mode().toBlockType(), destination, face, ray);
            }
            return true;
        }

        if (intent == StackGesture.EXTRACT) {
            extract(player, pos, type, ray);
            return true;
        }

        return false;
    }

    /**
     * Claims the player's next rotation sound: true at most once per {@link
     * #ROTATION_SOUND_INTERVAL_TICKS}.
     */
    public static boolean claimRotationSound(ServerPlayer player) {
        UUID id = player.getUUID();
        long tick = player.level().getGameTime();
        Long last = lastRotationSoundTick.get(id);
        if (last != null && tick - last < ROTATION_SOUND_INTERVAL_TICKS) {
            return false;
        }
        lastRotationSoundTick.put(id, tick);
        return true;
    }

    /** Forgets a player's rotation sound pacing when that player disconnects. */
    public static void clearRotationSoundPacing(UUID playerId) {
        lastRotationSoundTick.remove(playerId);
    }

    /**
     * Deposits the main-hand item into the stack at {@code pos}; a Storage pile that took nothing
     * because it reached the height limit says so. The callers' gesture classification has checked
     * the hand is not empty.
     */
    private static void deposit(ServerPlayer player, BlockPos pos, BlockType type, ViewRay ray) {
        Level level = player.level();
        boolean creative = player.getAbilities().instabuild;
        ItemStack held = player.getMainHandItem();
        ItemStack handStack = creative ? held.copy() : held;
        if (ItemOps.checkDisabledModAndNotify(handStack, player)
                || ItemOps.checkDisabledItemAndNotify(handStack, player)
                || !(level.getBlockEntity(pos) instanceof StackBlockEntity stack)) {
            return;
        }

        if (stack.depositFromHand(ray, handStack, player) > 0) {
            returnToHand(player, creative, handStack);
            playSound(level, pos, type.depositSound());
            emitBlockChange(player, pos, level.getBlockState(pos));
        } else if (stack instanceof StorageStackBE storage && type.admits(handStack)) {
            StoragePile pile = storage.pile();
            if (pile != null && pile.height() >= ServerConfig.settings().maxPileHeight()) {
                notifyMaximumHeight(player);
            }
        }
    }

    private static void emitBlockChange(ServerPlayer player, BlockPos pos, BlockState state) {
        player.level().gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, state));
    }

    /** Emits one sensor-visible event for one successful player mutation inside a stack block. */
    private static void emitSurvivingBlockChange(
            ServerPlayer player, BlockPos pos, BlockEntity edited) {
        Level level = player.level();
        if (level.getBlockEntity(pos) == edited) {
            emitBlockChange(player, pos, level.getBlockState(pos));
        }
    }

    /** Takes the targeted contents into the main hand, when it can hold them. */
    private static void extract(ServerPlayer player, BlockPos pos, BlockType type, ViewRay ray) {
        Level level = player.level();
        if (!(level.getBlockEntity(pos) instanceof StackBlockEntity stack)) {
            return;
        }
        int index = stack.extractionTarget(ray);
        if (index < 0) {
            return;
        }
        ItemStack taken = stack.extractIntoHand(index, player.getMainHandItem(), player);
        if (!taken.isEmpty()) {
            playSound(level, pos, type.extractSound());
            ItemOps.giveToPlayerOrDrop(player, InteractionHand.MAIN_HAND, taken);
            emitSurvivingBlockChange(player, pos, stack);
        }
    }

    /**
     * Handles a loader-native server right-click against a non-stack block when the selected stack
     * belongs in the adjacent position: deposits into a Singles or Bar stack there, otherwise places
     * the selected type where vanilla would place a block for this click. {@code blockAllowed} and
     * {@code itemAllowed} carry earlier listeners' verdicts; either false refuses. Returns true when
     * the click is consumed, including a deposit the destination's own interaction checks refused.
     */
    public static boolean handleAdjacentClick(
            ServerPlayer player,
            InteractionHand hand,
            BlockHitResult hit,
            boolean blockAllowed,
            boolean itemAllowed) {
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
        if (StackGesture.classify(
                                null,
                                gesture.mode(),
                                gesture.modifierDown(),
                                player.isShiftKeyDown(),
                                held,
                                true,
                                hand)
                        != StackGesture.DEPOSIT
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
            deposit(player, destination, destinationType, ray);
            return true;
        }

        BlockPlaceContext context = new BlockPlaceContext(player, hand, held, hit);
        if (context.canPlace()) {
            placeAndDeposit(
                    player,
                    gesture.mode().toBlockType(),
                    context.getClickedPos(),
                    hit.getDirection(),
                    ray);
        }
        return true;
    }

    /**
     * Handles a vanilla use call on an existing stack block. Returns true when the click is a
     * recognized gesture, which consumes it even when the action is refused.
     */
    public static boolean handleExistingStack(
            ServerPlayer player, InteractionHand hand, BlockHitResult hit, BlockType clickedType) {
        if (WorldEdits.isConsultingAdjacent() || hand != InteractionHand.MAIN_HAND) {
            return false;
        }

        ServerGestureState.State gesture = ServerGestureState.get(player);
        ViewRay ray = ViewRay.through(player, hit.getLocation());
        ItemStack held = player.getMainHandItem();
        return applyExistingStack(
                player, hit.getBlockPos(), clickedType, hit.getDirection(), ray, held, gesture);
    }

    /**
     * Handles the sneaking torch gestures before vanilla bypasses the block's use methods. Loader
     * callbacks call this only after earlier listeners have allowed both block and item use;
     * {@code blockAllowed} and {@code itemAllowed} carry those verdicts. Returns true when a
     * rotation gesture was recognized and the click is consumed.
     */
    public static boolean handleSneakingRotation(
            ServerPlayer player,
            InteractionHand hand,
            BlockHitResult hit,
            boolean blockAllowed,
            boolean itemAllowed) {
        if (WorldEdits.isConsultingAdjacent()
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

        StackGesture intent =
                StackGesture.classify(
                        type,
                        StackMode.STORAGE_STACK,
                        false,
                        true,
                        player.getMainHandItem(),
                        true,
                        InteractionHand.MAIN_HAND);
        if (intent == StackGesture.ROTATE_BLOCK) {
            rotateBlock(player, pos, type);
            return true;
        }
        if (intent == StackGesture.ROTATE_ITEM) {
            rotateItem(player, pos, ViewRay.through(player, hit.getLocation()));
            return true;
        }
        return false;
    }

    /** Tells the player a stack column cannot grow because it reached the configured height. */
    private static void notifyMaximumHeight(ServerPlayer player) {
        player.sendOverlayMessage(
                Component.translatable(
                        "somestacks.message.maximum_height",
                        ServerConfig.settings().maxPileHeight()));
    }

    /**
     * Places a new block of {@code type} at {@code pos} holding the main-hand item's first deposit.
     * Every check runs before placement, so a refused deposit leaves no empty block: the placement
     * gate, the player's item policy, enablement, column height, admission, a supported cell along
     * the ray, and obstruction by the block's shape once it holds the deposit.
     */
    private static void placeAndDeposit(
            ServerPlayer player, BlockType type, BlockPos pos, Direction face, ViewRay ray) {
        ServerLevel level = player.level();
        boolean creative = player.getAbilities().instabuild;
        ItemStack held = player.getMainHandItem();
        ItemStack handStack = creative ? held.copy() : held;
        Direction placedAgainst = face.getOpposite();
        if (!WorldEdits.mayPlace(player, level, pos, placedAgainst)
                || ItemOps.checkDisabledModAndNotify(handStack, player)
                || ItemOps.checkDisabledItemAndNotify(handStack, player)) {
            return;
        }
        if (!type.isEnabled()) {
            player.sendOverlayMessage(
                    Component.translatable(
                            "somestacks.message.type_disabled", type.getBlock().getName()));
            return;
        }
        if (!StackRun.columnHasRoomFor(level, pos, type)) {
            notifyMaximumHeight(player);
            return;
        }
        if (!type.admits(handStack)) {
            return;
        }

        BlockState state = StackBlock.placementState(type.getBlock(), level, pos);
        VoxelShape finalCollision = type.firstDepositShape(level, pos, state, ray);
        // Captured before the deposit consumes the stack, as vanilla captures the placing item.
        ItemStack originalHeldStack = held.copy();
        if (finalCollision == null
                || !WorldEdits.placeChecked(
                        player, level, pos, state, placedAgainst, finalCollision)) {
            return;
        }

        ((StackBlockEntity) level.getBlockEntity(pos)).depositFromHand(ray, handStack, player);
        playSound(level, pos, type.depositSound());
        returnToHand(player, creative, handStack);
        CriteriaTriggers.PLACED_BLOCK.trigger(player, pos, originalHeldStack);
    }

    /**
     * Broadcasts a slightly pitch-varied rotation sound if the player's sound pacing permits it.
     */
    private static void playRotation(ServerPlayer player, BlockPos pos, SoundEvent sound) {
        if (!claimRotationSound(player)) {
            return;
        }
        float pitch =
                ROTATION_PITCH_BASE
                        + player.level().getRandom().nextFloat() * ROTATION_PITCH_SPREAD;
        player.level().playSound(null, pos, sound, SoundSource.BLOCKS, SOUND_VOLUME, pitch);
    }

    /** Broadcasts one stack action sound at the block, heard by every nearby player. */
    private static void playSound(Level level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, SOUND_VOLUME, 1.0f);
    }

    private static void returnToHand(ServerPlayer player, boolean creative, ItemStack handStack) {
        if (!creative) {
            player.setItemInHand(InteractionHand.MAIN_HAND, handStack);
        }
    }

    private static void rotateBlock(ServerPlayer player, BlockPos pos, BlockType type) {
        int rotation;
        if (type == BlockType.SINGLES_STACK
                && player.level().getBlockEntity(pos) instanceof SinglesStackBE singles) {
            rotation = QuarterTurns.next(singles.getRotation());
            singles.setRotation(rotation);
            playRotation(player, pos, CommonRegistry.Sound.SINGLES_ROTATE.get());
        } else if (type == BlockType.STORAGE_STACK
                && player.level().getBlockEntity(pos) instanceof StorageStackBE storage) {
            rotation = QuarterTurns.next(storage.getRotation());
            storage.setRotation(rotation);
            playRotation(player, pos, CommonRegistry.Sound.STORAGE_ROTATE.get());
        } else {
            return;
        }
        emitBlockChange(player, pos, player.level().getBlockState(pos));
        player.sendOverlayMessage(
                Component.translatable(
                        "somestacks.message.rotation", QuarterTurns.degrees(rotation)));
    }

    private static void rotateItem(ServerPlayer player, BlockPos pos, ViewRay ray) {
        if (!(player.level().getBlockEntity(pos) instanceof SinglesStackBE singles)) {
            return;
        }
        int index = singles.extractionTarget(ray);
        if (index < 0) {
            return;
        }
        int rotation = QuarterTurns.next(singles.getCubeRotation(index));
        singles.setCubeRotation(index, rotation);
        emitBlockChange(player, pos, player.level().getBlockState(pos));
        playRotation(player, pos, CommonRegistry.Sound.SINGLES_ROTATE_ITEM.get());
        player.sendOverlayMessage(
                Component.translatable(
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
        player.sendOverlayMessage(
                Component.translatable(
                        "somestacks.message.pile_state",
                        Component.translatable(
                                permanent
                                        ? "somestacks.state.permanent"
                                        : "somestacks.state.temporary")));
    }
}
