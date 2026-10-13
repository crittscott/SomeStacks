package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.ServerConfig.Settings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fabric's world-specific JSON policy and immediate command persistence. */
public final class FabricServerConfig {
    private FabricServerConfig() {}

    public static void start(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT)
                .resolve("serverconfig").resolve(SomeStacksCommon.MODID + "-server.json");
        Persistence persistence = new Persistence(file);
        ServerConfig.start(persistence.read(Settings.defaults(true)), persistence::write);
    }

    /** JSON boundary, independent of registry baking and gameplay. */
    static final class Persistence {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private final Path file;

        Persistence(Path file) {
            this.file = file;
        }

        Settings read(Settings defaults) {
            if (!Files.exists(file)) {
                write(defaults);
                return defaults;
            }
            try {
                JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
                return root == null ? defaults : decode(root, defaults);
            } catch (Exception exception) {
                SomeStacksCommon.LOGGER.warn("Failed to read {}: {}", file, exception.getMessage());
                return defaults;
            }
        }

        private static Settings decode(JsonObject root, Settings defaults) {
            JsonObject piles = obj(root, "piles");
            JsonObject stacks = obj(root, "stacks");
            JsonObject compatibility = obj(root, "compatibility");
            JsonObject gallery = obj(root, "render_gallery");
            return new Settings(
                    clamp(intOr(piles, "max_pile_height", defaults.maxPileHeight()), 1, ServerConfig.MAX_PILE_HEIGHT_LIMIT),
                    boolOr(stacks, "enable_storage_stack_block", defaults.enableStorageStackBlock()),
                    boolOr(stacks, "enable_singles_stack_block", defaults.enableSinglesStackBlock()),
                    boolOr(stacks, "enable_bar_stack_block", defaults.enableBarStackBlock()),
                    stringListOr(compatibility, "disable_mods", defaults.disableMods()),
                    stringListOr(compatibility, "disable_items", defaults.disableItems()),
                    stringListOr(compatibility, "ingot_tags", defaults.ingotTags()),
                    Math.max(1, intOr(gallery, "placements_per_tick", defaults.renderGalleryPlacementsPerTick())),
                    boolOr(gallery, "enabled", defaults.galleryEnabled()),
                    clamp(intOr(gallery, "required_permission_level", defaults.galleryPermissionLevel()), 0, ServerConfig.GALLERY_PERMISSION_LEVEL_MAX),
                    stringListOr(gallery, "gen_mods", defaults.genMods()),
                    stringListOr(gallery, "gen_items", defaults.genItems()));
        }

        void write(Settings values) {
            JsonObject piles = new JsonObject();
            piles.addProperty("max_pile_height", values.maxPileHeight());
            JsonObject stacks = new JsonObject();
            stacks.addProperty("enable_storage_stack_block", values.enableStorageStackBlock());
            stacks.addProperty("enable_singles_stack_block", values.enableSinglesStackBlock());
            stacks.addProperty("enable_bar_stack_block", values.enableBarStackBlock());
            JsonObject compatibility = new JsonObject();
            compatibility.add("disable_mods", stringArray(values.disableMods()));
            compatibility.add("disable_items", stringArray(values.disableItems()));
            compatibility.add("ingot_tags", stringArray(values.ingotTags()));
            JsonObject gallery = new JsonObject();
            gallery.addProperty("placements_per_tick", values.renderGalleryPlacementsPerTick());
            gallery.addProperty("enabled", values.galleryEnabled());
            gallery.addProperty("required_permission_level", values.galleryPermissionLevel());
            gallery.add("gen_mods", stringArray(values.genMods()));
            gallery.add("gen_items", stringArray(values.genItems()));
            JsonObject root = new JsonObject();
            root.add("piles", piles);
            root.add("stacks", stacks);
            root.add("compatibility", compatibility);
            root.add("render_gallery", gallery);
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(root));
            } catch (IOException exception) {
                SomeStacksCommon.LOGGER.warn("Failed to write {}: {}", file, exception.getMessage());
            }
        }

        private static JsonObject obj(JsonObject parent, String key) {
            return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
        }

        private static int intOr(JsonObject obj, String key, int fallback) {
            return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsInt() : fallback;
        }

        private static boolean boolOr(JsonObject obj, String key, boolean fallback) {
            return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsBoolean() : fallback;
        }

        private static List<String> stringListOr(JsonObject obj, String key, List<String> fallback) {
            if (!obj.has(key) || !obj.get(key).isJsonArray()) {
                return fallback;
            }
            List<String> out = new ArrayList<>();
            for (JsonElement entry : obj.getAsJsonArray(key)) {
                out.add(entry.getAsString());
            }
            return out;
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

}
