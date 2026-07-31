package com.github.crittscott.somestacks.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.IItemHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The drops produced by one Bar collapse, packed by exact identity at each block they fell from.
 */
final class BarDropBatch {
    private final Map<BlockPos, Map<StackKey, Group>> dropsByPosition = new LinkedHashMap<>();

    void add(BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        Map<StackKey, Group> drops =
                dropsByPosition.computeIfAbsent(pos.immutable(), ignored -> new LinkedHashMap<>());
        StackKey key = StackKey.of(stack);
        Group group = drops.computeIfAbsent(key, ignored -> new Group(stack.copy()));
        group.count += stack.getCount();
    }

    void addAll(BlockPos pos, IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            add(pos, handler.getStackInSlot(slot));
        }
    }

    void spawn(Level level) {
        if (level.isClientSide) {
            return;
        }

        for (Map.Entry<BlockPos, Map<StackKey, Group>> position : dropsByPosition.entrySet()) {
            for (Group group : position.getValue().values()) {
                int remaining = group.count;
                while (remaining > 0) {
                    ItemStack drop = group.model.copy();
                    drop.setCount(Math.min(remaining, drop.getMaxStackSize()));
                    Block.popResource(level, position.getKey(), drop);
                    remaining -= drop.getCount();
                }
            }
        }
    }

    private static final class Group {
        private final ItemStack model;
        private int count;

        private Group(ItemStack model) {
            this.model = model;
        }
    }
}
