package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.PlatformServices;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.util.ViewRays;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Vanilla world-edit mechanics for stack placement, growth, and removal: the one placement gate,
 * world border and spawn protection, entity obstruction, actor propagation, and the actual block
 * edit. Automation identity and loader-native claim/logging hooks are delegated to {@link
 * EditAuthority}.
 */
public final class WorldEdits {
    private static final ThreadLocal<Boolean> consultingAdjacent =
            ThreadLocal.withInitial(() -> false);

    private WorldEdits() {}

    /** Vanilla's own gate on a block interaction: world border and spawn protection. */
    public static boolean isProtected(ServerPlayer sp, BlockPos pos) {
        return !sp.serverLevel().mayInteract(sp, pos);
    }

    /**
     * The one gate on putting a block at {@code pos}, without side effects: build height,
     * replaceability, vanilla's item-placement check ({@link Player#mayUseItemAt}) for the actor's
     * main-hand item against the block at {@code pos.relative(placedAgainst)}, world border and
     * spawn protection, and the loader's protection queries. {@link #placeChecked} applies it to
     * every placement; player placement and growth simulation ask it first, so they refuse before
     * promising anything. A null actor uses the level's automation identity.
     *
     * <p>The item-placement check rather than {@link Player#mayBuild} alone is what an Adventure
     * mode player's {@code can_place_on} item answers to, so a player who may place a stack by
     * gesture may also grow one by depositing that item. Automation holds nothing, so only its
     * build ability counts. Spawn protection exempts operators, so the answer depends on the actor.
     *
     * @param placedAgainst the direction from {@code pos} to the block the new one is placed
     *     against
     */
    public static boolean mayPlace(
            @Nullable ServerPlayer actor,
            ServerLevel level,
            BlockPos pos,
            Direction placedAgainst) {
        ServerPlayer editor = actor != null ? actor : automationActor(level);
        return !level.isOutsideBuildHeight(pos)
                && level.getBlockState(pos).canBeReplaced()
                && editor.mayUseItemAt(pos, placedAgainst.getOpposite(), editor.getMainHandItem())
                && !isProtected(editor, pos)
                && PlatformServices.editAuthority().mayPlace(editor, level, pos);
    }

    /** Consults destination protection without allowing the consultation to execute gestures. */
    public static boolean mayUseAdjacent(ServerPlayer player, BlockPos pos) {
        if (isConsultingAdjacent() || isProtected(player, pos)) {
            return false;
        }
        consultingAdjacent.set(true);
        try {
            return PlatformServices.editAuthority().mayUseAdjacent(player, pos);
        } finally {
            consultingAdjacent.remove();
        }
    }

    /**
     * Whether this thread is inside {@link #mayUseAdjacent}'s consultation of other listeners. Every
     * gesture entry point refuses while it is set, so a consulted listener cannot start a gesture.
     */
    public static boolean isConsultingAdjacent() {
        return consultingAdjacent.get();
    }

    /** Destination hit used by loader protection listeners for a neighboring deposit. */
    public static BlockHitResult adjacentHit(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        var ray = ViewRays.of(player);
        BlockHitResult hit =
                level.getBlockState(pos)
                        .getInteractionShape(level, pos)
                        .clip(ray.eye(), ray.end(), pos);
        return hit != null
                ? hit
                : new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    /** The actor automation-driven growth and removal are attributed to. */
    public static ServerPlayer automationActor(ServerLevel level) {
        return PlatformServices.editAuthority().automationActor(level);
    }

    /** Vanilla's placement obstruction test for a block-local collision shape. */
    public static boolean isUnobstructed(ServerLevel level, BlockPos pos, VoxelShape localShape) {
        return localShape.isEmpty()
                || level.isUnobstructed(null, localShape.move(pos.getX(), pos.getY(), pos.getZ()));
    }

    /**
     * {@link #placeChecked(ServerPlayer, ServerLevel, BlockPos, BlockState, Direction, VoxelShape)}
     * using {@code state}'s own collision shape.
     */
    public static boolean placeChecked(
            ServerPlayer placer,
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            Direction placedAgainst) {
        VoxelShape collision = state.getCollisionShape(level, pos, CollisionContext.empty());
        return placeChecked(placer, level, pos, state, placedAgainst, collision);
    }

    /**
     * Places {@code state} at {@code pos} after {@link #mayPlace} and an obstruction test. On Forge
     * and NeoForge, the placement event's transaction defers publication until protection accepts
     * the edit, restoring captured state and block-entity data on denial. {@code finalCollision} is the completed
     * placement's collision shape, since a block-entity stack can start empty and acquire its real
     * shape only with its first deposit. A null placer uses the level's automation identity.
     */
    public static boolean placeChecked(
            @Nullable ServerPlayer placer,
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            Direction placedAgainst,
            VoxelShape finalCollision) {
        ServerPlayer editor = placer != null ? placer : automationActor(level);
        if (!mayPlace(editor, level, pos, placedAgainst)
                || !isUnobstructed(level, pos, finalCollision)) {
            return false;
        }
        if (!PlatformServices.editAuthority().place(editor, level, pos, state, placedAgainst)) {
            return false;
        }
        BlockState placedState = level.getBlockState(pos);
        placedState
                .getBlock()
                .setPlacedBy(level, pos, placedState, editor, editor.getMainHandItem());
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(editor, placedState));
        return true;
    }

    /**
     * Removes a stack block on behalf of {@code actor}, preserving that identity through vanilla
     * protection, loader-native removal events, and the emitted game event. A null actor uses the
     * level's automation identity. Refuses a stack that still holds set-aside saved items.
     *
     * @return whether the block was removed
     */
    public static boolean removeChecked(
            @Nullable ServerPlayer actor, ServerLevel level, BlockPos pos) {
        if (actor == null) {
            actor = automationActor(level);
        }
        if (isProtected(actor, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof StackBlockEntity stack && stack.getItems().hasRetainedItems()) {
            return false;
        }
        if (PlatformServices.editAuthority().vetoesRemoval(actor, level, pos, state, blockEntity)) {
            return false;
        }
        if (!level.removeBlock(pos, false)) {
            return false;
        }
        PlatformServices.editAuthority().afterRemoval(actor, level, pos, state, blockEntity);
        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(actor, state));
        return true;
    }
}
