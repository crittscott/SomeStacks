package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import com.github.crittscott.somestacks.util.OverrideJsonCodec;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The client's item render configuration, layered by authority. {@link #resolve} walks
 * the layers and returns the first entry found, whole: server-synced admin overrides,
 * then the user's own override file, then the bundled resource corpus. An item no layer
 * mentions takes its complete profile from measurement.
 */
public class ItemRenderOverrides extends SimplePreparableReloadListener<Map<ResourceLocation, ItemRenderConfig>> {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path USER_FILE = FMLPaths.CONFIGDIR.get().resolve("somestacks/item_overrides.json");
    private static final float[] ZERO_OFFSET = new float[3];

    /** Bundled corpus, from client resource reload. */
    public static final Map<ResourceLocation, ItemRenderConfig> CONFIG_MAP = new HashMap<>();
    /** Admin overrides synced from the server. */
    public static final Map<ResourceLocation, ItemRenderConfig> SERVER_OVERRIDES = new HashMap<>();
    /** The user's own overrides: {@code ss item} changes plus the loaded user file. */
    private static final Map<ResourceLocation, ItemRenderConfig> USER_OVERRIDES = new HashMap<>();
    private static boolean userFileLoaded = false;

    @Override
    protected Map<ResourceLocation, ItemRenderConfig> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, ItemRenderConfig> configMap = new HashMap<>();

        var resources = resourceManager.listResources("item_render_overrides", loc -> loc.getPath().endsWith(".json"));

        // Read in file order so that two files covering one item resolve the same way every reload.
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(fileEntry -> {
            ResourceLocation fileLocation = fileEntry.getKey();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(fileEntry.getValue().open(), StandardCharsets.UTF_8))) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                configMap.putAll(OverrideJsonCodec.parse(json, fileLocation.toString()));
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

    public static void putUser(ResourceLocation itemId, ItemRenderConfig config) {
        ensureUserFileLoaded();
        USER_OVERRIDES.put(itemId, config);
    }

    public static void removeUser(ResourceLocation itemId) {
        ensureUserFileLoaded();
        USER_OVERRIDES.remove(itemId);
    }

    /**
     * Resolves the item's presentation through the override layers. The first layer
     * with an entry owns the presentation: only a missing mode is measured, because
     * scale and offset mean different things from one mode to the next; missing scale
     * and offset take plain defaults.
     *
     * @return the complete profile, or null for an empty stack.
     */
    @Nullable
    public static RenderProfile resolve(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ensureUserFileLoaded();

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        ItemRenderConfig entry = SERVER_OVERRIDES.get(itemId);
        if (entry == null) {
            entry = USER_OVERRIDES.get(itemId);
        }
        if (entry == null) {
            entry = CONFIG_MAP.get(itemId);
        }
        if (entry == null) {
            return AutoRenderProfiles.get(stack);
        }

        RenderMode mode = entry.mode() != null ? entry.mode() : AutoRenderProfiles.get(stack).mode();
        float scale = entry.scale() != null ? entry.scale() : 1.0f;
        float[] offset = entry.offset() != null ? entry.offset() : ZERO_OFFSET;
        return new RenderProfile(mode, scale, offset);
    }

    /**
     * Writes the user layer to the user override file and reports the result to the
     * player. The map contains only entries the user explicitly set, so the file never
     * accumulates measured or bundled values, and reset entries disappear from it.
     */
    public static void handleWriteRequest() {
        ensureUserFileLoaded();

        String message;
        try {
            Files.createDirectories(USER_FILE.getParent());
            Files.writeString(USER_FILE, PRETTY_GSON.toJson(OverrideJsonCodec.toJson(USER_OVERRIDES)));
            message = "Wrote " + USER_OVERRIDES.size() + " render override(s) to " + USER_FILE;
        } catch (IOException e) {
            SomeStacks.LOGGER.error("Failed to write {}", USER_FILE, e);
            message = "Failed to write " + USER_FILE + ": " + e.getMessage();
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal(message), false);
        }
    }

    private static void ensureUserFileLoaded() {
        if (userFileLoaded) {
            return;
        }
        userFileLoaded = true;

        if (!Files.exists(USER_FILE)) {
            return;
        }
        try {
            JsonObject json = GSON.fromJson(Files.readString(USER_FILE), JsonObject.class);
            if (json != null) {
                USER_OVERRIDES.putAll(OverrideJsonCodec.parse(json, USER_FILE.toString()));
            }
        } catch (Exception e) {
            SomeStacks.LOGGER.error("Failed to read {}", USER_FILE, e);
        }
    }
}
