package com.github.crittscott.somestacks.renderconfig;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
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
     * Applies the file-format bounds to an override, dropping invalid fields. Network overrides
     * bypass {@link #parse}, and serialization may receive measured data, so both use the same
     * validation.
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
                ResourceLocation itemId = ResourceLocation.parse(entry.getKey());
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
        RenderMode mode = json.has(FIELD_MODE)
                ? parseField(ItemRenderConfig.MODE_CODEC, json.get(FIELD_MODE), itemKey, FIELD_MODE)
                : null;
        Float scale = json.has(FIELD_SCALE)
                ? parseField(ItemRenderConfig.SCALE_CODEC, json.get(FIELD_SCALE), itemKey, FIELD_SCALE)
                : null;
        float[] offset = json.has(FIELD_OFFSET)
                ? parseField(ItemRenderConfig.OFFSET_CODEC, json.get(FIELD_OFFSET), itemKey, FIELD_OFFSET)
                : null;

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
        JsonElement encoded = ItemRenderConfig.CODEC.encodeStart(
                        JsonOps.INSTANCE, sanitize(config))
                .resultOrPartial(message -> SomeStacksCommon.LOGGER.warn(
                        "Failed to serialize render override: {}", message))
                .orElseGet(JsonObject::new);
        return encoded.isJsonObject() ? encoded.getAsJsonObject() : new JsonObject();
    }

    @Nullable
    private static <T> T parseField(
            Codec<T> codec, JsonElement value, String itemKey, String field) {
        return codec.parse(JsonOps.INSTANCE, value)
                .resultOrPartial(message -> SomeStacksCommon.LOGGER.warn(
                        "Invalid {} for item '{}': {}", field, itemKey, message))
                .orElse(null);
    }
}
