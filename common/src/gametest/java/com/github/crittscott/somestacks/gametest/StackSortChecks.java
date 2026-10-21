package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.util.StackSort;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;

/** Storage settling order across every item-identity component used by the comparator. */
public final class StackSortChecks {
    private StackSortChecks() {}

    /**
     * Internal ordering check with no direct manual reproduction: compare registry ids, damage,
     * absent versus present NBT, NBT variants, and descending count ties; assert
     * that sort-key snapshots survive source NBT mutation and exclude stack count.
     *
     * <p>Nearest visible check: deposit dirt, stone, and differently damaged wooden pickaxes into
     * Storage. After settlement, items pack in deterministic identity order and distinct damage/NBT
     * identities stay separate.
     */
    public static void comparatorOrdersEveryIdentityComponent(GameTestHelper helper) {
        ItemStack dirt = new ItemStack(Items.DIRT);
        ItemStack stone = new ItemStack(Items.STONE);
        check(StackSort.comparePieces(StackSort.key(dirt), dirt.getCount(),
                StackSort.key(stone), stone.getCount()) < 0,
                "Registry item id did not lead ordering");

        ItemStack undamaged = new ItemStack(Items.WOODEN_PICKAXE);
        ItemStack damaged = new ItemStack(Items.WOODEN_PICKAXE);
        damaged.setDamageValue(1);
        check(StackSort.comparePieces(StackSort.key(undamaged), undamaged.getCount(),
                StackSort.key(damaged), damaged.getCount()) < 0,
                "Damage did not lead variant ordering");

        ItemStack untagged = new ItemStack(Items.STONE);
        ItemStack tagOneFull = taggedStone(1, 64);
        ItemStack tagOnePartial = taggedStone(1, 3);
        ItemStack tagTwo = taggedStone(2, 64);
        check(StackSort.comparePieces(StackSort.key(untagged), untagged.getCount(),
                StackSort.key(tagOneFull), tagOneFull.getCount()) < 0,
                "Untagged stack did not sort before tagged stack");
        check(StackSort.comparePieces(StackSort.key(tagOneFull), tagOneFull.getCount(),
                StackSort.key(tagTwo), tagTwo.getCount()) < 0,
                "Tag identity was not ordered stably");
        check(StackSort.comparePieces(StackSort.key(tagOneFull), tagOneFull.getCount(),
                StackSort.key(tagOnePartial), tagOnePartial.getCount()) < 0,
                "Full stack did not sort before compatible partial");
        check(StackSort.key(tagOneFull).equals(StackSort.key(tagOnePartial)),
                "Precomputed identity fields depended on stack count");
        StackSort.SortKey snapshot = StackSort.key(tagOneFull);
        tagOneFull.getTag().putInt("variant", 99);
        check(snapshot.compareTo(StackSort.key(tagTwo)) < 0,
                "Sort key changed when the source NBT changed");
        ItemStack emptyTag = new ItemStack(Items.STONE);
        emptyTag.setTag(new CompoundTag());
        check(StackSort.key(untagged).compareTo(StackSort.key(emptyTag)) < 0,
                "Explicit empty NBT was treated as absent NBT");
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
