package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.StorageCubeIdx;

import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Immutable server policy, its file declaration ({@link Section}, {@link Setting}), command edits,
 * and normalized admission lookups. Loader backends own persistence and build their files from the
 * declaration.
 */
public final class ServerConfig {
    private ServerConfig() {}

    /** Persistence and lifecycle supplied by the selected loader. */
    public interface Backend {
        /**
         * Loads validated policy for this world after loader installation and before world
         * activity.
         */
        Settings loadFor(MinecraftServer server);

        /**
         * Returns validated policy from disk or the loader's published values; parsing fallback is
         * backend-owned.
         */
        Settings reload();

        /**
         * Persists the supplied policy; returns whether saving succeeded, without applying it here.
         */
        boolean save(Settings settings);
    }

    public static final int DEFAULT_MAX_PILE_HEIGHT = 8;
    public static final int MIN_PILE_HEIGHT = 1;
    public static final int MAX_PILE_HEIGHT_LIMIT = 64;
    public static final int DEFAULT_GALLERY_PLACEMENTS_PER_TICK = 64;
    public static final int MIN_GALLERY_PLACEMENTS_PER_TICK = 1;
    public static final int DEFAULT_GALLERY_PERMISSION_LEVEL = Commands.LEVEL_ADMINS;
    public static final int MIN_GALLERY_PERMISSION_LEVEL = Commands.LEVEL_ALL;
    public static final int MAX_GALLERY_PERMISSION_LEVEL = Commands.LEVEL_OWNERS;

    /**
     * Items barred by default. Each crashes the client when its plain {@link
     * net.minecraft.world.item.ItemStack} is drawn, as a gallery creates it and as extracting it
     * drops or hands it to the player:
     *
     * <ul>
     *   <li>{@code evilcraft:broom_part} (EvilCraft 1.2.62): its model throws {@code
     *       UnsupportedOperationException}.
     * </ul>
     */
    public static final List<String> DEFAULT_DISABLE_ITEMS = List.of("evilcraft:broom_part");

    public static final int MAX_NAMESPACE_LENGTH = 256;
    public static final int MAX_NAMESPACE_ENTRIES = 256;

    private static Backend backend;
    private static volatile Settings settings = defaults();
    private static volatile Set<String> disabledMods = Set.of();
    private static volatile Set<ResourceLocation> disabledItems = Set.of();

    /**
     * Installs loader persistence; each loader entry point calls this once, before any server
     * starts. Does not load or apply policy.
     */
    public static void install(Backend implementation) {
        backend = implementation;
    }

    /** Installed loader persistence. */
    public static Backend backend() {
        return backend;
    }

    /** Current immutable in-memory policy. */
    public static Settings settings() {
        return settings;
    }

    /**
     * Validates namespace lists and rebuilds admission sets; does not save or synchronize clients.
     */
    public static void apply(Settings policy) {
        settings =
                policy.with(
                                Setting.DISABLE_MODS,
                                prepareNamespaces(
                                        policy.disableMods(), Setting.DISABLE_MODS.key()))
                        .with(
                                Setting.GEN_MODS,
                                prepareNamespaces(policy.genMods(), Setting.GEN_MODS.key()));
        bakeServerLists();
    }

    /** Loads and applies this world's policy using the installed backend before world activity. */
    public static void loadFor(MinecraftServer server) {
        apply(defaults());
        try {
            apply(backend.loadFor(server));
        } catch (IllegalArgumentException rejected) {
            SomeStacksCommon.LOGGER.warn(
                    "Keeping default server policy: {}", rejected.getMessage());
        }
    }

    /**
     * Refreshes policy without saving or sending packets; rejected namespaces preserve active
     * policy.
     */
    public static boolean reload() {
        try {
            apply(backend.reload());
            return true;
        } catch (IllegalArgumentException rejected) {
            SomeStacksCommon.LOGGER.warn(
                    "Keeping previous server policy: {}", rejected.getMessage());
            return false;
        }
    }

    /** Persists policy through the installed backend; false means persistence failed. */
    public static boolean save() {
        return backend.save(settings);
    }

    /**
     * Immutable policy values. Lists are defensively copied; backends and other callers must
     * validate numeric ranges and list entries before application. This record does not validate
     * them.
     */
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
            disableMods = List.copyOf(disableMods);
            disableItems = List.copyOf(disableItems);
            genMods = List.copyOf(genMods);
            genItems = List.copyOf(genItems);
        }

        /**
         * Builds a policy from one raw value per declared setting, typed by its {@link
         * Setting.Kind}: an Integer, a Boolean, or a List of String.
         */
        @SuppressWarnings("unchecked")
        public static Settings from(Function<Setting, Object> value) {
            return new Settings(
                    (Integer) value.apply(Setting.MAX_PILE_HEIGHT),
                    (Boolean) value.apply(Setting.ENABLE_STORAGE_STACK_BLOCK),
                    (Boolean) value.apply(Setting.ENABLE_SINGLES_STACK_BLOCK),
                    (Boolean) value.apply(Setting.ENABLE_BAR_STACK_BLOCK),
                    (List<String>) value.apply(Setting.DISABLE_MODS),
                    (List<String>) value.apply(Setting.DISABLE_ITEMS),
                    (Integer) value.apply(Setting.PLACEMENTS_PER_TICK),
                    (Boolean) value.apply(Setting.GALLERY_ENABLED),
                    (Integer) value.apply(Setting.REQUIRED_PERMISSION_LEVEL),
                    (List<String>) value.apply(Setting.GEN_MODS),
                    (List<String>) value.apply(Setting.GEN_ITEMS));
        }

        /** This policy with one setting replaced by {@code value}, typed as in {@link #from}. */
        public Settings with(Setting changed, Object value) {
            return from(setting -> setting == changed ? value : setting.get(this));
        }
    }

    public static Settings defaults() {
        return new Settings(
                DEFAULT_MAX_PILE_HEIGHT,
                true,
                true,
                true,
                List.of(),
                DEFAULT_DISABLE_ITEMS,
                DEFAULT_GALLERY_PLACEMENTS_PER_TICK,
                false,
                DEFAULT_GALLERY_PERMISSION_LEVEL,
                List.of(),
                List.of());
    }

    /** A table of the server policy file; its declaration order is the order in the file. */
    public enum Section {
        PILES("piles", "Stack piles: vertical runs of one stack type that share one inventory."),
        STACKS(
                "stacks",
                "Which stack types players and automation may create.",
                "Turning a type off blocks new placement and growth only; existing stacks keep working and their contents stay extractable."),
        COMPATIBILITY(
                "compatibility",
                "Items refused by stacks. Disabling never removes or ejects existing contents."),
        RENDER_GALLERY(
                "render_gallery",
                "Render galleries: /ss gallery and /ss ingotgallery build rows of stacks showing every item of chosen mods,",
                "for checking how items render. Galleries bypass placement protection, so they are off by default.");

        private final List<String> comment;
        private final String key;

        Section(String key, String... comment) {
            this.key = key;
            this.comment = List.of(comment);
        }

        /** Explanation lines written above the table. */
        public List<String> comment() {
            return comment;
        }

        /** The table name in the file. */
        public String key() {
            return key;
        }

        /** This table's settings, in file order. */
        public List<Setting> settings() {
            return Arrays.stream(Setting.values()).filter(s -> s.section == this).toList();
        }
    }

    /**
     * One field of the server policy file: its table, key, kind, bounds, explanation, and the
     * {@link Settings} component it holds. Every loader builds its file, defaults, and range checks
     * from these; declaration order within a table is the order in the file.
     */
    public enum Setting {
        MAX_PILE_HEIGHT(
                Section.PILES,
                "max_pile_height",
                Settings::maxPileHeight,
                MIN_PILE_HEIGHT,
                MAX_PILE_HEIGHT_LIMIT,
                "Tallest a run of one stack type may grow, in blocks. Player deposits and automation stop growing a run at this height."),
        ENABLE_STORAGE_STACK_BLOCK(
                Section.STACKS,
                "enable_storage_stack_block",
                Kind.BOOLEAN,
                Settings::enableStorageStackBlock,
                "Storage Stack: %d item stacks per block that merge, sort, and pack down."
                        .formatted(StorageCubeIdx.CELLS)),
        ENABLE_SINGLES_STACK_BLOCK(
                Section.STACKS,
                "enable_singles_stack_block",
                Kind.BOOLEAN,
                Settings::enableSinglesStackBlock,
                "Singles Stack: a %d x %d x %d display grid holding one item per cell."
                        .formatted(
                                SinglesCubeIdx.GRID_EDGE,
                                SinglesCubeIdx.GRID_EDGE,
                                SinglesCubeIdx.GRID_EDGE)),
        ENABLE_BAR_STACK_BLOCK(
                Section.STACKS,
                "enable_bar_stack_block",
                Kind.BOOLEAN,
                Settings::enableBarStackBlock,
                "Bar Stack: %d ingots per block in %d alternating layers; ingots are the %s item tag."
                        .formatted(
                                BarCubeIdx.CELLS,
                                BarCubeIdx.CELLS / BarCubeIdx.LAYER_SIZE,
                                BarStackBE.INGOTS.location())),
        DISABLE_MODS(
                Section.COMPATIBILITY,
                "disable_mods",
                Kind.STRINGS,
                Settings::disableMods,
                "Mod ids (item namespaces) whose items players and automation may not put into stacks, e.g. [\"examplemod\"].",
                "Galleries skip these mods. Edit in game with /ss deny."),
        DISABLE_ITEMS(
                Section.COMPATIBILITY,
                "disable_items",
                Kind.STRINGS,
                Settings::disableItems,
                "Item ids players may not deposit into stacks, e.g. [\"minecraft:stone\"]. Automation is not affected.",
                "Galleries skip these items. The default lists items known to crash clients when drawn. Edit in game with /ss deny."),
        GALLERY_ENABLED(
                Section.RENDER_GALLERY,
                "enabled",
                Kind.BOOLEAN,
                Settings::galleryEnabled,
                "Whether /ss gallery and /ss ingotgallery are available."),
        REQUIRED_PERMISSION_LEVEL(
                Section.RENDER_GALLERY,
                "required_permission_level",
                Settings::galleryPermissionLevel,
                MIN_GALLERY_PERMISSION_LEVEL,
                MAX_GALLERY_PERMISSION_LEVEL,
                "Permission level needed to run the gallery commands (%d = everyone, %d = owner)."
                        .formatted(MIN_GALLERY_PERMISSION_LEVEL, MAX_GALLERY_PERMISSION_LEVEL)),
        PLACEMENTS_PER_TICK(
                Section.RENDER_GALLERY,
                "placements_per_tick",
                Settings::renderGalleryPlacementsPerTick,
                MIN_GALLERY_PLACEMENTS_PER_TICK,
                Integer.MAX_VALUE,
                "Stacks a gallery places per server tick; lower values spread the work over more ticks."),
        GEN_MODS(
                Section.RENDER_GALLERY,
                "gen_mods",
                Kind.STRINGS,
                Settings::genMods,
                "Mod ids the gallery 'list' form builds, one column per mod. Edit in game with /ss gen."),
        GEN_ITEMS(
                Section.RENDER_GALLERY,
                "gen_items",
                Kind.STRINGS,
                Settings::genItems,
                "Item ids the gallery 'items' form builds as a single column. Edit in game with /ss gen.");

        private final List<String> comment;
        private final Function<Settings, Object> component;
        private final String key;
        private final Kind kind;
        private final int max;
        private final int min;
        private final Section section;

        /** An unbounded boolean or string-list field. */
        Setting(
                Section section,
                String key,
                Kind kind,
                Function<Settings, Object> component,
                String... comment) {
            this(section, key, kind, component, 0, 0, comment);
        }

        /** A bounded integer field. */
        Setting(
                Section section,
                String key,
                Function<Settings, Object> component,
                int min,
                int max,
                String... comment) {
            this(section, key, Kind.INTEGER, component, min, max, comment);
        }

        Setting(
                Section section,
                String key,
                Kind kind,
                Function<Settings, Object> component,
                int min,
                int max,
                String... comment) {
            this.section = section;
            this.key = key;
            this.kind = kind;
            this.component = component;
            this.min = min;
            this.max = max;
            this.comment = List.of(comment);
        }

        /** The value type a loader stores for a setting. */
        public enum Kind {
            BOOLEAN,
            INTEGER,
            STRINGS
        }

        /** Explanation lines written above the field, without any range line. */
        public List<String> comment() {
            return comment;
        }

        /** This setting's value in {@code policy}: an Integer, Boolean, or List of String. */
        public Object get(Settings policy) {
            return component.apply(policy);
        }

        /** The field name within its table. */
        public String key() {
            return key;
        }

        public Kind kind() {
            return kind;
        }

        /** Largest accepted value of an {@link Kind#INTEGER} setting. */
        public int max() {
            return max;
        }

        /** Smallest accepted value of an {@link Kind#INTEGER} setting. */
        public int min() {
            return min;
        }

        /** The dotted {@code table.field} path. */
        public String path() {
            return section.key + "." + key;
        }

        public Section section() {
            return section;
        }
    }

    /** A command-editable policy list. */
    public enum ListSetting {
        DISABLE_MODS(Setting.DISABLE_MODS),
        DISABLE_ITEMS(Setting.DISABLE_ITEMS),
        GEN_MODS(Setting.GEN_MODS),
        GEN_ITEMS(Setting.GEN_ITEMS);

        private final Setting setting;

        ListSetting(Setting setting) {
            this.setting = setting;
        }

        @SuppressWarnings("unchecked")
        public List<String> get() {
            return (List<String>) setting.get(settings);
        }

        private void set(List<String> value) {
            apply(settings.with(setting, value));
        }
    }

    /** Canonical spelling for configured item namespaces. */
    public static String normalizeNamespace(String namespace) {
        return namespace.trim().toLowerCase(Locale.ROOT);
    }

    public static void bakeServerLists() {
        Set<String> mods = new HashSet<>();
        for (String entry : ListSetting.DISABLE_MODS.get()) {
            String modId = normalizeNamespace(entry);
            if (!modId.isEmpty()) {
                mods.add(modId);
            }
        }

        Set<ResourceLocation> items = new HashSet<>();
        for (String entry : ListSetting.DISABLE_ITEMS.get()) {
            ResourceLocation itemId =
                    ResourceLocation.tryParse(entry.trim().toLowerCase(Locale.ROOT));
            if (itemId == null) {
                SomeStacksCommon.LOGGER.warn(
                        "Ignoring malformed item id \"{}\" in disable_items", entry);
                continue;
            }
            items.add(itemId);
        }

        disabledMods = Set.copyOf(mods);
        disabledItems = Set.copyOf(items);
    }

    /** Validates namespace syntax using the platform's public identifier parser. */
    public static boolean isValidNamespace(String namespace) {
        return !namespace.isEmpty() && ResourceLocation.tryParse(namespace + ":_") != null;
    }

    private static List<String> prepareNamespaces(List<String> entries, String label) {
        Set<String> result = new LinkedHashSet<>();
        for (String entry : entries) {
            String namespace = entry.trim().toLowerCase(Locale.ROOT);
            if (namespace.isEmpty()) continue;
            if (!isValidNamespace(namespace)) {
                SomeStacksCommon.LOGGER.warn(
                        "Ignoring malformed namespace '{}' in {}", entry, label);
                continue;
            }
            if (namespace.length() > MAX_NAMESPACE_LENGTH) {
                throw new IllegalArgumentException(label + " contains an overlong namespace");
            }
            result.add(namespace);
            if (result.size() > MAX_NAMESPACE_ENTRIES) {
                throw new IllegalArgumentException(label + " exceeds the namespace entry limit");
            }
        }
        return List.copyOf(result);
    }

    /**
     * Adds an entry to one of the server's text lists, re-bakes the lookup sets, and saves.
     *
     * @return whether the entry was added; false when the list already contains it. A failed save
     *     does not change the result.
     * @throws IllegalArgumentException for an invalid or overlong namespace, or a full list
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
        list.set(updated);
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
}
