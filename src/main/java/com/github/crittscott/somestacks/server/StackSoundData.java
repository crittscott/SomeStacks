package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.ModSounds;
import com.github.crittscott.somestacks.SomeStacks;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.TreeMap;

/**
 * Loads the sound each stack block plays on deposit and extract from
 * {@code data/<namespace>/somestacks_sounds/*.json} and publishes it to {@link ModSounds}.
 *
 * <p>This runs on the logical server, which is what plays and broadcasts the sounds. Every file
 * found contributes its block-type keys; where several files define the same key, the one whose
 * id sorts last wins. Names that do not resolve to a registered sound fall back to a vanilla
 * default, so the fields {@link ModSounds} exposes are always usable.
 */
public final class StackSoundData extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "somestacks_sounds";

    public StackSoundData() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(@Nonnull Map<ResourceLocation, JsonElement> files, @Nonnull ResourceManager resourceManager,
                         @Nonnull ProfilerFiller profiler) {
        JsonObject merged = new JsonObject();

        new TreeMap<>(files).forEach((id, element) -> {
            if (!element.isJsonObject()) {
                SomeStacks.LOGGER.warn("Ignoring sound definition {}: root is not a JSON object", id);
                return;
            }
            JsonObject root = element.getAsJsonObject();
            for (String key : root.keySet()) {
                merged.add(key, root.get(key));
            }
        });

        ModSounds.BAR_DEPOSIT = parseSound(merged, "bar_stack_block", "deposit", SoundEvents.WOOD_PLACE);
        ModSounds.BAR_EXTRACT = parseSound(merged, "bar_stack_block", "extract", SoundEvents.WOOL_BREAK);
        ModSounds.SINGLES_DEPOSIT = parseSound(merged, "singles_stack_block", "deposit", SoundEvents.WOOD_PLACE);
        ModSounds.SINGLES_EXTRACT = parseSound(merged, "singles_stack_block", "extract", SoundEvents.WOOL_BREAK);
        ModSounds.STORAGE_DEPOSIT = parseSound(merged, "storage_stack_block", "deposit", SoundEvents.WOOD_PLACE);
        ModSounds.STORAGE_EXTRACT = parseSound(merged, "storage_stack_block", "extract", SoundEvents.WOOL_BREAK);
    }

    private static SoundEvent parseSound(JsonObject root, String blockType, String action, SoundEvent fallback) {
        try {
            JsonObject blockObj = root.getAsJsonObject(blockType);
            if (blockObj == null || !blockObj.has(action)) {
                SomeStacks.LOGGER.warn("No {} sound defined for {}, using fallback", action, blockType);
                return fallback;
            }

            String soundName = blockObj.get(action).getAsString();
            ResourceLocation loc = ResourceLocation.tryParse(soundName);
            SoundEvent sound = loc == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(loc);
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
