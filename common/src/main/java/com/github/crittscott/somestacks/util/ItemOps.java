package com.github.crittscott.somestacks.util;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.lighting.LightEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Item handling shared by all three stack types: what a hand can accept, how a taken stack reaches
 * the player, how loose stacks pack into whole ones, how a broken block sheds its contents, and how
 * stored items add up to a light level.
 */
public final class ItemOps {
    private ItemOps() {}

    /** The brightest a block can be, and so the top of each stack block's light property. */
    public static final int MAX_LIGHT_LEVEL = LightEngine.MAX_LEVEL;

    /** Share of a stored block's own emission that a cell holding it contributes. */
    private static final int LIGHT_SHARE_DIVISOR = 4;

    /**
     * One output stack of {@link #consolidate}: {@code count} items of {@code model}'s exact
     * identity. The model is one of the input stacks, not a copy.
     */
    public record Piece(ItemStack model, int count) {
        /** A new stack holding this piece. */
        public ItemStack toStack() {
            return model.copyWithCount(count);
        }
    }

    /**
     * Whether an extraction may land in this hand: it must be empty, or hold the same item and tags
     * with room left. This is the rule that keeps a player's hand from being swapped out
     * mid-gesture.
     */
    public static boolean canTakeIntoHand(ItemStack hand, ItemStack offer) {
        if (offer.isEmpty()) return false;
        if (hand.isEmpty()) return true;
        return ItemStack.isSameItemSameTags(hand, offer)
                && hand.getCount() < hand.getMaxStackSize();
    }

    /**
     * Totals stacks by exact identity, keeping first-seen order, and re-cuts each total into whole
     * stacks of the item's maximum size plus at most one remainder. Grouping by identity rather
     * than by adjacency means two compatible stacks always merge, wherever they sat.
     *
     * <p>Identity is vanilla's item-and-tags test, the same one insertion uses to decide whether
     * two stacks merge. No stack is copied: each piece refers to the first input stack of its
     * identity, so the caller must not mutate the inputs while it holds the pieces.
     */
    /**
     * Hashes and compares stacks by item and tags, ignoring count: vanilla's merge identity.
     * Vanilla's own strategy for this, {@code ItemStackLinkedSet.TYPE_AND_TAG}, is private.
     */
    private static final Hash.Strategy<ItemStack> SAME_ITEM_SAME_TAGS =
            new Hash.Strategy<>() {
                @Override
                public int hashCode(ItemStack stack) {
                    return stack == null ? 0 : Objects.hash(stack.getItem(), stack.getTag());
                }

                @Override
                public boolean equals(ItemStack a, ItemStack b) {
                    return a == b || (a != null && b != null && ItemStack.isSameItemSameTags(a, b));
                }
            };

    public static List<Piece> consolidate(Iterable<ItemStack> stacks) {
        Object2IntLinkedOpenCustomHashMap<ItemStack> totals =
                new Object2IntLinkedOpenCustomHashMap<>(SAME_ITEM_SAME_TAGS);
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                totals.addTo(stack, stack.getCount());
            }
        }

        List<Piece> pieces = new ArrayList<>();
        for (Object2IntMap.Entry<ItemStack> total : totals.object2IntEntrySet()) {
            ItemStack model = total.getKey();
            int max = Math.max(1, model.getMaxStackSize());
            for (int remaining = total.getIntValue(); remaining > 0; remaining -= max) {
                pieces.add(new Piece(model, Math.min(remaining, max)));
            }
        }
        return pieces;
    }

    /**
     * Moves as much of {@code incoming} into {@code hand} as it will hold, growing the hand in
     * place. The caller shrinks the source by the amount reported.
     *
     * @return how many items moved, zero when the two do not stack
     */
    public static int mergeIntoStack(ItemStack hand, ItemStack incoming) {
        if (hand.isEmpty() || incoming.isEmpty()) return 0;
        if (!ItemStack.isSameItemSameTags(hand, incoming)) return 0;
        int can = Math.min(incoming.getCount(), hand.getMaxStackSize() - hand.getCount());
        hand.grow(can);
        return can;
    }

    /** Drops every nonempty handler slot at the block position without mutating the handler. */
    public static void dropAllItems(StackItemStorage handler, Level level, BlockPos pos) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }

    /**
     * Puts an extracted stack in the player's hand, merging with what is already there, and drops
     * whatever will not fit at their feet.
     */
    public static void giveToPlayerOrDrop(Player player, InteractionHand hand, ItemStack taken) {
        ItemStack handStack = player.getItemInHand(hand);

        if (handStack.isEmpty()) {
            player.setItemInHand(hand, taken);
            taken = ItemStack.EMPTY;
        } else {
            int moved = mergeIntoStack(handStack, taken);
            taken.shrink(moved);
            player.setItemInHand(hand, handStack);
        }

        if (!taken.isEmpty()) {
            player.drop(taken, false);
        }
    }

    /** Whether every slot in the handler is empty. */
    public static boolean isHandlerEmpty(StackItemStorage handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Adds each stored BlockItem's fractional emission, capped at the vanilla maximum of 15. */
    public static int calculateLightLevelFromItems(StackItemStorage handler) {
        int totalLight = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem bi) {
                int blockLight = bi.getBlock().defaultBlockState().getLightEmission();
                totalLight += blockLight / LIGHT_SHARE_DIVISOR;
            }
        }
        return Math.min(totalLight, MAX_LIGHT_LEVEL);
    }
}
