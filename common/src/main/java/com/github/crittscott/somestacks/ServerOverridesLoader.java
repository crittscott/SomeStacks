package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.renderconfig.OverrideJsonCodec;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads the server's admin render overrides from {@code config/somestacks/server_item_overrides}.
 * Files use the same JSON schema as everywhere else, so an admin can drop in a
 * client-written override file verbatim. The folder is read at server start and on explicit
 * reload; later files (by name order) win on duplicate items.
 */
public final class ServerOverridesLoader {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Path DIR = PlatformServices.modConfigFolder().resolve("server_item_overrides");
    private static Map<ResourceLocation, ItemRenderConfig> overrides = Map.of();

    private ServerOverridesLoader() {}

    /** Replaces the cached snapshot from the files currently in the override directory. */
    public static void reload() {
        Map<ResourceLocation, ItemRenderConfig> map = new HashMap<>();

        try {
            Files.createDirectories(DIR);
        } catch (IOException e) {
            SomeStacksCommon.LOGGER.warn("Failed to create {}: {}", DIR, e.getMessage());
            overrides = Map.of();
            return;
        }

        List<Path> jsonFiles;
        try (Stream<Path> files = Files.list(DIR)) {
            jsonFiles = files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            SomeStacksCommon.LOGGER.warn("Failed to list {}: {}", DIR, e.getMessage());
            overrides = Map.of();
            return;
        }

        for (Path path : jsonFiles) {
            try {
                JsonObject json = GSON.fromJson(Files.readString(path), JsonObject.class);
                if (json != null) {
                    map.putAll(OverrideJsonCodec.parse(json, path.toString()));
                }
            } catch (Exception e) {
                SomeStacksCommon.LOGGER.warn("Failed to read {}: {}", path, e.getMessage());
            }
        }

        overrides = Map.copyOf(map);
        SomeStacksCommon.LOGGER.info("Loaded {} server render override(s) from {} file(s)",
                overrides.size(), jsonFiles.size());
    }

    /** The immutable snapshot most recently loaded at server start or by {@code /ss reload}. */
    public static Map<ResourceLocation, ItemRenderConfig> current() {
        return overrides;
    }
}
