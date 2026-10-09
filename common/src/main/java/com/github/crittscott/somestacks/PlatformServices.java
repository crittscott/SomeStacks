package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.server.AdjacentEditAuthority;
import com.github.crittscott.somestacks.server.EditAuthority;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.Objects;

/** The active loader's paths, networking, and world-edit services. */
public final class PlatformServices {
    /** All required services supplied together by the selected loader. */
    public interface Backend {
        Path configFolder();
        String modVersion(String namespace);
        void sendConfig(ServerPlayer player, ConfigSyncPkt packet);
        EditAuthority editAuthority();
        AdjacentEditAuthority adjacentEditAuthority();
    }

    private static Backend backend;

    private PlatformServices() {}

    /** Installs one complete backend before common client or world logic can run. */
    public static void install(Backend implementation) {
        if (backend != null) {
            throw new IllegalStateException("Platform services have already been installed");
        }
        Objects.requireNonNull(implementation, "Platform backend");
        Objects.requireNonNull(implementation.editAuthority(), "Edit authority");
        Objects.requireNonNull(implementation.adjacentEditAuthority(), "Adjacent edit authority");
        backend = implementation;
    }

    private static Backend backend() {
        return Objects.requireNonNull(backend, "Platform services have not been installed");
    }

    /** The game's config directory. */
    public static Path configFolder() {
        return backend().configFolder();
    }

    /** This mod's directory beneath the game's config directory. */
    public static Path modConfigFolder() {
        return configFolder().resolve(SomeStacksCommon.MODID);
    }

    /** The installed version of the namespace, or "unknown" when it is not loaded. */
    public static String modVersion(String namespace) {
        return backend().modVersion(namespace);
    }

    public static void sendConfig(ServerPlayer player, ConfigSyncPkt packet) {
        backend().sendConfig(player, packet);
    }

    public static EditAuthority editAuthority() {
        return backend().editAuthority();
    }

    public static AdjacentEditAuthority adjacentEditAuthority() {
        return backend().adjacentEditAuthority();
    }
}
