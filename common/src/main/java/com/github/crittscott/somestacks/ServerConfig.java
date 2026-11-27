package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.StorageCubeIdx;

import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Per-world immutable policy, its file declaration ({@link Section}, {@link Setting}), and resolved
 * admission sets; loaders own persistence.
 */
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
            maxPileHeight =
                    Math.max(MIN_PILE_HEIGHT, Math.min(MAX_PILE_HEIGHT_LIMIT, maxPileHeight));
            renderGalleryPlacementsPerTick =
                    Math.max(MIN_PLACEMENTS_PER_TICK, renderGalleryPlacementsPerTick);
            galleryPermissionLevel =
                    Math.max(
                            MIN_GALLERY_PERMISSION_LEVEL,
                            Math.min(GALLERY_PERMISSION_LEVEL_MAX, galleryPermissionLevel));
            disableMods = List.copyOf(disableMods);
            disableItems = List.copyOf(disableItems);
            genMods = List.copyOf(genMods);
            genItems = List.copyOf(genItems);
        }

        /**
         * Builds a policy from one raw value per declared setting, typed by its {@link
         * Setting.Kind}.
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
        DISABLE_MODS,
        DISABLE_ITEMS,
        GEN_MODS,
        GEN_ITEMS;

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

    /** A table of the server policy file; its declaration order is the order in the file. */
    public enum Section {
        PILES(Keys.PILES, "Stack piles: vertical runs of one stack type that share one inventory."),
        STACKS(
                Keys.STACKS,
                "Which stack types players and automation may create.",
                "Turning a type off blocks new placement and growth only; existing stacks keep working and their contents stay extractable."),
        COMPATIBILITY(
                Keys.COMPATIBILITY,
                "Items refused by stacks. Disabling never removes or ejects existing contents."),
        RENDER_GALLERY(
                Keys.RENDER_GALLERY,
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
     * {@link Settings} component it holds. Each loader builds its file, defaults, and range checks
     * from these; declaration order within a table is the order in the file.
     */
    public enum Setting {
        MAX_PILE_HEIGHT(
                Section.PILES,
                Keys.MAX_PILE_HEIGHT,
                Settings::maxPileHeight,
                MIN_PILE_HEIGHT,
                MAX_PILE_HEIGHT_LIMIT,
                "Tallest a run of one stack type may grow, in blocks. Player deposits and automation stop growing a run at this height."),
        ENABLE_STORAGE_STACK_BLOCK(
                Section.STACKS,
                Keys.ENABLE_STORAGE_STACK_BLOCK,
                Kind.BOOLEAN,
                Settings::enableStorageStackBlock,
                "Storage Stack: %d item stacks per block that merge, sort, and pack down."
                        .formatted(StorageCubeIdx.CELLS)),
        ENABLE_SINGLES_STACK_BLOCK(
                Section.STACKS,
                Keys.ENABLE_SINGLES_STACK_BLOCK,
                Kind.BOOLEAN,
                Settings::enableSinglesStackBlock,
                "Singles Stack: a %d x %d x %d display grid holding one item per cell."
                        .formatted(
                                SinglesCubeIdx.GRID_EDGE,
                                SinglesCubeIdx.GRID_EDGE,
                                SinglesCubeIdx.GRID_EDGE)),
        ENABLE_BAR_STACK_BLOCK(
                Section.STACKS,
                Keys.ENABLE_BAR_STACK_BLOCK,
                Kind.BOOLEAN,
                Settings::enableBarStackBlock,
                "Bar Stack: %d ingots per block in %d alternating layers; ingots are the %s item tag."
                        .formatted(
                                BarCubeIdx.CELLS,
                                BarCubeIdx.CELLS / BarCubeIdx.LAYER_SIZE,
                                BarStackBE.INGOTS.location())),
        DISABLE_MODS(
                Section.COMPATIBILITY,
                Keys.DISABLE_MODS,
                Kind.STRINGS,
                Settings::disableMods,
                "Mod ids (item namespaces) whose items players and automation may not put into stacks, e.g. [\"examplemod\"].",
                "Galleries skip these mods. Edit in game with /ss deny."),
        DISABLE_ITEMS(
                Section.COMPATIBILITY,
                Keys.DISABLE_ITEMS,
                Kind.STRINGS,
                Settings::disableItems,
                "Item ids players may not deposit into stacks, e.g. [\"minecraft:stone\"]. Automation is not affected.",
                "Galleries skip these items. The default lists items known to crash clients when drawn. Edit in game with /ss deny."),
        GALLERY_ENABLED(
                Section.RENDER_GALLERY,
                Keys.ENABLED,
                Kind.BOOLEAN,
                Settings::galleryEnabled,
                "Whether /ss gallery and /ss ingotgallery are available."),
        REQUIRED_PERMISSION_LEVEL(
                Section.RENDER_GALLERY,
                Keys.REQUIRED_PERMISSION_LEVEL,
                Settings::galleryPermissionLevel,
                MIN_GALLERY_PERMISSION_LEVEL,
                GALLERY_PERMISSION_LEVEL_MAX,
                "Permission level needed to run the gallery commands (%d = everyone, %d = owner)."
                        .formatted(MIN_GALLERY_PERMISSION_LEVEL, GALLERY_PERMISSION_LEVEL_MAX)),
        PLACEMENTS_PER_TICK(
                Section.RENDER_GALLERY,
                Keys.PLACEMENTS_PER_TICK,
                Settings::renderGalleryPlacementsPerTick,
                MIN_PLACEMENTS_PER_TICK,
                Integer.MAX_VALUE,
                "Stacks a gallery places per server tick; lower values spread the work over more ticks."),
        GEN_MODS(
                Section.RENDER_GALLERY,
                Keys.GEN_MODS,
                Kind.STRINGS,
                Settings::genMods,
                "Mod ids the gallery 'list' form builds, one column per mod. Edit in game with /ss gen."),
        GEN_ITEMS(
                Section.RENDER_GALLERY,
                Keys.GEN_ITEMS,
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

        /** This setting's value in {@code settings}: an Integer, Boolean, or List of String. */
        public Object get(Settings settings) {
            return component.apply(settings);
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

    public static Settings settings() {
        return settings;
    }

    /**
     * Starts a world with its loader's policy reader and command-save boundary, and publishes the
     * policy read now.
     */
    public static void start(
            Supplier<Settings> readPolicy,
            Consumer<Settings> savePolicy,
            Runnable synchronizeClients) {
        reader = readPolicy;
        saver = savePolicy;
        syncClients = synchronizeClients;
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
        try {
            replacement =
                    replacement.withList(
                            ListSetting.DISABLE_MODS,
                            prepareNamespaces(replacement.disableMods(), Keys.DISABLE_MODS));
            replacement =
                    replacement.withList(
                            ListSetting.GEN_MODS,
                            prepareNamespaces(replacement.genMods(), Keys.GEN_MODS));
        } catch (IllegalArgumentException rejected) {
            SomeStacksCommon.LOGGER.warn(
                    "Keeping previous server policy: {}", rejected.getMessage());
            return false;
        }
        boolean syncedFieldsChanged =
                !ConfigSyncPkt.snapshot(settings).equals(ConfigSyncPkt.snapshot(replacement));
        settings = replacement;
        bakeServerLists();
        if (syncedFieldsChanged) {
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
                SomeStacksCommon.LOGGER.warn(
                        "Ignoring malformed namespace '{}' in {}", entry, label);
                continue;
            }
            if (namespace.length() > MAX_NAMESPACE_LENGTH) {
                throw new IllegalArgumentException(
                        label
                                + " contains a namespace longer than "
                                + MAX_NAMESPACE_LENGTH
                                + " characters");
            }
            result.add(namespace);
            if (result.size() > MAX_NAMESPACE_ENTRIES) {
                throw new IllegalArgumentException(
                        label + " exceeds " + MAX_NAMESPACE_ENTRIES + " namespaces");
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
        saver.accept(settings);
    }

    /** Releases the world reader, saver, and derived admission state on server shutdown. */
    public static void clear() {
        reader = null;
        saver = null;
        syncClients = null;
        settings = Settings.defaults();
        disabledMods = Set.of();
        disabledItems = Set.of();
    }

    // --- Baking ---

    /**
     * Builds admission sets from prepared namespaces and free-form item ids. Malformed item ids are
     * reported here rather than reparsed on every deposit.
     */
    public static void bakeServerLists() {
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

        disabledMods = Set.copyOf(settings.disableMods());
        disabledItems = Set.copyOf(items);
    }

    /** Reports active policy at startup, explicit reload, or a changed loader file reload. */
    public static void logActivePolicy() {
        Settings current = settings;
        SomeStacksCommon.LOGGER.info(
                "Server policy active: Storage={}, Singles={}, Bar={}; maximum pile height={}; disabled mods={}, disabled items={}; gallery enabled={}, gallery permission level={}, gallery placements per tick={}, gallery mods={}, gallery items={}",
                current.enableStorageStackBlock(),
                current.enableSinglesStackBlock(),
                current.enableBarStackBlock(),
                current.maxPileHeight(),
                disabledMods.size(),
                disabledItems.size(),
                current.galleryEnabled(),
                current.galleryPermissionLevel(),
                current.renderGalleryPlacementsPerTick(),
                current.genMods().size(),
                current.genItems().size());
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
}
