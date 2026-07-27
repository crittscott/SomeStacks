package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.util.StackSort;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.github.crittscott.somestacks.gametest.GameTestSupport.check;

@GameTestHolder(SomeStacks.MODID)
@PrefixGameTestTemplate(false)
public final class StackSortGameTests {
    private StackSortGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE)
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

        ItemStack untagged = new ItemStack(Items.STONE);
        ItemStack tagOneFull = taggedStone(1, 64);
        ItemStack tagOnePartial = taggedStone(1, 3);
        ItemStack tagTwo = taggedStone(2, 64);
        check(StackSort.COMPARATOR.compare(untagged, tagOneFull) < 0,
                "Untagged stack did not sort before tagged stack");
        check(StackSort.COMPARATOR.compare(tagOneFull, tagTwo) < 0,
                "Tag identity was not ordered stably");
        check(StackSort.COMPARATOR.compare(tagOneFull, tagOnePartial) < 0,
                "Full stack did not sort before compatible partial");
        helper.succeed();
    }

    private static ItemStack taggedStone(int variant, int count) {
        ItemStack stack = new ItemStack(Items.STONE, count);
        CompoundTag tag = new CompoundTag();
        tag.putInt("variant", variant);
        stack.setTag(tag);
        return stack;
    }
}
