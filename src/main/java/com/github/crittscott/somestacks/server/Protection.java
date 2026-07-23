package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;

/**
 * Server-authoritative protection consults for the mod's world edits. The mod's custom packets
 * take the place of the vanilla interactions the client suppresses, so these re-run the checks a
 * vanilla interaction would have triggered: world border, spawn protection, the block-place
 * event, and the right-click interaction event that claim and logging mods hook.
 */
public final class Protection {
    private Protection() {}

    /**
     * World border and vanilla spawn protection (which already exempts operators). {@code true}
     * means the edit must not proceed.
     */
    public static boolean isProtected(ServerPlayer sp, BlockPos pos) {
        ServerLevel level = sp.serverLevel();
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return true;
        }
        MinecraftServer server = level.getServer();
        return server.isUnderSpawnProtection(level, pos, sp);
    }

    /**
     * Fires {@link PlayerInteractEvent.RightClickBlock} for a stack access at {@code pos} so
     * claim/protection mods can veto it, exactly as they would for a right-click on a vanilla
     * container. {@code true} means the interaction is allowed.
     */
    public static boolean mayInteract(ServerPlayer sp, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        PlayerInteractEvent.RightClickBlock evt =
                ForgeHooks.onRightClickBlock(sp, InteractionHand.MAIN_HAND, pos, hit);
        return !evt.isCanceled() && evt.getUseBlock() != Event.Result.DENY;
    }

    /**
     * Places {@code state} at {@code pos} honoring build height and firing
     * {@link net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent} so claim/protection/
     * logging mods can veto or record it. {@code placedAgainst} is the face the block rests
     * against, reported to the event. Restores the previous state and returns {@code false} on an
     * out-of-height target, a failed set, or a vetoed event; {@code true} when the block stands.
     */
    public static boolean placeChecked(Player placer, ServerLevel level, BlockPos pos,
                                       BlockState state, Direction placedAgainst) {
        if (level.isOutsideBuildHeight(pos)) {
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
}
