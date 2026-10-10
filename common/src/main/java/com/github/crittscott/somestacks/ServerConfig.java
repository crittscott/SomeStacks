package com.github.crittscott.somestacks;

import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Immutable server policy, command edits, and normalized admission lookups. */
public final class ServerConfig {
    private ServerConfig() {}

    /** Persistence and lifecycle supplied by the selected loader. */
    public interface Backend {
        Settings loadFor(MinecraftServer server);
        Settings reload();
        boolean save(Settings settings);
    }
    public static final int DEFAULT_MAX_PILE_HEIGHT = 8;
    public static final int MAX_PILE_HEIGHT = 64;
    public static final int DEFAULT_GALLERY_PLACEMENTS_PER_TICK = 64;

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
    private static volatile Set<ResourceLocation> disabledItems = Set.of();

    public static void install(Backend implementation) { backend = implementation; }
    public static Backend backend() { return backend; }
    public static Settings settings() { return settings; }
    public static void apply(Settings policy) {
        settings = policy;
        bakeServerLists();
    }
    public static void loadFor(MinecraftServer server) { apply(backend.loadFor(server)); }
    public static boolean reload() {
        if (backend == null) return false;
        apply(backend.reload());
        return true;
    }
    public static boolean save() { return backend != null && backend.save(settings); }

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
                Commands.LEVEL_ADMINS,
                List.of(),
                List.of());
    }

    /** A command-editable policy list. */
    public static final class ListSetting {
        private final String name;
        private final java.util.function.Function<Settings, List<String>> getter;
        private ListSetting(String name, java.util.function.Function<Settings, List<String>> getter) {
            this.name = name;
            this.getter = getter;
        }
        public List<String> get() { return getter.apply(settings); }
        private void set(List<String> value) {
            apply(new Settings(settings.maxPileHeight(), settings.enableStorageStackBlock(),
                    settings.enableSinglesStackBlock(), settings.enableBarStackBlock(),
                    name.equals("disable_mods") ? value : settings.disableMods(),
                    name.equals("disable_items") ? value : settings.disableItems(),
                    settings.renderGalleryPlacementsPerTick(), settings.galleryEnabled(),
                    settings.galleryPermissionLevel(),
                    name.equals("gen_mods") ? value : settings.genMods(),
                    name.equals("gen_items") ? value : settings.genItems()));
        }
    }
    public static final ListSetting DISABLE_MODS = new ListSetting("disable_mods", Settings::disableMods);
    public static final ListSetting DISABLE_ITEMS = new ListSetting("disable_items", Settings::disableItems);
    public static final ListSetting GEN_MODS = new ListSetting("gen_mods", Settings::genMods);
    public static final ListSetting GEN_ITEMS = new ListSetting("gen_items", Settings::genItems);
    public static int maxPileHeight() { return settings.maxPileHeight(); }
    public static boolean enableStorageStackBlock() { return settings.enableStorageStackBlock(); }
    public static boolean enableSinglesStackBlock() { return settings.enableSinglesStackBlock(); }
    public static boolean enableBarStackBlock() { return settings.enableBarStackBlock(); }
    public static int renderGalleryPlacementsPerTick() { return settings.renderGalleryPlacementsPerTick(); }
    public static boolean galleryEnabled() { return settings.galleryEnabled(); }
    public static int galleryRequiredPermissionLevel() { return settings.galleryPermissionLevel(); }
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
    public static boolean isItemDisabled(ResourceLocation itemId) {
        return disabledItems.contains(itemId);
    }

}
