package com.github.crittscott.somestacks;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ServerConfig {
    private ServerConfig() {}

    public static final ForgeConfigSpec SERVER_CONFIG;

    public static final ForgeConfigSpec.IntValue PILE_SORT_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue PILE_SORT_MAX_STACKS;

    public static final ForgeConfigSpec.BooleanValue ENABLE_STORAGE_STACK_BLOCK;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SINGLES_STACK_BLOCK;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BAR_STACK_BLOCK;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLE_MODS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLE_ITEMS;

    private static volatile Set<String> disabledMods = Set.of();
    private static volatile Set<ResourceLocation> disabledItems = Set.of();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Pile Sorting Configuration").push("pile_sorting");

        PILE_SORT_COOLDOWN_TICKS = builder
                .comment("Cooldown between pile resorts (in ticks, 20 = 1 second)")
                .defineInRange("cooldown_ticks", 20, 1, Integer.MAX_VALUE);

        PILE_SORT_MAX_STACKS = builder
                .comment("Maximum number of stacks to resort in a pile operation")
                .defineInRange("max_stacks_per_resort", 3, 1, Integer.MAX_VALUE);

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
                        Arrays.asList("spartanfire", "spartanweaponry"),
                        obj -> obj instanceof String);

        DISABLE_ITEMS = builder
                .comment("List of specific items to disable from being stored in stacks",
                        "Format: \"modid:itemname\"",
                        "Example: \"immersiveengineering:toolupgrade_drill_damage\"")
                .defineList("disable_items",
                        Collections.emptyList(),
                        obj -> obj instanceof String);

        builder.pop();

        SERVER_CONFIG = builder.build();
    }

    /**
     * Resolves the compatibility lists into lookup sets. Config entries are free-form text, so
     * mod ids are lowercased to match the namespace of a {@link ResourceLocation} and item ids are
     * parsed once here; a malformed item id is reported and dropped rather than being re-parsed and
     * swallowed on every deposit.
     *
     * <p>Called from the config load and reload events, which fire on Forge's file-watcher thread.
     * Each list is published as an immutable set through a volatile field, so a server thread
     * lookup sees either the old lists or the new ones.
     */
    public static void bakeCompatibilityLists() {
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
    }

    /**
     * @param namespace a mod id, in any case
     * @return whether items from that mod are barred from stacks
     */
    public static boolean isModDisabled(String namespace) {
        return disabledMods.contains(namespace.toLowerCase(Locale.ROOT));
    }

    /**
     * @param itemId a registry name
     * @return whether that item is barred from stacks
     */
    public static boolean isItemDisabled(ResourceLocation itemId) {
        return disabledItems.contains(itemId);
    }
}
