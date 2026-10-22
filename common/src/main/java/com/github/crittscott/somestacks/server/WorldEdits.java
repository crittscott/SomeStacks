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
 * Vanilla world-edit mechanics for automated growth and removal: world border and spawn
 * protection, entity obstruction, and the actual block placement/removal. The one loader-specific
 * authority — the automation actor, protection callbacks, and loader completion notifications —
 * is delegated to {@link EditAuthority}.
 */
public final class WorldEdits {
    private WorldEdits() {
    }

    private static final ThreadLocal<Boolean> consultingAdjacent = ThreadLocal.withInitial(() -> false);

    private static EditAuthority authority() {
        return PlatformServices.editAuthority();
    }

    /** Vanilla's own gate on a block interaction: world border and spawn protection. */
    public static boolean isProtected(ServerPlayer sp, BlockPos pos) {
        return !sp.serverLevel().mayInteract(sp, pos);
    }

    /**
     * Whether the actor may place a block: vanilla's gate plus loader protection queries.
     * A null actor uses the level's automation identity. Growth checks this before promising space.
     */
    public static boolean mayPlace(@Nullable ServerPlayer actor, ServerLevel level, BlockPos pos) {
        ServerPlayer editor = actor != null ? actor : automationActor(level);
        return !isProtected(editor, pos) && authority().mayPlace(editor, level, pos);
    }

    /** The actor automation-driven growth and removal are attributed to. */
    public static ServerPlayer automationActor(ServerLevel level) {
        return authority().automationActor(level);
    }

    /** Consults destination protection without allowing the consultation to run a gesture. */
    public static boolean mayUseAdjacent(ServerPlayer player, BlockPos pos) {
        if (isConsultingAdjacent() || isProtected(player, pos)) return false;
        consultingAdjacent.set(true);
        try {
            return authority().mayUseAdjacent(player, pos);
        } finally {
            consultingAdjacent.remove();
        }
    }

    public static boolean isConsultingAdjacent() {
        return consultingAdjacent.get();
    }

    /** Destination hit reported to protection listeners for an adjacent deposit. */
    public static BlockHitResult adjacentHit(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        var ray = ViewRay.of(player);
        BlockHitResult hit = level.getBlockState(pos).getInteractionShape(level, pos)
                .clip(ray.eye(), ray.end(), pos);
        return hit != null ? hit
                : new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    /** Vanilla's placement obstruction test for a block-local collision shape. */
    public static boolean isUnobstructed(ServerLevel level, BlockPos pos, VoxelShape localShape) {
        VoxelShape worldShape = localShape.move(pos.getX(), pos.getY(), pos.getZ());
        return level.isUnobstructed(null, worldShape);
    }

    /**
     * Places {@code state} through the loader transaction, restoring captured state and block-entity
     * data when protection denies it. {@code finalCollision} is the completed placement's collision shape, since a
     * block-entity stack can start empty and acquire its real shape only with its first deposit.
     * A null placer uses the level's automation identity for both placement and its game event.
     */
    public static boolean placeChecked(@Nullable Player placer, ServerLevel level, BlockPos pos,
                                       BlockState state, Direction placedAgainst,
                                       VoxelShape finalCollision) {
        if (level.isOutsideBuildHeight(pos) || !isUnobstructed(level, pos, finalCollision)) {
            return false;
        }
        Player editor = placer != null ? placer : automationActor(level);
        if (!authority().place(editor, level, pos, state, placedAgainst)) {
            return false;
        }
        level.gameEvent(GameEvent.BLOCK_PLACE, pos,
                GameEvent.Context.of(editor, level.getBlockState(pos)));
        return true;
    }

    /**
     * Removes a stack block on behalf of {@code actor}, preserving that identity through vanilla
     * protection, loader-native removal events, and the emitted game event. A waterlogged block
     * leaves its water behind. A null actor uses the level's automation identity.
     */
    public static boolean removeChecked(@Nullable ServerPlayer actor, ServerLevel level, BlockPos pos) {
        ServerPlayer editor = actor != null ? actor : automationActor(level);
        if (isProtected(editor, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (authority().vetoesRemoval(level, editor, pos, state, blockEntity)) {
            return false;
        }
        if (!level.removeBlock(pos, false)) {
            return false;
        }
        authority().afterRemoval(level, editor, pos, state, blockEntity);
        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(editor, state));
        return true;
    }
}
