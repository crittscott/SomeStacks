package com.github.crittscott.somestacks.client.renderconfig;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nullable;

/**
 * Parses and serializes the item render override schema shared by bundled resources, the client
 * override file, and the measured cache.
 *
 * <p>A document is a JSON object keyed by item id; each value contains optional {@code mode},
 * {@code scale}, and {@code offset} fields. Malformed entries and fields are logged and skipped.
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

    /** An offset is one value per axis. */
    private static final int OFFSET_COMPONENTS = 3;

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
                        mode = parseMode(field.getValue(), key, sourceName);
                        clean &= mode != null;
                    }
                    case FIELD_SCALE -> {
                        scale = parseScale(field.getValue(), key, sourceName);
                        clean &= scale != null;
                    }
                    case FIELD_OFFSET -> {
                        offset = parseOffset(field.getValue(), key, sourceName);
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

    private static JsonObject entryToJson(ItemRenderConfig config) {
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

    /** A number, or a string holding one, as a float; null for anything else. */
    @Nullable
    private static Float floatValue(JsonElement element) {
        if (!element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber() && !primitive.isString()) {
            return null;
        }
        try {
            return primitive.getAsFloat();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private static RenderMode parseMode(JsonElement element, String itemKey, String sourceName) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            SomeStacksCommon.LOGGER.warn(
                    "Mode for item '{}' in {} must be a string", itemKey, sourceName);
            return null;
        }
        RenderMode mode = RenderMode.fromString(element.getAsString());
        if (mode == null) {
            SomeStacksCommon.LOGGER.warn(
                    "Invalid render mode '{}' for item '{}' in {}",
                    element.getAsString(),
                    itemKey,
                    sourceName);
        }
        return mode;
    }

    @Nullable
    private static RenderOffset parseOffset(
            JsonElement element, String itemKey, String sourceName) {
        if (!element.isJsonArray() || element.getAsJsonArray().size() != OFFSET_COMPONENTS) {
            SomeStacksCommon.LOGGER.warn(
                    "Offset for item '{}' in {} must be an array of {} numbers",
                    itemKey,
                    sourceName,
                    OFFSET_COMPONENTS);
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        Float x = floatValue(array.get(0));
        Float y = floatValue(array.get(1));
        Float z = floatValue(array.get(2));
        if (x == null || y == null || z == null) {
            SomeStacksCommon.LOGGER.warn(
                    "Offset for item '{}' in {} must be an array of {} numbers",
                    itemKey,
                    sourceName,
                    OFFSET_COMPONENTS);
            return null;
        }
        if (!inRange(x, MIN_OFFSET, MAX_OFFSET)
                || !inRange(y, MIN_OFFSET, MAX_OFFSET)
                || !inRange(z, MIN_OFFSET, MAX_OFFSET)) {
            SomeStacksCommon.LOGGER.warn(
                    "Out of range offset for item '{}' in {}, each component must be between {} and {}; ignoring it",
                    itemKey,
                    sourceName,
                    MIN_OFFSET,
                    MAX_OFFSET);
            return null;
        }
        return new RenderOffset(x, y, z);
    }

    @Nullable
    private static Float parseScale(JsonElement element, String itemKey, String sourceName) {
        Float scale = floatValue(element);
        if (scale == null) {
            SomeStacksCommon.LOGGER.warn(
                    "Scale for item '{}' in {} must be a number", itemKey, sourceName);
            return null;
        }
        if (!inRange(scale, MIN_SCALE, MAX_SCALE)) {
            SomeStacksCommon.LOGGER.warn(
                    "Invalid scale {} for item '{}' in {}, must be between {} and {}",
                    scale,
                    itemKey,
                    sourceName,
                    MIN_SCALE,
                    MAX_SCALE);
            return null;
        }
        return scale;
    }
}
