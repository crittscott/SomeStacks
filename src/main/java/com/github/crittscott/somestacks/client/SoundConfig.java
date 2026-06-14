package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.ModSounds;
import com.github.crittscott.somestacks.SomeStacks;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class SoundConfig extends SimplePreparableReloadListener<JsonObject> {
    private static final Gson GSON = new Gson();

    @Override
    protected JsonObject prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        JsonObject merged = new JsonObject();

        var resources = resourceManager.listResources("sounds", loc -> loc.getPath().endsWith(".json"));

        resources.forEach((fileLocation, resource) -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);

                for (String key : root.keySet()) {
                    merged.add(key, root.get(key));
                }
            } catch (Exception e) {
                SomeStacks.LOGGER.warn("Failed to process sound config file {}: {}", fileLocation, e.getMessage());
            }
        });

        return merged;
    }

    @Override
    protected void apply(JsonObject data, ResourceManager resourceManager, ProfilerFiller profiler) {
        ModSounds.BAR_DEPOSIT = parseSound(data, "bar_stack_block", "deposit", SoundEvents.WOOD_PLACE);
        ModSounds.BAR_EXTRACT = parseSound(data, "bar_stack_block", "extract", SoundEvents.WOOL_BREAK);
        ModSounds.SINGLES_DEPOSIT = parseSound(data, "singles_stack_block", "deposit", SoundEvents.WOOD_PLACE);
        ModSounds.SINGLES_EXTRACT = parseSound(data, "singles_stack_block", "extract", SoundEvents.WOOL_BREAK);
        ModSounds.STORAGE_DEPOSIT = parseSound(data, "storage_stack_block", "deposit", SoundEvents.WOOD_PLACE);
        ModSounds.STORAGE_EXTRACT = parseSound(data, "storage_stack_block", "extract", SoundEvents.WOOL_BREAK);

        SomeStacks.LOGGER.info("Loaded sound configuration");
    }

    private static SoundEvent parseSound(JsonObject root, String blockType, String action, SoundEvent fallback) {
        try {
            JsonObject blockObj = root.getAsJsonObject(blockType);
            String soundName = blockObj.get(action).getAsString();
            ResourceLocation loc = new ResourceLocation(soundName);
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(loc);
            if (sound == null) {
                SomeStacks.LOGGER.warn("Unknown sound '{}', using fallback", soundName);
                return fallback;
            }
            return sound;
        } catch (Exception e) {
            SomeStacks.LOGGER.warn("Failed to parse sound for {}.{}: {}", blockType, action, e.getMessage());
            return fallback;
        }
    }
}
