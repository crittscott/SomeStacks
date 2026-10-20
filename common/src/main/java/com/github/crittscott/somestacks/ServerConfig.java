package com.github.crittscott.somestacks;

import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.PermissionCheck;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** Immutable server policy, command edits, and normalized admission lookups. */
public final class ServerConfig {
    private ServerConfig() {}

    /** Persistence and lifecycle supplied by the selected loader. */
    public interface Backend {
        /** Loads validated policy for this world after loader installation and before world activity. */
        Settings loadFor(MinecraftServer server);
        /** Returns validated policy from disk or the loader's published values; parsing fallback is backend-owned. */
        Settings reload();
        /** Persists the supplied policy; returns whether saving succeeded, without applying it here. */
        boolean save(Settings settings);
    }
    public static final int DEFAULT_MAX_PILE_HEIGHT = 8;
    public static final int MAX_PILE_HEIGHT = 64;
    public static final int DEFAULT_GALLERY_PLACEMENTS_PER_TICK = 64;
    /** Vanilla's command permission checks, indexed by {@code render_gallery.required_permission_level}. */
    private static final List<PermissionCheck> PERMISSION_LEVELS = List.of(
            Commands.LEVEL_ALL, Commands.LEVEL_MODERATORS, Commands.LEVEL_GAMEMASTERS,
            Commands.LEVEL_ADMINS, Commands.LEVEL_OWNERS);
    public static final int DEFAULT_GALLERY_PERMISSION_LEVEL = 3;
    /** Upper bound accepted for {@code render_gallery.required_permission_level}, vanilla's top op level. */
    public static final int GALLERY_PERMISSION_LEVEL_MAX = PERMISSION_LEVELS.size() - 1;

    /**
     * Items barred by default. Each crashes the client when its plain {@link
     * net.minecraft.world.item.ItemStack} is drawn, as a gallery creates it and as extracting it
     * drops or hands it to the player:
     * <ul>
     *   <li>{@code evilcraft:broom_part} (EvilCraft 1.2.62): its model throws
     *       {@code UnsupportedOperationException}.</li>
     * </ul>
     */
    public static final List<String> DEFAULT_DISABLE_ITEMS = List.of("evilcraft:broom_part");

    private static Backend backend;
    private static volatile Settings settings = defaults();
    private static volatile Set<String> disabledMods = Set.of();
    private static volatile Set<Identifier> disabledItems = Set.of();

    /** Installs loader persistence before loadFor; does not load or apply policy. */
    public static void install(Backend implementation) { backend = implementation; }
    /** Installed loader persistence, or null before installation. */
    public static Backend backend() { return backend; }
    /** Current immutable in-memory policy. */
    public static Settings settings() { return settings; }
    /** Publishes already-validated policy and rebuilds admission sets; does not save or synchronize clients. */
    public static void apply(Settings policy) {
        settings = policy;
        bakeServerLists();
    }
    /** Loads and applies this world's policy using the installed backend before world activity. */
    public static void loadFor(MinecraftServer server) { apply(backend.loadFor(server)); }
    /**
     * Applies the backend's current policy. True means a backend existed, not that parsing succeeded;
     * a backend may return defaults for invalid input. Does not save or synchronize clients.
     */
    public static boolean reload() {
        if (backend == null) return false;
        apply(backend.reload());
        return true;
    }
    /** Saves current policy without changing it; false when no backend exists or saving fails. */
    public static boolean save() { return backend != null && backend.save(settings); }

    /**
     * Immutable policy values. Lists are defensively copied; backends and other callers must
     * validate numeric ranges and list entries before application. This record does not validate them.
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

    /** A command-editable policy list. */
    public enum ListSetting {
        DISABLE_MODS(Settings::disableMods),
        DISABLE_ITEMS(Settings::disableItems),
        GEN_MODS(Settings::genMods),
        GEN_ITEMS(Settings::genItems);

        private final Function<Settings, List<String>> getter;

        ListSetting(Function<Settings, List<String>> getter) {
            this.getter = getter;
        }

        public List<String> get() { return getter.apply(settings); }

        private void set(List<String> value) {
            Settings current = settings;
            List<String> disableMods = current.disableMods();
            List<String> disableItems = current.disableItems();
            List<String> genMods = current.genMods();
            List<String> genItems = current.genItems();
            switch (this) {
                case DISABLE_MODS -> disableMods = value;
                case DISABLE_ITEMS -> disableItems = value;
                case GEN_MODS -> genMods = value;
                case GEN_ITEMS -> genItems = value;
            }
            apply(new Settings(current.maxPileHeight(), current.enableStorageStackBlock(),
                    current.enableSinglesStackBlock(), current.enableBarStackBlock(),
                    disableMods, disableItems, current.renderGalleryPlacementsPerTick(),
                    current.galleryEnabled(), current.galleryPermissionLevel(), genMods, genItems));
        }
    }
    public static int maxPileHeight() { return settings.maxPileHeight(); }
    public static boolean enableStorageStackBlock() { return settings.enableStorageStackBlock(); }
    public static boolean enableSinglesStackBlock() { return settings.enableSinglesStackBlock(); }
    public static boolean enableBarStackBlock() { return settings.enableBarStackBlock(); }
    public static int renderGalleryPlacementsPerTick() { return settings.renderGalleryPlacementsPerTick(); }
    public static boolean galleryEnabled() { return settings.galleryEnabled(); }
    public static int galleryRequiredPermissionLevel() { return settings.galleryPermissionLevel(); }
    public static PermissionCheck galleryPermission() { return PERMISSION_LEVELS.get(settings.galleryPermissionLevel()); }
    public static void bakeServerLists() {
        Set<String> mods = new HashSet<>();
        for (String entry : ListSetting.DISABLE_MODS.get()) {
            String modId = entry.trim().toLowerCase(Locale.ROOT);
            if (!modId.isEmpty()) {
                mods.add(modId);
            }
        }

        Set<Identifier> items = new HashSet<>();
        for (String entry : ListSetting.DISABLE_ITEMS.get()) {
            Identifier itemId = Identifier.tryParse(entry.trim().toLowerCase(Locale.ROOT));
            if (itemId == null) {
                SomeStacksCommon.LOGGER.warn("Ignoring malformed item id \"{}\" in disable_items", entry);
                continue;
            }
            items.add(itemId);
        }

        disabledMods = Set.copyOf(mods);
        disabledItems = Set.copyOf(items);

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
    public static boolean isItemDisabled(Identifier itemId) {
        return disabledItems.contains(itemId);
    }

}
