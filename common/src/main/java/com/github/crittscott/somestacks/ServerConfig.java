package com.github.crittscott.somestacks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * The server config values and the resolved lookup sets behind them, loaded from and saved to a
 * per-world JSON file the loader's entry point locates and hands to {@link #load(Path)}.
 *
 * <p>The text lists are stored as patterns and baked into sets that the hot paths can test cheaply.
 * Baking happens on load and whenever a list is edited, and for the ingot list, whenever item tags
 * are rebuilt with {@link #rebakeIngots()}, since its {@code #} entries name item tags whose
 * membership a data pack decides. The baked sets are volatile because a rebake can run off the main
 * thread relative to gameplay reads.
 *
 * <p>Everything here is server-side. Only the stack-type enable flags reach the client, through the
 * configuration sync.
 */
public final class ServerConfig {
    private ServerConfig() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final int DEFAULT_MAX_PILE_HEIGHT = 8;
    private static final int DEFAULT_GALLERY_PLACEMENTS_PER_TICK = 64;
    private static final int DEFAULT_GALLERY_PERMISSION_LEVEL = 3;

    /** Upper bound accepted for {@code piles.max_pile_height} in the config file. */
    private static final int MAX_PILE_HEIGHT_LIMIT = 64;

    /** Upper bound accepted for {@code render_gallery.required_permission_level}, vanilla's top op level. */
    private static final int GALLERY_PERMISSION_LEVEL_MAX = 4;

    private static int maxPileHeight = DEFAULT_MAX_PILE_HEIGHT;
    private static boolean enableStorageStackBlock = true;
    private static boolean enableSinglesStackBlock = true;
    private static boolean enableBarStackBlock = true;
    private static List<String> disableModsRaw = new ArrayList<>();
    private static List<String> disableItemsRaw = new ArrayList<>();
    private static List<String> ingotsRaw = new ArrayList<>(List.of("#forge:ingots*", "#somestacks:ingots"));
    private static int renderGalleryPlacementsPerTick = DEFAULT_GALLERY_PLACEMENTS_PER_TICK;
    private static boolean galleryEnabled = false;
    private static int galleryPermissionLevel = DEFAULT_GALLERY_PERMISSION_LEVEL;
    private static List<String> genModsRaw = new ArrayList<>();
    private static List<String> genItemsRaw = new ArrayList<>();

    private static Path configFile;
    private static boolean commonIngotDefaults;

    private static volatile Set<String> disabledMods = Set.of();
    private static volatile Set<ResourceLocation> disabledItems = Set.of();
    private static volatile Set<Item> ingotItems = Set.of();
    private static final AtomicInteger ingotGeneration = new AtomicInteger();

    /** A mutable, named text list backed by the config, the way {@code /ss} list editing addresses one. */
    public static final class ListSetting {
        private final Supplier<List<String>> getter;
        private final Consumer<List<String>> setter;

        private ListSetting(Supplier<List<String>> getter, Consumer<List<String>> setter) {
            this.getter = getter;
            this.setter = setter;
        }

        public List<String> get() {
            return getter.get();
        }

        private void set(List<String> value) {
            setter.accept(List.copyOf(value));
        }
    }

    public static final ListSetting DISABLE_MODS = new ListSetting(() -> disableModsRaw, v -> disableModsRaw = v);
    public static final ListSetting DISABLE_ITEMS = new ListSetting(() -> disableItemsRaw, v -> disableItemsRaw = v);
    public static final ListSetting INGOTS = new ListSetting(() -> ingotsRaw, v -> ingotsRaw = v);
    public static final ListSetting GEN_MODS = new ListSetting(() -> genModsRaw, v -> genModsRaw = v);
    public static final ListSetting GEN_ITEMS = new ListSetting(() -> genItemsRaw, v -> genItemsRaw = v);

    public static int maxPileHeight() {
        return maxPileHeight;
    }

    public static boolean enableStorageStackBlock() {
        return enableStorageStackBlock;
    }

    public static boolean enableSinglesStackBlock() {
        return enableSinglesStackBlock;
    }

    public static boolean enableBarStackBlock() {
        return enableBarStackBlock;
    }

    public static int renderGalleryPlacementsPerTick() {
        return renderGalleryPlacementsPerTick;
    }

    /** Whether {@code ss gallery} and {@code ss ingotgallery} may be run at all. Off by default. */
    public static boolean galleryEnabled() {
        return galleryEnabled;
    }

    /** The vanilla permission level {@code ss gallery} and {@code ss ingotgallery} require. */
    public static int galleryRequiredPermissionLevel() {
        return galleryPermissionLevel;
    }

    /**
     * Selects the {@code c:} common-tag convention for a new config, used by loaders that do not
     * populate the {@code forge:} tags (Fabric and NeoForge). An existing file remains authoritative.
     */
    public static void useCommonIngotDefaults() {
        commonIngotDefaults = true;
        if (configFile == null) {
            ingotsRaw = new ArrayList<>(List.of("#c:ingots*", "#somestacks:ingots"));
        }
    }

    // --- Loading and saving ---

    /**
     * Loads the config from {@code file}, writing it with defaults first if it does not exist yet.
     * Remembers {@code file} so later edits save back to it, then bakes the lookup sets.
     */
    public static void load(Path file) {
        configFile = file;
        Settings settings = defaults();

        if (Files.exists(file)) {
            try {
                JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
                if (root == null) {
                    throw new IllegalArgumentException("root is null");
                }
                settings = parseJson(root, settings);
                SomeStacksCommon.LOGGER.info("Loaded server config from {}", displayPath(file));
            } catch (Exception e) {
                SomeStacksCommon.LOGGER.warn("Failed to read {}: {}", file, e.getMessage());
            }
        }

        apply(settings);
        if (!Files.exists(file) && save()) {
            SomeStacksCommon.LOGGER.info("Created default server config at {}", displayPath(file));
        }

        bakeServerLists();
    }

    /** Re-reads the current world's policy file. */
    public static boolean reload() {
        if (configFile == null) {
            return false;
        }
        load(configFile);
        return true;
    }

    private static Settings parseJson(JsonObject root, Settings fallback) {
        JsonObject piles = obj(root, "piles");
        int parsedMaxPileHeight = clamp(
                intOr(piles, "max_pile_height", fallback.maxPileHeight()),
                1, MAX_PILE_HEIGHT_LIMIT);

        JsonObject stacks = obj(root, "stacks");
        boolean parsedStorageEnabled = boolOr(
                stacks, "enable_storage_stack_block", fallback.enableStorageStackBlock());
        boolean parsedSinglesEnabled = boolOr(
                stacks, "enable_singles_stack_block", fallback.enableSinglesStackBlock());
        boolean parsedBarEnabled = boolOr(
                stacks, "enable_bar_stack_block", fallback.enableBarStackBlock());

        JsonObject compatibility = obj(root, "compatibility");
        List<String> parsedDisableMods = stringListOr(
                compatibility, "disable_mods", fallback.disableMods());
        List<String> parsedDisableItems = stringListOr(
                compatibility, "disable_items", fallback.disableItems());
        List<String> parsedIngots = stringListOr(
                compatibility, "ingots", fallback.ingots());

        JsonObject gallery = obj(root, "render_gallery");
        int parsedPlacementsPerTick = Math.max(
                1, intOr(gallery, "placements_per_tick", fallback.renderGalleryPlacementsPerTick()));
        boolean parsedGalleryEnabled = boolOr(
                gallery, "enabled", fallback.galleryEnabled());
        int parsedPermissionLevel = clamp(
                intOr(gallery, "required_permission_level", fallback.galleryPermissionLevel()),
                0, GALLERY_PERMISSION_LEVEL_MAX);
        List<String> parsedGenMods = stringListOr(gallery, "gen_mods", fallback.genMods());
        List<String> parsedGenItems = stringListOr(gallery, "gen_items", fallback.genItems());

        return new Settings(
                parsedMaxPileHeight,
                parsedStorageEnabled,
                parsedSinglesEnabled,
                parsedBarEnabled,
                parsedDisableMods,
                parsedDisableItems,
                parsedIngots,
                parsedPlacementsPerTick,
                parsedGalleryEnabled,
                parsedPermissionLevel,
                parsedGenMods,
                parsedGenItems);
    }

    private static Settings defaults() {
        return new Settings(
                DEFAULT_MAX_PILE_HEIGHT,
                true,
                true,
                true,
                List.of(),
                List.of(),
                commonIngotDefaults
                        ? List.of("#c:ingots*", "#somestacks:ingots")
                        : List.of("#forge:ingots*", "#somestacks:ingots"),
                DEFAULT_GALLERY_PLACEMENTS_PER_TICK,
                false,
                DEFAULT_GALLERY_PERMISSION_LEVEL,
                List.of(),
                List.of());
    }

    private static void apply(Settings settings) {
        maxPileHeight = settings.maxPileHeight();
        enableStorageStackBlock = settings.enableStorageStackBlock();
        enableSinglesStackBlock = settings.enableSinglesStackBlock();
        enableBarStackBlock = settings.enableBarStackBlock();
        disableModsRaw = settings.disableMods();
        disableItemsRaw = settings.disableItems();
        ingotsRaw = settings.ingots();
        renderGalleryPlacementsPerTick = settings.renderGalleryPlacementsPerTick();
        galleryEnabled = settings.galleryEnabled();
        galleryPermissionLevel = settings.galleryPermissionLevel();
        genModsRaw = settings.genMods();
        genItemsRaw = settings.genItems();
    }

    /**
     * Writes the current values to the file {@link #load} was given.
     *
     * @return whether the file was written; false before the first load or after a write failure
     */
    public static boolean save() {
        if (configFile == null) {
            return false;
        }

        JsonObject piles = new JsonObject();
        piles.addProperty("max_pile_height", maxPileHeight);

        JsonObject stacks = new JsonObject();
        stacks.addProperty("enable_storage_stack_block", enableStorageStackBlock);
        stacks.addProperty("enable_singles_stack_block", enableSinglesStackBlock);
        stacks.addProperty("enable_bar_stack_block", enableBarStackBlock);

        JsonObject compatibility = new JsonObject();
        compatibility.add("disable_mods", stringArray(disableModsRaw));
        compatibility.add("disable_items", stringArray(disableItemsRaw));
        compatibility.add("ingots", stringArray(ingotsRaw));

        JsonObject gallery = new JsonObject();
        gallery.addProperty("placements_per_tick", renderGalleryPlacementsPerTick);
        gallery.addProperty("enabled", galleryEnabled);
        gallery.addProperty("required_permission_level", galleryPermissionLevel);
        gallery.add("gen_mods", stringArray(genModsRaw));
        gallery.add("gen_items", stringArray(genItemsRaw));

        JsonObject root = new JsonObject();
        root.add("piles", piles);
        root.add("stacks", stacks);
        root.add("compatibility", compatibility);
        root.add("render_gallery", gallery);

        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, GSON.toJson(root));
            return true;
        } catch (IOException e) {
            SomeStacksCommon.LOGGER.warn("Failed to write {}: {}", configFile, e.getMessage());
            return false;
        }
    }

    private static Path displayPath(Path file) {
        return file.toAbsolutePath().normalize();
    }

    private static JsonObject obj(JsonObject parent, String key) {
        if (!parent.has(key)) {
            return new JsonObject();
        }
        if (parent.get(key).isJsonObject()) {
            return parent.getAsJsonObject(key);
        }
        SomeStacksCommon.LOGGER.warn("Ignoring non-object server config section '{}'", key);
        return new JsonObject();
    }

    private static int intOr(JsonObject obj, String key, int fallback) {
        if (!obj.has(key)) {
            return fallback;
        }
        try {
            if (obj.get(key).isJsonPrimitive() && obj.getAsJsonPrimitive(key).isNumber()) {
                return Integer.parseInt(obj.get(key).getAsString());
            }
        } catch (RuntimeException ignored) {
        }
        SomeStacksCommon.LOGGER.warn("Ignoring non-integer server config field '{}'", key);
        return fallback;
    }

    private static boolean boolOr(JsonObject obj, String key, boolean fallback) {
        if (!obj.has(key)) {
            return fallback;
        }
        if (obj.get(key).isJsonPrimitive() && obj.getAsJsonPrimitive(key).isBoolean()) {
            return obj.get(key).getAsBoolean();
        }
        SomeStacksCommon.LOGGER.warn("Ignoring non-boolean server config field '{}'", key);
        return fallback;
    }

    private static List<String> stringListOr(JsonObject obj, String key, List<String> fallback) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) {
            if (obj.has(key)) {
                SomeStacksCommon.LOGGER.warn("Ignoring non-array server config field '{}'", key);
            }
            return fallback;
        }
        List<String> out = new ArrayList<>();
        for (JsonElement entry : obj.getAsJsonArray(key)) {
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                out.add(entry.getAsString());
            } else {
                SomeStacksCommon.LOGGER.warn("Ignoring non-string entry in server config field '{}'", key);
            }
        }
        return List.copyOf(out);
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Settings(
            int maxPileHeight,
            boolean enableStorageStackBlock,
            boolean enableSinglesStackBlock,
            boolean enableBarStackBlock,
            List<String> disableMods,
            List<String> disableItems,
            List<String> ingots,
            int renderGalleryPlacementsPerTick,
            boolean galleryEnabled,
            int galleryPermissionLevel,
            List<String> genMods,
            List<String> genItems) {}

    // --- Baking ---

    /**
     * Resolves the server's free-form text lists into lookup sets: the disabled-mod and
     * disabled-item compatibility lists, and the ingot list. Mod ids are lowercased and item ids
     * are parsed once here; a malformed item id is reported and dropped rather than being re-parsed
     * and swallowed on every deposit.
     */
    public static void bakeServerLists() {
        Set<String> mods = new HashSet<>();
        for (String entry : DISABLE_MODS.get()) {
            String modId = entry.trim().toLowerCase(Locale.ROOT);
            if (!modId.isEmpty()) {
                mods.add(modId);
            }
        }

        Set<ResourceLocation> items = new HashSet<>();
        for (String entry : DISABLE_ITEMS.get()) {
            ResourceLocation itemId = ResourceLocation.tryParse(entry.trim().toLowerCase(Locale.ROOT));
            if (itemId == null) {
                SomeStacksCommon.LOGGER.warn("Ignoring malformed item id \"{}\" in disable_items", entry);
                continue;
            }
            items.add(itemId);
        }

        disabledMods = Set.copyOf(mods);
        disabledItems = Set.copyOf(items);

        SomeStacksCommon.LOGGER.debug("Baked server lists: {} disabled mod(s), {} disabled item(s)",
                disabledMods.size(), disabledItems.size());

        bakeIngotItems();
    }

    /**
     * Resolves the ingot list into the set of items a Bar Stack accepts. A {@code #}-prefixed entry
     * is an item-tag name that may carry {@code *} wildcards, and contributes the contents of every
     * bound tag whose name it matches; any other entry is an item id and contributes that one item.
     */
    private static void bakeIngotItems() {
        List<Pattern> tagPatterns = new ArrayList<>();
        List<String> tagGlobs = new ArrayList<>();
        List<String> itemEntries = new ArrayList<>();
        for (String raw : INGOTS.get()) {
            String entry = raw.trim().toLowerCase(Locale.ROOT);
            if (entry.isEmpty()) {
                continue;
            }
            if (entry.startsWith("#")) {
                String glob = entry.substring(1);
                tagPatterns.add(globToPattern(glob));
                tagGlobs.add(glob);
            } else {
                itemEntries.add(entry);
            }
        }

        Registry<Item> itemRegistry = BuiltInRegistries.ITEM;
        Set<Item> items = new HashSet<>();
        int[] matchedTags = new int[tagPatterns.size()];
        int knownTags = 0;

        for (HolderSet.Named<Item> tag : itemRegistry.getTags().toList()) {
            knownTags++;
            String tagName = tag.key().location().toString();
            for (int i = 0; i < tagPatterns.size(); i++) {
                if (!tagPatterns.get(i).matcher(tagName).matches()) {
                    continue;
                }
                matchedTags[i]++;
                tag.forEach(holder -> items.add(holder.value()));
            }
        }

        List<String> unknownItems = new ArrayList<>();
        for (String entry : itemEntries) {
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id != null && itemRegistry.containsKey(id)) {
                items.add(itemRegistry.getValue(id));
            } else {
                unknownItems.add(entry);
            }
        }

        ingotItems = Set.copyOf(items);
        ingotGeneration.incrementAndGet();

        if (knownTags == 0) {
            // Before a level is loaded there are no tags to walk; a later rebake fills this in.
            return;
        }

        for (int i = 0; i < tagPatterns.size(); i++) {
            if (matchedTags[i] == 0) {
                SomeStacksCommon.LOGGER.warn("Ingot entry \"#{}\" matches no item tag", tagGlobs.get(i));
            }
        }
        for (String entry : unknownItems) {
            SomeStacksCommon.LOGGER.warn("Ingot entry \"{}\" is not a registered item", entry);
        }

        SomeStacksCommon.LOGGER.info(
                "Baked ingots: {} tag pattern(s) + {} item(s) over {} item tag(s) accept {} item(s)",
                tagPatterns.size(), itemEntries.size(), knownTags, ingotItems.size());
    }

    /**
     * Compiles one {@code #} ingot entry into a matcher over whole tag names, where {@code *} stands
     * for a run of any characters and every other character is literal.
     */
    private static Pattern globToPattern(String glob) {
        StringBuilder regex = new StringBuilder();
        boolean first = true;
        for (String part : glob.split("\\*", -1)) {
            if (!first) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
            first = false;
        }
        return Pattern.compile(regex.toString());
    }

    /**
     * Re-resolves the ingot list against the tags just bound. Item tags are data pack state, so the
     * set of items a Bar Stack accepts changes with a data pack reload even though the config naming
     * those tags has not. The loader's entry point calls this from its own tags-updated hook.
     */
    public static void rebakeIngots() {
        if (configFile == null) {
            return;
        }
        bakeIngotItems();
    }

    /**
     * Adds an entry to one of the server's text lists, re-bakes the lookup sets, and saves.
     *
     * @return whether the entry was added; false when the list already contains it
     */
    public static boolean addListEntry(ListSetting list, String entry) {
        List<String> updated = new ArrayList<>(list.get());
        for (String existing : updated) {
            if (existing.equalsIgnoreCase(entry)) {
                return false;
            }
        }

        updated.add(entry);
        list.set(updated);
        bakeServerLists();
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

        list.set(updated);
        bakeServerLists();
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

    /** Whether {@code item} is an ingot, which is what a Bar Stack holds and a Singles Stack refuses. */
    public static boolean isIngotItem(Item item) {
        return ingotItems.contains(item);
    }

    /** How many items the ingot list currently resolves to. */
    public static int ingotItemCount() {
        return ingotItems.size();
    }

    /**
     * Every item tag name the server knows, for completing a {@code #} ingot entry. Read on demand
     * rather than cached: tags change with a data pack reload, and a completion request is rare
     * next to the deposits the baked set serves.
     */
    public static List<String> itemTagNames() {
        List<String> names = new ArrayList<>();
        BuiltInRegistries.ITEM.getTags().forEach(tag -> names.add(tag.key().location().toString()));
        Collections.sort(names);
        return names;
    }

    /**
     * How many times the ingot item set has been resolved. A caller that groups or filters by
     * ingot-ness holds this alongside its own cache and rebuilds when it changes, which covers a
     * data pack reload and a config edit alike.
     */
    public static int ingotGeneration() {
        return ingotGeneration.get();
    }
}
