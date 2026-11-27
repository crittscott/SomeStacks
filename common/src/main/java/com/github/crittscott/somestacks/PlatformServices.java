package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.server.EditAuthority;
import com.github.crittscott.somestacks.server.WorldEdits;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Path;
import java.util.Objects;

/** The active loader's paths, player reach, networking, and world-edit services. */
public final class PlatformServices {
    /** All required services supplied together by the selected loader. */
    public interface Backend {
        /** How far, in blocks, this player can reach a block on this loader. */
        double blockReach(Player player);

        /** Sends one encoding of the policy snapshot to every connected player. */
        void broadcastConfig(MinecraftServer server, ConfigSyncPkt packet);

        /** The game's config directory. */
        Path configFolder();

        /** The loader's half of world edits, which {@link WorldEdits} consults. */
        EditAuthority editAuthority();

        /** The installed version of the namespace, or "unknown" when it is not loaded. */
        String modVersion(String namespace);

        /** Sends the policy snapshot to one player. */
        void sendConfig(ServerPlayer player, ConfigSyncPkt packet);
    }

    private static Backend backend;

    private PlatformServices() {}

    private static Backend backend() {
        return Objects.requireNonNull(backend, "Platform services have not been installed");
    }

    /** How far, in blocks, this player can reach a block. */
    public static double blockReach(Player player) {
        return backend().blockReach(player);
    }

    /** Sends one encoding of the policy snapshot to every connected player. */
    public static void broadcastConfig(MinecraftServer server, ConfigSyncPkt packet) {
        backend().broadcastConfig(server, packet);
    }

    /** The game's config directory. */
    public static Path configFolder() {
        return backend().configFolder();
    }

    /**
     * The loader's half of world edits: the automation actor, protection vetoes, and how placement
     * and removal are reported. {@link WorldEdits} is its consumer.
     */
    public static EditAuthority editAuthority() {
        return backend().editAuthority();
    }

    /**
     * Installs the loader's backend; each loader's entry point calls this once, before common
     * client or world logic can run.
     */
    public static void install(Backend implementation) {
        backend = implementation;
    }

    /** This mod's directory beneath the game's config directory. */
    public static Path modConfigFolder() {
        return configFolder().resolve(SomeStacksCommon.MODID);
    }

    /** The installed version of the namespace, or "unknown" when it is not loaded. */
    public static String modVersion(String namespace) {
        return backend().modVersion(namespace);
    }

    /** Sends the policy snapshot to one player, as at login. */
    public static void sendConfig(ServerPlayer player, ConfigSyncPkt packet) {
        backend().sendConfig(player, packet);
    }
}
