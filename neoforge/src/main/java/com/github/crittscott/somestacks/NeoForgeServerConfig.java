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
        maxHeight = builder.defineInRange("piles.max_pile_height", ServerConfig.DEFAULT_MAX_PILE_HEIGHT, 1, ServerConfig.MAX_PILE_HEIGHT);
        storage = builder.define("stacks.enable_storage_stack_block", true);
        singles = builder.define("stacks.enable_singles_stack_block", true);
        bars = builder.define("stacks.enable_bar_stack_block", true);
        placements = builder.defineInRange("render_gallery.placements_per_tick", ServerConfig.DEFAULT_GALLERY_PLACEMENTS_PER_TICK, 1, Integer.MAX_VALUE);
        gallery = builder.define("render_gallery.enabled", false);
        permission = builder.defineInRange("render_gallery.required_permission_level", net.minecraft.commands.Commands.LEVEL_ADMINS, 0, 4);
        disabledMods = builder.defineListAllowEmpty("compatibility.disable_mods", List.<String>of(), () -> "", value -> value instanceof String);
        disabledItems = builder.defineListAllowEmpty("compatibility.disable_items", List.<String>of(), () -> "", value -> value instanceof String);
        genMods = builder.defineListAllowEmpty("render_gallery.gen_mods", List.<String>of(), () -> "", value -> value instanceof String);
        genItems = builder.defineListAllowEmpty("render_gallery.gen_items", List.<String>of(), () -> "", value -> value instanceof String);
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
