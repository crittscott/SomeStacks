package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.util.StackSort;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;

/** Storage settling order across every item-identity component used by the comparator. */
public final class StackSortChecks {
    private StackSortChecks() {}

    /**
     * Storage sorting orders item ids, damage, data-component variants, and compatible counts
     * deterministically, with empty slots last. To reproduce in-game: insert those variants into
     * scattered Storage slots through automation and let the pile settle. The visible cubes follow
     * that stable order and each compatible partial follows its full stacks.
     */
    public static void comparatorOrdersEveryIdentityComponent(GameTestHelper helper) {
        ItemStack dirt = new ItemStack(Items.DIRT);
        ItemStack stone = new ItemStack(Items.STONE);
        check(StackSort.COMPARATOR.compare(dirt, stone) < 0,
                "Registry item id did not lead ordering");
        check(StackSort.COMPARATOR.compare(stone, ItemStack.EMPTY) < 0,
                "Empty stack did not sort last");

        ItemStack undamaged = new ItemStack(Items.WOODEN_PICKAXE);
        ItemStack damaged = new ItemStack(Items.WOODEN_PICKAXE);
        damaged.setDamageValue(1);
        check(StackSort.COMPARATOR.compare(undamaged, damaged) < 0,
                "Damage did not lead variant ordering");

        ItemStack plain = new ItemStack(Items.STONE);
        ItemStack componentOneFull = componentStone(1, 64);
        ItemStack componentOnePartial = componentStone(1, 3);
        ItemStack componentTwo = componentStone(2, 64);
        check(StackSort.COMPARATOR.compare(plain, componentOneFull) < 0,
                "Plain stack did not sort before component-bearing stack");
        check(StackSort.COMPARATOR.compare(componentOneFull, componentTwo) < 0,
                "Component identity was not ordered stably");
        check(StackSort.COMPARATOR.compare(componentOneFull, componentOnePartial) < 0,
                "Full stack did not sort before compatible partial");
        helper.succeed();
    }

    private static ItemStack componentStone(int variant, int count) {
        ItemStack stack = new ItemStack(Items.STONE, count);
        CompoundTag tag = new CompoundTag();
        tag.putInt("variant", variant);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }
}
