package com.github.crittscott.somestacks;

import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fabric's server policy backend: instance defaults in the config directory with an optional world
 * override. Fabric has no native config system, so {@link TomlPolicyFile} reads and writes the file.
 */
public final class FabricServerConfig implements ServerConfig.Backend {
    private final Path configDirectory;
    private Path file;

    public FabricServerConfig(Path configDirectory) {
        this.configDirectory = configDirectory;
    }

    /**
     * Migrates the world JSON, creates the instance file when absent, and reads only the selected
     * file, so a stale instance file is not reported while a world override is in effect.
     */
    @Override
    public ServerConfig.Settings loadFor(MinecraftServer server) {
        Path override = ServerConfigMigration.prepareWorld(server);
        Path global = configDirectory.resolve(SomeStacksCommon.MODID + "-server.toml");
        new TomlPolicyFile(global).createIfMissing(ServerConfig.defaults());
        file = Files.exists(override) ? override : global;
        SomeStacksCommon.LOGGER.info("Reading server policy from {}", file.toAbsolutePath());
        return reload();
    }

    @Override
    public ServerConfig.Settings reload() {
        return new TomlPolicyFile(file).read(ServerConfig.defaults());
    }

    @Override
    public boolean save(ServerConfig.Settings settings) {
        return new TomlPolicyFile(file).write(settings);
    }
}
