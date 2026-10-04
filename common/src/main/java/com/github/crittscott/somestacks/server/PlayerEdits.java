package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Loader-neutral access to the installed player-gesture protection implementation. */
public final class PlayerEdits {
    private PlayerEdits() {}

    private static PlayerEditAuthority authority;

    /** Installs the loader-specific implementation; called once during mod setup. */
    public static void setAuthority(PlayerEditAuthority authority) {
        PlayerEdits.authority = Objects.requireNonNull(authority);
    }

    private static PlayerEditAuthority authority() {
        return Objects.requireNonNull(authority, "Player edit authority has not been installed");
    }

    /** @see PlayerEditAuthority#claimInteraction */
    public static boolean claimInteraction(
            ServerPlayer player, BlockPos markPos, BlockPos... consulted) {
        return authority().claimInteraction(player, markPos, consulted);
    }

    /** @see PlayerEditAuthority#claimItemUse */
    public static boolean claimItemUse(
            ServerPlayer player, BlockPos markPos, BlockPos... consulted) {
        return authority().claimItemUse(player, markPos, consulted);
    }

    /** @see PlayerEditAuthority#claimPlacement */
    public static boolean claimPlacement(
            ServerPlayer player, BlockPos againstPos, BlockPos intoPos) {
        return authority().claimPlacement(player, againstPos, intoPos);
    }

    /** @see PlayerEditAuthority#mayInteract */
    public static boolean mayInteract(ServerPlayer player, BlockPos pos) {
        return authority().mayInteract(player, pos);
    }
}
