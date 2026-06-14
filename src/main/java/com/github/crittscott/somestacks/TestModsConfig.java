package com.github.crittscott.somestacks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TestModsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("somestacks/testmods.json");

    private TestModsConfig() {}

    public static List<String> loadModList() {
        if (!Files.exists(CONFIG_PATH)) {
            createEmptyConfig();
            return new ArrayList<>();
        }

        try {
            String json = Files.readString(CONFIG_PATH);
            JsonArray jsonArray = GSON.fromJson(json, JsonArray.class);

            if (jsonArray == null) {
                SomeStacks.LOGGER.error("Failed to parse testmods.json - invalid JSON");
                return new ArrayList<>();
            }

            List<String> modIds = new ArrayList<>();
            for (JsonElement element : jsonArray) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    modIds.add(element.getAsString());
                }
            }

            return modIds;
        } catch (IOException e) {
            SomeStacks.LOGGER.error("Failed to read testmods.json", e);
            return new ArrayList<>();
        } catch (Exception e) {
            SomeStacks.LOGGER.error("Failed to parse testmods.json", e);
            return new ArrayList<>();
        }
    }

    public static void saveModList(List<String> modIds) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            JsonArray jsonArray = new JsonArray();
            for (String modId : modIds) {
                jsonArray.add(modId);
            }

            String json = GSON.toJson(jsonArray);
            Files.writeString(CONFIG_PATH, json);
        } catch (IOException e) {
            SomeStacks.LOGGER.error("Failed to write testmods.json", e);
        }
    }

    private static void createEmptyConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, "[]");
        } catch (IOException e) {
            SomeStacks.LOGGER.error("Failed to create testmods.json", e);
        }
    }
}
