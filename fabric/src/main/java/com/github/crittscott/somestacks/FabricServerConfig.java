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

    /** Reads the world's JSON now; {@code /ss reload} rereads it. */
    public static void start(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT)
                .resolve("serverconfig").resolve(SomeStacksCommon.MODID + "-server.json");
        Persistence persistence = new Persistence(file);
        ServerConfig.start(() -> persistence.read(Settings.defaults()), persistence::write);
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
            JsonObject piles = obj(root, ServerConfig.Keys.PILES);
            JsonObject stacks = obj(root, ServerConfig.Keys.STACKS);
            JsonObject compatibility = obj(root, ServerConfig.Keys.COMPATIBILITY);
            JsonObject gallery = obj(root, ServerConfig.Keys.RENDER_GALLERY);
            return new Settings(
                    intOr(piles, ServerConfig.Keys.MAX_PILE_HEIGHT, defaults.maxPileHeight()),
                    boolOr(stacks, ServerConfig.Keys.ENABLE_STORAGE_STACK_BLOCK, defaults.enableStorageStackBlock()),
                    boolOr(stacks, ServerConfig.Keys.ENABLE_SINGLES_STACK_BLOCK, defaults.enableSinglesStackBlock()),
                    boolOr(stacks, ServerConfig.Keys.ENABLE_BAR_STACK_BLOCK, defaults.enableBarStackBlock()),
                    stringListOr(compatibility, ServerConfig.Keys.DISABLE_MODS, defaults.disableMods()),
                    stringListOr(compatibility, ServerConfig.Keys.DISABLE_ITEMS, defaults.disableItems()),
                    intOr(gallery, ServerConfig.Keys.PLACEMENTS_PER_TICK, defaults.renderGalleryPlacementsPerTick()),
                    boolOr(gallery, ServerConfig.Keys.ENABLED, defaults.galleryEnabled()),
                    intOr(gallery, ServerConfig.Keys.REQUIRED_PERMISSION_LEVEL, defaults.galleryPermissionLevel()),
                    stringListOr(gallery, ServerConfig.Keys.GEN_MODS, defaults.genMods()),
                    stringListOr(gallery, ServerConfig.Keys.GEN_ITEMS, defaults.genItems()));
        }

        void write(Settings values) {
            JsonObject piles = new JsonObject();
            piles.addProperty(ServerConfig.Keys.MAX_PILE_HEIGHT, values.maxPileHeight());
            JsonObject stacks = new JsonObject();
            stacks.addProperty(ServerConfig.Keys.ENABLE_STORAGE_STACK_BLOCK, values.enableStorageStackBlock());
            stacks.addProperty(ServerConfig.Keys.ENABLE_SINGLES_STACK_BLOCK, values.enableSinglesStackBlock());
            stacks.addProperty(ServerConfig.Keys.ENABLE_BAR_STACK_BLOCK, values.enableBarStackBlock());
            JsonObject compatibility = new JsonObject();
            compatibility.add(ServerConfig.Keys.DISABLE_MODS, stringArray(values.disableMods()));
            compatibility.add(ServerConfig.Keys.DISABLE_ITEMS, stringArray(values.disableItems()));
            JsonObject gallery = new JsonObject();
            gallery.addProperty(ServerConfig.Keys.PLACEMENTS_PER_TICK, values.renderGalleryPlacementsPerTick());
            gallery.addProperty(ServerConfig.Keys.ENABLED, values.galleryEnabled());
            gallery.addProperty(ServerConfig.Keys.REQUIRED_PERMISSION_LEVEL, values.galleryPermissionLevel());
            gallery.add(ServerConfig.Keys.GEN_MODS, stringArray(values.genMods()));
            gallery.add(ServerConfig.Keys.GEN_ITEMS, stringArray(values.genItems()));
            JsonObject root = new JsonObject();
            root.add(ServerConfig.Keys.PILES, piles);
            root.add(ServerConfig.Keys.STACKS, stacks);
            root.add(ServerConfig.Keys.COMPATIBILITY, compatibility);
            root.add(ServerConfig.Keys.RENDER_GALLERY, gallery);
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

    }

}
