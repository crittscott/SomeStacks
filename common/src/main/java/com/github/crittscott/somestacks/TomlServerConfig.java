package com.github.crittscott.somestacks;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Files;
import java.nio.file.Path;

/** Fabric's TOML backend: instance defaults with an optional world policy override. */
public final class TomlServerConfig implements ServerConfig.Backend {
    private final Path configDirectory;
    private Path file;

    /** Explicit-file constructor for loader-neutral configuration fixtures. */
    public TomlServerConfig() { this.configDirectory = null; }
    public TomlServerConfig(Path configDirectory) { this.configDirectory = configDirectory; }
    public Path path() { return file; }

    public ServerConfig.Settings load(Path path) {
        file = path;
        return new TomlPolicyFile(file).read(ServerConfig.defaults());
    }

    @Override
    public ServerConfig.Settings loadFor(MinecraftServer server) {
        Path world = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig");
        Path override = world.resolve(SomeStacksCommon.MODID + "-server.toml");
        Path json = world.resolve(SomeStacksCommon.MODID + "-server.json");
        ServerConfigMigration.convertJson(json, override, ServerConfig.defaults());
        if (Files.exists(json) && !Files.exists(override)) {
            throw new IllegalStateException("Repair the JSON server policy before migration: " + json);
        }
        Path global = configDirectory.resolve(SomeStacksCommon.MODID + "-server.toml");
        new TomlPolicyFile(global).read(ServerConfig.defaults());
        file = Files.exists(override) ? override : global;
        SomeStacksCommon.LOGGER.info("Reading server policy from {}", file.toAbsolutePath());
        return reload();
    }

    @Override
    public ServerConfig.Settings reload() { return new TomlPolicyFile(file).read(ServerConfig.defaults()); }

    @Override
    public boolean save(ServerConfig.Settings settings) { return new TomlPolicyFile(file).write(settings); }
}
