package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacks;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class BarTextureStore extends SimplePreparableReloadListener<Map<ResourceLocation, BarTextureStore.BarTextureData>> {
    private static final Gson GSON = new Gson();
    private static final Map<ResourceLocation, BarTextureData> textureMap = new HashMap<>();
    /** Auto-tints for items no mapping covers, computed on first render and held until the next reload. */
    private static final Map<ResourceLocation, BarTextureData> unmappedTints = new HashMap<>();
    private static final ResourceLocation DEFAULT_TEXTURE = new ResourceLocation("somestacks", "block/minecraft/base_ingot");
    private static final BarTextureData FALLBACK = new BarTextureData(DEFAULT_TEXTURE);
    private static final int AUTO_TINT_MARKER = -1;
    private static final float BRIGHTEN_FACTOR = 0.1f;

    public record BarTextureData(ResourceLocation texture, int color) {
        public static final int WHITE = 0xFFFFFFFF;

        public BarTextureData(ResourceLocation texture) {
            this(texture, WHITE);
        }

        public int red() {
            return (color >> 16) & 0xFF;
        }

        public int green() {
            return (color >> 8) & 0xFF;
        }

        public int blue() {
            return color & 0xFF;
        }

        public int alpha() {
            return (color >> 24) & 0xFF;
        }

        public boolean needsAutoTint() {
            return color == AUTO_TINT_MARKER;
        }
    }

    @Override
    protected Map<ResourceLocation, BarTextureData> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, BarTextureData> configMap = new HashMap<>();

        var resources = resourceManager.listResources("textures/bars", loc -> loc.getPath().endsWith(".json"));

        // Read in file order so that two files mapping one item resolve the same way every reload.
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(fileEntry -> {
            ResourceLocation fileLocation = fileEntry.getKey();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(fileEntry.getValue().open(), StandardCharsets.UTF_8))) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);

                for (String key : root.keySet()) {
                    if (key.startsWith("_comment")) continue;

                    try {
                        ResourceLocation itemLoc = new ResourceLocation(key);
                        JsonElement value = root.get(key);

                        BarTextureData data;
                        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                            // Simple string format: texture specified, needs auto-tint
                            ResourceLocation textureLoc = new ResourceLocation(value.getAsString());
                            data = new BarTextureData(textureLoc, AUTO_TINT_MARKER);
                        } else if (value.isJsonObject()) {
                            JsonObject obj = value.getAsJsonObject();

                            // Determine texture
                            ResourceLocation textureLoc = obj.has("texture")
                                    ? new ResourceLocation(obj.get("texture").getAsString())
                                    : DEFAULT_TEXTURE;

                            // Determine tint
                            int color;
                            if (obj.has("tint")) {
                                String tintStr = obj.get("tint").getAsString();
                                color = parseColor(tintStr);
                            } else {
                                color = AUTO_TINT_MARKER;
                            }

                            data = new BarTextureData(textureLoc, color);
                        } else {
                            SomeStacks.LOGGER.warn("Invalid format for '{}': expected string or object", key);
                            continue;
                        }

                        configMap.put(itemLoc, data);
                    } catch (Exception e) {
                        SomeStacks.LOGGER.warn("Invalid mapping for '{}': {}", key, e.getMessage());
                    }
                }
            } catch (Exception e) {
                SomeStacks.LOGGER.warn("Failed to process file {}: {}", fileLocation, e.getMessage());
            }
        });

        SomeStacks.LOGGER.debug("Read {} bar texture mappings", configMap.size());
        return configMap;
    }

    @Override
    protected void apply(Map<ResourceLocation, BarTextureData> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        textureMap.clear();
        unmappedTints.clear();
        int autoTinted = 0;

        for (Map.Entry<ResourceLocation, BarTextureData> entry : prepared.entrySet()) {
            ResourceLocation itemLoc = entry.getKey();
            BarTextureData data = entry.getValue();

            if (data.needsAutoTint()) {
                data = new BarTextureData(data.texture(), calculateTintFromItemTexture(itemLoc, resourceManager));
                autoTinted++;
            }

            textureMap.put(itemLoc, data);
            SomeStacks.LOGGER.debug("  {} -> texture: {}, tint: #{}",
                    itemLoc, data.texture(), String.format("%08X", data.color()));
        }

        SomeStacks.LOGGER.info("Applied {} bar texture mappings ({} auto-tinted)",
                textureMap.size(), autoTinted);
    }

    private static int calculateTintFromItemTexture(ResourceLocation itemLoc, ResourceManager resourceManager) {
        SomeStacks.LOGGER.debug("Auto-calculating tint for {}", itemLoc);
        try {
            Item item = ForgeRegistries.ITEMS.getValue(itemLoc);
            if (item == null) {
                SomeStacks.LOGGER.debug("  Item not found in registry, returning WHITE");
                return BarTextureData.WHITE;
            }
            SomeStacks.LOGGER.debug("  Found item: {}", item.getClass().getSimpleName());

            ItemStack stack = new ItemStack(item);
            SomeStacks.LOGGER.debug("  Created ItemStack: {}", stack);

            Minecraft mc = Minecraft.getInstance();
            BakedModel model = mc.getItemRenderer().getModel(stack, null, null, 0);
            SomeStacks.LOGGER.debug("  Got BakedModel: {}", model.getClass().getSimpleName());

            TextureAtlasSprite sprite = model.getParticleIcon();
            if (sprite == null) {
                SomeStacks.LOGGER.debug("  Particle icon is null, returning WHITE");
                return BarTextureData.WHITE;
            }
            SomeStacks.LOGGER.debug("  Got sprite: {}", sprite);

            ResourceLocation spriteName = sprite.contents().name();
            SomeStacks.LOGGER.debug("  Sprite name: {}", spriteName);

            ResourceLocation texturePath = getTextureResourceLocation(spriteName);
            SomeStacks.LOGGER.debug("  Texture path: {}", texturePath);

            NativeImage image = loadTextureImage(texturePath, resourceManager);
            if (image == null) {
                SomeStacks.LOGGER.debug("  Failed to load texture image, returning WHITE");
                return BarTextureData.WHITE;
            }

            try {
                int tint = analyzePixelsForTint(image);
                SomeStacks.LOGGER.debug("  Calculated tint: #{}", String.format("%08X", tint));
                return tint;
            } finally {
                image.close();
            }
        } catch (Exception e) {
            SomeStacks.LOGGER.warn("Could not auto-calculate tint for {}: {}", itemLoc, e.getMessage(), e);
            return BarTextureData.WHITE;
        }
    }

    private static ResourceLocation getTextureResourceLocation(ResourceLocation spriteName) {
        String namespace = spriteName.getNamespace();
        String path = spriteName.getPath();
        return new ResourceLocation(namespace, "textures/" + path + ".png");
    }

    private static NativeImage loadTextureImage(ResourceLocation texturePath, ResourceManager resourceManager) {
        try {
            var resource = resourceManager.getResource(texturePath);
            if (resource.isEmpty()) {
                SomeStacks.LOGGER.debug("  Texture resource not found: {}", texturePath);
                return null;
            }

            try (var inputStream = resource.get().open()) {
                NativeImage image = NativeImage.read(inputStream);
                SomeStacks.LOGGER.debug("  Loaded texture: {}x{}", image.getWidth(), image.getHeight());
                return image;
            }
        } catch (Exception e) {
            SomeStacks.LOGGER.debug("  Failed to load texture {}: {}", texturePath, e.getMessage());
            return null;
        }
    }

    private static int analyzePixelsForTint(NativeImage image) {
        SomeStacks.LOGGER.debug("  Analyzing pixels from image: {}x{}", image.getWidth(), image.getHeight());
        try {
            long sumR = 0, sumG = 0, sumB = 0;
            int count = 0;

            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    int a = (abgr >> 24) & 0xFF;

                    if (a > 127) {
                        int r = abgr & 0xFF;
                        int g = (abgr >> 8) & 0xFF;
                        int b = (abgr >> 16) & 0xFF;

                        sumR += r;
                        sumG += g;
                        sumB += b;
                        count++;
                    }
                }
            }

            SomeStacks.LOGGER.debug("    Analyzed {} opaque pixels", count);

            if (count == 0) {
                SomeStacks.LOGGER.debug("    No opaque pixels found, returning WHITE");
                return BarTextureData.WHITE;
            }

            int avgR = (int) (sumR / count);
            int avgG = (int) (sumG / count);
            int avgB = (int) (sumB / count);

            SomeStacks.LOGGER.debug("    Average RGB (raw): R={}, G={}, B={}", avgR, avgG, avgB);

            // Brighten to compensate for dark outlines in ingot textures
            int brightR = (int) Math.min(255, avgR + (255 - avgR) * BRIGHTEN_FACTOR);
            int brightG = (int) Math.min(255, avgG + (255 - avgG) * BRIGHTEN_FACTOR);
            int brightB = (int) Math.min(255, avgB + (255 - avgB) * BRIGHTEN_FACTOR);

            SomeStacks.LOGGER.debug("    Average RGB (brightened): R={}, G={}, B={}", brightR, brightG, brightB);

            int result = 0xFF000000 | (brightR << 16) | (brightG << 8) | brightB;
            SomeStacks.LOGGER.debug("    Final color: #{}", String.format("%08X", result));
            return result;
        } catch (Exception e) {
            SomeStacks.LOGGER.warn("    Failed to analyze pixels: {}", e.getMessage(), e);
            return BarTextureData.WHITE;
        }
    }

    private static int parseColor(String colorStr) {
        if (colorStr.startsWith("#")) {
            colorStr = colorStr.substring(1);
        }

        if (colorStr.length() == 6) {
            // RGB format - add full alpha
            return (int) Long.parseLong("FF" + colorStr, 16);
        } else if (colorStr.length() == 8) {
            // ARGB format
            return (int) Long.parseLong(colorStr, 16);
        }

        throw new IllegalArgumentException("Invalid color format: " + colorStr);
    }

    public static BarTextureData getTexture(ItemStack stack) {
        if (stack.isEmpty()) return FALLBACK;

        ResourceLocation itemLoc = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemLoc == null) return FALLBACK;

        BarTextureData mapped = textureMap.get(itemLoc);
        if (mapped != null) return mapped;

        return unmappedTints.computeIfAbsent(itemLoc, loc -> new BarTextureData(
                DEFAULT_TEXTURE,
                calculateTintFromItemTexture(loc, Minecraft.getInstance().getResourceManager())));
    }
}
