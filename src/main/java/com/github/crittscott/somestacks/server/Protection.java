package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.util.ViewRay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;

import java.util.function.BiPredicate;

/**
 * Server-authoritative protection consults for the mod's world edits. The mod's custom packets
 * take the place of the vanilla interactions the client suppresses, so these re-run the checks a
 * vanilla interaction would have triggered: world border, spawn protection, the block-place
 * event, the block-break event, and the right-click interaction event that claim and logging mods
 * hook.
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
 *       tick, which is exactly as long as the click it answers.</li>
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
     * of the hand off the click, with nothing in between. So it answers to permission to use an item
     * here as well as permission to reach the block, which is the pair vanilla weighs for any
     * item-driven interaction and the pair {@link #claimPlacement} already weighs.
     */
    public static boolean claimItemUse(ServerPlayer sp, BlockPos markPos, BlockPos... consulted) {
        return claim(sp, markPos, Protection::mayUseItemOn, consulted);
    }

    private static boolean claim(ServerPlayer sp, BlockPos markPos,
                                 BiPredicate<ServerPlayer, BlockPos> gate, BlockPos... consulted) {
        for (BlockPos pos : consulted) {
            if (isProtected(sp, pos)) {
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
     * claims the click for it. Vanilla weighs both positions, because the block being used answers
     * for the interaction and the position being filled answers for the placement, and the two can
     * fall on opposite sides of a protection boundary. The click landed on {@code againstPos}, so
     * that is what is marked.
     */
    public static boolean claimPlacement(ServerPlayer sp, BlockPos againstPos, BlockPos intoPos) {
        if (isProtected(sp, againstPos) || isProtected(sp, intoPos)) {
            return false;
        }
        if (!mayUseItemOn(sp, againstPos)) {
            return false;
        }
        RightClickBlockSuppressor.suppress(sp, againstPos, sp.level());
        return true;
    }

    /**
     * Vanilla's own gate on a block interaction: world border and spawn protection, which already
     * exempts operators. {@code true} means the edit must not proceed.
     */
    public static boolean isProtected(ServerPlayer sp, BlockPos pos) {
        return !sp.serverLevel().mayInteract(sp, pos);
    }

    /**
     * Protection as automation sees it: the level's fake player, which is never an operator and so
     * is never exempt from spawn protection. Growth driven by a capability insertion carries no
     * player and answers to this, both when it is planned and when it is committed.
     */
    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        return isProtected(FakePlayerFactory.getMinecraft(level), pos);
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
        return ForgeHooks.onRightClickBlock(sp, GESTURE_HAND, pos, lookHit(sp, pos));
    }

    /**
     * Where the player is actually looking at {@code pos}, taken against the block's interaction
     * shape, which is the full cube every stack block exposes so that it can be clicked through the
     * gaps between its contents. Falls back to the centre of the block when the ray no longer meets
     * it, which a player who has turned away since sending the packet can produce; the operation
     * itself has already cleared the reach check, so a missed ray is a stale aim rather than a
     * reason to refuse.
     */
    private static BlockHitResult lookHit(ServerPlayer sp, BlockPos pos) {
        ServerLevel level = sp.serverLevel();
        ViewRay ray = ViewRay.of(sp);

        BlockHitResult hit = level.getBlockState(pos).getInteractionShape(level, pos)
                .clip(ray.eye(), ray.end(), pos);
        return hit != null ? hit : new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    /**
     * Places {@code state} at {@code pos} honoring build height and firing
     * {@link net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent} so claim/protection/
     * logging mods can veto or record it. {@code placedAgainst} is the face the block rests
     * against, reported to the event. Uses the state's ordinary collision shape for the vanilla
     * entity-obstruction check.
     */
    public static boolean placeChecked(Player placer, ServerLevel level, BlockPos pos,
                                       BlockState state, Direction placedAgainst) {
        VoxelShape collision = state.getCollisionShape(level, pos, CollisionContext.empty());
        return placeChecked(placer, level, pos, state, placedAgainst, collision);
    }

    /**
     * Places a block whose completed placement will have {@code finalCollision}. Block-entity
     * stacks can start empty and acquire their real collision shape with the first deposit, so
     * their block state alone cannot describe the shape vanilla placement must weigh.
     *
     * <p>Restores the previous state and returns {@code false} on an out-of-height or obstructed
     * target, a failed set, or a vetoed event; {@code true} when the block stands.
     */
    public static boolean placeChecked(Player placer, ServerLevel level, BlockPos pos,
                                       BlockState state, Direction placedAgainst,
                                       VoxelShape finalCollision) {
        if (level.isOutsideBuildHeight(pos) || !isUnobstructed(level, pos, finalCollision)) {
            return false;
        }
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, state, 3)) {
            return false;
        }
        if (ForgeEventFactory.onBlockPlace(placer, snapshot, placedAgainst)) {
            snapshot.restore(true, false);
            return false;
        }
        return true;
    }

    /**
     * Removes a stack block the mod itself decided to take down: an emptied block a settle or a
     * collapse leaves behind. Answers to the same protection growth does, through the level's fake
     * player, and reports the removal as a break so claim and logging mods see it.
     *
     * <p>A settle runs on a scheduled tick with no actor left to ask, which is why the fake player
     * stands in. The consequence is that a run inside spawn protection, or under a claim that
     * refuses that player, keeps its empty blocks: the mod may not delete where it may not build.
     * That is the safe direction, and every caller treats a refusal as "this block stands" and
     * stops rather than assuming the world matches its own model.
     *
     * @return whether the block was removed
     */
    public static boolean removeChecked(ServerLevel level, BlockPos pos) {
        if (isProtected(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        ServerPlayer breaker = FakePlayerFactory.getMinecraft(level);
        BlockEvent.BreakEvent evt = new BlockEvent.BreakEvent(level, pos, state, breaker);
        if (MinecraftForge.EVENT_BUS.post(evt)) {
            return false;
        }
        return level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** Vanilla's placement obstruction test for a block-local collision shape. */
    public static boolean isUnobstructed(ServerLevel level, BlockPos pos, VoxelShape localShape) {
        if (localShape.isEmpty()) {
            return true;
        }
        VoxelShape worldShape = localShape.move(pos.getX(), pos.getY(), pos.getZ());
        return level.getEntities(
                (Entity) null,
                worldShape.bounds(),
                entity -> !entity.isRemoved()
                        && entity.blocksBuilding
                        && Shapes.joinIsNotEmpty(
                                worldShape,
                                Shapes.create(entity.getBoundingBox()),
                                BooleanOp.AND))
                .isEmpty();
    }
}
