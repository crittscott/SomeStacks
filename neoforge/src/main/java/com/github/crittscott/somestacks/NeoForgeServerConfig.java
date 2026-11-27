package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.ServerConfig.Section;
import com.github.crittscott.somestacks.ServerConfig.Setting;
import com.github.crittscott.somestacks.ServerConfig.Settings;

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Loader-managed server configuration exposed as one immutable common policy snapshot. The spec is
 * built from {@link ServerConfig}'s declaration of sections and settings.
 */
public final class NeoForgeServerConfig implements ServerConfig.Backend {
    public static final NeoForgeServerConfig INSTANCE = new NeoForgeServerConfig();

    private ModConfig loadedConfig;
    private volatile MinecraftServer runningServer;
    public final ModConfigSpec spec;
    private final Map<Setting, ModConfigSpec.ConfigValue<?>> values = new EnumMap<>(Setting.class);

    private NeoForgeServerConfig() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        Settings defaults = ServerConfig.defaults();
        for (Section section : Section.values()) {
            builder.comment(section.comment().toArray(String[]::new)).push(section.key());
            for (Setting setting : section.settings()) {
                builder.comment(setting.comment().toArray(String[]::new));
                values.put(setting, define(builder, setting, setting.get(defaults)));
            }
            builder.pop();
        }
        spec = builder.build();
    }

    /** Declares one shared setting; the spec adds the range line to bounded integers itself. */
    @SuppressWarnings("unchecked")
    private static ModConfigSpec.ConfigValue<?> define(
            ModConfigSpec.Builder builder, Setting setting, Object fallback) {
        return switch (setting.kind()) {
            case INTEGER ->
                    builder.defineInRange(
                            setting.key(), (int) (Integer) fallback, setting.min(), setting.max());
            case BOOLEAN -> builder.define(setting.key(), (boolean) (Boolean) fallback);
            case STRINGS ->
                    builder.defineListAllowEmpty(
                            setting.key(),
                            (List<String>) fallback,
                            () -> "",
                            value -> value instanceof String);
        };
    }

    @Override
    public ServerConfig.Settings loadFor(MinecraftServer server) {
        runningServer = server;
        return reload();
    }

    private void onConfigChanged(ModConfig config) {
        if (config.getSpec() != spec) return;
        loadedConfig = config;
        if (ServerConfig.backend() != this) return;
        MinecraftServer server = runningServer;
        if (server != null) {
            server.execute(() -> {
                if (runningServer == server) SomeStacksServer.reloadPolicy(server);
            });
        }
    }

    /**
     * Mod-bus listener for the loader loading this spec's file. Records the loaded config and,
     * while a server is running with this backend installed, reapplies the policy and resyncs
     * clients on the server thread.
     */
    public void onLoading(ModConfigEvent.Loading event) {
        onConfigChanged(event.getConfig());
    }

    /**
     * Mod-bus listener for an edit to this spec's file. Records the reloaded config and, while a
     * server is running with this backend installed, reapplies the policy and resyncs clients on
     * the server thread.
     */
    public void onReloading(ModConfigEvent.Reloading event) {
        onConfigChanged(event.getConfig());
    }

    public Path path() {
        return loadedConfig.getFullPath();
    }

    /** Reads the values published by the loader's load/reload lifecycle. */
    @Override
    public ServerConfig.Settings reload() {
        return Settings.from(setting -> values.get(setting).get());
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean save(ServerConfig.Settings settings) {
        for (Setting setting : Setting.values()) {
            ((ModConfigSpec.ConfigValue<Object>) values.get(setting)).set(setting.get(settings));
        }
        spec.save();
        return true;
    }

    /**
     * Forgets the running server on server stop, so a later file event does not reload or resync a
     * stopped server.
     */
    public void stop() {
        runningServer = null;
    }
}
