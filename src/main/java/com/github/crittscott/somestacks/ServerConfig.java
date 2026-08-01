package com.github.crittscott.somestacks;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITag;
import net.minecraftforge.registries.tags.ITagManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * The server config spec and the resolved values behind it.
 *
 * <p>The text lists are stored as patterns and baked into sets that the hot paths can test cheaply.
 * Baking happens on config load, on config reload, and, for the ingot tags, whenever tags are
 * rebuilt, since those name item tags whose membership a data pack decides. The baked sets are
 * volatile because the config can be reloaded off the server thread while gameplay reads them.
 *
 * <p>Everything here is server-side. Only the stack-type enable flags reach the client, through the
 * configuration sync.
 */
public final class ServerConfig {
    private ServerConfig() {}

    public static final ForgeConfigSpec SERVER_CONFIG;

    public static final ForgeConfigSpec.IntValue MAX_PILE_HEIGHT;

    public static final ForgeConfigSpec.BooleanValue ENABLE_STORAGE_STACK_BLOCK;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SINGLES_STACK_BLOCK;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BAR_STACK_BLOCK;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLE_MODS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLE_ITEMS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> INGOT_TAGS;

    public static final ForgeConfigSpec.IntValue RENDER_GALLERY_PLACEMENTS_PER_TICK;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GEN_MODS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GEN_ITEMS;

    private static volatile Set<String> disabledMods = Set.of();
    private static volatile Set<ResourceLocation> disabledItems = Set.of();
    private static volatile Set<Item> ingotItems = Set.of();
    private static final AtomicInteger ingotGeneration = new AtomicInteger();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Pile and Column Configuration").push("piles");

        MAX_PILE_HEIGHT = builder
                .comment("Maximum number of blocks in one vertical Storage pile, Singles column or",
                        "Bar column. Each is one inventory over its whole height: a Storage pile",
                        "fills from the bottom up, packs down and sorts; a Singles column fills its",
                        "lowest supported cells and falls down over an emptied one; a Bar column",
                        "fills its lowest supported positions and backfills holes from its top.",
                        "This bounds that work. Placement that would produce a taller column is",
                        "refused, and a pile or column stops growing here.")
                .defineInRange("max_pile_height", 8, 1, 64);

        builder.pop();

        builder.comment("Stack Enable/Disable Configuration").push("stacks");

        ENABLE_STORAGE_STACK_BLOCK = builder
                .comment("Enable/disable Storage Stack Block")
                .define("enable_storage_stack_block", true);

        ENABLE_SINGLES_STACK_BLOCK = builder
                .comment("Enable/disable Singles Stack Block")
                .define("enable_singles_stack_block", true);

        ENABLE_BAR_STACK_BLOCK = builder
                .comment("Enable/disable Bar Stack Block")
                .define("enable_bar_stack_block", true);

        builder.pop();

        builder.comment("Mod Compatibility Configuration").push("compatibility");

        DISABLE_MODS = builder
                .comment("List of mod IDs to disable compatibility with")
                .defineList("disable_mods",
                        Collections.emptyList(),
                        obj -> obj instanceof String);

        DISABLE_ITEMS = builder
                .comment("List of specific items to disable from being stored in stacks",
                        "Format: \"modid:itemname\"",
                        "Example: \"immersiveengineering:toolupgrade_drill_damage\"")
                .defineList("disable_items",
                        Collections.emptyList(),
                        obj -> obj instanceof String);

        INGOT_TAGS = builder
                .comment("Item tags whose contents a Bar Stack accepts. An entry may end in, or",
                        "contain, '*' to match a run of any characters, so the default covers",
                        "forge:ingots itself and every forge:ingots/<metal> beneath it.",
                        "A mod that tags its ingots only under forge:ingots/<metal>, without adding",
                        "that tag to forge:ingots, is reached by the default for the same reason.",
                        "somestacks:ingots is the mod's own tag, which a data pack may extend.",
                        "To accept a hand-picked set of items, make an item tag holding them and add",
                        "it here. Whatever a Bar Stack accepts, a Singles Stack refuses.",
                        "Editable in game with 'ss ingot add' and 'ss ingot remove'.",
                        "Tags must be quoted: [\"forge:ingots*\", \"mypack:bar_items\"]")
                .defineList("ingot_tags",
                        Arrays.asList("forge:ingots*", "somestacks:ingots"),
                        obj -> obj instanceof String);

        builder.pop();

        builder.comment("Render Gallery Configuration").push("render_gallery");

        RENDER_GALLERY_PLACEMENTS_PER_TICK = builder
                .comment("Blocks the 'ss gallery' and 'ss ingotgallery' commands place per tick, counting both stacks and floor.",
                        "A gallery spanning every loaded mod is tens of thousands of placements; lower",
                        "values spread it over more ticks.")
                .defineInRange("placements_per_tick", 64, 1, Integer.MAX_VALUE);

        GEN_MODS = builder
                .comment("Mod IDs the 'ss gallery list' and 'ss ingotgallery list' commands build galleries for.",
                        "The order given is the order of the gallery's columns.",
                        "Empty by default; editable in game with 'ss gen mod add' and 'ss gen mod remove'.",
                        "Mod IDs must be quoted: [\"create\", \"farmersdelight\"]")
                .defineList("gen_mods",
                        Collections.emptyList(),
                        obj -> obj instanceof String);

        GEN_ITEMS = builder
                .comment("Items the 'ss gallery items' command builds a row for. The row is sorted by",
                        "mod id and then item name, so this list may be kept in any order.",
                        "Empty by default; editable in game with 'ss gen item add' and 'ss gen item remove'.",
                        "Format: \"modid:itemname\", quoted: [\"minecraft:torch\", \"alexscaves:sea_staff\"]")
                .defineList("gen_items",
                        Collections.emptyList(),
                        obj -> obj instanceof String);

        builder.pop();

        SERVER_CONFIG = builder.build();
    }

    /**
     * Resolves the server's free-form text lists into lookup sets: the disabled-mod and
     * disabled-item compatibility lists, and the ingot tag list. Mod ids are lowercased and item ids
     * are parsed once here; a malformed item id is reported and dropped rather than being re-parsed
     * and swallowed on every deposit.
     *
     * <p>Runs on the server thread whenever there is one: the {@code ss} list-editing commands are
     * already there, and the config reload event hands this off to it, because resolving the ingot
     * tags reads data pack state that only that thread may be walking. Config load runs before any
     * server exists, where there is nothing to race. Each list is published as an immutable set
     * through a volatile field, so a lookup sees either the old lists or the new ones.
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
                SomeStacks.LOGGER.warn("Ignoring malformed item id \"{}\" in disable_items", entry);
                continue;
            }
            items.add(itemId);
        }

        disabledMods = Set.copyOf(mods);
        disabledItems = Set.copyOf(items);

        SomeStacks.LOGGER.info("Baked server lists: {} disabled mod(s), {} disabled item(s)",
                disabledMods.size(), disabledItems.size());

        bakeIngotItems();
    }

    /**
     * Resolves the ingot tag list into the set of items a Bar Stack accepts, by walking the item
     * tags once and unioning the contents of every tag whose name a list entry matches. Resolving
     * to items rather than keeping the patterns makes the validity test one set lookup, which is
     * what every deposit, column insertion and capability path runs.
     *
     * <p>Unlike the other lists this one also depends on data pack state, so it is re-baked when
     * tags are bound as well as when the config changes. Config loading may precede tag binding,
     * in which case this publishes an empty set and the later tag event rebuilds it. Initial world
     * creation may bind tags first instead; that event waits for the world-specific server config,
     * whose loading event then performs the first bake against the already-bound tags.
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

        Set<Item> items = new HashSet<>();
        int[] matchedTags = new int[patterns.size()];
        int knownTags = 0;

        ITagManager<Item> tags = ForgeRegistries.ITEMS.tags();
        for (ITag<Item> tag : tags == null ? List.<ITag<Item>>of() : tags) {
            knownTags++;
            String tagName = tag.getKey().location().toString();
            for (int i = 0; i < patterns.size(); i++) {
                if (!patterns.get(i).matcher(tagName).matches()) {
                    continue;
                }
                matchedTags[i]++;
                tag.forEach(items::add);
            }
        }

        ingotItems = Set.copyOf(items);
        ingotGeneration.incrementAndGet();

        if (knownTags == 0) {
            // Before a level is loaded there are no tags to walk; the tag event re-bakes this.
            return;
        }

        for (int i = 0; i < patterns.size(); i++) {
            if (matchedTags[i] == 0) {
                SomeStacks.LOGGER.warn("Ingot tag entry \"{}\" matches no item tag", names.get(i));
            }
        }

        SomeStacks.LOGGER.info("Baked ingot tags: {} entr(ies) over {} item tag(s) accept {} item(s)",
                patterns.size(), knownTags, ingotItems.size());
    }

    /**
     * Compiles one ingot tag entry into a matcher over whole tag names, where {@code *} stands for
     * a run of any characters and every other character is literal. Everything outside the wildcards
     * is quoted, so a tag name's own colons and slashes cannot be read as pattern syntax.
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
     * naming those tags has not. A world-specific server config is not necessarily loaded when
     * initial tags are bound; its loading event will perform the bake once both inputs are ready.
     */
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (!SERVER_CONFIG.isLoaded()) {
            return;
        }
        bakeIngotItems();
    }

    /**
     * Adds an entry to one of the server's text lists and re-bakes the lookup sets. The entry is
     * stored as it was typed but compared without regard to case, matching the case-insensitive
     * lookups the bake produces. Setting the config value writes through to the config file, so
     * the file and the running server agree and a later save cannot undo the change.
     *
     * @return whether the entry was added; false when the list already contains it
     */
    public static boolean addListEntry(ForgeConfigSpec.ConfigValue<List<? extends String>> list, String entry) {
        List<String> updated = new ArrayList<>(list.get());
        for (String existing : updated) {
            if (existing.equalsIgnoreCase(entry)) {
                return false;
            }
        }

        updated.add(entry);
        list.set(updated);
        bakeServerLists();
        return true;
    }

    /**
     * Removes an entry from one of the server's text lists and re-bakes the lookup sets, matching
     * without regard to case as {@link #addListEntry} does.
     *
     * @return whether an entry was removed; false when the list does not contain it
     */
    public static boolean removeListEntry(ForgeConfigSpec.ConfigValue<List<? extends String>> list, String entry) {
        List<String> updated = new ArrayList<>(list.get());
        if (!updated.removeIf(existing -> existing.equalsIgnoreCase(entry))) {
            return false;
        }

        list.set(updated);
        bakeServerLists();
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
        ITagManager<Item> tags = ForgeRegistries.ITEMS.tags();
        if (tags == null) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        for (ITag<Item> tag : tags) {
            names.add(tag.getKey().location().toString());
        }
        Collections.sort(names);
        return names;
    }

    /**
     * How many times the ingot item set has been resolved. A caller that groups or filters by
     * ingot-ness holds this alongside its own cache and rebuilds when it changes, which covers a
     * data pack reload and a config edit alike. Counted atomically because a config edit and a tag
     * update can resolve the set from different threads, and a repeated number would leave such a
     * cache stale for good.
     */
    public static int ingotGeneration() {
        return ingotGeneration.get();
    }
}
