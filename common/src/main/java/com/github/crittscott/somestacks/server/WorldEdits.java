package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.PlatformServices;
import com.github.crittscott.somestacks.util.ViewRay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Vanilla world-edit mechanics for stack placement, growth, and removal: the one placement gate,
 * world border and spawn protection, entity obstruction, actor propagation, and the actual block
 * edit. Automation identity and loader-native claim/logging hooks are delegated to {@link
 * EditAuthority}.
 */
public final class WorldEdits {
    // Consultation suppresses recursive gesture dispatch. The loader's placing guard protects
    // snapshot capture; RunEdit separately protects transaction commits and structural mutation.
    private static boolean consultingAdjacent;

    private WorldEdits() {}

    /** Vanilla's own gate on a block interaction: world border and spawn protection. */
    public static boolean isProtected(ServerPlayer sp, BlockPos pos) {
        return !sp.level().mayInteract(sp, pos);
    }

    /** Resolves player-attributed edits or the level's automation identity in one place. */
    private static ServerPlayer actor(ServerLevel level, @Nullable ServerPlayer actor) {
        return actor != null ? actor : automationActor(level);
    }

    /**
     * The one gate on putting a block at {@code pos}, without side effects: build height,
     * replaceability, vanilla's item-placement check ({@link ServerPlayer#mayUseItemAt}) for the
     * actor's main-hand item against the block at {@code pos.relative(placedAgainst)}, world border
     * and spawn protection, and the loader's protection queries. {@link #placeChecked} applies it to
     * every placement; player placement and growth simulation ask it first, so they refuse before
     * promising anything. A null actor uses the level's automation identity.
     *
     * <p>The item-placement check rather than {@code mayBuild} alone is what an Adventure mode
     * player's {@code can_place_on} item answers to, so a player who may place a stack by gesture
     * may also grow one by depositing that item. Automation holds nothing, so only its build ability
     * counts. Spawn protection exempts operators, so the answer depends on the actor.
     *
     * @param placedAgainst the direction from {@code pos} to the block the new one is placed
     *     against
     */
    public static boolean mayPlace(
            @Nullable ServerPlayer source,
            ServerLevel level,
            BlockPos pos,
            Direction placedAgainst) {
        ServerPlayer actor = actor(level, source);
        return !level.isOutsideBuildHeight(pos)
                && level.getBlockState(pos).canBeReplaced()
                && actor.mayUseItemAt(pos, placedAgainst.getOpposite(), actor.getMainHandItem())
                && !isProtected(actor, pos)
                && PlatformServices.editAuthority().mayPlace(actor, level, pos);
    }

    /** Consults destination protection without allowing the consultation to execute gestures. */
    public static boolean mayUseAdjacent(ServerPlayer player, BlockPos pos) {
        if (isConsultingAdjacent() || isProtected(player, pos)) {
            return false;
        }
        consultingAdjacent = true;
        try {
            return PlatformServices.editAuthority().mayUseAdjacent(player, pos);
        } finally {
            consultingAdjacent = false;
        }
    }

    /**
     * Whether this thread is inside {@link #mayUseAdjacent}'s consultation of other listeners. Every
     * gesture entry point refuses while it is set, so a consulted listener cannot start a gesture.
     */
    public static boolean isConsultingAdjacent() {
        return consultingAdjacent;
    }

    /** Destination hit used by loader protection listeners for a neighboring deposit. */
    public static BlockHitResult adjacentHit(ServerPlayer player, BlockPos pos) {
        var level = player.level();
        var ray = ViewRay.of(player);
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
     * Places {@code state} at {@code pos} after {@link #mayPlace} and an obstruction test. Loader
     * placement transactions defer publication until protection accepts the edit, restoring
     * captured state and block-entity data on denial. {@code finalCollision} is the completed
     * placement's collision shape, since a block-entity stack can start empty and acquire its real
     * shape only with its first deposit. A null placer uses the level's automation identity.
     */
    public static boolean placeChecked(
            @Nullable ServerPlayer source,
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            Direction placedAgainst,
            VoxelShape finalCollision) {
        ServerPlayer placer = actor(level, source);
        if (!mayPlace(placer, level, pos, placedAgainst)
                || !isUnobstructed(level, pos, finalCollision)) {
            return false;
        }
        if (!PlatformServices.editAuthority().place(placer, level, pos, state, placedAgainst)) {
            return false;
        }
        completePlacement(placer, level, pos);
        return true;
    }

    /**
     * Finishes loader-accepted placement with the actor callback and one placement game event.
     * NeoForge's deferred automation growth calls it once a transfer commit publishes the block.
     */
    public static void completePlacement(ServerPlayer placer, ServerLevel level, BlockPos pos) {
        BlockState placedState = level.getBlockState(pos);
        placedState
                .getBlock()
                .setPlacedBy(level, pos, placedState, placer, placer.getMainHandItem());
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(placer, placedState));
    }

    /**
     * Removes a stack block on behalf of a player or automation, preserving that identity through
     * vanilla protection, loader-native removal events, and the emitted game event.
     */
    public static boolean removeChecked(
            @Nullable ServerPlayer source, ServerLevel level, BlockPos pos) {
        ServerPlayer actor = actor(level, source);
        if (isProtected(actor, pos)
                || actor.blockActionRestricted(level, pos, actor.gameMode.getGameModeForPlayer())) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
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
