package com.github.crittscott.somestacks.util;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StackSortTest {
    private static StackSort.SortKey key(String id, int damage, String tag) {
        return new StackSort.SortKey(new ResourceLocation(id), damage, tag);
    }

    @Test
    void keysPreserveItemDamageAndNullableNbtOrdering() {
        assertTrue(key("minecraft:dirt", 9, "z").compareTo(key("minecraft:stone", 0, null)) < 0);
        assertTrue(key("minecraft:stone", 0, "z").compareTo(key("minecraft:stone", 1, null)) < 0);
        assertTrue(key("minecraft:stone", 0, null).compareTo(key("minecraft:stone", 0, "{}")) < 0);
        assertTrue(key("minecraft:stone", 0, "{variant:1}").compareTo(
                key("minecraft:stone", 0, "{variant:2}")) < 0);
        assertEquals(0, key("minecraft:stone", 0, null).compareTo(key("minecraft:stone", 0, null)));
    }
}
