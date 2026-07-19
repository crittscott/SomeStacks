package com.github.crittscott.somestacks.client.measure;

import com.github.crittscott.somestacks.client.ItemRenderOverrides;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Values the author forced this session via {@code /somestacks item}. They outrank
 * guessed profiles on auto-rendered blocks and in dumped override files, so an
 * auto-rendered stack always previews exactly what a dump would ship.
 */
public final class SessionCorrections {
    private static final Map<ResourceLocation, ItemRenderOverrides.ItemRenderConfig> CORRECTIONS = new HashMap<>();

    private SessionCorrections() {}

    public static void put(ResourceLocation itemId, ItemRenderOverrides.ItemRenderConfig config) {
        CORRECTIONS.put(itemId, config);
    }

    @Nullable
    public static ItemRenderOverrides.ItemRenderConfig get(ResourceLocation itemId) {
        return CORRECTIONS.get(itemId);
    }

    public static Map<ResourceLocation, ItemRenderOverrides.ItemRenderConfig> all() {
        return Collections.unmodifiableMap(CORRECTIONS);
    }

    public static int size() {
        return CORRECTIONS.size();
    }
}
