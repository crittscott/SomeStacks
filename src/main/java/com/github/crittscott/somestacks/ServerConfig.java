package com.github.crittscott.somestacks;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
}
