package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.PlatformServices;
import com.github.crittscott.somestacks.util.ViewRay;

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
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Vanilla world-edit mechanics for player placement, growth, and removal: the one placement gate,
 * world border and spawn protection, entity obstruction, and the actual block placement/removal.
 * The one loader-specific authority — the automation actor, protection callbacks, and loader
 * completion notifications — is delegated to {@link EditAuthority}.
 */
public final class WorldEdits {
    private WorldEdits() {}

    private static final ThreadLocal<Boolean> consultingAdjacent =
            ThreadLocal.withInitial(() -> false);

    /** Vanilla's own gate on a block interaction: world border and spawn protection. */
    public static boolean isProtected(ServerPlayer sp, BlockPos pos) {
        return !sp.serverLevel().mayInteract(sp, pos);
    }

    /**
     * The one gate on putting a block at {@code pos}, without side effects: build height,
     * replaceability, vanilla's item-placement check ({@link Player#mayUseItemAt}) for the actor's
     * main-hand item against the block at {@code pos.relative(placedAgainst)}, world border and
     * spawn protection, and the loader's protection queries. {@link #placeChecked} applies it to
     * every placement; player placement and growth ask it first to refuse before promising
     * anything. A null actor uses the level's automation identity.
     *
     * <p>The item-placement check rather than {@link Player#mayBuild} alone is what an Adventure
     * mode player's {@code CanPlaceOn} item answers to, so a player who may place a stack by
     * gesture may also grow one by depositing that item. Automation holds nothing, so only its
     * build ability counts.
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

    /** The actor automation-driven growth and removal are attributed to. */
    public static ServerPlayer automationActor(ServerLevel level) {
        return PlatformServices.editAuthority().automationActor(level);
    }

    /** Consults destination protection without allowing the consultation to run a gesture. */
    public static boolean mayUseAdjacent(ServerPlayer player, BlockPos pos) {
        if (isConsultingAdjacent() || isProtected(player, pos)) return false;
        consultingAdjacent.set(true);
        try {
            return PlatformServices.editAuthority().mayUseAdjacent(player, pos);
        } finally {
            consultingAdjacent.remove();
        }
    }

    /**
     * Whether {@link #mayUseAdjacent} is consulting destination protection on this thread; every
     * gesture entry point refuses while it is, so the probe cannot run a gesture.
     */
    public static boolean isConsultingAdjacent() {
        return consultingAdjacent.get();
    }

    /** Destination hit reported to protection listeners for an adjacent deposit. */
    public static BlockHitResult adjacentHit(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        var ray = ViewRay.of(player);
        BlockHitResult hit =
                level.getBlockState(pos)
                        .getInteractionShape(level, pos)
                        .clip(ray.eye(), ray.end(), pos);
        return hit != null
                ? hit
                : new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    /** Vanilla's placement obstruction test for a block-local collision shape. */
    public static boolean isUnobstructed(ServerLevel level, BlockPos pos, VoxelShape localShape) {
        VoxelShape worldShape = localShape.move(pos.getX(), pos.getY(), pos.getZ());
        return level.isUnobstructed(null, worldShape);
    }

    /**
     * Places {@code state} after {@link #mayPlace} and an obstruction test, through the loader
     * transaction, restoring captured state and block-entity data when protection denies it. {@code
     * finalCollision} is the completed placement's collision shape, since a block-entity stack can
     * start empty and acquire its real shape only with its first deposit. {@code placedAgainst}
     * points from {@code pos} to the block the new one is placed against. A null placer uses the
     * level's automation identity for both placement and its game event.
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
        level.gameEvent(
                GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(editor, level.getBlockState(pos)));
        return true;
    }

    /**
     * Removes a stack block on behalf of {@code actor}, preserving that identity through vanilla
     * protection, loader-native removal events, and the emitted game event. A waterlogged block
     * leaves its water behind. A null actor uses the level's automation identity.
     */
    public static boolean removeChecked(
            @Nullable ServerPlayer actor, ServerLevel level, BlockPos pos) {
        ServerPlayer editor = actor != null ? actor : automationActor(level);
        if (isProtected(editor, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (PlatformServices.editAuthority()
                .vetoesRemoval(level, editor, pos, state, blockEntity)) {
            return false;
        }
        if (!level.removeBlock(pos, false)) {
            return false;
        }
        PlatformServices.editAuthority().afterRemoval(level, editor, pos, state, blockEntity);
        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(editor, state));
        return true;
    }
}
