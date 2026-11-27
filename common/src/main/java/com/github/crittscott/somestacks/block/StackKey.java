package com.github.crittscott.somestacks.block;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import javax.annotation.Nullable;

/**
 * Exact stack identity: two stacks merge if and only if their keys are equal.
 *
 * <p>The component patch is immutable, so later mutation or removal of the source stack cannot
 * change the key's equality or hash code.
 */
record StackKey(Item item, DataComponentPatch components) {
    /** {@code count} items of {@code model}'s identity; the model itself is never handed out. */
    record Piece(ItemStack model, int count) {
        /** A fresh stack of this piece. */
        ItemStack toStack() {
            return model.copyWithCount(count);
        }
    }

    static StackKey of(ItemStack stack) {
        return new StackKey(stack.getItem(), stack.getComponentsPatch());
    }

    /**
     * Totals {@code stacks} by exact identity and re-cuts each total into pieces no larger than
     * {@code pieceLimit} of the identity's model. Identities come out in first-seen order, or in {@code order} of their
     * first-seen stacks when one is given; each identity's full pieces precede its partial one.
     * Nothing is copied: each piece's model is the identity's first-seen source stack, which the
     * caller must not change while it uses the pieces.
     */
    static List<Piece> consolidate(
            Iterable<ItemStack> stacks,
            @Nullable Comparator<ItemStack> order,
            ToIntFunction<ItemStack> pieceLimit) {
        Map<StackKey, Piece> totals = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                totals.merge(
                        of(stack),
                        new Piece(stack, stack.getCount()),
                        (total, more) -> new Piece(total.model(), total.count() + more.count()));
            }
        }
        List<Piece> ordered = new ArrayList<>(totals.values());
        if (order != null) {
            ordered.sort(Comparator.comparing(Piece::model, order));
        }
        List<Piece> pieces = new ArrayList<>();
        for (Piece total : ordered) {
            int limit = pieceLimit.applyAsInt(total.model());
            for (int remaining = total.count(); remaining > 0; remaining -= limit) {
                pieces.add(new Piece(total.model(), Math.min(remaining, limit)));
            }
        }
        return pieces;
    }
}
