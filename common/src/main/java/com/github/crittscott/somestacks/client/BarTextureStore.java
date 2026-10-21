package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.datafixers.util.Either;
import java.util.Optional;
import java.util.function.Function;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.FileToIdConverter;
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
public class BarTextureStore extends SimplePreparableReloadListener<Map<ResourceLocation, BarTextureStore.BarTextureData>> {
    public static final ResourceLocation RELOAD_LISTENER_ID =
            ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "bar_textures");

    private static final Gson GSON = new Gson();
    /** Resource-pack mappings; an automatic-tint entry is replaced by its derived tint on first render. */
    private static final Map<ResourceLocation, BarTextureData> textureMap = new HashMap<>();
    /** Auto-tints for items no mapping covers, computed on first render and held until the next reload. */
    private static final Map<ResourceLocation, BarTextureData> unmappedTints = new HashMap<>();
    private static final ResourceLocation DEFAULT_TEXTURE = ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "block/minecraft/base_ingot");
    private static final BarTextureData FALLBACK = BarTextureData.tinted(DEFAULT_TEXTURE, BarTextureData.WHITE);
    private static final float BRIGHTEN_FACTOR = 0.1f;
    private static final int OPAQUE_PIXEL_ALPHA_THRESHOLD = 127;
    private static final String COMMENT_PREFIX = "_comment";
    private static final String FIELD_TEXTURE = "texture";
    private static final String FIELD_TINT = "tint";

    private static final Codec<Integer> COLOR_CODEC = Codec.STRING.comapFlatMap(value -> {
        try {
            return DataResult.success(parseColor(value));
        } catch (IllegalArgumentException error) {
            return DataResult.error(error::getMessage);
        }
    }, color -> String.format("#%08X", color));
    private static final Codec<BarTextureData> OBJECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf(FIELD_TEXTURE, DEFAULT_TEXTURE).forGetter(BarTextureData::texture),
            COLOR_CODEC.optionalFieldOf(FIELD_TINT).forGetter(
                    data -> data.autoTint() ? Optional.empty() : Optional.of(data.color())))
            .apply(instance, (texture, tint) -> tint.map(color -> BarTextureData.tinted(texture, color))
                    .orElseGet(() -> BarTextureData.auto(texture))));
    private static final Codec<BarTextureData> ENTRY_CODEC = Codec.either(ResourceLocation.CODEC, OBJECT_CODEC)
            .xmap(entry -> entry.map(BarTextureData::auto, Function.identity()), Either::right);

    /**
     * A bar's texture and tint. {@code autoTint} is separate from {@code color} because white is a
     * valid authored color meaning "no tint," not a sentinel requesting automatic tinting.
     */
    public record BarTextureData(ResourceLocation texture, int color, boolean autoTint) {
        public static final int WHITE = 0xFFFFFFFF;

        /** Creates a mapping whose tint will be derived from the sprite and tint the item draws with. */
        public static BarTextureData auto(ResourceLocation texture) {
            return new BarTextureData(texture, WHITE, true);
        }

        /** Creates a mapping with its final authored or computed tint. */
        public static BarTextureData tinted(ResourceLocation texture, int color) {
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
    protected Map<ResourceLocation, BarTextureData> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, BarTextureData> configMap = new HashMap<>();

        var resources = FileToIdConverter.json("textures/bars").listMatchingResources(resourceManager);

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
        if (!value.isJsonObject()
                && !(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString())) return null;
        return ENTRY_CODEC.parse(JsonOps.INSTANCE, value).getOrThrow();
    }

    @Override
    protected void apply(Map<ResourceLocation, BarTextureData> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        textureMap.clear();
        unmappedTints.clear();
        textureMap.putAll(prepared);
        SomeStacksCommon.LOGGER.info("Applied {} bar texture mappings", textureMap.size());
    }

    private static int calculateTintFromItemTexture(ResourceLocation itemLoc, ResourceManager resourceManager) {
        try {
            Item item = BuiltInRegistries.ITEM.getValue(itemLoc);

            // Rendering multiplies a quad's sprite by its layer's tint, so the derived bar tint
            // must include both. The first quad drawn stands for the item.
            ItemCapture capture = ItemCapture.capture(
                    new ItemStack(item), ItemDisplayContext.GUI, null, new PoseStack());
            if (capture.quads().isEmpty()) {
                return BarTextureData.WHITE;
            }
            ItemCapture.TintedQuad first = capture.quads().getFirst();
            return ARGB.multiply(averageSpriteColor(first.quad().sprite(), resourceManager),
                    ARGB.color(0xFF, ARGB.red(first.color()), ARGB.green(first.color()),
                            ARGB.blue(first.color())));
        } catch (Exception e) {
            SomeStacksCommon.LOGGER.warn("Could not auto-calculate tint for {}", itemLoc, e);
            return BarTextureData.WHITE;
        }
    }

    /** The average color of {@code sprite}'s source texture, or white when there is none to read. */
    private static int averageSpriteColor(TextureAtlasSprite sprite, ResourceManager resourceManager) {
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
     * Resolves an explicit bar mapping or the default texture. Automatic tints are derived and
     * cached on first render, when reloaded item models and atlas sprites are ready.
     * Empty stacks use the fallback mapping.
     */
    public static BarTextureData getTexture(ItemStack stack) {
        if (stack.isEmpty()) return FALLBACK;

        ResourceLocation itemLoc = BuiltInRegistries.ITEM.getKey(stack.getItem());

        BarTextureData mapped = textureMap.get(itemLoc);
        if (mapped != null) {
            if (mapped.autoTint()) {
                mapped = BarTextureData.tinted(mapped.texture(), calculateTintFromItemTexture(
                        itemLoc, Minecraft.getInstance().getResourceManager()));
                textureMap.put(itemLoc, mapped);
            }
            return mapped;
        }

        return unmappedTints.computeIfAbsent(itemLoc, loc -> BarTextureData.tinted(
                DEFAULT_TEXTURE,
                calculateTintFromItemTexture(loc, Minecraft.getInstance().getResourceManager())));
    }
}
