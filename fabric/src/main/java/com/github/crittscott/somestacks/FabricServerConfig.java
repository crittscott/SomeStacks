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
        SomeStacksCommon.LOGGER.info("Reading server policy from {}", file);
        ServerConfig.start(() -> persistence.read(Settings.defaults()), persistence::write,
                () -> ServerLifecycle.syncAllPlayers(server));
    }

    /** JSON boundary, independent of registry baking and gameplay. */
    static final class Persistence {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private final Path file;
        private String readingKey = "$";

        Persistence(Path file) {
            this.file = file;
        }

        Settings read(Settings defaults) {
            if (!Files.exists(file)) {
                if (write(defaults)) {
                    SomeStacksCommon.LOGGER.info("Created default server policy at {}", file);
                }
                return defaults;
            }
            readingKey = "$";
            try {
                JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
                if (root == null) {
                    SomeStacksCommon.LOGGER.warn("Empty server policy in {}; using all defaults. Fix the file and run /ss reload; config commands save the active defaults over this file", file);
                    return defaults;
                }
                return decode(root, defaults);
            } catch (Exception exception) {
                SomeStacksCommon.LOGGER.warn("Failed to read server policy {} at {}; using all defaults. Fix the file and run /ss reload; config commands save the active defaults over this file", file, readingKey, exception);
                return defaults;
            }
        }

        private Settings decode(JsonObject root, Settings defaults) {
            JsonObject piles = obj(root, ServerConfig.Keys.PILES);
            JsonObject stacks = obj(root, ServerConfig.Keys.STACKS);
            JsonObject compatibility = obj(root, ServerConfig.Keys.COMPATIBILITY);
            JsonObject gallery = obj(root, ServerConfig.Keys.RENDER_GALLERY);
            int height = intOr(piles, ServerConfig.Keys.PILES, ServerConfig.Keys.MAX_PILE_HEIGHT, defaults.maxPileHeight());
            int speed = intOr(gallery, ServerConfig.Keys.RENDER_GALLERY, ServerConfig.Keys.PLACEMENTS_PER_TICK, defaults.renderGalleryPlacementsPerTick());
            int permission = intOr(gallery, ServerConfig.Keys.RENDER_GALLERY, ServerConfig.Keys.REQUIRED_PERMISSION_LEVEL, defaults.galleryPermissionLevel());
            Settings decoded = new Settings(
                    height,
                    boolOr(stacks, ServerConfig.Keys.STACKS, ServerConfig.Keys.ENABLE_STORAGE_STACK_BLOCK, defaults.enableStorageStackBlock()),
                    boolOr(stacks, ServerConfig.Keys.STACKS, ServerConfig.Keys.ENABLE_SINGLES_STACK_BLOCK, defaults.enableSinglesStackBlock()),
                    boolOr(stacks, ServerConfig.Keys.STACKS, ServerConfig.Keys.ENABLE_BAR_STACK_BLOCK, defaults.enableBarStackBlock()),
                    stringListOr(compatibility, ServerConfig.Keys.COMPATIBILITY, ServerConfig.Keys.DISABLE_MODS, defaults.disableMods()),
                    stringListOr(compatibility, ServerConfig.Keys.COMPATIBILITY, ServerConfig.Keys.DISABLE_ITEMS, defaults.disableItems()),
                    speed,
                    boolOr(gallery, ServerConfig.Keys.RENDER_GALLERY, ServerConfig.Keys.ENABLED, defaults.galleryEnabled()),
                    permission,
                    stringListOr(gallery, ServerConfig.Keys.RENDER_GALLERY, ServerConfig.Keys.GEN_MODS, defaults.genMods()),
                    stringListOr(gallery, ServerConfig.Keys.RENDER_GALLERY, ServerConfig.Keys.GEN_ITEMS, defaults.genItems()));
            warnCorrection(ServerConfig.Keys.PILES + "." + ServerConfig.Keys.MAX_PILE_HEIGHT, height, decoded.maxPileHeight());
            warnCorrection(ServerConfig.Keys.RENDER_GALLERY + "." + ServerConfig.Keys.PLACEMENTS_PER_TICK, speed, decoded.renderGalleryPlacementsPerTick());
            warnCorrection(ServerConfig.Keys.RENDER_GALLERY + "." + ServerConfig.Keys.REQUIRED_PERMISSION_LEVEL, permission, decoded.galleryPermissionLevel());
            return decoded;
        }

        private void warnCorrection(String key, int value, int used) {
            if (value != used) {
                SomeStacksCommon.LOGGER.warn("Corrected {} in {} from {} to {}", key, file, value, used);
            }
        }

        boolean write(Settings values) {
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
                return true;
            } catch (IOException exception) {
                SomeStacksCommon.LOGGER.warn("Failed to write server policy {}", file, exception);
                return false;
            }
        }

        private JsonObject obj(JsonObject parent, String key) {
            readingKey = key;
            if (!parent.has(key)) return new JsonObject();
            if (parent.get(key).isJsonObject()) return parent.getAsJsonObject(key);
            SomeStacksCommon.LOGGER.warn("Invalid section {} in {}; using defaults for this section", key, file);
            return new JsonObject();
        }

        private int intOr(JsonObject obj, String section, String key, int fallback) {
            readingKey = section + "." + key;
            if (!obj.has(key)) return fallback;
            if (!obj.get(key).isJsonPrimitive()) {
                warnFallback(fallback);
                return fallback;
            }
            return obj.get(key).getAsInt();
        }

        private boolean boolOr(JsonObject obj, String section, String key, boolean fallback) {
            readingKey = section + "." + key;
            if (!obj.has(key)) return fallback;
            if (!obj.get(key).isJsonPrimitive()) {
                warnFallback(fallback);
                return fallback;
            }
            boolean value = obj.get(key).getAsBoolean();
            if (!obj.get(key).getAsJsonPrimitive().isBoolean()) {
                SomeStacksCommon.LOGGER.warn("Non-boolean value for {} in {}; interpreted as {}", readingKey, file, value);
            }
            return value;
        }

        private List<String> stringListOr(JsonObject obj, String section, String key, List<String> fallback) {
            String path = section + "." + key;
            readingKey = path;
            if (!obj.has(key)) return fallback;
            if (!obj.get(key).isJsonArray()) {
                warnFallback(fallback);
                return fallback;
            }
            List<String> out = new ArrayList<>();
            for (JsonElement entry : obj.getAsJsonArray(key)) {
                readingKey = path + "[" + out.size() + "]";
                out.add(entry.getAsString());
            }
            return out;
        }

        private void warnFallback(Object fallback) {
            SomeStacksCommon.LOGGER.warn("Invalid value for {} in {}; using default {}", readingKey, file, fallback);
        }

        private static JsonArray stringArray(List<String> values) {
            JsonArray array = new JsonArray();
            values.forEach(array::add);
            return array;
        }

    }

}
