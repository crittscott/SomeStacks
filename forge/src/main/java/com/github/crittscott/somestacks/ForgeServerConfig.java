package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.ServerConfig.Section;
import com.github.crittscott.somestacks.ServerConfig.Setting;
import com.github.crittscott.somestacks.ServerConfig.Settings;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Forge's native world policy, published into common gameplay on the server thread. */
public final class ForgeServerConfig {
    private static final Settings DEFAULTS = Settings.defaults();
    public static final ForgeConfigSpec SPEC;
    private static final Map<Setting, ForgeConfigSpec.ConfigValue<?>> VALUES =
            new EnumMap<>(Setting.class);
    private static volatile MinecraftServer server;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        for (Section section : Section.values()) {
            builder.comment(section.comment().toArray(String[]::new)).push(section.key());
            for (Setting setting : section.settings()) {
                VALUES.put(
                        setting,
                        define(builder.comment(setting.comment().toArray(String[]::new)), setting));
            }
            builder.pop();
        }
        SPEC = builder.build();
    }

    private ForgeServerConfig() {}

    /** Declares one shared setting; Forge adds the range line to bounded integers itself. */
    @SuppressWarnings("unchecked")
    private static ForgeConfigSpec.ConfigValue<?> define(
            ForgeConfigSpec.Builder builder, Setting setting) {
        Object fallback = setting.get(DEFAULTS);
        return switch (setting.kind()) {
            case INTEGER ->
                    builder.defineInRange(
                            setting.key(), (int) (Integer) fallback, setting.min(), setting.max());
            case BOOLEAN -> builder.define(setting.key(), (boolean) (Boolean) fallback);
            case STRINGS -> {
                List<String> strings = (List<String>) fallback;
                yield builder.defineListAllowEmpty(
                        List.of(setting.key()), () -> strings, value -> value instanceof String);
            }
        };
    }

    /**
     * Registers the spec as the per-world {@code SERVER} config and listens for its load and file
     * reload events; runs once from the mod constructor. Publication waits for {@link #start}.
     */
    public static void register(IEventBus modBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC);
        modBus.addListener(ForgeServerConfig::onLoading);
        modBus.addListener(ForgeServerConfig::onReloading);
    }

    /**
     * Forge has loaded the world's server config before ServerAboutToStartEvent. A reload reads the
     * values Forge currently holds, which its config lifecycle keeps in step with the file.
     */
    public static void start(MinecraftServer startingServer) {
        server = startingServer;
        ServerConfig.start(
                ForgeServerConfig::read,
                ForgeServerConfig::save,
                () -> ServerLifecycle.syncAllPlayers(startingServer));
    }

    /** Forgets the stopped server, so a config event queued for it no longer publishes. */
    public static void stop() {
        server = null;
    }

    private static void onLoading(ModConfigEvent.Loading event) {
        queuePublication(event.getConfig());
    }

    private static void onReloading(ModConfigEvent.Reloading event) {
        queuePublication(event.getConfig());
    }

    /** Publishes policy on the server thread; shared publication handles client synchronization. */
    private static void queuePublication(ModConfig config) {
        if (config.getType() != ModConfig.Type.SERVER || config.getSpec() != SPEC) return;
        MinecraftServer expected = server;
        if (expected == null) return;
        expected.execute(() -> {
            if (server != expected || !SPEC.isLoaded()) return;
            Settings previous = ServerConfig.settings();
            if (ServerConfig.reload() && !previous.equals(ServerConfig.settings())) {
                ServerLifecycle.refreshCommands(expected, previous);
                ServerConfig.logActivePolicy();
            }
        });
    }

    private static Settings read() {
        return Settings.from(setting -> VALUES.get(setting).get());
    }

    private static void save(Settings settings) {
        for (Setting setting : Setting.values()) {
            setIfChanged(VALUES.get(setting), setting.get(settings));
        }
        SPEC.save();
    }

    @SuppressWarnings("unchecked")
    private static void setIfChanged(ForgeConfigSpec.ConfigValue<?> value, Object replacement) {
        if (!value.get().equals(replacement)) {
            ((ForgeConfigSpec.ConfigValue<Object>) value).set(replacement);
        }
    }
}
