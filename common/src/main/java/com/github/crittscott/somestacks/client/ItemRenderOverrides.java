package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.PlatformPaths;
import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import com.github.crittscott.somestacks.util.OverrideJsonCodec;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The client's item render configuration, layered by authority. The first matching entry owns the
 * entire presentation: server-synced admin overrides, then the user's override file, then bundled
 * resource overrides. Items absent from every layer use measured profiles.
 *
 * <p>A bundled resource-override file is named for the namespace whose items it covers, and covers
 * no other. The bundled data spans mods, most of which any one client cannot use, so a
 * file naming a namespace this client does not have is skipped without parsing or retention. A file
 * whose name is not a namespace therefore applies to nothing.
 */
public class ItemRenderOverrides extends SimplePreparableReloadListener<Map<ResourceLocation, ItemRenderConfig>> {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path USER_FILE = PlatformPaths.modConfigFolder().resolve("item_overrides.json");
    private static final Path GENERATED_DIR = PlatformPaths.modConfigFolder().resolve("generated_overrides");
    private static final float[] ZERO_OFFSET = new float[3];

    /** Bundled resource overrides, from client resource reload. */
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
        Set<String> present = presentNamespaces();
        int skipped = 0;

        // Read in file order so that two files covering one item resolve the same way every reload.
        for (Map.Entry<ResourceLocation, Resource> fileEntry
                : new TreeMap<>(resources).entrySet()) {
            ResourceLocation fileLocation = fileEntry.getKey();
            if (!present.contains(coveredNamespace(fileLocation))) {
                skipped++;
                continue;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(fileEntry.getValue().open(), StandardCharsets.UTF_8))) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                configMap.putAll(OverrideJsonCodec.parse(json, fileLocation.toString()));
            } catch (Exception e) {
                SomeStacksCommon.LOGGER.warn("Failed to process file {}: {}", fileLocation, e.getMessage());
            }
        }

        SomeStacksCommon.LOGGER.info("Loaded {} render override(s) from {} file(s); skipped {} for absent namespaces",
                configMap.size(), resources.size() - skipped, skipped);
        return configMap;
    }

    /** The namespace a bundled override file covers: its file name without the {@code .json}. */
    private static String coveredNamespace(ResourceLocation fileLocation) {
        String path = fileLocation.getPath();
        return path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
    }

    /**
     * The namespaces represented in the item registry. Client resource reloads run after item
     * registration, so a bundled override file outside this set covers nothing the client can render.
     */
    private static Set<String> presentNamespaces() {
        Set<String> namespaces = new HashSet<>();
        for (ResourceLocation itemId : BuiltInRegistries.ITEM.keySet()) {
            namespaces.add(itemId.getNamespace());
        }
        return namespaces;
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

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
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

        Component message;
        try {
            Files.createDirectories(USER_FILE.getParent());
            Files.writeString(USER_FILE, PRETTY_GSON.toJson(OverrideJsonCodec.toJson(USER_OVERRIDES)));
            message = Component.translatable("somestacks.command.write.success",
                    USER_OVERRIDES.size(), USER_FILE.toString());
        } catch (IOException e) {
            SomeStacksCommon.LOGGER.error("Failed to write {}", USER_FILE, e);
            message = Component.translatable("somestacks.command.write.failure",
                    USER_FILE.toString(), e.getMessage());
        }

        report(message);
    }

    /**
     * Dumps the resolved presentation of every item in these namespaces, one file per namespace
     * under {@code generated_overrides}, and reports the result to the player. Entries are
     * complete. The generated directory is output only, not an active override layer, so dumping a
     * modpack does not freeze its current profiles into the user layer. Each file can be edited and
     * copied into a resource pack's {@code item_render_overrides} or the server override directory.
     *
     * <p>Every item a layer does not configure is measured here rather than at first sight of it,
     * so a dump of a large pack is the measurement pass for all of it. The results reach the
     * measured cache, which is saved once the dump is done rather than waiting for logout.
     */
    public static void handleDumpRequest(List<String> namespaces) {
        Map<String, Map<ResourceLocation, ItemRenderConfig>> byNamespace = new LinkedHashMap<>();
        List<String> rejected = new ArrayList<>();
        for (String namespace : namespaces) {
            // A valid namespace contains no path separator, so it cannot escape the dump directory.
            if (ResourceLocation.tryBuild(namespace, "validation") == null) {
                SomeStacksCommon.LOGGER.warn("Ignoring dump request for invalid namespace '{}'", namespace);
                rejected.add(namespace);
                continue;
            }
            byNamespace.put(namespace, new HashMap<>());
        }

        for (Map.Entry<ResourceKey<Item>, Item> entry : BuiltInRegistries.ITEM.entrySet()) {
            ResourceLocation itemId = entry.getKey().location();
            Map<ResourceLocation, ItemRenderConfig> namespaceEntries = byNamespace.get(itemId.getNamespace());
            if (namespaceEntries == null) {
                continue;
            }

            RenderProfile profile = resolve(new ItemStack(entry.getValue()));
            if (profile != null) {
                namespaceEntries.put(itemId, new ItemRenderConfig(profile.mode(), profile.scale(), profile.offset()));
            }
        }

        AutoRenderProfiles.saveCache();

        int itemCount = 0;
        List<String> failed = new ArrayList<>();
        try {
            Files.createDirectories(GENERATED_DIR);
        } catch (IOException e) {
            SomeStacksCommon.LOGGER.error("Failed to create {}", GENERATED_DIR, e);
            report(Component.translatable("somestacks.command.dump.create_failure",
                    GENERATED_DIR.toString(), e.getMessage()));
            return;
        }

        for (Map.Entry<String, Map<ResourceLocation, ItemRenderConfig>> entry : byNamespace.entrySet()) {
            Path file = GENERATED_DIR.resolve(entry.getKey() + ".json").normalize();
            // Retain a path-containment check at the filesystem boundary as a final invariant.
            if (!file.startsWith(GENERATED_DIR)) {
                SomeStacksCommon.LOGGER.error("Refusing to write dump outside {}: {}", GENERATED_DIR, file);
                failed.add(entry.getKey());
                continue;
            }
            try {
                Files.writeString(file, PRETTY_GSON.toJson(OverrideJsonCodec.toJson(entry.getValue())));
                itemCount += entry.getValue().size();
            } catch (IOException e) {
                SomeStacksCommon.LOGGER.error("Failed to write {}", file, e);
                failed.add(entry.getKey());
            }
        }

        MutableComponent message = Component.translatable("somestacks.command.dump.success",
                itemCount, byNamespace.size() - failed.size(), GENERATED_DIR.toString());
        if (!failed.isEmpty()) {
            message.append(Component.translatable("somestacks.command.dump.failed",
                    failed.size(), String.join(", ", failed)));
        }
        if (!rejected.isEmpty()) {
            message.append(Component.translatable("somestacks.command.dump.rejected", rejected.size()));
        }
        report(message);
    }

    private static void report(Component message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(message, false);
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
            SomeStacksCommon.LOGGER.error("Failed to read {}", USER_FILE, e);
        }
    }
}
