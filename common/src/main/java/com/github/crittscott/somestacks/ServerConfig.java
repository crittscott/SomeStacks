package com.github.crittscott.somestacks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
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
import java.util.regex.Pattern;

/** Per-world immutable policy, JSON persistence, and resolved admission sets. */
public final class ServerConfig {
    private ServerConfig() {}

    private static final int MAX_PILE_HEIGHT_LIMIT = 64;
    private static final int GALLERY_PERMISSION_LEVEL_MAX = 4;
    private static boolean fabricDefaults;
    private static volatile Settings settings = Settings.defaults(false);
    private static Persistence persistence;
    private static volatile Set<String> disabledMods = Set.of();
    private static volatile Set<ResourceLocation> disabledItems = Set.of();
    private static volatile Set<Item> ingotItems = Set.of();
    private static final AtomicInteger ingotGeneration = new AtomicInteger();

    /** One immutable publication of server policy; lists cannot be changed by readers. */
    public record Settings(
            int maxPileHeight,
            boolean enableStorageStackBlock,
            boolean enableSinglesStackBlock,
            boolean enableBarStackBlock,
            List<String> disableMods,
            List<String> disableItems,
            List<String> ingotTags,
            int renderGalleryPlacementsPerTick,
            boolean galleryEnabled,
            int galleryPermissionLevel,
            List<String> genMods,
            List<String> genItems) {
        public Settings {
            disableMods = List.copyOf(disableMods);
            disableItems = List.copyOf(disableItems);
            ingotTags = List.copyOf(ingotTags);
            genMods = List.copyOf(genMods);
            genItems = List.copyOf(genItems);
        }

        static Settings defaults(boolean fabric) {
            return new Settings(
                    8,
                    true,
                    true,
                    true,
                    List.of(),
                    List.of(),
                    List.of(fabric ? "c:ingots*" : "forge:ingots*", "somestacks:ingots"),
                    64,
                    false,
                    3,
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
                    list == ListSetting.INGOT_TAGS ? value : ingotTags,
                    renderGalleryPlacementsPerTick,
                    galleryEnabled,
                    galleryPermissionLevel,
                    list == ListSetting.GEN_MODS ? value : genMods,
                    list == ListSetting.GEN_ITEMS ? value : genItems);
        }
    }

    /** The named lists edited by /ss; each edit replaces the complete policy snapshot. */
    public enum ListSetting {
        DISABLE_MODS, DISABLE_ITEMS, INGOT_TAGS, GEN_MODS, GEN_ITEMS;

        public List<String> get() {
            Settings current = settings;
            return switch (this) {
                case DISABLE_MODS -> current.disableMods();
                case DISABLE_ITEMS -> current.disableItems();
                case INGOT_TAGS -> current.ingotTags();
                case GEN_MODS -> current.genMods();
                case GEN_ITEMS -> current.genItems();
            };
        }

        private void set(List<String> value) {
            settings = settings.withList(this, value);
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

    /** Selects defaults for new Fabric worlds; existing files remain authoritative. */
    public static void useFabricIngotTagDefaults() {
        if (persistence == null) {
            fabricDefaults = true;
            settings = Settings.defaults(true);
        }
    }

    /** Starts a world session from loader defaults, never from the previous world's values. */
    public static void load(Path file) {
        persistence = new Persistence(file);
        settings = persistence.read(Settings.defaults(fabricDefaults));
        bakeServerLists();
    }

    /** Writes command edits immediately; /ss reload does not call this policy reader. */
    public static void save() {
        if (persistence != null) {
            persistence.write(settings);
        }
    }

    /** Releases the world path and derived admission state on server shutdown. */
    public static void clear() {
        persistence = null;
        settings = Settings.defaults(fabricDefaults);
        disabledMods = Set.of();
        disabledItems = Set.of();
        ingotItems = Set.of();
        ingotGeneration.incrementAndGet();
    }

    /** JSON boundary, independent of registry baking and gameplay. */
    static final class Persistence {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private final Path file;

        Persistence(Path file) {
            this.file = file;
        }

        Settings read(Settings defaults) {
            if (!Files.exists(file)) {
                write(defaults);
                return defaults;
            }
            try {
                JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
                return root == null ? defaults : decode(root, defaults);
            } catch (Exception exception) {
                SomeStacksCommon.LOGGER.warn("Failed to read {}: {}", file, exception.getMessage());
                return defaults;
            }
        }

        private static Settings decode(JsonObject root, Settings defaults) {
            JsonObject piles = obj(root, "piles");
            JsonObject stacks = obj(root, "stacks");
            JsonObject compatibility = obj(root, "compatibility");
            JsonObject gallery = obj(root, "render_gallery");
            return new Settings(
                    clamp(intOr(piles, "max_pile_height", defaults.maxPileHeight()), 1, MAX_PILE_HEIGHT_LIMIT),
                    boolOr(stacks, "enable_storage_stack_block", defaults.enableStorageStackBlock()),
                    boolOr(stacks, "enable_singles_stack_block", defaults.enableSinglesStackBlock()),
                    boolOr(stacks, "enable_bar_stack_block", defaults.enableBarStackBlock()),
                    stringListOr(compatibility, "disable_mods", defaults.disableMods()),
                    stringListOr(compatibility, "disable_items", defaults.disableItems()),
                    stringListOr(compatibility, "ingot_tags", defaults.ingotTags()),
                    Math.max(1, intOr(gallery, "placements_per_tick", defaults.renderGalleryPlacementsPerTick())),
                    boolOr(gallery, "enabled", defaults.galleryEnabled()),
                    clamp(intOr(gallery, "required_permission_level", defaults.galleryPermissionLevel()), 0, GALLERY_PERMISSION_LEVEL_MAX),
                    stringListOr(gallery, "gen_mods", defaults.genMods()),
                    stringListOr(gallery, "gen_items", defaults.genItems()));
        }

        void write(Settings values) {
            JsonObject piles = new JsonObject();
            piles.addProperty("max_pile_height", values.maxPileHeight());
            JsonObject stacks = new JsonObject();
            stacks.addProperty("enable_storage_stack_block", values.enableStorageStackBlock());
            stacks.addProperty("enable_singles_stack_block", values.enableSinglesStackBlock());
            stacks.addProperty("enable_bar_stack_block", values.enableBarStackBlock());
            JsonObject compatibility = new JsonObject();
            compatibility.add("disable_mods", stringArray(values.disableMods()));
            compatibility.add("disable_items", stringArray(values.disableItems()));
            compatibility.add("ingot_tags", stringArray(values.ingotTags()));
            JsonObject gallery = new JsonObject();
            gallery.addProperty("placements_per_tick", values.renderGalleryPlacementsPerTick());
            gallery.addProperty("enabled", values.galleryEnabled());
            gallery.addProperty("required_permission_level", values.galleryPermissionLevel());
            gallery.add("gen_mods", stringArray(values.genMods()));
            gallery.add("gen_items", stringArray(values.genItems()));
            JsonObject root = new JsonObject();
            root.add("piles", piles);
            root.add("stacks", stacks);
            root.add("compatibility", compatibility);
            root.add("render_gallery", gallery);
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(root));
            } catch (IOException exception) {
                SomeStacksCommon.LOGGER.warn("Failed to write {}: {}", file, exception.getMessage());
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

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

    }

    // --- Baking ---

    /**
     * Resolves the server's free-form text lists into lookup sets: the disabled-mod and
     * disabled-item compatibility lists, and the ingot tag list. Mod ids are lowercased and item ids
     * are parsed once here; a malformed item id is reported and dropped rather than being re-parsed
     * and swallowed on every deposit.
     */
    public static void bakeServerLists() {
        Set<String> mods = new HashSet<>();
        for (String entry : ListSetting.DISABLE_MODS.get()) {
            String modId = entry.trim().toLowerCase(Locale.ROOT);
            if (!modId.isEmpty()) {
                mods.add(modId);
            }
        }

        Set<ResourceLocation> items = new HashSet<>();
        for (String entry : ListSetting.DISABLE_ITEMS.get()) {
            ResourceLocation itemId = ResourceLocation.tryParse(entry.trim().toLowerCase(Locale.ROOT));
            if (itemId == null) {
                SomeStacksCommon.LOGGER.warn("Ignoring malformed item id \"{}\" in disable_items", entry);
                continue;
            }
            items.add(itemId);
        }

        disabledMods = Set.copyOf(mods);
        disabledItems = Set.copyOf(items);

        SomeStacksCommon.LOGGER.info("Baked server lists: {} disabled mod(s), {} disabled item(s)",
                disabledMods.size(), disabledItems.size());

        bakeIngotItems();
    }

    /**
     * Resolves the ingot tag list into the set of items a Bar Stack accepts, by walking the item
     * tags once and unioning the contents of every tag whose name a list entry matches.
     */
    private static void bakeIngotItems() {
        List<Pattern> patterns = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String entry : ListSetting.INGOT_TAGS.get()) {
            String glob = entry.trim().toLowerCase(Locale.ROOT);
            if (!glob.isEmpty()) {
                patterns.add(globToPattern(glob));
                names.add(glob);
            }
        }

        Registry<Item> itemRegistry = BuiltInRegistries.ITEM;
        Set<Item> items = new HashSet<>();
        int[] matchedTags = new int[patterns.size()];
        int knownTags = 0;

        for (TagKey<Item> tagKey : itemRegistry.getTagNames().toList()) {
            var tag = itemRegistry.getTag(tagKey).orElse(null);
            if (tag == null) {
                continue;
            }
            knownTags++;
            String tagName = tagKey.location().toString();
            for (int i = 0; i < patterns.size(); i++) {
                if (!patterns.get(i).matcher(tagName).matches()) {
                    continue;
                }
                matchedTags[i]++;
                tag.forEach(holder -> items.add(holder.value()));
            }
        }

        ingotItems = Set.copyOf(items);
        ingotGeneration.incrementAndGet();

        if (knownTags == 0) {
            // Before a level is loaded there are no tags to walk; a later rebake fills this in.
            return;
        }

        for (int i = 0; i < patterns.size(); i++) {
            if (matchedTags[i] == 0) {
                SomeStacksCommon.LOGGER.warn("Ingot tag entry \"{}\" matches no item tag", names.get(i));
            }
        }

        SomeStacksCommon.LOGGER.info("Baked ingot tags: {} entr(ies) over {} item tag(s) accept {} item(s)",
                patterns.size(), knownTags, ingotItems.size());
    }

    /**
     * Compiles one ingot tag entry into a matcher over whole tag names, where {@code *} stands for
     * a run of any characters and every other character is literal.
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
     * Re-resolves the ingot tag list against the tags just bound. Item tags are data pack state, so
     * the set of items a Bar Stack accepts changes with a data pack reload even though the config
     * naming those tags has not. The loader's entry point calls this from its own tags-updated hook.
     */
    public static void rebakeIngotTags() {
        if (persistence == null) {
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

    /** How many items the ingot tag list currently resolves to. */
    public static int ingotItemCount() {
        return ingotItems.size();
    }

    /**
     * Every item tag name the server knows, for completing an ingot tag list entry. Read on demand
     * rather than cached: tags change with a data pack reload, and a completion request is rare
     * next to the deposits the baked set serves.
     */
    public static List<String> itemTagNames() {
        List<String> names = new ArrayList<>();
        for (TagKey<Item> tagKey : BuiltInRegistries.ITEM.getTagNames().toList()) {
            names.add(tagKey.location().toString());
        }
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
