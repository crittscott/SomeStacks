package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.server.EditAuthority;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** The active loader's paths, networking, and world-edit services. */
public final class PlatformServices {
    /** All required services supplied together by the selected loader. */
    public interface Backend {
        /** Sends one packet object to every connected player through the loader's broadcast. */
        void broadcastConfig(MinecraftServer server, ConfigSyncPkt packet);

        /** The game's config directory. */
        Path configFolder();

        /** The loaded version of the mod with this namespace, or "unknown" when none is loaded. */
        String modVersion(String namespace);

        /** Sends one config packet to one player on the loader's play channel. */
        void sendConfig(ServerPlayer player, ConfigSyncPkt packet);

        /** The loader's placement, removal, and protection queries for world edits. */
        EditAuthority editAuthority();
    }

    private static Backend backend;

    private PlatformServices() {}

    /**
     * Installs the loader's backend; each loader entry point calls this once, before common client
     * or world logic can run.
     */
    public static void install(Backend implementation) {
        backend = implementation;
    }

    private static Backend backend() {
        return Objects.requireNonNull(backend, "Platform services have not been installed");
    }

    /** Sends one config packet to every connected player as a single broadcast. */
    public static void broadcastConfig(MinecraftServer server, ConfigSyncPkt packet) {
        backend().broadcastConfig(server, packet);
    }

    /** The game's config directory. */
    public static Path configFolder() {
        return backend().configFolder();
    }

    /** This mod's directory beneath the game's config directory. */
    public static Path modConfigFolder() {
        return configFolder().resolve(SomeStacksCommon.MODID);
    }

    /**
     * Logs that the loader entry point finished, with the mod version and network protocol, and the
     * protection mods the edit authority queries directly. Call once, last in the entry point.
     */
    public static void logStartup(String loader) {
        SomeStacksCommon.LOGGER.info(
                "Some Stacks v{} initialized for {} (network protocol {})",
                modVersion(SomeStacksCommon.MODID),
                loader,
                SomeStacksCommon.PROTOCOL_VERSION);
        List<String> integrations = editAuthority().detectedIntegrations();
        SomeStacksCommon.LOGGER.info(
                "Protection mods queried directly for stack edits: {}",
                integrations.isEmpty() ? "none detected" : String.join(", ", integrations));
    }

    /** The installed version of the namespace, or "unknown" when it is not loaded. */
    public static String modVersion(String namespace) {
        return backend().modVersion(namespace);
    }

    /** Sends one config packet to one player. */
    public static void sendConfig(ServerPlayer player, ConfigSyncPkt packet) {
        backend().sendConfig(player, packet);
    }

    /** The installed loader's world-edit authority. */
    public static EditAuthority editAuthority() {
        return backend().editAuthority();
    }
}
