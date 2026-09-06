package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * What each item's bar looks like, loaded from {@code assets/<namespace>/textures/bars/*.json} and
 * reloaded with the resource packs. A mapping names a texture and an optional tint; an item no
 * mapping covers falls back to the base ingot texture with a tint derived from the item's own
 * sprite.
 *
 * <p>This is client resource data and is never synchronized, so two players may see the same bar
 * differently if their resource packs differ.
 */
public class BarTextureStore extends SimplePreparableReloadListener<Map<ResourceLocation, BarTextureStore.BarTextureData>> {
    private static final Gson GSON = new Gson();
    private static final Map<ResourceLocation, BarTextureData> textureMap = new HashMap<>();
    /** Auto-tints for items no mapping covers, computed on first render and held until the next reload. */
    private static final Map<ResourceLocation, BarTextureData> unmappedTints = new HashMap<>();
    private static final ResourceLocation DEFAULT_TEXTURE = ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "block/minecraft/base_ingot");
    private static final BarTextureData FALLBACK = BarTextureData.tinted(DEFAULT_TEXTURE, BarTextureData.WHITE);
    private static final float BRIGHTEN_FACTOR = 0.1f;
    private static final String COMMENT_PREFIX = "_comment";
    private static final String FIELD_TEXTURE = "texture";
    private static final String FIELD_TINT = "tint";

    /**
     * A bar's texture and tint. {@code autoTint} is separate from {@code color} because white is a
     * valid authored color meaning "no tint," not a sentinel requesting automatic tinting.
     */
    public record BarTextureData(ResourceLocation texture, int color, boolean autoTint) {
        public static final int WHITE = 0xFFFFFFFF;

        /** Creates a mapping whose tint will be derived from the item's sprite and registered tint. */
        public static BarTextureData auto(ResourceLocation texture) {
            return new BarTextureData(texture, WHITE, true);
        }

        /** Creates a mapping with its final authored or computed tint. */
        public static BarTextureData tinted(ResourceLocation texture, int color) {
            return new BarTextureData(texture, color, false);
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
                    if (key.startsWith(COMMENT_PREFIX)) continue;

                    try {
                        ResourceLocation itemLoc = ResourceLocation.parse(key);
                        BarTextureData data = parseEntry(root.get(key));
                        if (data == null) {
                            SomeStacksCommon.LOGGER.warn("Invalid format for '{}': expected string or object", key);
                            continue;
                        }
                        configMap.put(itemLoc, data);
                    } catch (Exception e) {
                        SomeStacksCommon.LOGGER.warn("Invalid mapping for '{}': {}", key, e.getMessage());
                    }
                }
            } catch (Exception e) {
                SomeStacksCommon.LOGGER.warn("Failed to process file {}: {}", fileLocation, e.getMessage());
            }
        });

        return configMap;
    }

    /**
     * One mapping: a bare texture id, or an object with optional {@code texture} and {@code tint}.
     * An explicit tint is final, including white to disable tinting. Omitting the tint requests
     * automatic tinting from the item.
     *
     * @return the mapping, or null when the value is neither a string nor an object.
     * @throws IllegalArgumentException for a malformed id or color, which the caller reports.
     */
    @Nullable
    static BarTextureData parseEntry(JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return BarTextureData.auto(ResourceLocation.parse(value.getAsString()));
        }
        if (!value.isJsonObject()) {
            return null;
        }

        JsonObject obj = value.getAsJsonObject();
        ResourceLocation texture = obj.has(FIELD_TEXTURE)
                ? ResourceLocation.parse(obj.get(FIELD_TEXTURE).getAsString())
                : DEFAULT_TEXTURE;
        return obj.has(FIELD_TINT)
                ? BarTextureData.tinted(texture, parseColor(obj.get(FIELD_TINT).getAsString()))
                : BarTextureData.auto(texture);
    }

    @Override
    protected void apply(Map<ResourceLocation, BarTextureData> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        textureMap.clear();
        unmappedTints.clear();
        int autoTinted = 0;

        for (Map.Entry<ResourceLocation, BarTextureData> entry : prepared.entrySet()) {
            ResourceLocation itemLoc = entry.getKey();
            BarTextureData data = entry.getValue();

            if (data.autoTint()) {
                data = BarTextureData.tinted(
                        data.texture(), calculateTintFromItemTexture(itemLoc, resourceManager));
                autoTinted++;
            }

            textureMap.put(itemLoc, data);
        }

        SomeStacksCommon.LOGGER.info("Applied {} bar texture mappings ({} auto-tinted)",
                textureMap.size(), autoTinted);
    }

    private static int calculateTintFromItemTexture(ResourceLocation itemLoc, ResourceManager resourceManager) {
        try {
            if (!BuiltInRegistries.ITEM.containsKey(itemLoc)) {
                return BarTextureData.WHITE;
            }
            Item item = BuiltInRegistries.ITEM.get(itemLoc);

            ItemStack stack = new ItemStack(item);

            // Rendering multiplies the sprite by the item's registered tint, so the derived bar tint
            // must include both. Either contribution is white when the item does not supply it.
            int spriteColor = averageSpriteColor(stack, resourceManager);
            int itemColor = registeredItemColor(stack);
            int tint = multiplyColors(spriteColor, itemColor);
            return tint;
        } catch (Exception e) {
            SomeStacksCommon.LOGGER.warn("Could not auto-calculate tint for {}: {}", itemLoc, e.getMessage(), e);
            return BarTextureData.WHITE;
        }
    }

    /**
     * The average of the sprite the item's baked model puts on the item atlas, or white when there
     * is none to read. An item drawn by a custom renderer carries its art in that renderer rather
     * than in its model, so its particle resolves to the missing texture and only its registered
     * tint describes its color.
     */
    private static int averageSpriteColor(ItemStack stack, ResourceManager resourceManager) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getItemRenderer().getModel(stack, null, null, 0);

        TextureAtlasSprite sprite = model.getParticleIcon();
        if (sprite == null) {
            return BarTextureData.WHITE;
        }

        ResourceLocation spriteName = sprite.contents().name();
        if (spriteName.equals(MissingTextureAtlasSprite.getLocation())) {
            return BarTextureData.WHITE;
        }

        ResourceLocation texturePath = getTextureResourceLocation(spriteName);

        NativeImage image = loadTextureImage(texturePath, resourceManager);
        if (image == null) {
            return BarTextureData.WHITE;
        }

        try {
            return analyzePixelsForTint(image);
        } finally {
            image.close();
        }
    }

    /** The color the item's mod registers for the primary layer, or white when it registers none. */
    private static int registeredItemColor(ItemStack stack) {
        return ClientRenderPlatform.itemColor(stack, 0) | 0xFF000000;
    }

    private static int multiplyColors(int left, int right) {
        int r = (((left >> 16) & 0xFF) * ((right >> 16) & 0xFF)) / 0xFF;
        int g = (((left >> 8) & 0xFF) * ((right >> 8) & 0xFF)) / 0xFF;
        int b = ((left & 0xFF) * (right & 0xFF)) / 0xFF;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static ResourceLocation getTextureResourceLocation(ResourceLocation spriteName) {
        String namespace = spriteName.getNamespace();
        String path = spriteName.getPath();
        return ResourceLocation.fromNamespaceAndPath(namespace, "textures/" + path + ".png");
    }

    private static NativeImage loadTextureImage(ResourceLocation texturePath, ResourceManager resourceManager) {
        try {
            var resource = resourceManager.getResource(texturePath);
            if (resource.isEmpty()) {
                return null;
            }

            try (var inputStream = resource.get().open()) {
                return NativeImage.read(inputStream);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static int analyzePixelsForTint(NativeImage image) {
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

        if (count == 0) {
            return BarTextureData.WHITE;
        }

        int avgR = (int) (sumR / count);
        int avgG = (int) (sumG / count);
        int avgB = (int) (sumB / count);

        // Brighten to compensate for dark outlines in ingot textures.
        int brightR = (int) Math.min(255, avgR + (255 - avgR) * BRIGHTEN_FACTOR);
        int brightG = (int) Math.min(255, avgG + (255 - avgG) * BRIGHTEN_FACTOR);
        int brightB = (int) Math.min(255, avgB + (255 - avgB) * BRIGHTEN_FACTOR);

        return 0xFF000000 | (brightR << 16) | (brightG << 8) | brightB;
    }

    private static int parseColor(String colorStr) {
        if (colorStr.startsWith("#")) {
            colorStr = colorStr.substring(1);
        }

        if (colorStr.length() == 6) {
            // RGB input: add full alpha.
            return (int) Long.parseLong("FF" + colorStr, 16);
        } else if (colorStr.length() == 8) {
            // ARGB input.
            return (int) Long.parseLong(colorStr, 16);
        }

        throw new IllegalArgumentException("Invalid color format: " + colorStr);
    }

    public static BarTextureData getTexture(ItemStack stack) {
        if (stack.isEmpty()) return FALLBACK;

        ResourceLocation itemLoc = BuiltInRegistries.ITEM.getKey(stack.getItem());

        BarTextureData mapped = textureMap.get(itemLoc);
        if (mapped != null) return mapped;

        return unmappedTints.computeIfAbsent(itemLoc, loc -> BarTextureData.tinted(
                DEFAULT_TEXTURE,
                calculateTintFromItemTexture(loc, Minecraft.getInstance().getResourceManager())));
    }
}
