package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.client.ItemRenderConfig;
import com.github.crittscott.somestacks.util.OverrideJsonCodec;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads the server's admin render overrides from {@code config/somestacks/server_item_overrides}.
 * Files use the same JSON schema as everywhere else, so an admin can drop in a
 * client-written override file verbatim. The folder is read fresh at every sync point;
 * later files (by name order) win on duplicate items.
 */
public final class ServerOverridesLoader {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Path DIR = FMLPaths.CONFIGDIR.get().resolve("somestacks/server_item_overrides");

    private ServerOverridesLoader() {}

    public static Map<ResourceLocation, ItemRenderConfig> load() {
        Map<ResourceLocation, ItemRenderConfig> map = new HashMap<>();

        try {
            Files.createDirectories(DIR);
        } catch (IOException e) {
            SomeStacks.LOGGER.warn("Failed to create {}: {}", DIR, e.getMessage());
            return map;
        }

        try (Stream<Path> files = Files.list(DIR)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            JsonObject json = GSON.fromJson(Files.readString(path), JsonObject.class);
                            if (json != null) {
                                map.putAll(OverrideJsonCodec.parse(json, path.toString()));
                            }
                        } catch (Exception e) {
                            SomeStacks.LOGGER.warn("Failed to read {}: {}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            SomeStacks.LOGGER.warn("Failed to list {}: {}", DIR, e.getMessage());
        }

        return map;
    }
}
