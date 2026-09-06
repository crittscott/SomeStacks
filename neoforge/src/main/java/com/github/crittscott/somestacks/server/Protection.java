package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.util.ViewRay;
import com.github.crittscott.somestacks.util.ViewRays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.function.BiPredicate;

/**
 * Server-authoritative protection consults for the mod's packet-driven player gestures, the
 * NeoForge counterpart to Forge's {@code Protection}. The mod's custom packets take the place of the
 * vanilla interactions the client suppresses, so these re-run the checks a vanilla interaction would
 * have triggered: world border, spawn protection, and the right-click interaction event that claim
 * and logging mods hook.
 *
 * <p>Growth and removal driven by automation live in the loader-neutral {@link WorldEdits}; this is
 * the player-packet-facing half, which stays loader-specific because it fires
 * {@code PlayerInteractEvent.RightClickBlock}. See {@link RightClickBlockSuppressor} for why a
 * claimed click marks the position last, after every consult.
 *
 * <p>The custom packet arrives before the vanilla use-item-on packet for the same click. A successful
 * claim therefore runs every protection consult first and only then marks the clicked position so
 * the trailing vanilla action cannot act a second time or cause a later consult to veto itself.
 */
public final class Protection {
    private Protection() {}

    /** Custom gesture packets represent only the main-hand gestures recognized by the client. */
    private static final InteractionHand GESTURE_HAND = InteractionHand.MAIN_HAND;

    /** @see PlayerEditAuthority#claimInteraction */
    public static boolean claimInteraction(ServerPlayer sp, BlockPos markPos, BlockPos... consulted) {
        return claim(sp, markPos, Protection::mayInteract, consulted);
    }

    /** @see PlayerEditAuthority#claimItemUse */
    public static boolean claimItemUse(ServerPlayer sp, BlockPos markPos, BlockPos... consulted) {
        return claim(sp, markPos, Protection::mayUseItemOn, consulted);
    }

    private static boolean claim(ServerPlayer sp, BlockPos markPos,
                                 BiPredicate<ServerPlayer, BlockPos> gate, BlockPos... consulted) {
        for (BlockPos pos : consulted) {
            if (WorldEdits.isProtected(sp, pos)) {
                return false;
            }
        }
        for (BlockPos pos : consulted) {
            if (!gate.test(sp, pos)) {
                return false;
            }
        }
        RightClickBlockSuppressor.suppress(sp, markPos, sp.level());
        return true;
    }

    /** @see PlayerEditAuthority#claimPlacement */
    public static boolean claimPlacement(ServerPlayer sp, BlockPos againstPos, BlockPos intoPos) {
        if (WorldEdits.isProtected(sp, againstPos) || WorldEdits.isProtected(sp, intoPos)) {
            return false;
        }
        if (!mayUseItemOn(sp, againstPos)) {
            return false;
        }
        RightClickBlockSuppressor.suppress(sp, againstPos, sp.level());
        return true;
    }

    /** @see PlayerEditAuthority#mayInteract */
    public static boolean mayInteract(ServerPlayer sp, BlockPos pos) {
        PlayerInteractEvent.RightClickBlock evt = rightClickBlock(sp, pos);
        return !evt.isCanceled() && evt.getUseBlock() != TriState.FALSE;
    }

    /** Whether NeoForge permits both block interaction and held-item use at {@code pos}. */
    public static boolean mayUseItemOn(ServerPlayer sp, BlockPos pos) {
        PlayerInteractEvent.RightClickBlock evt = rightClickBlock(sp, pos);
        return !evt.isCanceled()
                && evt.getUseBlock() != TriState.FALSE
                && evt.getUseItem() != TriState.FALSE;
    }

    private static PlayerInteractEvent.RightClickBlock rightClickBlock(ServerPlayer sp, BlockPos pos) {
        PlayerInteractEvent.RightClickBlock evt =
                new PlayerInteractEvent.RightClickBlock(sp, GESTURE_HAND, pos, lookHit(sp, pos));
        NeoForge.EVENT_BUS.post(evt);
        return evt;
    }

    /** Returns the viewed point on the interaction shape, or the block center for stale aim. */
    private static BlockHitResult lookHit(ServerPlayer sp, BlockPos pos) {
        var level = sp.serverLevel();
        ViewRay ray = ViewRays.of(sp);

        BlockHitResult hit = level.getBlockState(pos).getInteractionShape(level, pos)
                .clip(ray.eye(), ray.end(), pos);
        return hit != null ? hit : new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }
}
