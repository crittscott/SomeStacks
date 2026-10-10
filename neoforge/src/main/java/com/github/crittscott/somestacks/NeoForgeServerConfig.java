package com.github.crittscott.somestacks;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.minecraft.server.MinecraftServer;
import java.util.List;

/** Loader-managed server configuration exposed as one immutable common policy snapshot. */
public final class NeoForgeServerConfig implements ServerConfig.Backend {
    public static final NeoForgeServerConfig INSTANCE = new NeoForgeServerConfig();
    private net.neoforged.fml.config.ModConfig loadedConfig;
    public java.nio.file.Path path() { return loadedConfig.getFullPath(); }

    public final ModConfigSpec spec;
    private final ModConfigSpec.IntValue maxHeight;
    private final ModConfigSpec.BooleanValue storage;
    private final ModConfigSpec.BooleanValue singles;
    private final ModConfigSpec.BooleanValue bars;
    private final ModConfigSpec.IntValue placements;
    private final ModConfigSpec.BooleanValue gallery;
    private final ModConfigSpec.IntValue permission;
    private final ModConfigSpec.ConfigValue<List<? extends String>> disabledMods;
    private final ModConfigSpec.ConfigValue<List<? extends String>> disabledItems;
    private final ModConfigSpec.ConfigValue<List<? extends String>> genMods;
    private final ModConfigSpec.ConfigValue<List<? extends String>> genItems;

    private NeoForgeServerConfig() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Stack piles: vertical runs of one stack type that share one inventory.").push("piles");
        maxHeight = builder
                .comment("Tallest a run of one stack type may grow, in blocks. Player deposits and automation stop growing a run at this height.")
                .defineInRange("max_pile_height", ServerConfig.DEFAULT_MAX_PILE_HEIGHT, 1, ServerConfig.MAX_PILE_HEIGHT);
        builder.pop();

        builder.comment("Which stack types players and automation may create.",
                "Turning a type off blocks new placement and growth only; existing stacks keep working and their contents stay extractable.").push("stacks");
        storage = builder
                .comment("Storage Stack: 27 item stacks per block that merge, sort, and pack down.")
                .define("enable_storage_stack_block", true);
        singles = builder
                .comment("Singles Stack: a 4 x 4 x 4 display grid holding one item per cell.")
                .define("enable_singles_stack_block", true);
        bars = builder
                .comment("Bar Stack: 64 ingots per block in eight alternating layers; ingots are the somestacks:ingots item tag.")
                .define("enable_bar_stack_block", true);
        builder.pop();

        builder.comment("Items refused by stacks. Disabling never removes or ejects existing contents.").push("compatibility");
        disabledMods = builder
                .comment("Mod ids (item namespaces) whose items players and automation may not put into stacks, e.g. [\"examplemod\"].",
                        "Galleries skip these mods. Edit in game with /ss deny.")
                .defineListAllowEmpty("disable_mods", List.<String>of(), () -> "", value -> value instanceof String);
        disabledItems = builder
                .comment("Item ids players may not deposit into stacks, e.g. [\"minecraft:stone\"]. Automation is not affected.",
                        "Galleries skip these items. The default lists items known to crash clients when drawn. Edit in game with /ss deny.")
                .defineListAllowEmpty("disable_items", ServerConfig.DEFAULT_DISABLE_ITEMS, () -> "", value -> value instanceof String);
        builder.pop();

        builder.comment("Render galleries: /ss gallery and /ss ingotgallery build rows of stacks showing every item of chosen mods,",
                "for checking how items render. Galleries bypass placement protection, so they are off by default.").push("render_gallery");
        gallery = builder
                .comment("Whether /ss gallery and /ss ingotgallery are available.")
                .define("enabled", false);
        permission = builder
                .comment("Permission level needed to run the gallery commands (0 = everyone, 4 = owner).")
                .defineInRange("required_permission_level", net.minecraft.commands.Commands.LEVEL_ADMINS, 0, 4);
        placements = builder
                .comment("Stacks a gallery places per server tick; lower values spread the work over more ticks.")
                .defineInRange("placements_per_tick", ServerConfig.DEFAULT_GALLERY_PLACEMENTS_PER_TICK, 1, Integer.MAX_VALUE);
        genMods = builder
                .comment("Mod ids the gallery 'list' form builds, one column per mod. Edit in game with /ss gen.")
                .defineListAllowEmpty("gen_mods", List.<String>of(), () -> "", value -> value instanceof String);
        genItems = builder
                .comment("Item ids the gallery 'items' form builds as a single column. Edit in game with /ss gen.")
                .defineListAllowEmpty("gen_items", List.<String>of(), () -> "", value -> value instanceof String);
        builder.pop();

        spec = builder.build();
    }

    public void onLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == spec) {
            loadedConfig = event.getConfig();
            if (ServerConfig.backend() != this) return;
            SomeStacksServer.onPolicyReloaded();
        }
    }

    public void onReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == spec) {
            loadedConfig = event.getConfig();
            if (ServerConfig.backend() != this) return;
            SomeStacksServer.onPolicyReloaded();
        }
    }

    @Override
    public ServerConfig.Settings loadFor(MinecraftServer server) { return reload(); }

    /** Reads the values published by the loader's load/reload lifecycle. */
    @Override
    public ServerConfig.Settings reload() {
        return new ServerConfig.Settings(maxHeight.get(), storage.get(), singles.get(), bars.get(),
                List.copyOf(disabledMods.get()), List.copyOf(disabledItems.get()), placements.get(),
                gallery.get(), permission.get(), List.copyOf(genMods.get()), List.copyOf(genItems.get()));
    }

    @Override
    public boolean save(ServerConfig.Settings settings) {
        maxHeight.set(settings.maxPileHeight());
        storage.set(settings.enableStorageStackBlock());
        singles.set(settings.enableSinglesStackBlock());
        bars.set(settings.enableBarStackBlock());
        disabledMods.set(settings.disableMods());
        disabledItems.set(settings.disableItems());
        placements.set(settings.renderGalleryPlacementsPerTick());
        gallery.set(settings.galleryEnabled());
        permission.set(settings.galleryPermissionLevel());
        genMods.set(settings.genMods());
        genItems.set(settings.genItems());
        spec.save();
        return true;
    }
}
