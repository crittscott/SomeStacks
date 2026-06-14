package com.github.crittscott.somestacks;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModTags {
    private ModTags() {}

    public static final TagKey<Item> FORGE_INGOTS = TagKey.create(
            Registries.ITEM,
            new ResourceLocation(SomeStacks.MODID, "ingots")
    );
}
