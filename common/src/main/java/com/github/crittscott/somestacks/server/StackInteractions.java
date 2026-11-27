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
import com.github.crittscott.somestacks.util.CubeGrid;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.StackGestures;
import com.github.crittscott.somestacks.util.StackPlacement;
import com.github.crittscott.somestacks.util.ViewRay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
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
 * Applies server-authoritative stack gestures carried by vanilla right-click interactions. Only the
 * main hand acts. Existing-stack use handles permanence, deposit/top placement, then extraction;
 * loader callbacks supply sneaking torch rotations and adjacent placement/deposit. Gestures use
 * synchronized mode/modifier state and target along the ray through the clicked point. Creative
 * deposits retain held items, and edits retain the player for protection and game events. Each
 * successful action plays its type's sound; rotation sounds are paced per player.
 */
public final class StackInteractions {
    /** Minimum ticks between rotation sounds initiated by the same player. */
    private static final long ROTATION_SOUND_INTERVAL_TICKS = 4L;

    private static final float ROTATION_PITCH_BASE = 0.9f;
    private static final float ROTATION_PITCH_SPREAD = 0.2f;

    /** Loudness of every stack action sound. */
    private static final float SOUND_VOLUME = 0.5f;

    /** Server tick of each player's last rotation sound, cleared on logout and session reset. */
    private static final Map<UUID, Long> lastRotationSoundTick = new HashMap<>();

    private StackInteractions() {}

    /**
     * Claims this player's rotation-sound allowance, returning false while the previous rotation
     * sound is still within the pacing interval; the rotation itself has already happened.
     */
    public static boolean claimRotationSound(ServerPlayer player) {
        UUID id = player.getUUID();
        long tick = player.serverLevel().getGameTime();
        Long last = lastRotationSoundTick.get(id);
        if (last != null && tick - last < ROTATION_SOUND_INTERVAL_TICKS) {
            return false;
        }
        lastRotationSoundTick.put(id, tick);
        return true;
    }

    /** Forgets every player's rotation-sound pacing. */
    public static void clearRotationSoundPacing() {
        lastRotationSoundTick.clear();
    }

    /** Forgets one player's rotation-sound pacing. */
    public static void clearRotationSoundPacing(UUID playerId) {
        lastRotationSoundTick.remove(playerId);
    }

    /** Handles a vanilla use call on an existing stack block. */
    public static boolean handleExistingStack(
            ServerPlayer player, InteractionHand hand, BlockHitResult hit, BlockType clickedType) {
        if (WorldEdits.isConsultingAdjacent() || hand != InteractionHand.MAIN_HAND) {
            return false;
        }

        ServerGestureState.State gesture = ServerGestureState.get(player);
        ViewRay ray = ViewRay.through(player, hit.getLocation());
        return applyExistingStack(
                player,
                hit.getBlockPos(),
                clickedType,
                hit.getDirection(),
                ray,
                player.getMainHandItem(),
                gesture);
    }

    /**
     * Whether the Stack Modifier claims a main-hand item use that reached the server; see {@link
     * StackGestures#modifierClaimsItemUse}. The client claims the same uses locally but may still
     * deliver the ordinary use-item packet.
     */
    public static boolean modifierClaimsItemUse(ServerPlayer player, InteractionHand hand) {
        return StackGestures.modifierClaimsItemUse(
                hand, ServerGestureState.get(player).modifierDown());
    }

    /**
     * Handles the sneaking torch gestures, which vanilla never routes to the block's use method.
     * {@code blockAllowed} and {@code itemAllowed} carry earlier listeners' verdicts on block and
     * item use; either false refuses.
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
                || player.isSpectator()) {
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        BlockType type = BlockType.of(player.level().getBlockState(pos).getBlock());
        ItemStack held = player.getMainHandItem();
        boolean sneaking = player.isShiftKeyDown();
        boolean rotatesBlock = StackGestures.rotatesBlock(sneaking, held, type);
        if (!(rotatesBlock || StackGestures.rotatesItem(sneaking, held, type))
                || WorldEdits.isProtected(player, pos)) {
            return false;
        }

        if (rotatesBlock) {
            rotateBlock(player, pos, type);
        } else {
            rotateItem(player, pos, ViewRay.through(player, hit.getLocation()));
        }
        return true;
    }

    /**
     * Handles a loader-native server right-click against a non-stack block when the selected stack
     * belongs in the adjacent position. {@code blockAllowed} and {@code itemAllowed} carry earlier
     * listeners' verdicts on block and item use; either false refuses.
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
        ItemStack held = player.getMainHandItem();
        if (!StackGestures.modifiedItemClick(
                        gesture.modifierDown(), gesture.mode(), player.isShiftKeyDown(), held)
                || !blockAllowed
                || !itemAllowed
                || player.isSpectator()
                || WorldEdits.isProtected(player, clickedPos)) {
            return false;
        }

        ViewRay ray = ViewRay.through(player, hit.getLocation());
        BlockPos destination = clickedPos.relative(hit.getDirection());
        BlockType destinationType = BlockType.of(level.getBlockState(destination).getBlock());
        if (destinationType == BlockType.SINGLES_STACK || destinationType == BlockType.BAR_STACK) {
            if (WorldEdits.mayUseAdjacent(player, destination)) {
                deposit(player, destination, destinationType, ray);
            }
            return true;
        }

        placeAndDeposit(player, gesture.mode().toBlockType(), destination, hit.getDirection(), ray);
        return true;
    }

    /**
     * Gives empty-hand Toggle Permanent on Storage precedence over other modified clicks. Otherwise
     * a nonsneaking modifier-plus-item click deposits, or on a top face with no deposit target
     * places against it; unmodified nonsneaking use extracts. Recognized gestures consume use even
     * on refusal.
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

        boolean sneaking = player.isShiftKeyDown();
        if (StackGestures.togglesPermanence(gesture.modifierDown(), gesture.mode(), held, type)) {
            togglePermanent(player, pos);
            return true;
        }

        if (StackGestures.modifiedItemClick(
                gesture.modifierDown(), gesture.mode(), sneaking, held)) {
            if (face != Direction.UP
                    || !(player.level().getBlockEntity(pos) instanceof StackBlockEntity stack)
                    || stack.acceptsDepositAlong(ray)) {
                deposit(player, pos, type, ray);
            } else {
                placeAndDeposit(
                        player, gesture.mode().toBlockType(), pos.relative(face), face, ray);
            }
            return true;
        }

        if (StackGestures.extracts(gesture.modifierDown(), sneaking)) {
            extract(player, pos, type, ray);
            return true;
        }

        return false;
    }

    /**
     * Deposits the main-hand item into the stack at {@code pos}; a Storage pile that took nothing
     * because it reached the height limit says so.
     */
    private static void deposit(ServerPlayer player, BlockPos pos, BlockType type, ViewRay ray) {
        Level level = player.level();
        boolean creative = player.getAbilities().instabuild;
        ItemStack held = player.getMainHandItem();
        ItemStack handStack = creative ? held.copy() : held;
        if (!BlockType.permitsPlayerItemAndNotify(handStack, player)
                || !(level.getBlockEntity(pos) instanceof StackBlockEntity stack)) {
            return;
        }

        if (stack.depositFromHand(ray, handStack, player) > 0) {
            returnToHand(player, creative, handStack);
            playSound(level, pos, type.depositSound());
            emitBlockChange(player, pos, level.getBlockState(pos));
        } else if (stack instanceof StorageStackBE storage) {
            StoragePile pile = storage.pile();
            if (pile != null && pile.height() >= ServerConfig.settings().maxPileHeight()) {
                notifyMaximumHeight(player);
            }
        }
    }

    /**
     * Places a new block of {@code type} at {@code pos} holding the main-hand item's first deposit.
     * Every check runs before placement, so a refused deposit leaves no empty block: the placement
     * gate, the player's item policy, enablement, column height, admission, a supported cell along
     * the ray, and obstruction by the block's shape once it holds the deposit.
     */
    private static void placeAndDeposit(
            ServerPlayer player, BlockType type, BlockPos pos, Direction face, ViewRay ray) {
        Level level = player.level();
        boolean creative = player.getAbilities().instabuild;
        ItemStack held = player.getMainHandItem();
        ItemStack handStack = creative ? held.copy() : held;
        if (!WorldEdits.mayPlace(player, player.serverLevel(), pos, face.getOpposite())
                || !BlockType.permitsPlayerItemAndNotify(handStack, player)) {
            return;
        }
        if (!type.isEnabled()) {
            player.displayClientMessage(
                    Component.translatable(
                            "somestacks.message.type_disabled", type.getBlock().getName()),
                    true);
            return;
        }
        if (!StackRun.columnHasRoomFor(level, pos, type)) {
            notifyMaximumHeight(player);
            return;
        }
        if (!type.admits(handStack, null)) {
            return;
        }

        BlockState state = StackPlacement.stateFor(type.getBlock(), level, pos);
        VoxelShape finalCollision =
                ((StackBlock) type.getBlock()).firstDepositShape(level, pos, state, ray);
        if (finalCollision == null
                || !WorldEdits.placeChecked(
                        player,
                        player.serverLevel(),
                        pos,
                        state,
                        face.getOpposite(),
                        finalCollision)) {
            return;
        }

        ((StackBlockEntity) level.getBlockEntity(pos)).depositFromHand(ray, handStack, player);
        playSound(level, pos, type.depositSound());
        returnToHand(player, creative, handStack);
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

    private static void rotateBlock(ServerPlayer player, BlockPos pos, BlockType type) {
        if (!(player.level().getBlockEntity(pos) instanceof StackBlockEntity stack)) {
            return;
        }
        CommonRegistry.Sound sound;
        if (type == BlockType.SINGLES_STACK) {
            sound = CommonRegistry.Sound.SINGLES_ROTATE;
        } else if (type == BlockType.STORAGE_STACK) {
            sound = CommonRegistry.Sound.STORAGE_ROTATE;
        } else {
            return;
        }
        int rotation = (stack.getRotation() + 1) % CubeGrid.ROTATIONS;
        stack.setRotation(rotation);
        playRotation(player, pos, sound.get());

        emitBlockChange(player, pos, player.level().getBlockState(pos));
        player.displayClientMessage(
                Component.translatable(
                        "somestacks.message.rotation", rotation * CubeGrid.DEGREES_PER_ROTATION),
                true);
    }

    private static void rotateItem(ServerPlayer player, BlockPos pos, ViewRay ray) {
        if (!(player.level().getBlockEntity(pos) instanceof SinglesStackBE singles)) {
            return;
        }
        int index = singles.extractionTarget(ray);
        if (index < 0) {
            return;
        }
        int rotation = (singles.getCubeRotation(index) + 1) % CubeGrid.ROTATIONS;
        singles.setCubeRotation(index, rotation);
        emitBlockChange(player, pos, player.level().getBlockState(pos));

        playRotation(player, pos, CommonRegistry.Sound.SINGLES_ROTATE_ITEM.get());
        player.displayClientMessage(
                Component.translatable(
                        "somestacks.message.item_rotation",
                        rotation * CubeGrid.DEGREES_PER_ROTATION),
                true);
    }

    private static void togglePermanent(ServerPlayer player, BlockPos pos) {
        StoragePile pile = StoragePile.at(player.level(), pos);
        if (pile == null) {
            return;
        }
        boolean permanent = !pile.isPermanent();
        pile.setPermanent(permanent, player);
        emitBlockChange(player, pos, player.level().getBlockState(pos));

        player.displayClientMessage(
                Component.translatable(
                        "somestacks.message.pile_state",
                        Component.translatable(
                                permanent
                                        ? "somestacks.state.permanent"
                                        : "somestacks.state.temporary")),
                true);
    }

    /** Tells the player a stack column cannot grow because it reached the configured height. */
    private static void notifyMaximumHeight(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable(
                        "somestacks.message.maximum_height",
                        ServerConfig.settings().maxPileHeight()),
                true);
    }

    /** Broadcasts one stack action sound at the block, heard by every nearby player. */
    private static void playSound(Level level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, SOUND_VOLUME, 1.0f);
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
                        + player.serverLevel().getRandom().nextFloat() * ROTATION_PITCH_SPREAD;
        player.serverLevel().playSound(null, pos, sound, SoundSource.BLOCKS, SOUND_VOLUME, pitch);
    }

    private static void returnToHand(ServerPlayer player, boolean creative, ItemStack handStack) {
        if (!creative) {
            player.setItemInHand(InteractionHand.MAIN_HAND, handStack);
        }
    }

    /**
     * Emits the change event for a successful extraction only when the extracted block entity is
     * still in place; an extraction that emptied the block away has already emitted its destruction
     * event.
     */
    private static void emitSurvivingBlockChange(
            ServerPlayer player, BlockPos pos, BlockEntity edited) {
        Level level = player.level();
        if (level.getBlockEntity(pos) == edited) {
            emitBlockChange(player, pos, level.getBlockState(pos));
        }
    }

    /** Emits one sensor-visible event for one successful player mutation inside a stack block. */
    private static void emitBlockChange(ServerPlayer player, BlockPos pos, BlockState state) {
        player.serverLevel()
                .gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, state));
    }
}
