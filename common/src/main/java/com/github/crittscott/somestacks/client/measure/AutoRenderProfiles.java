package com.github.crittscott.somestacks.client.measure;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.client.ClientRenderPlatform;
import com.github.crittscott.somestacks.client.CubeRenderHelper;
import com.github.crittscott.somestacks.client.ItemRenderConfig;
import com.github.crittscott.somestacks.client.RenderMode;
import com.github.crittscott.somestacks.client.RenderProfile;
import com.github.crittscott.somestacks.util.OverrideJsonCodec;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Per-item cache of measured render profiles, persisted client-side so each item is normally
 * measured only once across sessions.
 *
 * <p>Measurements describe baked models and are invalidated with them:
 *
 * <ul>
 *   <li>A mod-version change drops entries from that mod's namespace.</li>
 *   <li>A manual resource reload drops the entire cache.</li>
 *   <li>A different resource-pack selection between sessions rejects the persisted cache.</li>
 * </ul>
 *
 * The initial resource reload must preserve the persisted cache long enough to compare its recorded
 * mod versions and resource-pack list with the current client.
 */
public final class AutoRenderProfiles {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CACHE_FILE = Platform.getConfigFolder().resolve("somestacks/measured_cache.json");

    /** Fraction of a stack cell the fitted model should span. */
    private static final float TARGET_FILL = 0.9f;

    /**
     * Thinnest-to-longest axis ratio below which geometry is treated as a flat card, catching
     * models that report gui3d but draw no real depth.
     */
    private static final float FLAT_RATIO = 0.1f;
    private static final float MIN_EXTENT = 0.001f;
    private static final float BUTTON_FIT_SCALE = 0.75f;

    private static final Map<Item, RenderProfile> CACHE = new HashMap<>();
    private static boolean cacheLoaded = false;
    private static boolean dirty = false;
    private static boolean initialResourceLoadSeen = false;

    private AutoRenderProfiles() {}

    public static RenderProfile get(ItemStack stack) {
        loadCacheOnce();
        RenderProfile profile = CACHE.get(stack.getItem());
        if (profile == null) {
            profile = compute(stack);
            CACHE.put(stack.getItem(), profile);
            dirty = true;
        }
        return profile;
    }

    /**
     * The first invocation is the initial resource load and keeps the persisted cache;
     * any later invocation is a genuine reload that may have changed models, so both
     * the in-memory cache and the file are discarded.
     */
    public static void onResourceReload() {
        if (!initialResourceLoadSeen) {
            initialResourceLoadSeen = true;
            return;
        }
        CACHE.clear();
        cacheLoaded = true;
        dirty = false;
        try {
            Files.deleteIfExists(CACHE_FILE);
        } catch (IOException e) {
            SomeStacksCommon.LOGGER.warn("Failed to delete {}: {}", CACHE_FILE, e.getMessage());
        }
    }

    public static void saveCache() {
        if (!dirty) {
            return;
        }

        Map<ResourceLocation, ItemRenderConfig> entries = new TreeMap<>();
        JsonObject versions = new JsonObject();
        for (Map.Entry<Item, RenderProfile> entry : CACHE.entrySet()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(entry.getKey());
            if (id == null) {
                continue;
            }
            RenderProfile profile = entry.getValue();
            entries.put(id, new ItemRenderConfig(profile.mode(), profile.scale(), profile.offset()));
            if (!versions.has(id.getNamespace())) {
                versions.addProperty(id.getNamespace(), modVersion(id.getNamespace()));
            }
        }

        JsonObject root = new JsonObject();
        root.addProperty("packs", selectedPackIds());
        root.add("versions", versions);
        root.add("entries", OverrideJsonCodec.toJson(entries));

        try {
            Files.createDirectories(CACHE_FILE.getParent());
            Files.writeString(CACHE_FILE, PRETTY_GSON.toJson(root));
            dirty = false;
        } catch (IOException e) {
            SomeStacksCommon.LOGGER.warn("Failed to write {}: {}", CACHE_FILE, e.getMessage());
        }
    }

    private static void loadCacheOnce() {
        if (cacheLoaded) {
            return;
        }
        cacheLoaded = true;

        if (!Files.exists(CACHE_FILE)) {
            return;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(CACHE_FILE), JsonObject.class);
            if (root == null || !root.has("entries")) {
                return;
            }

            // Measurements read baked models, which a resource pack rewrites as surely as a mod
            // update does. A cache written under a different set of packs describes models that are
            // no longer loaded, so none of it is kept.
            String packs = root.has("packs") ? root.get("packs").getAsString() : null;
            if (packs == null || !packs.equals(selectedPackIds())) {
                dirty = true;
                return;
            }

            JsonObject versions = root.has("versions") ? root.getAsJsonObject("versions") : new JsonObject();

            Map<ResourceLocation, ItemRenderConfig> entries =
                    OverrideJsonCodec.parse(root.getAsJsonObject("entries"), CACHE_FILE.toString());
            for (Map.Entry<ResourceLocation, ItemRenderConfig> entry : entries.entrySet()) {
                ResourceLocation id = entry.getKey();
                ItemRenderConfig config = entry.getValue();
                if (config.mode() == null || config.scale() == null || config.offset() == null) {
                    dirty = true;
                    continue;
                }
                JsonElement recorded = versions.get(id.getNamespace());
                if (recorded == null || !recorded.getAsString().equals(modVersion(id.getNamespace()))) {
                    dirty = true;
                    continue;
                }
                if (!BuiltInRegistries.ITEM.containsKey(id)) {
                    dirty = true;
                    continue;
                }
                Item item = BuiltInRegistries.ITEM.get(id);
                CACHE.put(item, new RenderProfile(config.mode(), config.scale(), config.offset()));
            }
        } catch (Exception e) {
            SomeStacksCommon.LOGGER.warn("Failed to read {}: {}", CACHE_FILE, e.getMessage());
        }
    }

    /** The enabled resource packs in application order, which determines the baked models. */
    private static String selectedPackIds() {
        return String.join("\n", Minecraft.getInstance().getResourcePackRepository().getSelectedIds());
    }

    private static String modVersion(String namespace) {
        return ClientRenderPlatform.modVersion(namespace);
    }

    private static RenderProfile compute(ItemStack stack) {
        // Blocks whose art lies in the horizontal plane read as a one-pixel edge when
        // projected flat, so they are presented the way an inventory slot shows them.
        float scaleFactor = fitScaleFactor(stack);

        if (wantsGuiPresentation(stack)) {
            ModelMeasurement.Result guiResult = ModelMeasurement.measureGui(stack);
            RenderProfile fitted = fit(guiResult, RenderMode.GUI, scaleFactor);
            if (fitted != null) {
                return fitted;
            }
        }

        ModelMeasurement.Result result = ModelMeasurement.measure(stack);
        AABB bounds = result.bounds();

        if (bounds == null) {
            logFallback(stack, result.failure() != null ? result.failure() : "no geometry");
            return new RenderProfile(RenderMode.TWO_D, 1.0f, new float[3]);
        }

        double xSize = bounds.getXsize();
        double ySize = bounds.getYsize();
        double zSize = bounds.getZsize();
        double minExtent = Math.min(xSize, Math.min(ySize, zSize));
        double maxExtent = Math.max(xSize, Math.max(ySize, zSize));

        if (maxExtent < MIN_EXTENT) {
            logFallback(stack, "degenerate bounds");
            return new RenderProfile(RenderMode.TWO_D, 1.0f, new float[3]);
        }

        // Vanilla's own signal: a generated item sprite reports gui3d false. For custom
        // renderers the flag describes the placeholder model rather than the drawn
        // geometry, so only the measured shape is trusted there.
        boolean flatByModel = !result.customRenderer() && !result.gui3d();
        boolean flatByShape = minExtent / maxExtent < FLAT_RATIO;

        if (flatByModel || flatByShape) {
            return new RenderProfile(RenderMode.TWO_D, 1.0f, new float[3]);
        }

        RenderProfile fitted = fit(result, RenderMode.THREE_D, scaleFactor);
        if (fitted == null) {
            logFallback(stack, "fit failed");
            return new RenderProfile(RenderMode.TWO_D, 1.0f, new float[3]);
        }
        return fitted;
    }

    private static void logFallback(ItemStack stack, String reason) {
        SomeStacksCommon.LOGGER.debug("Render measurement fell back to 2d for {}: {}",
                BuiltInRegistries.ITEM.getKey(stack.getItem()), reason);
    }

    /**
     * Fits measured bounds to a stack cell. Returns null when the measurement produced
     * nothing usable, leaving the caller to fall back.
     */
    @Nullable
    private static RenderProfile fit(ModelMeasurement.Result result, RenderMode mode, float scaleFactor) {
        AABB bounds = result.bounds();
        if (bounds == null) {
            return null;
        }

        double maxExtent = Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize()));
        if (maxExtent < MIN_EXTENT) {
            return null;
        }

        float scale = (float) (TARGET_FILL * scaleFactor * CubeRenderHelper.CELL_LOCAL_SIZE / maxExtent);
        scale = Math.max(OverrideJsonCodec.MIN_SCALE, Math.min(OverrideJsonCodec.MAX_SCALE, scale));

        // The measured bounds already include the renderer's -0.5 shift, so the geometry
        // center directly gives the offset that recenters it on the cell center.
        float[] offset = new float[]{
                (float) (-scale * (bounds.minX + bounds.maxX) * 0.5),
                (float) (-scale * (bounds.minY + bounds.maxY) * 0.5),
                (float) (-scale * (bounds.minZ + bounds.maxZ) * 0.5)
        };

        return new RenderProfile(mode, scale, offset);
    }

    /**
     * Scale applied after fitting a model to the cell. Small block families such as buttons require
     * a lower factor or automatic fitting exaggerates their size relative to nearby blocks.
     */
    private static float fitScaleFactor(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ButtonBlock) {
            return BUTTON_FIT_SCALE;
        }
        return 1.0f;
    }

    private static boolean wantsGuiPresentation(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        Block block = blockItem.getBlock();
        return block instanceof BasePressurePlateBlock
                || block instanceof CarpetBlock;
    }
}
