package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.command.CommandNetwork;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Per-world immutable policy and resolved admission sets; loaders own persistence. */
public final class ServerConfig {
    private ServerConfig() {}

    /** Section and field names shared by the loader config formats. */
    public static final class Keys {
        private Keys() {}

        public static final String PILES = "piles";
        public static final String MAX_PILE_HEIGHT = "max_pile_height";
        public static final String STACKS = "stacks";
        public static final String ENABLE_STORAGE_STACK_BLOCK = "enable_storage_stack_block";
        public static final String ENABLE_SINGLES_STACK_BLOCK = "enable_singles_stack_block";
        public static final String ENABLE_BAR_STACK_BLOCK = "enable_bar_stack_block";
        public static final String COMPATIBILITY = "compatibility";
        public static final String DISABLE_MODS = "disable_mods";
        public static final String DISABLE_ITEMS = "disable_items";
        public static final String RENDER_GALLERY = "render_gallery";
        public static final String PLACEMENTS_PER_TICK = "placements_per_tick";
        public static final String ENABLED = "enabled";
        public static final String REQUIRED_PERMISSION_LEVEL = "required_permission_level";
        public static final String GEN_MODS = "gen_mods";
        public static final String GEN_ITEMS = "gen_items";
    }

    public static final int MIN_PILE_HEIGHT = 1;
    public static final int MIN_PLACEMENTS_PER_TICK = 1;
    public static final int MIN_GALLERY_PERMISSION_LEVEL = Commands.LEVEL_ALL;
    public static final int DEFAULT_MAX_PILE_HEIGHT = 8;
    public static final int DEFAULT_PLACEMENTS_PER_TICK = 64;
    public static final int DEFAULT_GALLERY_PERMISSION_LEVEL = Commands.LEVEL_ADMINS;
    public static final int MAX_PILE_HEIGHT_LIMIT = 64;
    public static final int GALLERY_PERMISSION_LEVEL_MAX = Commands.LEVEL_OWNERS;
    /** Bounds for each namespace list, shared by policy publication and the wire decoder. */
    public static final int MAX_NAMESPACE_ENTRIES = 256;
    public static final int MAX_NAMESPACE_LENGTH = 256;
    /** Items whose models are known to crash clients when drawn. */
    public static final List<String> DEFAULT_DISABLE_ITEMS = List.of("evilcraft:broom_part");

    private static volatile Settings settings = Settings.defaults();
    private static Supplier<Settings> reader;
    private static Consumer<Settings> saver;
    private static Runnable syncClients;
    private static Runnable refreshCommands;
    private static volatile Set<String> disabledMods = Set.of();
    private static volatile Set<ResourceLocation> disabledItems = Set.of();

    /** One immutable publication of server policy; lists cannot be changed by readers. */
    public record Settings(
            int maxPileHeight,
            boolean enableStorageStackBlock,
            boolean enableSinglesStackBlock,
            boolean enableBarStackBlock,
            List<String> disableMods,
            List<String> disableItems,
            int renderGalleryPlacementsPerTick,
            boolean galleryEnabled,
            int galleryPermissionLevel,
            List<String> genMods,
            List<String> genItems) {
        public Settings {
            maxPileHeight = Math.max(MIN_PILE_HEIGHT, Math.min(MAX_PILE_HEIGHT_LIMIT, maxPileHeight));
            renderGalleryPlacementsPerTick = Math.max(MIN_PLACEMENTS_PER_TICK, renderGalleryPlacementsPerTick);
            galleryPermissionLevel = Math.max(MIN_GALLERY_PERMISSION_LEVEL,
                    Math.min(GALLERY_PERMISSION_LEVEL_MAX, galleryPermissionLevel));
            disableMods = List.copyOf(disableMods);
            disableItems = List.copyOf(disableItems);
            genMods = List.copyOf(genMods);
            genItems = List.copyOf(genItems);
        }

        public static Settings defaults() {
            return new Settings(
                    DEFAULT_MAX_PILE_HEIGHT,
                    true,
                    true,
                    true,
                    List.of(),
                    DEFAULT_DISABLE_ITEMS,
                    DEFAULT_PLACEMENTS_PER_TICK,
                    false,
                    DEFAULT_GALLERY_PERMISSION_LEVEL,
                    List.of(),
                    List.of());
        }

        private Settings withList(ListSetting list, List<String> value) {
            return new Settings(
                    maxPileHeight,
                    enableStorageStackBlock,
                    enableSinglesStackBlock,
                    enableBarStackBlock,
                    list == ListSetting.DISABLE_MODS ? value : disableMods,
                    list == ListSetting.DISABLE_ITEMS ? value : disableItems,
                    renderGalleryPlacementsPerTick,
                    galleryEnabled,
                    galleryPermissionLevel,
                    list == ListSetting.GEN_MODS ? value : genMods,
                    list == ListSetting.GEN_ITEMS ? value : genItems);
        }
    }

    /** The named lists edited by /ss; each edit replaces the complete policy snapshot. */
    public enum ListSetting {
        DISABLE_MODS, DISABLE_ITEMS, GEN_MODS, GEN_ITEMS;

        public List<String> get() {
            Settings current = settings;
            return switch (this) {
                case DISABLE_MODS -> current.disableMods();
                case DISABLE_ITEMS -> current.disableItems();
                case GEN_MODS -> current.genMods();
                case GEN_ITEMS -> current.genItems();
            };
        }
    }

    public static Settings settings() {
        return settings;
    }

    public static int maxPileHeight() {
        return settings.maxPileHeight();
    }

    public static boolean enableStorageStackBlock() {
        return settings.enableStorageStackBlock();
    }

    public static boolean enableSinglesStackBlock() {
        return settings.enableSinglesStackBlock();
    }

    public static boolean enableBarStackBlock() {
        return settings.enableBarStackBlock();
    }

    public static int renderGalleryPlacementsPerTick() {
        return settings.renderGalleryPlacementsPerTick();
    }

    public static boolean galleryEnabled() {
        return settings.galleryEnabled();
    }

    public static int galleryRequiredPermissionLevel() {
        return settings.galleryPermissionLevel();
    }

    /**
     * Starts a world with its loader's policy reader and command-save boundary, and publishes the
     * policy read now.
     */
    public static void start(Supplier<Settings> readPolicy, Consumer<Settings> savePolicy,
                             Runnable synchronizeClients) {
        reader = Objects.requireNonNull(readPolicy, "Policy reader");
        saver = Objects.requireNonNull(savePolicy, "Policy saver");
        syncClients = Objects.requireNonNull(synchronizeClients, "Policy synchronization");
        publish(reader.get());
        logActivePolicy();
    }

    /**
     * Rereads the world's policy through its loader and publishes it.
     *
     * @return false when no world is running or the replacement policy exceeds namespace limits
     */
    public static boolean reload() {
        if (reader == null) {
            return false;
        }
        return publish(reader.get());
    }

    /** Publishes prepared policy; an over-limit replacement leaves the current policy intact. */
    public static boolean publish(Settings replacement) {
        Objects.requireNonNull(replacement, "Server policy");
        try {
            replacement = replacement.withList(ListSetting.DISABLE_MODS,
                    prepareNamespaces(replacement.disableMods(), Keys.DISABLE_MODS));
            replacement = replacement.withList(ListSetting.GEN_MODS,
                    prepareNamespaces(replacement.genMods(), Keys.GEN_MODS));
        } catch (IllegalArgumentException rejected) {
            SomeStacksCommon.LOGGER.warn("Keeping previous server policy: {}", rejected.getMessage());
            return false;
        }
        boolean syncedFieldsChanged = settings.enableStorageStackBlock() != replacement.enableStorageStackBlock()
                || settings.enableSinglesStackBlock() != replacement.enableSinglesStackBlock()
                || settings.enableBarStackBlock() != replacement.enableBarStackBlock();
        boolean galleryChanged = settings.galleryEnabled() != replacement.galleryEnabled()
                || settings.galleryPermissionLevel() != replacement.galleryPermissionLevel();
        settings = replacement;
        bakeServerLists();
        if (galleryChanged && refreshCommands != null) refreshCommands.run();
        if (syncedFieldsChanged && syncClients != null) {
            syncClients.run();
        }
        return true;
    }

    private static List<String> prepareNamespaces(List<String> entries, String label) {
        Set<String> result = new LinkedHashSet<>();
        for (String entry : entries) {
            String namespace = entry.trim().toLowerCase(Locale.ROOT);
            if (namespace.isEmpty()) continue;
            if (!isValidNamespace(namespace)) {
                SomeStacksCommon.LOGGER.warn("Ignoring malformed namespace '{}' in {}", entry, label);
                continue;
            }
            if (namespace.length() > MAX_NAMESPACE_LENGTH) {
                throw new IllegalArgumentException(label + " contains a namespace longer than "
                        + MAX_NAMESPACE_LENGTH + " characters");
            }
            result.add(namespace);
            if (result.size() > MAX_NAMESPACE_ENTRIES) {
                throw new IllegalArgumentException(label + " exceeds " + MAX_NAMESPACE_ENTRIES
                        + " namespaces");
            }
        }
        return List.copyOf(result);
    }

    /** Validates namespace syntax through the public identifier parser with a fixed valid path. */
    public static boolean isValidNamespace(String namespace) {
        return !namespace.isEmpty() && ResourceLocation.tryParse(namespace + ":_") != null;
    }

    /** Writes command edits through the current world's loader. */
    public static void save() {
        if (saver != null) {
            saver.accept(settings);
        }
    }

    /** Releases the world reader, saver, and derived admission state on server shutdown. */
    public static void clear() {
        reader = null;
        saver = null;
        syncClients = null;
        refreshCommands = null;
        settings = Settings.defaults();
        disabledMods = Set.of();
        disabledItems = Set.of();
    }

    // --- Baking ---

    /**
     * Builds admission sets from prepared namespaces and free-form item ids. Malformed item ids
     * are reported here rather than reparsed on every deposit.
     */
    public static void bakeServerLists() {
        Set<ResourceLocation> items = new HashSet<>();
        for (String entry : ListSetting.DISABLE_ITEMS.get()) {
            ResourceLocation itemId = ResourceLocation.tryParse(entry.trim().toLowerCase(Locale.ROOT));
            if (itemId == null) {
                SomeStacksCommon.LOGGER.warn("Ignoring malformed item id \"{}\" in disable_items", entry);
                continue;
            }
            items.add(itemId);
        }

        RenderGalleryGenerator.invalidateBarItems();
        disabledMods = Set.copyOf(settings.disableMods());
        disabledItems = Set.copyOf(items);
    }

    /** Reports active policy at startup, explicit reload, or a changed loader file reload. */
    public static void logActivePolicy() {
        Settings current = settings;
        SomeStacksCommon.LOGGER.info(
                "Server policy active: Storage={}, Singles={}, Bar={}; maximum pile height={}; disabled mods={}, disabled items={}, gallery mods={}, gallery items={}",
                current.enableStorageStackBlock(), current.enableSinglesStackBlock(), current.enableBarStackBlock(),
                current.maxPileHeight(), disabledMods.size(), disabledItems.size(),
                current.genMods().size(), current.genItems().size());
    }

    /**
     * Adds an entry to one of the server's text lists, re-bakes the lookup sets, and saves.
     *
     * @return whether the entry was added; false when the list already contains it
     * @throws IllegalArgumentException if a namespace is malformed or exceeds policy limits
     */
    public static boolean addListEntry(ListSetting list, String entry) {
        if (list == ListSetting.DISABLE_MODS || list == ListSetting.GEN_MODS) {
            entry = entry.trim().toLowerCase(Locale.ROOT);
            if (entry.length() > MAX_NAMESPACE_LENGTH || !isValidNamespace(entry)) {
                throw new IllegalArgumentException("Invalid namespace");
            }
        }
        List<String> updated = new ArrayList<>(list.get());
        for (String existing : updated) {
            if (existing.equalsIgnoreCase(entry)) {
                return false;
            }
        }

        updated.add(entry);
        if (!publish(settings.withList(list, updated))) {
            throw new IllegalArgumentException("Namespace list exceeds policy limits");
        }
        save();
        return true;
    }

    /**
     * Removes an entry from one of the server's text lists, re-bakes the lookup sets, and saves.
     *
     * @return whether an entry was removed; false when the list does not contain it
     */
    public static boolean removeListEntry(ListSetting list, String entry) {
        List<String> updated = new ArrayList<>(list.get());
        if (!updated.removeIf(existing -> existing.equalsIgnoreCase(entry))) {
            return false;
        }

        publish(settings.withList(list, updated));
        save();
        return true;
    }

    /** Whether items from {@code namespace}, in any case, are barred from stacks. */
    public static boolean isModDisabled(String namespace) {
        return disabledMods.contains(namespace.toLowerCase(Locale.ROOT));
    }

    /** Whether the item registered as {@code itemId} is barred from stacks. */
    public static boolean isItemDisabled(ResourceLocation itemId) {
        return disabledItems.contains(itemId);
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Loads this world's JSON policy and connects changed-policy publication to its server. */
    public static void load(Path file, MinecraftServer server) {
        clear();
        refreshCommands = () -> {
            for (var player : server.getPlayerList().getPlayers()) server.getCommands().sendCommands(player);
        };
        start(() -> readPolicy(file), policy -> writePolicy(file, policy),
                () -> CommandNetwork.syncAllPlayers(server));
        if (!Files.exists(file)) save();
    }

    private static Settings readPolicy(Path file) {
        if (!Files.exists(file)) return Settings.defaults();
        try {
            JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (root == null) throw new IllegalArgumentException("Policy root is null");
            Settings fallback = Settings.defaults();
            JsonObject piles = obj(root, "piles");
            JsonObject stacks = obj(root, "stacks");
            JsonObject compatibility = obj(root, "compatibility");
            JsonObject gallery = obj(root, "render_gallery");
            return new Settings(intOr(piles, "max_pile_height", fallback.maxPileHeight()),
                    boolOr(stacks, "enable_storage_stack_block", true),
                    boolOr(stacks, "enable_singles_stack_block", true),
                    boolOr(stacks, "enable_bar_stack_block", true),
                    stringListOr(compatibility, "disable_mods", fallback.disableMods()),
                    stringListOr(compatibility, "disable_items", fallback.disableItems()),
                    intOr(gallery, "placements_per_tick", fallback.renderGalleryPlacementsPerTick()),
                    boolOr(gallery, "enabled", fallback.galleryEnabled()),
                    intOr(gallery, "required_permission_level", fallback.galleryPermissionLevel()),
                    stringListOr(gallery, "gen_mods", fallback.genMods()),
                    stringListOr(gallery, "gen_items", fallback.genItems()));
        } catch (IOException | RuntimeException invalid) {
            SomeStacksCommon.LOGGER.warn("Keeping active policy after reading {}: {}", file, invalid.getMessage());
            return settings;
        }
    }

    private static void writePolicy(Path file, Settings policy) {
        JsonObject piles = new JsonObject();
        piles.addProperty("max_pile_height", policy.maxPileHeight());
        JsonObject stacks = new JsonObject();
        stacks.addProperty("enable_storage_stack_block", policy.enableStorageStackBlock());
        stacks.addProperty("enable_singles_stack_block", policy.enableSinglesStackBlock());
        stacks.addProperty("enable_bar_stack_block", policy.enableBarStackBlock());
        JsonObject compatibility = new JsonObject();
        compatibility.add("disable_mods", stringArray(policy.disableMods()));
        compatibility.add("disable_items", stringArray(policy.disableItems()));
        JsonObject gallery = new JsonObject();
        gallery.addProperty("placements_per_tick", policy.renderGalleryPlacementsPerTick());
        gallery.addProperty("enabled", policy.galleryEnabled());
        gallery.addProperty("required_permission_level", policy.galleryPermissionLevel());
        gallery.add("gen_mods", stringArray(policy.genMods()));
        gallery.add("gen_items", stringArray(policy.genItems()));
        JsonObject root = new JsonObject();
        root.add("piles", piles);
        root.add("stacks", stacks);
        root.add("compatibility", compatibility);
        root.add("render_gallery", gallery);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException failure) {
            SomeStacksCommon.LOGGER.warn("Failed to write {}: {}", file, failure.getMessage());
        }
    }

    private static JsonObject obj(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static int intOr(JsonObject obj, String key, int fallback) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsInt() : fallback;
    }

    private static boolean boolOr(JsonObject obj, String key, boolean fallback) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsBoolean() : fallback;
    }

    private static List<String> stringListOr(JsonObject obj, String key, List<String> fallback) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) {
            return fallback;
        }
        List<String> out = new ArrayList<>();
        for (JsonElement entry : obj.getAsJsonArray(key)) {
            out.add(entry.getAsString());
        }
        return out;
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

}
