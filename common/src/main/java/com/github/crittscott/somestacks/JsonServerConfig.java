package com.github.crittscott.somestacks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.Commands;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** JSON persistence installed by Fabric and used by loader-neutral configuration fixtures. */
public final class JsonServerConfig implements ServerConfig.Backend {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CONFIG_DIRECTORY = "serverconfig";
    private static final String CONFIG_FILE_SUFFIX = "-server.json";

    private static final String SECTION_PILES = "piles";
    private static final String SECTION_STACKS = "stacks";
    private static final String SECTION_COMPATIBILITY = "compatibility";
    private static final String SECTION_RENDER_GALLERY = "render_gallery";

    private static final String KEY_MAX_PILE_HEIGHT = "max_pile_height";
    private static final String KEY_ENABLE_STORAGE = "enable_storage_stack_block";
    private static final String KEY_ENABLE_SINGLES = "enable_singles_stack_block";
    private static final String KEY_ENABLE_BAR = "enable_bar_stack_block";
    private static final String KEY_DISABLE_MODS = "disable_mods";
    private static final String KEY_DISABLE_ITEMS = "disable_items";
    private static final String KEY_PLACEMENTS_PER_TICK = "placements_per_tick";
    private static final String KEY_GALLERY_ENABLED = "enabled";
    private static final String KEY_REQUIRED_PERMISSION_LEVEL = "required_permission_level";
    private static final String KEY_GEN_MODS = "gen_mods";
    private static final String KEY_GEN_ITEMS = "gen_items";



    /** Upper bound accepted for {@code piles.max_pile_height} in the config file. */
    private static final int MAX_PILE_HEIGHT_LIMIT = ServerConfig.MAX_PILE_HEIGHT;

    /** Upper bound accepted for {@code render_gallery.required_permission_level}, vanilla's top op level. */
    private static final int GALLERY_PERMISSION_LEVEL_MAX = Commands.LEVEL_OWNERS;

    private Path configFile;

    public void load(Path file) {
        configFile = file;
        ServerConfig.Settings settings = ServerConfig.defaults();

        if (Files.exists(file)) {
            try {
                JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
                if (root == null) {
                    throw new IllegalArgumentException("root is null");
                }
                settings = parseJson(root, settings);
                SomeStacksCommon.LOGGER.info("Loaded server config from {}", displayPath(file));
            } catch (Exception e) {
                SomeStacksCommon.LOGGER.warn(
                        "Failed to read {}: {}. Using defaults; the next successful save by /ss deny, /ss gen will replace it",
                        displayPath(file), e.getMessage());
            }
        }

        ServerConfig.install(this);
        ServerConfig.apply(settings);
        if (!Files.exists(file) && save(ServerConfig.settings())) {
            SomeStacksCommon.LOGGER.info("Created default server config at {}", displayPath(file));
        }

    }

    @Override
    public ServerConfig.Settings loadFor(MinecraftServer server) {
        load(server.getWorldPath(LevelResource.ROOT).resolve(CONFIG_DIRECTORY)
                .resolve(SomeStacksCommon.MODID + CONFIG_FILE_SUFFIX));
        return ServerConfig.settings();
    }

    @Override
    public ServerConfig.Settings reload() {
        load(configFile);
        return ServerConfig.settings();
    }

    private static ServerConfig.Settings parseJson(JsonObject root, ServerConfig.Settings fallback) {
        JsonObject piles = obj(root, SECTION_PILES);
        int parsedMaxPileHeight = clamp(
                intOr(piles, KEY_MAX_PILE_HEIGHT, fallback.maxPileHeight()),
                1, MAX_PILE_HEIGHT_LIMIT);

        JsonObject stacks = obj(root, SECTION_STACKS);
        boolean parsedStorageEnabled = boolOr(
                stacks, KEY_ENABLE_STORAGE, fallback.enableStorageStackBlock());
        boolean parsedSinglesEnabled = boolOr(
                stacks, KEY_ENABLE_SINGLES, fallback.enableSinglesStackBlock());
        boolean parsedBarEnabled = boolOr(
                stacks, KEY_ENABLE_BAR, fallback.enableBarStackBlock());

        JsonObject compatibility = obj(root, SECTION_COMPATIBILITY);
        List<String> parsedDisableMods = stringListOr(
                compatibility, KEY_DISABLE_MODS, fallback.disableMods());
        List<String> parsedDisableItems = stringListOr(
                compatibility, KEY_DISABLE_ITEMS, fallback.disableItems());

        JsonObject gallery = obj(root, SECTION_RENDER_GALLERY);
        int parsedPlacementsPerTick = Math.max(
                1, intOr(gallery, KEY_PLACEMENTS_PER_TICK, fallback.renderGalleryPlacementsPerTick()));
        boolean parsedGalleryEnabled = boolOr(
                gallery, KEY_GALLERY_ENABLED, fallback.galleryEnabled());
        int parsedPermissionLevel = clamp(
                intOr(gallery, KEY_REQUIRED_PERMISSION_LEVEL, fallback.galleryPermissionLevel()),
                0, GALLERY_PERMISSION_LEVEL_MAX);
        List<String> parsedGenMods = stringListOr(gallery, KEY_GEN_MODS, fallback.genMods());
        List<String> parsedGenItems = stringListOr(gallery, KEY_GEN_ITEMS, fallback.genItems());

        return new ServerConfig.Settings(
                parsedMaxPileHeight,
                parsedStorageEnabled,
                parsedSinglesEnabled,
                parsedBarEnabled,
                parsedDisableMods,
                parsedDisableItems,
                parsedPlacementsPerTick,
                parsedGalleryEnabled,
                parsedPermissionLevel,
                parsedGenMods,
                parsedGenItems);
    }

    public boolean save(ServerConfig.Settings settings) {
        if (configFile == null) {
            return false;
        }

        JsonObject piles = new JsonObject();
        piles.addProperty(KEY_MAX_PILE_HEIGHT, settings.maxPileHeight());

        JsonObject stacks = new JsonObject();
        stacks.addProperty(KEY_ENABLE_STORAGE, settings.enableStorageStackBlock());
        stacks.addProperty(KEY_ENABLE_SINGLES, settings.enableSinglesStackBlock());
        stacks.addProperty(KEY_ENABLE_BAR, settings.enableBarStackBlock());

        JsonObject compatibility = new JsonObject();
        compatibility.add(KEY_DISABLE_MODS, stringArray(settings.disableMods()));
        compatibility.add(KEY_DISABLE_ITEMS, stringArray(settings.disableItems()));

        JsonObject gallery = new JsonObject();
        gallery.addProperty(KEY_PLACEMENTS_PER_TICK, settings.renderGalleryPlacementsPerTick());
        gallery.addProperty(KEY_GALLERY_ENABLED, settings.galleryEnabled());
        gallery.addProperty(KEY_REQUIRED_PERMISSION_LEVEL, settings.galleryPermissionLevel());
        gallery.add(KEY_GEN_MODS, stringArray(settings.genMods()));
        gallery.add(KEY_GEN_ITEMS, stringArray(settings.genItems()));

        JsonObject root = new JsonObject();
        root.add(SECTION_PILES, piles);
        root.add(SECTION_STACKS, stacks);
        root.add(SECTION_COMPATIBILITY, compatibility);
        root.add(SECTION_RENDER_GALLERY, gallery);

        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, GSON.toJson(root));
            return true;
        } catch (IOException e) {
            SomeStacksCommon.LOGGER.warn("Failed to write {}: {}", configFile, e.getMessage());
            return false;
        }
    }

    private static Path displayPath(Path file) {
        return file.toAbsolutePath().normalize();
    }

    private static JsonObject obj(JsonObject parent, String key) {
        if (!parent.has(key)) {
            return new JsonObject();
        }
        if (parent.get(key).isJsonObject()) {
            return parent.getAsJsonObject(key);
        }
        SomeStacksCommon.LOGGER.warn("Ignoring non-object server config section '{}'", key);
        return new JsonObject();
    }

    private static int intOr(JsonObject obj, String key, int fallback) {
        if (!obj.has(key)) {
            return fallback;
        }
        try {
            if (obj.get(key).isJsonPrimitive() && obj.getAsJsonPrimitive(key).isNumber()) {
                return Integer.parseInt(obj.get(key).getAsString());
            }
        } catch (RuntimeException ignored) {
        }
        SomeStacksCommon.LOGGER.warn("Ignoring non-integer server config field '{}'", key);
        return fallback;
    }

    private static boolean boolOr(JsonObject obj, String key, boolean fallback) {
        if (!obj.has(key)) {
            return fallback;
        }
        if (obj.get(key).isJsonPrimitive() && obj.getAsJsonPrimitive(key).isBoolean()) {
            return obj.get(key).getAsBoolean();
        }
        SomeStacksCommon.LOGGER.warn("Ignoring non-boolean server config field '{}'", key);
        return fallback;
    }

    private static List<String> stringListOr(JsonObject obj, String key, List<String> fallback) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) {
            if (obj.has(key)) {
                SomeStacksCommon.LOGGER.warn("Ignoring non-array server config field '{}'", key);
            }
            return fallback;
        }
        List<String> out = new ArrayList<>();
        for (JsonElement entry : obj.getAsJsonArray(key)) {
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                out.add(entry.getAsString());
            } else {
                SomeStacksCommon.LOGGER.warn("Ignoring non-string entry in server config field '{}'", key);
            }
        }
        return List.copyOf(out);
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

}
