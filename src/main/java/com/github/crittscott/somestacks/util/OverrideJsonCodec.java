package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.client.ItemRenderConfig;
import com.github.crittscott.somestacks.client.RenderMode;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.TreeMap;

/**
 * Parses and serializes the item render override schema used everywhere overrides are
 * stored: the bundled {@code item_render_overrides} resources, the client's user
 * override file, the client's measured cache, and the server's admin override folder.
 *
 * <p>A document is a JSON object keyed by item id; each value holds optional
 * {@code mode}, {@code scale}, and {@code offset} fields. Malformed entries and fields
 * are logged and skipped.
 */
public final class OverrideJsonCodec {
    private OverrideJsonCodec() {}

    public static Map<ResourceLocation, ItemRenderConfig> parse(JsonObject root, String sourceName) {
        Map<ResourceLocation, ItemRenderConfig> map = new TreeMap<>();

        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            try {
                ResourceLocation itemId = new ResourceLocation(entry.getKey());
                ItemRenderConfig config = parseEntry(entry.getKey(), entry.getValue().getAsJsonObject());
                if (config != null) {
                    map.put(itemId, config);
                }
            } catch (Exception e) {
                SomeStacks.LOGGER.warn("Skipping override entry '{}' in {}: {}",
                        entry.getKey(), sourceName, e.getMessage());
            }
        }

        return map;
    }

    /**
     * @return the parsed entry, or null when no field parsed successfully.
     */
    @Nullable
    public static ItemRenderConfig parseEntry(String itemKey, JsonObject json) {
        RenderMode mode = null;
        Float scale = null;
        float[] offset = null;

        if (json.has("mode")) {
            try {
                String modeString = json.get("mode").getAsString();
                mode = RenderMode.fromString(modeString);
                if (mode == null) {
                    SomeStacks.LOGGER.warn("Invalid render mode '{}' for item '{}'", modeString, itemKey);
                }
            } catch (Exception e) {
                SomeStacks.LOGGER.warn("Failed to parse mode for item '{}': {}", itemKey, e.getMessage());
            }
        }

        if (json.has("scale")) {
            try {
                scale = json.get("scale").getAsFloat();
                if (scale <= 0) {
                    SomeStacks.LOGGER.warn("Invalid scale {} for item '{}', must be positive", scale, itemKey);
                    scale = null;
                }
            } catch (Exception e) {
                SomeStacks.LOGGER.warn("Failed to parse scale for item '{}': {}", itemKey, e.getMessage());
            }
        }

        if (json.has("offset")) {
            try {
                JsonArray offsetArray = json.getAsJsonArray("offset");
                if (offsetArray.size() == 3) {
                    offset = new float[]{
                            offsetArray.get(0).getAsFloat(),
                            offsetArray.get(1).getAsFloat(),
                            offsetArray.get(2).getAsFloat()
                    };
                } else {
                    SomeStacks.LOGGER.warn("Invalid offset array size for item '{}', expected 3 elements", itemKey);
                }
            } catch (Exception e) {
                SomeStacks.LOGGER.warn("Failed to parse offset for item '{}': {}", itemKey, e.getMessage());
            }
        }

        if (mode == null && scale == null && offset == null) {
            return null;
        }
        return new ItemRenderConfig(mode, scale, offset);
    }

    /** Serializes entries sorted by item id for stable files. */
    public static JsonObject toJson(Map<ResourceLocation, ItemRenderConfig> map) {
        JsonObject root = new JsonObject();
        new TreeMap<>(map).forEach((itemId, config) -> root.add(itemId.toString(), entryToJson(config)));
        return root;
    }

    public static JsonObject entryToJson(ItemRenderConfig config) {
        JsonObject entry = new JsonObject();
        if (config.mode() != null) {
            entry.addProperty("mode", config.mode().getId());
        }
        if (config.scale() != null) {
            entry.addProperty("scale", config.scale());
        }
        if (config.offset() != null) {
            JsonArray offsetArray = new JsonArray();
            for (float component : config.offset()) {
                offsetArray.add(component);
            }
            entry.add("offset", offsetArray);
        }
        return entry;
    }
}
