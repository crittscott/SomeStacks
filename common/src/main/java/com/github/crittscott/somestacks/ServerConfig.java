package com.github.crittscott.somestacks;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Per-world immutable policy and resolved admission sets; loaders own persistence. */
public final class ServerConfig {
    private ServerConfig() {}

    public static final int MAX_PILE_HEIGHT_LIMIT = 64;
    public static final int GALLERY_PERMISSION_LEVEL_MAX = 4;
    /** Items whose models are known to crash clients when drawn. */
    public static final List<String> DEFAULT_DISABLE_ITEMS = List.of("evilcraft:broom_part");

    private static volatile Settings settings = Settings.defaults();
    private static Supplier<Settings> reader;
    private static Consumer<Settings> saver;
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
            disableMods = List.copyOf(disableMods);
            disableItems = List.copyOf(disableItems);
            genMods = List.copyOf(genMods);
            genItems = List.copyOf(genItems);
        }

        public static Settings defaults() {
            return new Settings(
                    8,
                    true,
                    true,
                    true,
                    List.of(),
                    DEFAULT_DISABLE_ITEMS,
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

    /**
     * Starts a world with its loader's policy reader and command-save boundary, and publishes the
     * policy read now.
     */
    public static void start(Supplier<Settings> readPolicy, Consumer<Settings> savePolicy) {
        reader = Objects.requireNonNull(readPolicy, "Policy reader");
        saver = Objects.requireNonNull(savePolicy, "Policy saver");
        publish(reader.get());
    }

    /**
     * Rereads the world's policy through its loader and publishes it.
     *
     * @return false when no world is running
     */
    public static boolean reload() {
        if (reader == null) {
            return false;
        }
        publish(reader.get());
        return true;
    }

    /** Publishes one complete loader policy and rebuilds its admission sets. */
    public static void publish(Settings replacement) {
        settings = Objects.requireNonNull(replacement, "Server policy");
        bakeServerLists();
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
        settings = Settings.defaults();
        disabledMods = Set.of();
        disabledItems = Set.of();
    }

    // --- Baking ---

    /**
     * Resolves the server's free-form text lists into lookup sets: the disabled-mod and
     * disabled-item compatibility lists. Mod ids are lowercased and item ids
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
}
