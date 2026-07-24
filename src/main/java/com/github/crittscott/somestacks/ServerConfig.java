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

    public static final ForgeConfigSpec.IntValue MAX_PILE_HEIGHT;

    public static final ForgeConfigSpec.BooleanValue ENABLE_STORAGE_STACK_BLOCK;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SINGLES_STACK_BLOCK;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BAR_STACK_BLOCK;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLE_MODS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLE_ITEMS;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SS_COMMAND_ALLOWLIST;

    public static final ForgeConfigSpec.IntValue TEST_WALL_PLACEMENTS_PER_TICK;

    private static volatile Set<String> disabledMods = Set.of();
    private static volatile Set<ResourceLocation> disabledItems = Set.of();
    private static volatile Set<String> ssAllowlist = Set.of();

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

        builder.comment("Command Configuration").push("commands");

        SS_COMMAND_ALLOWLIST = builder
                .comment("Player names permitted to use the /ss render-tuning command.",
                        "Empty by default: no one may use /ss until a name is added here.",
                        "In single player, add your own name.",
                        "Names must be quoted: [\"Alice\", \"Bob\"]")
                .defineList("ss_command_allowlist",
                        Collections.emptyList(),
                        obj -> obj instanceof String);

        builder.pop();

        builder.comment("Test Wall Configuration").push("test_wall");

        TEST_WALL_PLACEMENTS_PER_TICK = builder
                .comment("Blocks the 'ss test' command places per tick, counting both stacks and floor.",
                        "A wall spanning every loaded mod is tens of thousands of placements; lower",
                        "values spread it over more ticks.")
                .defineInRange("placements_per_tick", 64, 1, Integer.MAX_VALUE);

        builder.pop();

        SERVER_CONFIG = builder.build();
    }

    /**
     * Resolves the server's free-form text lists into lookup sets: the disabled-mod and
     * disabled-item compatibility lists and the {@code ss} command allow list. Mod ids and player
     * names are lowercased and item ids are parsed once here; a malformed item id is reported and
     * dropped rather than being re-parsed and swallowed on every deposit.
     *
     * <p>Called from the config load and reload events, which fire on Forge's file-watcher thread.
     * Each list is published as an immutable set through a volatile field, so a server thread
     * lookup sees either the old lists or the new ones.
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

        Set<String> allowed = new HashSet<>();
        for (String entry : SS_COMMAND_ALLOWLIST.get()) {
            String name = entry.trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty()) {
                allowed.add(name);
            }
        }

        disabledMods = Set.copyOf(mods);
        disabledItems = Set.copyOf(items);
        ssAllowlist = Set.copyOf(allowed);
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

    /**
     * @param playerName a player's profile name, in any case
     * @return whether that player may use the {@code ss} command
     */
    public static boolean isSsAllowed(String playerName) {
        return ssAllowlist.contains(playerName.toLowerCase(Locale.ROOT));
    }
}
