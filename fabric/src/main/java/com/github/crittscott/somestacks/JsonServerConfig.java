package com.github.crittscott.somestacks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Fabric's server-policy persistence: per-world JSON at
 * {@code <world>/serverconfig/somestacks-server.json}, reread by {@code /ss reload}. Malformed
 * fields fall back to their defaults independently.
 */
public final class JsonServerConfig implements ServerConfig.Backend {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CONFIG_DIRECTORY = "serverconfig";
    private static final String CONFIG_FILE_SUFFIX = "-server.json";


    private Path configFile;

    /**
     * Selects a world config file and returns validated settings without publishing them. Malformed
     * fields use defaults; an unreadable file uses the complete defaults. A missing file is created.
     * Existing files are written only through save; loading does not synchronize players.
     */
    public ServerConfig.Settings load(Path file) {
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

        if (!Files.exists(file) && save(settings)) {
            SomeStacksCommon.LOGGER.info("Created default server config at {}", displayPath(file));
        }

        return settings;
    }

    @Override
    public ServerConfig.Settings loadFor(MinecraftServer server) {
        return load(server.getWorldPath(LevelResource.ROOT).resolve(CONFIG_DIRECTORY)
                .resolve(SomeStacksCommon.MODID + CONFIG_FILE_SUFFIX));
    }

    @Override
    public ServerConfig.Settings reload() {
        return load(configFile);
    }

    private static ServerConfig.Settings parseJson(JsonObject root, ServerConfig.Settings fallback) {
        JsonObject piles = obj(root, ServerConfig.SECTION_PILES);
        int parsedMaxPileHeight = Mth.clamp(
                intOr(piles, ServerConfig.KEY_MAX_PILE_HEIGHT, fallback.maxPileHeight()),
                1, ServerConfig.MAX_PILE_HEIGHT);

        JsonObject stacks = obj(root, ServerConfig.SECTION_STACKS);
        boolean parsedStorageEnabled = boolOr(
                stacks, ServerConfig.KEY_ENABLE_STORAGE, fallback.enableStorageStackBlock());
        boolean parsedSinglesEnabled = boolOr(
                stacks, ServerConfig.KEY_ENABLE_SINGLES, fallback.enableSinglesStackBlock());
        boolean parsedBarEnabled = boolOr(
                stacks, ServerConfig.KEY_ENABLE_BAR, fallback.enableBarStackBlock());

        JsonObject compatibility = obj(root, ServerConfig.SECTION_COMPATIBILITY);
        List<String> parsedDisableMods = stringListOr(
                compatibility, ServerConfig.KEY_DISABLE_MODS, fallback.disableMods());
        List<String> parsedDisableItems = stringListOr(
                compatibility, ServerConfig.KEY_DISABLE_ITEMS, fallback.disableItems());

        JsonObject gallery = obj(root, ServerConfig.SECTION_RENDER_GALLERY);
        int parsedPlacementsPerTick = Math.max(
                1, intOr(gallery, ServerConfig.KEY_PLACEMENTS_PER_TICK, fallback.renderGalleryPlacementsPerTick()));
        boolean parsedGalleryEnabled = boolOr(
                gallery, ServerConfig.KEY_GALLERY_ENABLED, fallback.galleryEnabled());
        int parsedPermissionLevel = Mth.clamp(
                intOr(gallery, ServerConfig.KEY_REQUIRED_PERMISSION_LEVEL, fallback.galleryPermissionLevel()),
                0, ServerConfig.GALLERY_PERMISSION_LEVEL_MAX);
        List<String> parsedGenMods = stringListOr(gallery, ServerConfig.KEY_GEN_MODS, fallback.genMods());
        List<String> parsedGenItems = stringListOr(gallery, ServerConfig.KEY_GEN_ITEMS, fallback.genItems());

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
        piles.addProperty(ServerConfig.KEY_MAX_PILE_HEIGHT, settings.maxPileHeight());

        JsonObject stacks = new JsonObject();
        stacks.addProperty(ServerConfig.KEY_ENABLE_STORAGE, settings.enableStorageStackBlock());
        stacks.addProperty(ServerConfig.KEY_ENABLE_SINGLES, settings.enableSinglesStackBlock());
        stacks.addProperty(ServerConfig.KEY_ENABLE_BAR, settings.enableBarStackBlock());

        JsonObject compatibility = new JsonObject();
        compatibility.add(ServerConfig.KEY_DISABLE_MODS, stringArray(settings.disableMods()));
        compatibility.add(ServerConfig.KEY_DISABLE_ITEMS, stringArray(settings.disableItems()));

        JsonObject gallery = new JsonObject();
        gallery.addProperty(ServerConfig.KEY_PLACEMENTS_PER_TICK, settings.renderGalleryPlacementsPerTick());
        gallery.addProperty(ServerConfig.KEY_GALLERY_ENABLED, settings.galleryEnabled());
        gallery.addProperty(ServerConfig.KEY_REQUIRED_PERMISSION_LEVEL, settings.galleryPermissionLevel());
        gallery.add(ServerConfig.KEY_GEN_MODS, stringArray(settings.genMods()));
        gallery.add(ServerConfig.KEY_GEN_ITEMS, stringArray(settings.genItems()));

        JsonObject root = new JsonObject();
        root.add(ServerConfig.SECTION_PILES, piles);
        root.add(ServerConfig.SECTION_STACKS, stacks);
        root.add(ServerConfig.SECTION_COMPATIBILITY, compatibility);
        root.add(ServerConfig.SECTION_RENDER_GALLERY, gallery);

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

}
