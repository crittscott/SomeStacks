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
     * Storage sorting orders item ids, damage, and data-component variants
     * deterministically. To reproduce in-game: insert those variants into
     * scattered Storage slots through automation and let the pile settle. The visible cubes follow
     * that stable order and each compatible partial follows its full stacks.
     */
    public static void comparatorOrdersEveryIdentityComponent(GameTestHelper helper) {
        ItemStack dirt = new ItemStack(Items.DIRT);
        ItemStack stone = new ItemStack(Items.STONE);
        check(StackSort.key(dirt).compareTo(StackSort.key(stone)) < 0,
                "Registry item id did not lead ordering");

        ItemStack undamaged = new ItemStack(Items.WOODEN_PICKAXE);
        ItemStack damaged = new ItemStack(Items.WOODEN_PICKAXE);
        damaged.setDamageValue(1);
        check(StackSort.key(undamaged).compareTo(StackSort.key(damaged)) < 0,
                "Damage did not lead variant ordering");

        ItemStack plain = new ItemStack(Items.STONE);
        ItemStack componentOneFull = componentStone(1, 64);
        ItemStack componentTwo = componentStone(2, 64);
        check(StackSort.key(plain).compareTo(StackSort.key(componentOneFull)) < 0,
                "Plain stack did not sort before component-bearing stack");
        check(StackSort.key(componentOneFull).compareTo(StackSort.key(componentTwo)) < 0,
                "Component identity was not ordered stably");
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
