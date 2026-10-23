package com.github.crittscott.somestacks;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Files;
import java.nio.file.Path;

/** Fabric's documented TOML policy with instance defaults and an optional world override. */
public final class FabricServerConfig {
    private FabricServerConfig() {}

    public static void start(MinecraftServer server) {
        ServerConfig.Settings defaults = ServerConfig.Settings.defaults();
        Path world = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig");
        Path override = world.resolve(SomeStacksCommon.MODID + "-server.toml");
        Path json = world.resolve(SomeStacksCommon.MODID + "-server.json");
        ServerConfigMigration.convertJson(json, override, defaults);
        if (Files.exists(json) && !Files.exists(override)) {
            throw new IllegalStateException("Repair the JSON server policy before migration: " + json);
        }
        Path global = FabricLoader.getInstance().getConfigDir().resolve(SomeStacksCommon.MODID + "-server.toml");
        new TomlPolicyFile(global).read(defaults);
        Path selected = Files.exists(override) ? override : global;
        TomlPolicyFile persistence = new TomlPolicyFile(selected);
        SomeStacksCommon.LOGGER.info("Reading server policy from {}", selected.toAbsolutePath());
        ServerConfig.start(() -> persistence.read(defaults), persistence::write,
                () -> ServerLifecycle.syncAllPlayers(server));
    }
}
