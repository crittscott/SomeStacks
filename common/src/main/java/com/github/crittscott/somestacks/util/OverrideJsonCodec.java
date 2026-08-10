package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.SomeStacksCommon;
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
 * Parses and serializes the item render override schema shared by bundled resources, the client
 * override file, the measured cache, and the server override directory.
 *
 * <p>A document is a JSON object keyed by item id; each value contains optional {@code mode},
 * {@code scale}, and {@code offset} fields. Malformed entries and fields are logged and skipped.
 */
public final class OverrideJsonCodec {
    private static final String FIELD_MODE = "mode";
    private static final String FIELD_SCALE = "scale";
    private static final String FIELD_OFFSET = "offset";

    private OverrideJsonCodec() {}

    /**
     * Inclusive scale bounds shared by authored overrides, synchronized overrides, and automatic
     * measurement.
     */
    public static final float MIN_SCALE = 0.01f;
    public static final float MAX_SCALE = 20.0f;

    /** Inclusive offset bounds in cell widths. */
    public static final float MIN_OFFSET = -1.0f;
    public static final float MAX_OFFSET = 1.0f;

    /** Whether a value is finite and within the inclusive bounds. */
    public static boolean inRange(float value, float min, float max) {
        return Float.isFinite(value) && value >= min && value <= max;
    }

    /**
     * Applies the file-format bounds to a synchronized override, dropping invalid fields. Network
     * overrides bypass {@link #parse}, so they require the same validation at the packet boundary.
     */
    public static ItemRenderConfig sanitize(ItemRenderConfig config) {
        Float scale = config.scale();
        if (scale != null && !inRange(scale, MIN_SCALE, MAX_SCALE)) {
            SomeStacksCommon.LOGGER.warn("Dropping out of range scale {} from a synchronized override", scale);
            scale = null;
        }

        float[] offset = config.offset();
        if (offset != null
                && (offset.length != 3
                        || !inRange(offset[0], MIN_OFFSET, MAX_OFFSET)
                        || !inRange(offset[1], MIN_OFFSET, MAX_OFFSET)
                        || !inRange(offset[2], MIN_OFFSET, MAX_OFFSET))) {
            SomeStacksCommon.LOGGER.warn("Dropping out of range offset from a synchronized override");
            offset = null;
        }

        return new ItemRenderConfig(config.mode(), scale, offset);
    }

    /** Parses every valid item entry, logging and skipping malformed entries or fields. */
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
                SomeStacksCommon.LOGGER.warn("Skipping override entry '{}' in {}: {}",
                        entry.getKey(), sourceName, e.getMessage());
            }
        }

        return map;
    }

    /** Returns the parsed entry, or null if none of its fields are valid. */
    @Nullable
    public static ItemRenderConfig parseEntry(String itemKey, JsonObject json) {
        RenderMode mode = null;
        Float scale = null;
        float[] offset = null;

        if (json.has(FIELD_MODE)) {
            try {
                String modeString = json.get(FIELD_MODE).getAsString();
                mode = RenderMode.fromString(modeString);
                if (mode == null) {
                    SomeStacksCommon.LOGGER.warn("Invalid render mode '{}' for item '{}'", modeString, itemKey);
                }
            } catch (Exception e) {
                SomeStacksCommon.LOGGER.warn("Failed to parse mode for item '{}': {}", itemKey, e.getMessage());
            }
        }

        if (json.has(FIELD_SCALE)) {
            try {
                scale = json.get(FIELD_SCALE).getAsFloat();
                if (!inRange(scale, MIN_SCALE, MAX_SCALE)) {
                    SomeStacksCommon.LOGGER.warn("Invalid scale {} for item '{}', must be between {} and {}",
                            scale, itemKey, MIN_SCALE, MAX_SCALE);
                    scale = null;
                }
            } catch (Exception e) {
                SomeStacksCommon.LOGGER.warn("Failed to parse scale for item '{}': {}", itemKey, e.getMessage());
            }
        }

        if (json.has(FIELD_OFFSET)) {
            try {
                JsonArray offsetArray = json.getAsJsonArray(FIELD_OFFSET);
                if (offsetArray.size() != 3) {
                    SomeStacksCommon.LOGGER.warn("Invalid offset array size for item '{}', expected 3 elements", itemKey);
                } else {
                    float[] parsed = new float[]{
                            offsetArray.get(0).getAsFloat(),
                            offsetArray.get(1).getAsFloat(),
                            offsetArray.get(2).getAsFloat()
                    };
                    if (inRange(parsed[0], MIN_OFFSET, MAX_OFFSET)
                            && inRange(parsed[1], MIN_OFFSET, MAX_OFFSET)
                            && inRange(parsed[2], MIN_OFFSET, MAX_OFFSET)) {
                        offset = parsed;
                    } else {
                        SomeStacksCommon.LOGGER.warn("Out of range offset for item '{}', each component must be between {} and {}; ignoring it",
                                itemKey, MIN_OFFSET, MAX_OFFSET);
                    }
                }
            } catch (Exception e) {
                SomeStacksCommon.LOGGER.warn("Failed to parse offset for item '{}': {}", itemKey, e.getMessage());
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
            entry.addProperty(FIELD_MODE, config.mode().getId());
        }
        if (config.scale() != null) {
            entry.addProperty(FIELD_SCALE, config.scale());
        }
        if (config.offset() != null) {
            JsonArray offsetArray = new JsonArray();
            for (float component : config.offset()) {
                offsetArray.add(component);
            }
            entry.add(FIELD_OFFSET, offsetArray);
        }
        return entry;
    }
}
