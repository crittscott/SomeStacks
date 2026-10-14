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
    private static final Settings DEFAULTS = Settings.defaults();
    private static final ForgeConfigSpec.IntValue MAX_HEIGHT;
    private static final ForgeConfigSpec.BooleanValue STORAGE_ENABLED;
    private static final ForgeConfigSpec.BooleanValue SINGLES_ENABLED;
    private static final ForgeConfigSpec.BooleanValue BAR_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLE_MODS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLE_ITEMS;
    private static final ForgeConfigSpec.BooleanValue GALLERY_ENABLED;
    private static final ForgeConfigSpec.IntValue GALLERY_PERMISSION;
    private static final ForgeConfigSpec.IntValue PLACEMENTS_PER_TICK;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> GEN_MODS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> GEN_ITEMS;
    public static final ForgeConfigSpec SPEC;
    private static volatile MinecraftServer server;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Stack piles: vertical runs of one stack type that share one inventory.").push("piles");
        MAX_HEIGHT = builder
                .comment("Tallest a run of one stack type may grow, in blocks. Player deposits and automation stop growing a run at this height.")
                .defineInRange("max_pile_height", DEFAULTS.maxPileHeight(), 1, ServerConfig.MAX_PILE_HEIGHT_LIMIT);
        builder.pop();

        builder.comment("Which stack types players and automation may create.",
                "Turning a type off blocks new placement and growth only; existing stacks keep working and their contents stay extractable.").push("stacks");
        STORAGE_ENABLED = builder
                .comment("Storage Stack: 27 item stacks per block that merge, sort, and pack down.")
                .define("enable_storage_stack_block", DEFAULTS.enableStorageStackBlock());
        SINGLES_ENABLED = builder
                .comment("Singles Stack: a 4 x 4 x 4 display grid holding one item per cell.")
                .define("enable_singles_stack_block", DEFAULTS.enableSinglesStackBlock());
        BAR_ENABLED = builder
                .comment("Bar Stack: 64 ingots per block in eight alternating layers; ingots are the somestacks:ingots item tag.")
                .define("enable_bar_stack_block", DEFAULTS.enableBarStackBlock());
        builder.pop();

        builder.comment("Items refused by stacks. Disabling never removes or ejects existing contents.").push("compatibility");
        DISABLE_MODS = strings(builder.comment(
                        "Mod ids (item namespaces) whose items players and automation may not put into stacks, e.g. [\"examplemod\"].",
                        "Galleries skip these mods. Edit in game with /ss deny."),
                "disable_mods", DEFAULTS.disableMods());
        DISABLE_ITEMS = strings(builder.comment(
                        "Item ids players may not deposit into stacks, e.g. [\"minecraft:stone\"]. Automation is not affected.",
                        "Galleries skip these items. The default lists items known to crash clients when drawn. Edit in game with /ss deny."),
                "disable_items", DEFAULTS.disableItems());
        builder.pop();

        builder.comment("Render galleries: /ss gallery and /ss ingotgallery build rows of stacks showing every item of chosen mods,",
                "for checking how items render. Galleries bypass placement protection, so they are off by default.").push("render_gallery");
        GALLERY_ENABLED = builder
                .comment("Whether /ss gallery and /ss ingotgallery are available.")
                .define("enabled", DEFAULTS.galleryEnabled());
        GALLERY_PERMISSION = builder
                .comment("Permission level needed to run the gallery commands (0 = everyone, 4 = owner).")
                .defineInRange("required_permission_level", DEFAULTS.galleryPermissionLevel(),
                        0, ServerConfig.GALLERY_PERMISSION_LEVEL_MAX);
        PLACEMENTS_PER_TICK = builder
                .comment("Stacks a gallery places per server tick; lower values spread the work over more ticks.")
                .defineInRange("placements_per_tick", DEFAULTS.renderGalleryPlacementsPerTick(), 1, Integer.MAX_VALUE);
        GEN_MODS = strings(builder.comment(
                        "Mod ids the gallery 'list' form builds, one column per mod. Edit in game with /ss gen."),
                "gen_mods", DEFAULTS.genMods());
        GEN_ITEMS = strings(builder.comment(
                        "Item ids the gallery 'items' form builds as a single column. Edit in game with /ss gen."),
                "gen_items", DEFAULTS.genItems());
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

    /**
     * Forge has loaded the world's server config before ServerAboutToStartEvent. A reload reads
     * the values Forge currently holds, which its config lifecycle keeps in step with the file.
     */
    public static void start(MinecraftServer startingServer) {
        server = startingServer;
        ServerConfig.start(ForgeServerConfig::read, ForgeServerConfig::save);
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

    /** Publishes a changed policy on the server thread and resyncs every player. */
    private static void queuePublication(ModConfig config) {
        if (config.getType() != ModConfig.Type.SERVER || config.getSpec() != SPEC) return;
        MinecraftServer expected = server;
        if (expected == null) return;
        expected.execute(() -> {
            if (server != expected || !SPEC.isLoaded()) return;
            Settings before = ServerConfig.settings();
            ServerConfig.reload();
            if (!before.equals(ServerConfig.settings())) {
                ServerLifecycle.syncAllPlayers(expected);
            }
        });
    }

    private static Settings read() {
        return new Settings(MAX_HEIGHT.get(), STORAGE_ENABLED.get(), SINGLES_ENABLED.get(), BAR_ENABLED.get(),
                List.copyOf(DISABLE_MODS.get()), List.copyOf(DISABLE_ITEMS.get()),
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
        setIfChanged(PLACEMENTS_PER_TICK, settings.renderGalleryPlacementsPerTick());
        setIfChanged(GALLERY_ENABLED, settings.galleryEnabled());
        setIfChanged(GALLERY_PERMISSION, settings.galleryPermissionLevel());
        setIfChanged(GEN_MODS, settings.genMods());
        setIfChanged(GEN_ITEMS, settings.genItems());
        SPEC.save();
    }
}
