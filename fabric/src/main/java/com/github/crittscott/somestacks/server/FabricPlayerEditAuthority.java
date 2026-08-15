package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.util.ViewRay;
import com.github.crittscott.somestacks.util.ViewRays;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative protection consults for the mod's packet-driven player gestures, the
 * Fabric counterpart to {@code Protection}/{@code ForgePlayerEditAuthority} on Forge. The mod's
 * custom packets take the place of the vanilla interactions the client suppresses, so these fire
 * the same {@link UseBlockCallback} a claim or protection mod would see for a vanilla right-click.
 *
 * <p>Fabric API has no split between "use the block" and "use the item on it" the way Forge's
 * right-click event does, so every gesture below is one merged check, matching how Fabric claim
 * mods themselves collapse block interaction and edit into one policy on this platform.
 *
 * <p>Unlike Forge, no trailing-click suppression is needed: {@code FabricClientEvents} already
 * cancels the client's own {@link UseBlockCallback} for a claimed gesture, which stops the vanilla
 * packet from ever being sent, so the consult below is the only server-side firing for the click.
 */
public final class FabricPlayerEditAuthority implements PlayerEditAuthority {
    /** Every gesture the mod recognizes is a main-hand gesture. */
    private static final InteractionHand GESTURE_HAND = InteractionHand.MAIN_HAND;

    @Override
    public boolean claimInteraction(ServerPlayer player, BlockPos markPos, BlockPos... consulted) {
        return claim(player, consulted);
    }

    @Override
    public boolean claimItemUse(ServerPlayer player, BlockPos markPos, BlockPos... consulted) {
        return claim(player, consulted);
    }

    @Override
    public boolean claimPlacement(ServerPlayer player, BlockPos againstPos, BlockPos intoPos) {
        if (WorldEdits.isProtected(player, againstPos) || WorldEdits.isProtected(player, intoPos)) {
            return false;
        }
        return mayUse(player, againstPos);
    }

    @Override
    public boolean mayInteract(ServerPlayer player, BlockPos pos) {
        return claim(player, pos);
    }

    private static boolean claim(ServerPlayer player, BlockPos... consulted) {
        for (BlockPos pos : consulted) {
            if (WorldEdits.isProtected(player, pos)) {
                return false;
            }
        }
        for (BlockPos pos : consulted) {
            if (!mayUse(player, pos)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Fires {@link UseBlockCallback} for a stack access at {@code pos} so claim/protection mods can
     * veto it, exactly as they would for a right-click on a vanilla container. {@code FAIL} is the
     * documented convention such mods use to deny the interaction; anything else is allowed.
     */
    private static boolean mayUse(ServerPlayer player, BlockPos pos) {
        InteractionResult result = UseBlockCallback.EVENT.invoker()
                .interact(player, player.level(), GESTURE_HAND, lookHit(player, pos));
        return result != InteractionResult.FAIL;
    }

    /**
     * Where the player is actually looking at {@code pos}, taken against the block's interaction
     * shape, which is the full cube every stack block exposes so that it can be clicked through the
     * gaps between its contents. Falls back to the center of the block when the ray no longer meets
     * it, which a player who has turned away since sending the packet can produce; the operation
     * itself has already cleared the reach check, so a missed ray is a stale aim rather than a
     * reason to refuse.
     */
    private static BlockHitResult lookHit(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        ViewRay ray = ViewRays.of(player);

        BlockHitResult hit = level.getBlockState(pos).getInteractionShape(level, pos)
                .clip(ray.eye(), ray.end(), pos);
        return hit != null ? hit : new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }
}
