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
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * The server config values and the resolved lookup sets behind them, loaded from and saved to a
 * per-world JSON file the loader's entry point locates and hands to {@link #load(Path)}.
 *
 * <p>The text lists are stored as patterns and baked into sets that the hot paths can test cheaply.
 * Baking happens on load and whenever a list is edited, and for the ingot tags, whenever item tags
 * are rebuilt with {@link #rebakeIngotTags()}, since those name item tags whose membership a data
 * pack decides. The baked sets are volatile because a rebake can run off the main thread relative to
 * gameplay reads.
 *
 * <p>Everything here is server-side. Only the stack-type enable flags reach the client, through the
 * configuration sync.
 */
public final class ServerConfig {
    private ServerConfig() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Upper bound accepted for {@code piles.max_pile_height} in the config file. */
    private static final int MAX_PILE_HEIGHT_LIMIT = 64;

    private static int maxPileHeight = 8;
    private static boolean enableStorageStackBlock = true;
    private static boolean enableSinglesStackBlock = true;
    private static boolean enableBarStackBlock = true;
    private static List<String> disableModsRaw = new ArrayList<>();
    private static List<String> disableItemsRaw = new ArrayList<>();
    private static List<String> ingotTagsRaw = new ArrayList<>(List.of("forge:ingots*", "somestacks:ingots"));
    private static int renderGalleryPlacementsPerTick = 64;
    private static List<String> genModsRaw = new ArrayList<>();
    private static List<String> genItemsRaw = new ArrayList<>();

    private static Path configFile;

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
    public static final ListSetting INGOT_TAGS = new ListSetting(() -> ingotTagsRaw, v -> ingotTagsRaw = v);
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

    /** Selects Fabric convention tags for a new config; an existing file remains authoritative. */
    public static void useFabricIngotTagDefaults() {
        if (configFile == null) {
            ingotTagsRaw = new ArrayList<>(List.of("c:ingots*", "somestacks:ingots"));
        }
    }

    // --- Loading and saving ---

    /**
     * Loads the config from {@code file}, writing it with defaults first if it does not exist yet.
     * Remembers {@code file} so later edits save back to it, then bakes the lookup sets.
     */
    public static void load(Path file) {
        configFile = file;

        if (Files.exists(file)) {
            try {
                JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
                if (root != null) {
                    applyJson(root);
                }
            } catch (Exception e) {
                SomeStacksCommon.LOGGER.warn("Failed to read {}: {}", file, e.getMessage());
            }
        } else {
            save();
        }

        bakeServerLists();
    }

    private static void applyJson(JsonObject root) {
        JsonObject piles = obj(root, "piles");
        maxPileHeight = clamp(intOr(piles, "max_pile_height", maxPileHeight), 1, MAX_PILE_HEIGHT_LIMIT);

        JsonObject stacks = obj(root, "stacks");
        enableStorageStackBlock = boolOr(stacks, "enable_storage_stack_block", enableStorageStackBlock);
        enableSinglesStackBlock = boolOr(stacks, "enable_singles_stack_block", enableSinglesStackBlock);
        enableBarStackBlock = boolOr(stacks, "enable_bar_stack_block", enableBarStackBlock);

        JsonObject compatibility = obj(root, "compatibility");
        disableModsRaw = stringListOr(compatibility, "disable_mods", disableModsRaw);
        disableItemsRaw = stringListOr(compatibility, "disable_items", disableItemsRaw);
        ingotTagsRaw = stringListOr(compatibility, "ingot_tags", ingotTagsRaw);

        JsonObject gallery = obj(root, "render_gallery");
        renderGalleryPlacementsPerTick =
                Math.max(1, intOr(gallery, "placements_per_tick", renderGalleryPlacementsPerTick));
        genModsRaw = stringListOr(gallery, "gen_mods", genModsRaw);
        genItemsRaw = stringListOr(gallery, "gen_items", genItemsRaw);
    }

    /** Writes the current values to the file {@link #load} was given. A no-op before the first load. */
    public static void save() {
        if (configFile == null) {
            return;
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
        compatibility.add("ingot_tags", stringArray(ingotTagsRaw));

        JsonObject gallery = new JsonObject();
        gallery.addProperty("placements_per_tick", renderGalleryPlacementsPerTick);
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
        } catch (IOException e) {
            SomeStacksCommon.LOGGER.warn("Failed to write {}: {}", configFile, e.getMessage());
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

    // --- Baking ---

    /**
     * Resolves the server's free-form text lists into lookup sets: the disabled-mod and
     * disabled-item compatibility lists, and the ingot tag list. Mod ids are lowercased and item ids
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
        for (String entry : INGOT_TAGS.get()) {
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
