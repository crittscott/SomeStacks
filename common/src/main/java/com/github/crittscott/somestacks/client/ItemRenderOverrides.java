package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.PlatformServices;
import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import com.github.crittscott.somestacks.client.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.client.renderconfig.OverrideJsonCodec;
import com.github.crittscott.somestacks.client.renderconfig.RenderMode;
import com.github.crittscott.somestacks.client.renderconfig.RenderOffset;
import com.github.crittscott.somestacks.client.renderconfig.RenderProfile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
import java.util.TreeSet;

import javax.annotation.Nullable;

/**
 * The client's item render configuration, layered by authority. The first matching entry owns the
 * entire presentation: the user's override file, then resource-pack overrides. Items absent from
 * every layer use measured profiles.
 *
 * <p>A corpus file is named for the namespace whose items it covers, and covers no other. The
 * corpus is cross-mod compatibility data, most of which any one client cannot use, so a file naming
 * a namespace this client does not have is skipped without parsing or retention. A file whose name
 * is not a namespace therefore applies to nothing.
 */
public class ItemRenderOverrides
        extends SimplePreparableReloadListener<Map<ResourceLocation, ItemRenderConfig>> {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path USER_FILE =
            PlatformServices.modConfigFolder().resolve("item_overrides.json");
    private static final Path GENERATED_DIR =
            PlatformServices.modConfigFolder().resolve("generated_overrides");

    /** Corpus files: {@code assets/<namespace>/item_render_overrides/**.json}. */
    private static final FileToIdConverter FILES = FileToIdConverter.json("item_render_overrides");

    /** Bundled corpus, from client resource reload. */
    private static final Map<ResourceLocation, ItemRenderConfig> CONFIG_MAP = new HashMap<>();

    /** The user's own overrides: {@code ss item} changes plus the loaded user file. */
    private static final Map<ResourceLocation, ItemRenderConfig> USER_OVERRIDES = new HashMap<>();

    /**
     * User file entries that could not be fully used, verbatim under their own keys. A write puts
     * each back unchanged unless this session set or reset that item.
     */
    private static final Map<String, JsonElement> USER_SET_ASIDE = new TreeMap<>();

    private static final Map<Item, RenderProfile> RESOLVED_PROFILES = new HashMap<>();
    private static UserFileState userFileState = UserFileState.UNREAD;

    /**
     * Whether the user file has been read. An unreadable file is never overwritten: rendering does
     * not retry it, and each write retries it so a fixed file is picked up.
     */
    private enum UserFileState {
        LOADED,
        UNREAD,
        UNREADABLE
    }

    /** Whether {@link #writeUserFile} wrote the file, and the message reporting it. */
    public record WriteResult(boolean written, Component message) {}

    @Override
    protected Map<ResourceLocation, ItemRenderConfig> prepare(
            ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, ItemRenderConfig> configMap = new HashMap<>();

        Map<ResourceLocation, Resource> resources = FILES.listMatchingResources(resourceManager);
        Set<String> present = installedNamespaces();
        int skipped = 0;

        // Read in file order so that two files covering one item resolve the same way every reload.
        for (Map.Entry<ResourceLocation, Resource> fileEntry :
                new TreeMap<>(resources).entrySet()) {
            ResourceLocation fileLocation = fileEntry.getKey();
            if (!present.contains(coveredNamespace(fileLocation))) {
                skipped++;
                continue;
            }
            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    fileEntry.getValue().open(), StandardCharsets.UTF_8))) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json != null) {
                    configMap.putAll(
                            OverrideJsonCodec.parse(json, fileLocation.toString()).entries());
                }
            } catch (IOException | JsonParseException e) {
                SomeStacksCommon.LOGGER.warn(
                        "Failed to process file {}: {}", fileLocation, e.getMessage());
            }
        }

        SomeStacksCommon.LOGGER.info(
                "Loaded {} render override(s) from {} file(s); skipped {} for absent namespaces",
                configMap.size(),
                resources.size() - skipped,
                skipped);
        return configMap;
    }

    /**
     * The namespace a corpus file covers: its file name without the {@code .json}, wherever it sits
     * under {@code item_render_overrides}.
     */
    private static String coveredNamespace(ResourceLocation fileLocation) {
        String path = FILES.fileToId(fileLocation).getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * The namespaces represented in the item registry, in id order. Client resource reloads run
     * after item registration, so a corpus file outside this set covers nothing the client can
     * render, and a dump of a namespace outside it has nothing to write.
     */
    public static Set<String> installedNamespaces() {
        Set<String> namespaces = new TreeSet<>();
        for (ResourceLocation itemId : BuiltInRegistries.ITEM.keySet()) {
            namespaces.add(itemId.getNamespace());
        }
        return namespaces;
    }

    @Override
    protected void apply(
            Map<ResourceLocation, ItemRenderConfig> prepared,
            ResourceManager resourceManager,
            ProfilerFiller profiler) {
        CONFIG_MAP.clear();
        CONFIG_MAP.putAll(prepared);
        clearResolvedProfiles();
    }

    /**
     * Loads the user file if needed, then replaces this item's in-memory user override and
     * invalidates resolved profiles. Does not save to disk.
     */
    public static void putUser(ResourceLocation itemId, ItemRenderConfig config) {
        ensureUserFileLoaded();
        USER_OVERRIDES.put(itemId, config);
        forgetSetAside(itemId);
        clearResolvedProfiles();
    }

    /**
     * Loads the user file if needed, removes this item's user override, and invalidates resolved
     * profiles. Does not save to disk; resolution falls through according to the remaining layers.
     */
    public static void removeUser(ResourceLocation itemId) {
        ensureUserFileLoaded();
        USER_OVERRIDES.remove(itemId);
        forgetSetAside(itemId);
        clearResolvedProfiles();
    }

    /** Drops set-aside user file entries for an item this session has set or reset. */
    private static void forgetSetAside(ResourceLocation itemId) {
        USER_SET_ASIDE.keySet().removeIf(key -> itemId.equals(ResourceLocation.tryParse(key)));
    }

    /**
     * Resolves the item's presentation through the override layers. The first layer with an entry
     * owns the presentation: only a missing mode is measured, because scale and offset mean
     * different things from one mode to the next; missing scale and offset take plain defaults.
     *
     * @return the complete profile, or null for an empty stack.
     */
    @Nullable
    public static RenderProfile resolve(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ensureUserFileLoaded();

        return RESOLVED_PROFILES.computeIfAbsent(
                stack.getItem(), ignored -> resolveUncached(stack));
    }

    /** Discards profiles after override or baked-model changes. */
    public static void clearResolvedProfiles() {
        RESOLVED_PROFILES.clear();
    }

    private static RenderProfile resolveUncached(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ItemRenderConfig entry = USER_OVERRIDES.get(itemId);
        if (entry == null) {
            entry = CONFIG_MAP.get(itemId);
        }
        if (entry == null) {
            return AutoRenderProfiles.get(stack);
        }

        RenderMode mode =
                entry.mode() != null ? entry.mode() : AutoRenderProfiles.get(stack).mode();
        float scale = entry.scale() != null ? entry.scale() : OverrideJsonCodec.DEFAULT_SCALE;
        RenderOffset offset = entry.offset() != null ? entry.offset() : RenderOffset.ZERO;
        return new RenderProfile(mode, scale, offset);
    }

    /**
     * Writes the user layer to the user override file. The map contains only entries the user
     * explicitly set, so the file never accumulates measured or bundled values, and reset entries
     * disappear from it. Entries the file held but could not fully use are written back verbatim
     * unless this session set or reset that item.
     *
     * <p>A file that could not be read is read again first, and is not written while it stays
     * unreadable, so a typo in a hand-edited file never costs the player its contents.
     */
    public static WriteResult writeUserFile() {
        if (userFileState == UserFileState.UNREADABLE) {
            userFileState = UserFileState.UNREAD;
        }
        ensureUserFileLoaded();
        if (userFileState == UserFileState.UNREADABLE) {
            return new WriteResult(
                    false,
                    Component.translatable(
                            "somestacks.command.write.unreadable", USER_FILE.toString()));
        }

        // A set-aside entry under a full item id replaces the usable part parsed from it.
        JsonObject root = OverrideJsonCodec.toJson(USER_OVERRIDES);
        USER_SET_ASIDE.forEach(root::add);
        try {
            Files.createDirectories(USER_FILE.getParent());
            Files.writeString(USER_FILE, PRETTY_GSON.toJson(root));
        } catch (IOException e) {
            SomeStacksCommon.LOGGER.error("Failed to write {}", USER_FILE, e);
            return new WriteResult(
                    false,
                    Component.translatable(
                            "somestacks.command.write.failure",
                            USER_FILE.toString(),
                            e.getMessage()));
        }
        return new WriteResult(
                true,
                Component.translatable(
                        "somestacks.command.write.success", root.size(), USER_FILE.toString()));
    }

    /**
     * Dumps the resolved presentation of every item in these namespaces, one file per namespace
     * under {@code generated_overrides}, and reports the result to the player. Entries are
     * complete. The generated directory is output only, not an active override layer, so dumping a
     * modpack does not freeze its current profiles into the user layer. Each file can be edited and
     * copied into a resource pack's {@code item_render_overrides}.
     *
     * <p>Every item a layer does not configure is measured here rather than at first sight of it,
     * so a dump of a large pack is the measurement pass for all of it. The results reach the
     * measured cache, which is saved once the dump is done rather than waiting for logout.
     *
     * @param namespaces installed item namespaces already selected by the client commands
     */
    public static void handleDumpRequest(List<String> namespaces) {
        Map<String, Map<ResourceLocation, ItemRenderConfig>> byNamespace = new LinkedHashMap<>();
        for (String namespace : namespaces) {
            byNamespace.put(namespace, new HashMap<>());
        }

        for (Map.Entry<ResourceKey<Item>, Item> entry : BuiltInRegistries.ITEM.entrySet()) {
            ResourceLocation itemId = entry.getKey().location();
            Map<ResourceLocation, ItemRenderConfig> namespaceEntries =
                    byNamespace.get(itemId.getNamespace());
            if (namespaceEntries == null) {
                continue;
            }

            RenderProfile profile = resolve(new ItemStack(entry.getValue()));
            if (profile != null) {
                namespaceEntries.put(
                        itemId,
                        new ItemRenderConfig(profile.mode(), profile.scale(), profile.offset()));
            }
        }

        AutoRenderProfiles.saveCache();

        int itemCount = 0;
        List<String> failed = new ArrayList<>();
        try {
            Files.createDirectories(GENERATED_DIR);
        } catch (IOException e) {
            SomeStacksCommon.LOGGER.error("Failed to create {}", GENERATED_DIR, e);
            report(
                    Component.translatable(
                            "somestacks.command.dump.create_failure",
                            GENERATED_DIR.toString(),
                            e.getMessage()));
            return;
        }

        for (Map.Entry<String, Map<ResourceLocation, ItemRenderConfig>> entry :
                byNamespace.entrySet()) {
            Path file = GENERATED_DIR.resolve(entry.getKey() + ".json").normalize();
            try {
                Files.writeString(
                        file, PRETTY_GSON.toJson(OverrideJsonCodec.toJson(entry.getValue())));
                itemCount += entry.getValue().size();
            } catch (IOException e) {
                SomeStacksCommon.LOGGER.error("Failed to write {}", file, e);
                failed.add(entry.getKey());
            }
        }

        report(
                failed.isEmpty()
                        ? Component.translatable(
                                "somestacks.command.dump.success",
                                itemCount,
                                byNamespace.size(),
                                GENERATED_DIR.toString())
                        : Component.translatable(
                                "somestacks.command.dump.partial",
                                itemCount,
                                byNamespace.size() - failed.size(),
                                GENERATED_DIR.toString(),
                                failed.size(),
                                String.join(", ", failed)));
    }

    private static void report(Component message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(message, false);
        }
    }

    /**
     * Reads the user file once. Edits this session made while the file was unreadable take
     * precedence over its entries for the same items.
     */
    private static void ensureUserFileLoaded() {
        if (userFileState != UserFileState.UNREAD) {
            return;
        }
        if (!Files.exists(USER_FILE)) {
            userFileState = UserFileState.LOADED;
            return;
        }

        JsonObject json;
        try {
            json = GSON.fromJson(Files.readString(USER_FILE), JsonObject.class);
        } catch (IOException | JsonParseException e) {
            userFileState = UserFileState.UNREADABLE;
            SomeStacksCommon.LOGGER.warn(
                    "Ignoring user render overrides in {} until it is fixed: {}. Resource-pack and"
                            + " measured profiles stay in effect, and /ss write changed will not"
                            + " overwrite the file.",
                    USER_FILE,
                    e.getMessage());
            return;
        }
        userFileState = UserFileState.LOADED;

        OverrideJsonCodec.Document document =
                json == null
                        ? new OverrideJsonCodec.Document(Map.of(), Map.of())
                        : OverrideJsonCodec.parse(json, USER_FILE.toString());
        Set<ResourceLocation> edited = new HashSet<>(USER_OVERRIDES.keySet());
        document.entries().forEach(USER_OVERRIDES::putIfAbsent);
        document.malformed()
                .forEach((key, value) -> {
                    if (!edited.contains(ResourceLocation.tryParse(key))) {
                        USER_SET_ASIDE.put(key, value);
                    }
                });
        clearResolvedProfiles();
        SomeStacksCommon.LOGGER.info(
                "Loaded {} user render override(s) from {}; {} malformed entr(ies) kept in the"
                        + " file unchanged",
                document.entries().size(),
                USER_FILE,
                USER_SET_ASIDE.size());
    }
}
