package com.github.crittscott.somestacks.server;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads the sound each stack block plays for each of its actions from
 * {@code data/<namespace>/somestacks_sounds/*.json} and publishes it to {@link StackSounds}.
 *
 * <p>This runs on the logical server, which is what plays and broadcasts the sounds. Files layer
 * one action at a time, so a file naming a single action leaves the rest of that block's actions
 * standing. The mod's own namespace is the base layer and every other namespace applies over it;
 * among those, the one whose id sorts last wins. Names that do not resolve to a registered sound
 * fall back to a vanilla default, so the fields {@link StackSounds} exposes are always usable.
 *
 * <p>Actions are per block type rather than uniform: only the types a rotation gesture reaches
 * carry rotation sounds.
 */
public final class StackSoundData extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "somestacks_sounds";

    private static final String DEPOSIT = "deposit";
    private static final String EXTRACT = "extract";
    private static final String ROTATE = "rotate";
    private static final String ROTATE_ITEM = "rotate_item";

    private static final String STORAGE = "storage_stack_block";
    private static final String SINGLES = "singles_stack_block";
    private static final String BAR = "bar_stack_block";

    /** Supported sound actions by block type; other actions in data files are rejected. */
    private static final Map<String, Set<String>> ACTIONS = Map.of(
            STORAGE, Set.of(DEPOSIT, EXTRACT, ROTATE),
            SINGLES, Set.of(DEPOSIT, EXTRACT, ROTATE, ROTATE_ITEM),
            BAR, Set.of(DEPOSIT, EXTRACT));

    public StackSoundData() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(@Nonnull Map<ResourceLocation, JsonElement> files, @Nonnull ResourceManager resourceManager,
                         @Nonnull ProfilerFiller profiler) {
        JsonObject merged = merge(files);

        StackSounds.BAR_DEPOSIT = parseSound(merged, BAR, DEPOSIT, SoundEvents.WOOD_PLACE);
        StackSounds.BAR_EXTRACT = parseSound(merged, BAR, EXTRACT, SoundEvents.WOOD_BREAK);
        StackSounds.SINGLES_DEPOSIT = parseSound(merged, SINGLES, DEPOSIT, SoundEvents.WOOD_PLACE);
        StackSounds.SINGLES_EXTRACT = parseSound(merged, SINGLES, EXTRACT, SoundEvents.WOOD_BREAK);
        StackSounds.SINGLES_ROTATE = parseSound(merged, SINGLES, ROTATE, SoundEvents.WOOD_HIT);
        StackSounds.SINGLES_ROTATE_ITEM = parseSound(merged, SINGLES, ROTATE_ITEM, SoundEvents.WOOD_HIT);
        StackSounds.STORAGE_DEPOSIT = parseSound(merged, STORAGE, DEPOSIT, SoundEvents.WOOD_PLACE);
        StackSounds.STORAGE_EXTRACT = parseSound(merged, STORAGE, EXTRACT, SoundEvents.WOOD_BREAK);
        StackSounds.STORAGE_ROTATE = parseSound(merged, STORAGE, ROTATE, SoundEvents.WOOD_HIT);
    }

    /**
     * Layers every file into one block-type-to-action object.
     *
     * <p>The reload listener has already settled which pack supplies each id, but not how ids stand
     * against one another. The mod's own namespace carries the bundled defaults and so goes down
     * first; the rest follow in id order, and each one overwrites only the actions it names.
     */
    private static JsonObject merge(Map<ResourceLocation, JsonElement> files) {
        List<Map.Entry<ResourceLocation, JsonElement>> ordered = new ArrayList<>(files.entrySet());
        ordered.sort(Comparator
                .comparing((Map.Entry<ResourceLocation, JsonElement> e) ->
                        !e.getKey().getNamespace().equals(SomeStacks.MODID))
                .thenComparing(e -> e.getKey().toString()));

        JsonObject merged = new JsonObject();
        for (Map.Entry<ResourceLocation, JsonElement> entry : ordered) {
            ResourceLocation id = entry.getKey();
            if (!entry.getValue().isJsonObject()) {
                SomeStacks.LOGGER.warn("Ignoring sound definition {}: root is not a JSON object", id);
                continue;
            }

            JsonObject root = entry.getValue().getAsJsonObject();
            for (String blockType : root.keySet()) {
                Set<String> actions = ACTIONS.get(blockType);
                if (actions == null) {
                    SomeStacks.LOGGER.warn("Ignoring unknown block type '{}' in sound definition {}", blockType, id);
                    continue;
                }
                if (!root.get(blockType).isJsonObject()) {
                    SomeStacks.LOGGER.warn("Ignoring '{}' in sound definition {}: not a JSON object", blockType, id);
                    continue;
                }

                JsonObject blockObj = root.getAsJsonObject(blockType);
                JsonObject target = merged.has(blockType) ? merged.getAsJsonObject(blockType) : new JsonObject();
                for (String action : blockObj.keySet()) {
                    if (!actions.contains(action)) {
                        SomeStacks.LOGGER.warn("Ignoring unknown action '{}.{}' in sound definition {}",
                                blockType, action, id);
                        continue;
                    }
                    target.add(action, blockObj.get(action));
                }
                merged.add(blockType, target);
            }
        }
        return merged;
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
