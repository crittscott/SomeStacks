package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loader-specific protection checks for packet-driven player gestures. The mod's custom packets
 * take the place of the vanilla interactions the client suppresses, so implementations re-run the
 * checks a vanilla interaction would have triggered on that platform: world/spawn protection and
 * whatever interaction event claim or logging mods hook.
 */
public interface PlayerEditAuthority {
    /**
     * Gates a stack access and claims the click for it: every position in {@code consulted} must
     * clear the platform's protection and interaction checks before the gesture may act.
     * {@code markPos} is the position a vanilla click that still follows the packet, on platforms
     * where one does, should be suppressed for.
     */
    boolean claimInteraction(ServerPlayer player, BlockPos markPos, BlockPos... consulted);

    /**
     * Gates a gesture that spends the held item and claims the click for it, the way
     * {@link #claimInteraction} gates one that only reaches the block. A deposit takes the stack
     * straight out of the hand on the click, so it requires permission to use the held item as
     * well as permission to reach the block.
     */
    boolean claimItemUse(ServerPlayer player, BlockPos markPos, BlockPos... consulted);

    /**
     * Gates an item-driven placement of a block into {@code intoPos} against {@code againstPos}
     * and claims the click for it. Both positions are checked: the block being used against
     * governs the interaction, while the position being filled governs the placement, and they
     * can fall on opposite sides of a protection boundary.
     */
    boolean claimPlacement(ServerPlayer player, BlockPos againstPos, BlockPos intoPos);

    /** Whether {@code player} may access the stack at {@code pos}, without claiming the click. */
    boolean mayInteract(ServerPlayer player, BlockPos pos);
}
