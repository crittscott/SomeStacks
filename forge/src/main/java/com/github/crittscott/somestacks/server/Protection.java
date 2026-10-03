package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.util.ViewRay;
import com.github.crittscott.somestacks.util.ViewRays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;

import java.util.function.BiPredicate;

/**
 * Server-authoritative protection consults for the mod's packet-driven player gestures. The mod's
 * custom packets take the place of the vanilla interactions the client suppresses, so these re-run
 * the checks a vanilla interaction would have triggered: world border, spawn protection, and the
 * right-click interaction event that claim and logging mods hook.
 *
 * <p>Growth and removal driven by automation, and the vanilla mechanics behind them, live in the
 * loader-neutral {@link WorldEdits} instead; this class is only the player-packet-facing half,
 * which stays Forge-specific because it fires {@code PlayerInteractEvent.RightClickBlock}.
 *
 * <h2>The vanilla click a gesture displaces</h2>
 *
 * A gesture reaches the server twice. The client sends the mod's own packet from inside its
 * {@code RightClickBlock} handler, and then the vanilla {@code ServerboundUseItemOnPacket} for the
 * same click follows. Both are enqueued onto the server task queue in arrival order, so the mod's
 * packet is handled first, in the same tick. Everything below rests on that:
 *
 * <ul>
 *   <li>A gesture that claims a click must mark {@link RightClickBlockSuppressor} for the position
 *       it claimed, so the vanilla click that follows cannot also act there. The mark lives one
 *       tick, matching the lifetime of the displaced click.</li>
 *   <li>The mark vetoes {@code RightClickBlock} at that position, and the consults here fire that
 *       same event. A consult run after the mark would therefore refuse itself, so the mark is
 *       always placed last.</li>
 * </ul>
 *
 * The {@code claim} methods below exist to make that ordering structural: they run every consult
 * and only then mark. No caller places a mark of its own.
 */
public final class Protection {
    private Protection() {}

    /**
     * Every gesture the mod recognizes is a main-hand gesture; the client sends no other, and the
     * packets carry no hand for a spoofed one to disagree with.
     */
    private static final InteractionHand GESTURE_HAND = InteractionHand.MAIN_HAND;

    /**
     * Gates a stack access and claims the click for it: every position in {@code consulted} must
     * clear both vanilla's own protection and the right-click event, and only then is
     * {@code markPos} marked against the vanilla click that follows.
     *
     * <p>The cheap consults run across all positions before the first event fires, so a gesture
     * refused for reaching outside the world border does not first announce itself to the listeners
     * of a position it was never allowed to touch.
     */
    public static boolean claimInteraction(ServerPlayer sp, BlockPos markPos, BlockPos... consulted) {
        return claim(sp, markPos, Protection::mayInteract, consulted);
    }

    /**
     * Gates a gesture that spends the held item and claims the click for it, the way
     * {@link #claimInteraction} gates one that only reaches the block.
     *
     * <p>A deposit is not the container access it resembles. Opening a chest costs nothing and its
     * contents are gated again by whatever guards the screen; a deposit takes the stack straight out
     * of the hand off the click, with nothing in between. It therefore requires permission to use
     * an item here as well as permission to reach the block, matching the checks vanilla applies to
     * item-driven interaction and {@link #claimPlacement} applies to placement.
     */
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

    /**
     * Gates an item-driven placement of a block into {@code intoPos} against {@code againstPos} and
     * claims the click for it. Vanilla checks both positions: the block being used governs the
     * interaction, while the position being filled governs placement. They can fall on opposite
     * sides of a protection boundary. The click landed on {@code againstPos}, so
     * that is what is marked.
     */
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

    /**
     * Fires {@link PlayerInteractEvent.RightClickBlock} for a stack access at {@code pos} so
     * claim/protection mods can veto it, exactly as they would for a right-click on a vanilla
     * container. {@code true} means the interaction is allowed.
     *
     * <p>The event describes the interaction that is really happening: the face and point the
     * player's own view ray meets. A mod that only asks who and where is unaffected, but one that
     * logs what was clicked is told the truth rather than a placeholder.
     */
    public static boolean mayInteract(ServerPlayer sp, BlockPos pos) {
        PlayerInteractEvent.RightClickBlock evt = rightClickBlock(sp, pos);
        return !evt.isCanceled() && evt.getUseBlock() != Event.Result.DENY;
    }

    /**
     * Fires {@link PlayerInteractEvent.RightClickBlock} for an item-driven interaction at
     * {@code pos}: a placement, or a deposit that spends the held stack. Either requires both access
     * to the block and permission to use the held item on it, and a listener that refuses only the
     * item refuses both.
     */
    public static boolean mayUseItemOn(ServerPlayer sp, BlockPos pos) {
        PlayerInteractEvent.RightClickBlock evt = rightClickBlock(sp, pos);
        return !evt.isCanceled()
                && evt.getUseBlock() != Event.Result.DENY
                && evt.getUseItem() != Event.Result.DENY;
    }

    private static PlayerInteractEvent.RightClickBlock rightClickBlock(
            ServerPlayer sp, BlockPos pos) {
        PlayerInteractEvent.RightClickBlock evt =
                new PlayerInteractEvent.RightClickBlock(sp, GESTURE_HAND, pos, lookHit(sp, pos));
        MinecraftForge.EVENT_BUS.post(evt);
        return evt;
    }

    /**
     * Where the player is actually looking at {@code pos}, taken against the block's interaction
     * shape, which is the full cube every stack block exposes so that it can be clicked through the
     * gaps between its contents. Falls back to the center of the block when the ray no longer meets
     * it, which a player who has turned away since sending the packet can produce; the operation
     * itself has already cleared the reach check, so a missed ray is a stale aim rather than a
     * reason to refuse.
     */
    private static BlockHitResult lookHit(ServerPlayer sp, BlockPos pos) {
        var level = sp.serverLevel();
        ViewRay ray = ViewRays.of(sp);

        BlockHitResult hit = level.getBlockState(pos).getInteractionShape(level, pos)
                .clip(ray.eye(), ray.end(), pos);
        return hit != null ? hit : new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }
}
