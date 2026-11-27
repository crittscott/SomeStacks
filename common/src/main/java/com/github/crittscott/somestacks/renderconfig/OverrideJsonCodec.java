package com.github.crittscott.somestacks.renderconfig;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nullable;

/**
 * Parses and serializes the item render override schema shared by bundled resources, the client
 * override file, and the measured cache.
 *
 * <p>A document is a JSON object keyed by item id; each value contains optional {@code mode},
 * {@code scale}, and {@code offset} fields. Each field is decoded by its own codec, so a malformed
 * field is logged and skipped while the entry's valid fields are kept.
 */
public final class OverrideJsonCodec {
    /** Scale of an entry that names none, and of {@code ss item} given none. */
    public static final float DEFAULT_SCALE = 1.0f;

    private static final String FIELD_MODE = "mode";
    private static final String FIELD_OFFSET = "offset";
    private static final String FIELD_SCALE = "scale";

    /** Inclusive offset bounds in cell widths. */
    public static final float MAX_OFFSET = 1.0f;

    /** Inclusive scale bounds shared by authored overrides and automatic measurement. */
    public static final float MAX_SCALE = 20.0f;

    public static final float MIN_OFFSET = -1.0f;
    public static final float MIN_SCALE = 0.01f;

    private OverrideJsonCodec() {}

    /**
     * One parsed document. {@code entries} holds every usable entry, including the valid fields of
     * a partly malformed one. {@code malformed} holds, verbatim and under its original key, every
     * entry with a key, value, or field that could not be used, so that a writer of a player's own
     * file can put it back unchanged.
     */
    public record Document(
            Map<ResourceLocation, ItemRenderConfig> entries, Map<String, JsonElement> malformed) {}

    /** Whether a value is finite and within the inclusive bounds. */
    public static boolean inRange(float value, float min, float max) {
        return Float.isFinite(value) && value >= min && value <= max;
    }

    /**
     * Applies the file-format bounds to an override, dropping invalid fields. Serialization may
     * receive measured data rather than parsed data, so it validates through here.
     */
    public static ItemRenderConfig sanitize(ItemRenderConfig config) {
        Float scale = config.scale();
        if (scale != null && !inRange(scale, MIN_SCALE, MAX_SCALE)) {
            SomeStacksCommon.LOGGER.warn(
                    "Dropping out of range scale {} from a render override", scale);
            scale = null;
        }

        RenderOffset offset = config.offset();
        if (offset != null && !offset.isValid()) {
            SomeStacksCommon.LOGGER.warn("Dropping out of range offset from a render override");
            offset = null;
        }

        return new ItemRenderConfig(config.mode(), scale, offset);
    }

    /** Parses every entry, logging malformed entries and fields with the source and item key. */
    public static Document parse(JsonObject root, String sourceName) {
        Map<ResourceLocation, ItemRenderConfig> entries = new TreeMap<>();
        Map<String, JsonElement> malformed = new TreeMap<>();

        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            ResourceLocation itemId = ResourceLocation.tryParse(key);
            if (itemId == null || !value.isJsonObject()) {
                SomeStacksCommon.LOGGER.warn(
                        "Skipping override entry '{}' in {}: {}",
                        key,
                        sourceName,
                        itemId == null ? "the key is not an item id" : "expected an object");
                malformed.put(key, value);
                continue;
            }

            boolean clean = true;
            RenderMode mode = null;
            Float scale = null;
            RenderOffset offset = null;
            for (Map.Entry<String, JsonElement> field : value.getAsJsonObject().entrySet()) {
                switch (field.getKey()) {
                    case FIELD_MODE -> {
                        mode =
                                parseField(
                                        ItemRenderConfig.MODE_CODEC,
                                        field.getValue(),
                                        key,
                                        FIELD_MODE,
                                        sourceName);
                        clean &= mode != null;
                    }
                    case FIELD_SCALE -> {
                        scale =
                                parseField(
                                        ItemRenderConfig.SCALE_CODEC,
                                        field.getValue(),
                                        key,
                                        FIELD_SCALE,
                                        sourceName);
                        clean &= scale != null;
                    }
                    case FIELD_OFFSET -> {
                        offset =
                                parseField(
                                        RenderOffset.CODEC,
                                        field.getValue(),
                                        key,
                                        FIELD_OFFSET,
                                        sourceName);
                        clean &= offset != null;
                    }
                    default -> {
                        SomeStacksCommon.LOGGER.warn(
                                "Unknown field '{}' for item '{}' in {}",
                                field.getKey(),
                                key,
                                sourceName);
                        clean = false;
                    }
                }
            }

            if (mode != null || scale != null || offset != null) {
                entries.put(itemId, new ItemRenderConfig(mode, scale, offset));
            }
            if (!clean) {
                malformed.put(key, value);
            }
        }

        return new Document(entries, malformed);
    }

    /** Serializes entries sorted by item id for stable files. */
    public static JsonObject toJson(Map<ResourceLocation, ItemRenderConfig> map) {
        JsonObject root = new JsonObject();
        new TreeMap<>(map)
                .forEach((itemId, config) -> root.add(itemId.toString(), entryToJson(config)));
        return root;
    }

    /** Serializes one sanitized override entry, returning an empty object if encoding fails. */
    private static JsonObject entryToJson(ItemRenderConfig config) {
        JsonElement encoded =
                ItemRenderConfig.CODEC
                        .encodeStart(JsonOps.INSTANCE, sanitize(config))
                        .resultOrPartial(message ->
                                SomeStacksCommon.LOGGER.warn(
                                        "Failed to serialize render override: {}", message))
                        .orElseGet(JsonObject::new);
        return encoded.isJsonObject() ? encoded.getAsJsonObject() : new JsonObject();
    }

    /** The decoded field, or null after logging why it failed. A partial result is not used. */
    @Nullable
    private static <T> T parseField(
            Codec<T> codec, JsonElement value, String itemKey, String field, String sourceName) {
        DataResult<T> result = codec.parse(JsonOps.INSTANCE, value);
        result.resultOrPartial(message ->
                SomeStacksCommon.LOGGER.warn(
                        "Invalid {} for item '{}' in {}: {}", field, itemKey, sourceName, message));
        return result.result().orElse(null);
    }
}
