package com.github.crittscott.somestacks.client.renderconfig;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Parses and serializes the item render override schema shared by bundled resources, the client
 * override file, and the measured cache.
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
     * Inclusive scale bounds shared by authored overrides and automatic measurement.
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
        RenderOffset offset = null;

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
                    RenderOffset parsed = new RenderOffset(
                            offsetArray.get(0).getAsFloat(),
                            offsetArray.get(1).getAsFloat(),
                            offsetArray.get(2).getAsFloat()
                    );
                    if (inRange(parsed.x(), MIN_OFFSET, MAX_OFFSET)
                            && inRange(parsed.y(), MIN_OFFSET, MAX_OFFSET)
                            && inRange(parsed.z(), MIN_OFFSET, MAX_OFFSET)) {
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
            offsetArray.add(config.offset().x());
            offsetArray.add(config.offset().y());
            offsetArray.add(config.offset().z());
            entry.add(FIELD_OFFSET, offsetArray);
        }
        return entry;
    }
}
