package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Loader-neutral access to the installed player-gesture protection implementation. */
public final class PlayerEdits {
    private PlayerEdits() {}

    private static PlayerEditAuthority authority = new VanillaPlayerEditAuthority();

    /** Installs the loader-specific implementation; called once during mod setup. */
    public static void setAuthority(PlayerEditAuthority authority) {
        PlayerEdits.authority = authority;
    }

    /** @see PlayerEditAuthority#claimInteraction */
    public static boolean claimInteraction(
            ServerPlayer player, BlockPos markPos, BlockPos... consulted) {
        return authority.claimInteraction(player, markPos, consulted);
    }

    /** @see PlayerEditAuthority#claimItemUse */
    public static boolean claimItemUse(
            ServerPlayer player, BlockPos markPos, BlockPos... consulted) {
        return authority.claimItemUse(player, markPos, consulted);
    }

    /** @see PlayerEditAuthority#claimPlacement */
    public static boolean claimPlacement(
            ServerPlayer player, BlockPos againstPos, BlockPos intoPos) {
        return authority.claimPlacement(player, againstPos, intoPos);
    }

    /** @see PlayerEditAuthority#mayInteract */
    public static boolean mayInteract(ServerPlayer player, BlockPos pos) {
        return authority.mayInteract(player, pos);
    }

    /** Safe setup-time default: vanilla world, spawn, and build permissions only. */
    private static final class VanillaPlayerEditAuthority implements PlayerEditAuthority {
        @Override
        public boolean claimInteraction(
                ServerPlayer player, BlockPos markPos, BlockPos... consulted) {
            return mayUseAll(player, consulted);
        }

        @Override
        public boolean claimItemUse(
                ServerPlayer player, BlockPos markPos, BlockPos... consulted) {
            return mayUseAll(player, consulted);
        }

        @Override
        public boolean claimPlacement(
                ServerPlayer player, BlockPos againstPos, BlockPos intoPos) {
            return mayUse(player, againstPos) && mayUse(player, intoPos);
        }

        @Override
        public boolean mayInteract(ServerPlayer player, BlockPos pos) {
            return mayUse(player, pos);
        }

        private static boolean mayUseAll(ServerPlayer player, BlockPos... positions) {
            for (BlockPos pos : positions) {
                if (!mayUse(player, pos)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean mayUse(ServerPlayer player, BlockPos pos) {
            return !WorldEdits.isProtected(player, pos);
        }
    }
}
