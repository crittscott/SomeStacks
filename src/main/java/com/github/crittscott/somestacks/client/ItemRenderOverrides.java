package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacks;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ItemRenderOverrides extends SimplePreparableReloadListener<Map<ResourceLocation, ItemRenderOverrides.ItemRenderConfig>> {
    private static final Gson GSON = new GsonBuilder().create();
    public static final Map<ResourceLocation, ItemRenderConfig> CONFIG_MAP = new HashMap<>();
    public static final Map<ResourceLocation, ItemRenderConfig> SERVER_OVERRIDES = new HashMap<>();

    @Override
    protected Map<ResourceLocation, ItemRenderConfig> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, ItemRenderConfig> configMap = new HashMap<>();

        var resources = resourceManager.listResources("item_render_overrides", loc -> loc.getPath().endsWith(".json"));

        resources.forEach((fileLocation, resource) -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);

                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    try {
                        ResourceLocation itemId = new ResourceLocation(entry.getKey());
                        JsonObject configJson = entry.getValue().getAsJsonObject();

                        RenderMode mode = null;
                        Float scale = null;
                        float[] offset = null;

                        // Parse mode (optional)
                        if (configJson.has("mode")) {
                            try {
                                String modeString = configJson.get("mode").getAsString();
                                mode = RenderMode.fromString(modeString);
                                if (mode == null) {
                                    SomeStacks.LOGGER.warn("Invalid render mode '{}' for item '{}'", modeString, entry.getKey());
                                }
                            } catch (Exception e) {
                                SomeStacks.LOGGER.warn("Failed to parse mode for item '{}': {}", entry.getKey(), e.getMessage());
                            }
                        }

                        // Parse scale (optional)
                        if (configJson.has("scale")) {
                            try {
                                scale = configJson.get("scale").getAsFloat();
                                if (scale <= 0) {
                                    SomeStacks.LOGGER.warn("Invalid scale {} for item '{}', must be positive", scale, entry.getKey());
                                    scale = null;
                                }
                            } catch (Exception e) {
                                SomeStacks.LOGGER.warn("Failed to parse scale for item '{}': {}", entry.getKey(), e.getMessage());
                            }
                        }

                        // Parse offset (optional)
                        if (configJson.has("offset")) {
                            try {
                                JsonArray offsetArray = configJson.getAsJsonArray("offset");
                                if (offsetArray.size() == 3) {
                                    offset = new float[]{
                                            offsetArray.get(0).getAsFloat(),
                                            offsetArray.get(1).getAsFloat(),
                                            offsetArray.get(2).getAsFloat()
                                    };
                                } else {
                                    SomeStacks.LOGGER.warn("Invalid offset array size for item '{}', expected 3 elements", entry.getKey());
                                }
                            } catch (Exception e) {
                                SomeStacks.LOGGER.warn("Failed to parse offset for item '{}': {}", entry.getKey(), e.getMessage());
                            }
                        }

                        // Only add to map if at least one field was successfully parsed
                        if (mode != null || scale != null || offset != null) {
                            ItemRenderConfig config = new ItemRenderConfig(mode, scale, offset);
                            configMap.put(itemId, config);
                        }
                    } catch (Exception e) {
                        SomeStacks.LOGGER.warn("Failed to parse entry '{}': {}", entry.getKey(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                SomeStacks.LOGGER.warn("Failed to process file {}: {}", fileLocation, e.getMessage());
            }
        });

        return configMap;
    }

    @Override
    protected void apply(Map<ResourceLocation, ItemRenderConfig> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        CONFIG_MAP.clear();
        CONFIG_MAP.putAll(prepared);
    }

    public static void setSyncedServerOverrides(Map<ResourceLocation, ItemRenderConfig> overrides) {
        SERVER_OVERRIDES.clear();
        SERVER_OVERRIDES.putAll(overrides);
    }

    @Nullable
    public static RenderMode getMode(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());

        // Check server overrides first (highest priority)
        ItemRenderConfig serverConfig = SERVER_OVERRIDES.get(itemId);
        if (serverConfig != null && serverConfig.mode() != null) {
            return serverConfig.mode();
        }

        // Check JSON overrides second
        ItemRenderConfig config = CONFIG_MAP.get(itemId);
        return config != null ? config.mode() : null;
    }

    public static float getScale(ItemStack stack) {
        if (stack.isEmpty()) {
            return 1.0f;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());

        // Check server overrides first (highest priority)
        ItemRenderConfig serverConfig = SERVER_OVERRIDES.get(itemId);
        if (serverConfig != null && serverConfig.scale() != null) {
            return serverConfig.scale();
        }

        // Check JSON overrides second
        ItemRenderConfig config = CONFIG_MAP.get(itemId);
        return (config != null && config.scale() != null) ? config.scale() : 1.0f;
    }

    public static float[] getOffset(ItemStack stack) {
        if (stack.isEmpty()) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());

        // Check server overrides first (highest priority)
        ItemRenderConfig serverConfig = SERVER_OVERRIDES.get(itemId);
        if (serverConfig != null && serverConfig.offset() != null) {
            return serverConfig.offset();
        }

        // Check JSON overrides second
        ItemRenderConfig config = CONFIG_MAP.get(itemId);
        return (config != null && config.offset() != null) ? config.offset() : new float[]{0.0f, 0.0f, 0.0f};
    }

    public record ItemRenderConfig(
            @Nullable RenderMode mode,
            @Nullable Float scale,
            @Nullable float[] offset
    ) {}
}
