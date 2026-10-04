package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.util.ViewRay;
import com.github.crittscott.somestacks.util.ViewRays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shared ordering for loader-native player protection events. Every vanilla protection check and
 * native event runs before a successful gesture marks its trailing click, so the mark cannot veto
 * the consult that created it.
 */
public abstract class EventPlayerEditAuthority implements PlayerEditAuthority {
    @Override
    public final boolean claimInteraction(
            ServerPlayer player, BlockPos markPos, BlockPos... consulted) {
        return claim(player, markPos, false, consulted);
    }

    @Override
    public final boolean claimItemUse(
            ServerPlayer player, BlockPos markPos, BlockPos... consulted) {
        return claim(player, markPos, true, consulted);
    }

    private boolean claim(ServerPlayer player, BlockPos markPos,
                          boolean requireItemUse, BlockPos... consulted) {
        for (BlockPos pos : consulted) {
            if (WorldEdits.isProtected(player, pos)) {
                return false;
            }
        }
        for (BlockPos pos : consulted) {
            if (!fireRightClick(player, pos, requireItemUse)) {
                return false;
            }
        }
        markClaim(player, markPos);
        return true;
    }

    @Override
    public final boolean claimPlacement(
            ServerPlayer player, BlockPos againstPos, BlockPos intoPos) {
        if (WorldEdits.isProtected(player, againstPos)
                || WorldEdits.isProtected(player, intoPos)
                || !fireRightClick(player, againstPos, true)) {
            return false;
        }
        markClaim(player, againstPos);
        return true;
    }

    @Override
    public final boolean mayInteract(ServerPlayer player, BlockPos pos) {
        return fireRightClick(player, pos, false);
    }

    /** Fires the loader's right-click hook and returns whether it permits the interaction. */
    protected abstract boolean fireRightClick(
            ServerPlayer player, BlockPos pos, boolean requireItemUse);

    /** Marks a successful claim against a trailing vanilla click; Fabric has no trailing click. */
    protected abstract void markClaim(ServerPlayer player, BlockPos pos);

    /** Returns the viewed point on the interaction shape, or the block center for stale aim. */
    protected static BlockHitResult lookHit(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        ViewRay ray = ViewRays.of(player);
        BlockHitResult hit = level.getBlockState(pos).getInteractionShape(level, pos)
                .clip(ray.eye(), ray.end(), pos);
        return hit != null
                ? hit
                : new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }
}
