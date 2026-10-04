package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.util.ViewRay;
import com.github.crittscott.somestacks.util.ViewRays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/** Protection seam used only when a click reaches a stack through a neighboring block. */
public final class AdjacentEdits {
    private static final ThreadLocal<Boolean> consulting = ThreadLocal.withInitial(() -> false);
    private static AdjacentEditAuthority authority;

    private AdjacentEdits() {}

    public static void setAuthority(AdjacentEditAuthority authority) {
        AdjacentEdits.authority = Objects.requireNonNull(authority);
    }

    public static boolean mayUseItemAt(ServerPlayer player, BlockPos pos) {
        if (WorldEdits.isProtected(player, pos)) {
            return false;
        }
        consulting.set(true);
        try {
            return Objects.requireNonNull(authority,
                    "Adjacent edit authority has not been installed").mayUseItemAt(player, pos);
        } finally {
            consulting.remove();
        }
    }

    /** Prevents the synthetic destination consultation from re-entering the placement hook. */
    public static boolean isConsulting() {
        return consulting.get();
    }

    /** Returns the viewed point on the interaction shape, or the block center for stale aim. */
    public static BlockHitResult lookHit(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        ViewRay ray = ViewRays.of(player);
        BlockHitResult hit = level.getBlockState(pos).getInteractionShape(level, pos)
                .clip(ray.eye(), ray.end(), pos);
        return hit != null
                ? hit
                : new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }
}
