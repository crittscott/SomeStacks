package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.ServerConfig.Settings;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

/** Forge's native world policy, published into common gameplay on the server thread. */
public final class ForgeServerConfig {
    private static final Settings DEFAULTS = Settings.defaults(false);
    private static final ForgeConfigSpec.IntValue MAX_HEIGHT;
    private static final ForgeConfigSpec.BooleanValue STORAGE_ENABLED;
    private static final ForgeConfigSpec.BooleanValue SINGLES_ENABLED;
    private static final ForgeConfigSpec.BooleanValue BAR_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLE_MODS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLE_ITEMS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> INGOT_TAGS;
    private static final ForgeConfigSpec.IntValue PLACEMENTS_PER_TICK;
    private static final ForgeConfigSpec.BooleanValue GALLERY_ENABLED;
    private static final ForgeConfigSpec.IntValue GALLERY_PERMISSION;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> GEN_MODS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> GEN_ITEMS;
    public static final ForgeConfigSpec SPEC;
    private static volatile MinecraftServer server;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("piles");
        MAX_HEIGHT = builder.defineInRange("max_pile_height", DEFAULTS.maxPileHeight(),
                1, ServerConfig.MAX_PILE_HEIGHT_LIMIT);
        builder.pop();
        builder.push("stacks");
        STORAGE_ENABLED = builder.define("enable_storage_stack_block", DEFAULTS.enableStorageStackBlock());
        SINGLES_ENABLED = builder.define("enable_singles_stack_block", DEFAULTS.enableSinglesStackBlock());
        BAR_ENABLED = builder.define("enable_bar_stack_block", DEFAULTS.enableBarStackBlock());
        builder.pop();
        builder.push("compatibility");
        DISABLE_MODS = strings(builder, "disable_mods", DEFAULTS.disableMods());
        DISABLE_ITEMS = strings(builder, "disable_items", DEFAULTS.disableItems());
        INGOT_TAGS = strings(builder, "ingot_tags", DEFAULTS.ingotTags());
        builder.pop();
        builder.push("render_gallery");
        PLACEMENTS_PER_TICK = builder.defineInRange("placements_per_tick",
                DEFAULTS.renderGalleryPlacementsPerTick(), 1, Integer.MAX_VALUE);
        GALLERY_ENABLED = builder.define("enabled", DEFAULTS.galleryEnabled());
        GALLERY_PERMISSION = builder.defineInRange("required_permission_level",
                DEFAULTS.galleryPermissionLevel(), 0, ServerConfig.GALLERY_PERMISSION_LEVEL_MAX);
        GEN_MODS = strings(builder, "gen_mods", DEFAULTS.genMods());
        GEN_ITEMS = strings(builder, "gen_items", DEFAULTS.genItems());
        builder.pop();
        SPEC = builder.build();
    }

    private ForgeServerConfig() {}

    private static ForgeConfigSpec.ConfigValue<List<? extends String>> strings(
            ForgeConfigSpec.Builder builder, String name, List<String> defaults) {
        return builder.defineListAllowEmpty(List.of(name), () -> defaults, value -> value instanceof String);
    }

    public static void register(IEventBus modBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC);
        modBus.addListener(ForgeServerConfig::onLoading);
        modBus.addListener(ForgeServerConfig::onReloading);
    }

    /** Forge has loaded the world's server config before ServerAboutToStartEvent. */
    public static void start(MinecraftServer startingServer) {
        server = startingServer;
        ServerConfig.start(read(), ForgeServerConfig::save);
    }

    public static void stop() {
        server = null;
    }

    private static void onLoading(ModConfigEvent.Loading event) {
        queuePublication(event.getConfig());
    }

    private static void onReloading(ModConfigEvent.Reloading event) {
        queuePublication(event.getConfig());
    }

    private static void queuePublication(ModConfig config) {
        if (config.getType() != ModConfig.Type.SERVER || config.getSpec() != SPEC) return;
        MinecraftServer expected = server;
        if (expected == null) return;
        expected.execute(() -> {
            if (server != expected || !SPEC.isLoaded()) return;
            Settings before = ServerConfig.settings();
            Settings next = read();
            if (next.equals(before)) return;
            ServerConfig.publish(next);
            if (before.enableStorageStackBlock() != next.enableStorageStackBlock()
                    || before.enableSinglesStackBlock() != next.enableSinglesStackBlock()
                    || before.enableBarStackBlock() != next.enableBarStackBlock()) {
                ServerLifecycle.syncAllPlayers(expected);
            }
        });
    }

    private static Settings read() {
        return new Settings(MAX_HEIGHT.get(), STORAGE_ENABLED.get(), SINGLES_ENABLED.get(), BAR_ENABLED.get(),
                List.copyOf(DISABLE_MODS.get()), List.copyOf(DISABLE_ITEMS.get()), List.copyOf(INGOT_TAGS.get()),
                PLACEMENTS_PER_TICK.get(), GALLERY_ENABLED.get(), GALLERY_PERMISSION.get(),
                List.copyOf(GEN_MODS.get()), List.copyOf(GEN_ITEMS.get()));
    }

    private static <T> void setIfChanged(ForgeConfigSpec.ConfigValue<T> value, T replacement) {
        if (!value.get().equals(replacement)) value.set(replacement);
    }

    private static void save(Settings settings) {
        setIfChanged(MAX_HEIGHT, settings.maxPileHeight());
        setIfChanged(STORAGE_ENABLED, settings.enableStorageStackBlock());
        setIfChanged(SINGLES_ENABLED, settings.enableSinglesStackBlock());
        setIfChanged(BAR_ENABLED, settings.enableBarStackBlock());
        setIfChanged(DISABLE_MODS, settings.disableMods());
        setIfChanged(DISABLE_ITEMS, settings.disableItems());
        setIfChanged(INGOT_TAGS, settings.ingotTags());
        setIfChanged(PLACEMENTS_PER_TICK, settings.renderGalleryPlacementsPerTick());
        setIfChanged(GALLERY_ENABLED, settings.galleryEnabled());
        setIfChanged(GALLERY_PERMISSION, settings.galleryPermissionLevel());
        setIfChanged(GEN_MODS, settings.genMods());
        setIfChanged(GEN_ITEMS, settings.genItems());
        SPEC.save();
    }
}
