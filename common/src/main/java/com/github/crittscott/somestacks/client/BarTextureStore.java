package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.ARGB;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
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
public class BarTextureStore extends SimplePreparableReloadListener<Map<Identifier, BarTextureStore.BarTextureData>> {
    public static final Identifier RELOAD_LISTENER_ID =
            Identifier.fromNamespaceAndPath(SomeStacksCommon.MODID, "bar_textures");

    private static final Gson GSON = new Gson();
    private static final Map<Identifier, BarTextureData> textureMap = new HashMap<>();
    /** Auto-tints computed on first render, after item models are ready, and held until reload. */
    private static final Map<Identifier, BarTextureData> resolvedTints = new HashMap<>();
    private static final Identifier DEFAULT_TEXTURE = Identifier.fromNamespaceAndPath(SomeStacksCommon.MODID, "block/minecraft/base_ingot");
    private static final BarTextureData FALLBACK = BarTextureData.tinted(DEFAULT_TEXTURE, BarTextureData.WHITE);
    private static final float BRIGHTEN_FACTOR = 0.1f;
    private static final int OPAQUE_PIXEL_ALPHA_THRESHOLD = 127;
    private static final String COMMENT_PREFIX = "_comment";
    private static final String FIELD_TEXTURE = "texture";
    private static final String FIELD_TINT = "tint";

    /**
     * A bar's texture and tint. {@code autoTint} is separate from {@code color} because white is a
     * valid authored color meaning "no tint," not a sentinel requesting automatic tinting.
     */
    public record BarTextureData(Identifier texture, int color, boolean autoTint) {
        public static final int WHITE = 0xFFFFFFFF;

        /** Creates a mapping whose tint will be derived from the sprite and tint the item draws with. */
        public static BarTextureData auto(Identifier texture) {
            return new BarTextureData(texture, WHITE, true);
        }

        /** Creates a mapping with its final authored or computed tint. */
        public static BarTextureData tinted(Identifier texture, int color) {
            return new BarTextureData(texture, color, false);
        }

        public int red() {
            return ARGB.red(color);
        }

        public int green() {
            return ARGB.green(color);
        }

        public int blue() {
            return ARGB.blue(color);
        }

        public int alpha() {
            return ARGB.alpha(color);
        }
    }

    @Override
    protected Map<Identifier, BarTextureData> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, BarTextureData> configMap = new HashMap<>();

        var resources = resourceManager.listResources("textures/bars", loc -> loc.getPath().endsWith(".json"));

        // Read in file order so that two files mapping one item resolve the same way every reload.
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(fileEntry -> {
            Identifier fileLocation = fileEntry.getKey();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(fileEntry.getValue().open(), StandardCharsets.UTF_8))) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);

                for (String key : root.keySet()) {
                    if (key.startsWith(COMMENT_PREFIX)) continue;

                    try {
                        Identifier itemLoc = Identifier.parse(key);
                        BarTextureData data = parseEntry(root.get(key));
                        if (data == null) {
                            SomeStacksCommon.LOGGER.warn("Invalid format for '{}' in {}: expected string or object", key, fileLocation);
                            continue;
                        }
                        configMap.put(itemLoc, data);
                    } catch (Exception e) {
                        SomeStacksCommon.LOGGER.warn("Invalid mapping for '{}' in {}", key, fileLocation, e);
                    }
                }
            } catch (Exception e) {
                SomeStacksCommon.LOGGER.warn("Failed to process file {}", fileLocation, e);
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
            return BarTextureData.auto(Identifier.parse(value.getAsString()));
        }
        if (!value.isJsonObject()) {
            return null;
        }

        JsonObject obj = value.getAsJsonObject();
        Identifier texture = obj.has(FIELD_TEXTURE)
                ? Identifier.parse(obj.get(FIELD_TEXTURE).getAsString())
                : DEFAULT_TEXTURE;
        return obj.has(FIELD_TINT)
                ? BarTextureData.tinted(texture, parseColor(obj.get(FIELD_TINT).getAsString()))
                : BarTextureData.auto(texture);
    }

    @Override
    protected void apply(Map<Identifier, BarTextureData> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        textureMap.clear();
        resolvedTints.clear();
        textureMap.putAll(prepared);

        SomeStacksCommon.LOGGER.info("Applied {} bar texture mappings", textureMap.size());
    }

    private static int calculateTintFromItemTexture(Identifier itemLoc, ResourceManager resourceManager) {
        try {
            Item item = BuiltInRegistries.ITEM.getValue(itemLoc);

            // Rendering multiplies a quad's sprite by its layer's tint, so the derived bar tint
            // must include both. The first quad drawn stands for the item.
            ItemCapture capture = ItemCapture.captureQuads(
                    new ItemStack(item), ItemDisplayContext.GUI, null, new PoseStack());
            var quads = capture.quads();
            if (quads.isEmpty()) {
                return BarTextureData.WHITE;
            }
            ItemCapture.TintedQuad first = quads.getFirst();
            return ARGB.multiply(averageSpriteColor(first.quad().materialInfo().sprite(), resourceManager),
                    ARGB.color(0xFF, ARGB.red(first.color()), ARGB.green(first.color()),
                            ARGB.blue(first.color())));
        } catch (Exception e) {
            SomeStacksCommon.LOGGER.warn("Could not auto-calculate tint for {}", itemLoc, e);
            return BarTextureData.WHITE;
        }
    }

    /** The average color of {@code sprite}'s source texture, or white when there is none to read. */
    private static int averageSpriteColor(TextureAtlasSprite sprite, ResourceManager resourceManager) {
        Identifier spriteName = sprite.contents().name();
        if (spriteName.equals(MissingTextureAtlasSprite.getLocation())) {
            return BarTextureData.WHITE;
        }

        Identifier texturePath = getTextureIdentifier(spriteName);

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

    private static Identifier getTextureIdentifier(Identifier spriteName) {
        String namespace = spriteName.getNamespace();
        String path = spriteName.getPath();
        return Identifier.fromNamespaceAndPath(namespace, "textures/" + path + ".png");
    }

    private static NativeImage loadTextureImage(Identifier texturePath, ResourceManager resourceManager) {
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
                int argb = image.getPixel(x, y);
                int a = ARGB.alpha(argb);

                if (a > OPAQUE_PIXEL_ALPHA_THRESHOLD) {
                    int r = ARGB.red(argb);
                    int g = ARGB.green(argb);
                    int b = ARGB.blue(argb);

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

        return ARGB.color(0xFF, brightR, brightG, brightB);
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

    /**
     * Resolves authored colors directly and derives automatic tints on first render, when item
     * models are ready. Unmapped items use the default texture. Empty stacks use the fallback.
     */
    public static BarTextureData getTexture(ItemStack stack) {
        if (stack.isEmpty()) return FALLBACK;

        Identifier itemLoc = BuiltInRegistries.ITEM.getKey(stack.getItem());

        BarTextureData mapped = textureMap.get(itemLoc);
        if (mapped != null && !mapped.autoTint()) return mapped;

        return resolvedTints.computeIfAbsent(itemLoc, loc -> BarTextureData.tinted(
                mapped != null ? mapped.texture() : DEFAULT_TEXTURE,
                calculateTintFromItemTexture(loc, Minecraft.getInstance().getResourceManager())));
    }
}
