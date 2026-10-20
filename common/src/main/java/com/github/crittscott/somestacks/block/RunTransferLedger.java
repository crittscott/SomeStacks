package com.github.crittscott.somestacks.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Loader-neutral staged slot differences, rollback state, and ordered commit replay. */
public final class RunTransferLedger {
    private LinkedHashMap<Integer, ItemStack> originals = new LinkedHashMap<>();
    private LinkedHashMap<Integer, ItemStack> staged = new LinkedHashMap<>();
    private int structuralExtractionSlot = -1;

    public ItemStack current(int slot, StackRunItemAccess run) {
        ItemStack pending = staged.get(slot);
        return pending != null ? pending : run == null ? ItemStack.EMPTY : run.getSlot(slot);
    }

    public boolean allowsInsertion() { return structuralExtractionSlot < 0; }

    public boolean mayExtract(int slot) {
        if (structuralExtractionSlot >= 0 && structuralExtractionSlot != slot) return false;
        return staged.entrySet().stream().noneMatch(entry -> entry.getKey() != slot
                && !ItemStack.matches(entry.getValue(), originals.get(entry.getKey())));
    }

    public void stage(int slot, ItemStack result, StackRunItemAccess run) {
        originals.computeIfAbsent(slot, ignored -> run.getSlot(slot).copy());
        staged.put(slot, result);
    }

    public void noteStructuralExtraction(int slot) { structuralExtractionSlot = slot; }

    public boolean growsBeyond(int slots) {
        return staged.entrySet().stream().anyMatch(entry -> entry.getKey() >= slots
                && !entry.getValue().isEmpty());
    }

    public State snapshot() {
        return new State(copy(originals), copy(staged), structuralExtractionSlot);
    }

    public void restore(State state) {
        originals = copy(state.originals());
        staged = copy(state.staged());
        structuralExtractionSlot = state.structuralExtractionSlot();
    }

    /** Clears staged state, then removes before inserting bottom-up, resolving after each edit. */
    public void replay(Supplier<? extends StackRunItemAccess> run, BlockPos where) {
        var before = originals;
        var after = staged;
        originals = new LinkedHashMap<>();
        staged = new LinkedHashMap<>();
        structuralExtractionSlot = -1;
        for (var entry : before.entrySet()) {
            ItemStack old = entry.getValue();
            ItemStack result = after.get(entry.getKey());
            int removed = ItemStack.isSameItemSameComponents(old, result)
                    ? old.getCount() - result.getCount() : old.getCount();
            if (removed > 0 && run.get().extract(entry.getKey(), removed, false).getCount() != removed) {
                throw new IllegalStateException("Committed extraction changed capacity at "
                        + where + " slot " + entry.getKey());
            }
        }
        for (int slot : before.keySet().stream().sorted().toList()) {
            ItemStack old = before.get(slot);
            ItemStack result = after.get(slot);
            int added = ItemStack.isSameItemSameComponents(old, result)
                    ? result.getCount() - old.getCount() : result.getCount();
            if (added > 0 && run.get().insertAt(slot, result.copyWithCount(added), false) != added) {
                throw new IllegalStateException("Committed insertion changed capacity at " + where + " slot " + slot);
            }
        }
    }

    private static LinkedHashMap<Integer, ItemStack> copy(Map<Integer, ItemStack> source) {
        var result = new LinkedHashMap<Integer, ItemStack>();
        source.forEach((slot, stack) -> result.put(slot, stack.copy()));
        return result;
    }

    public record State(LinkedHashMap<Integer, ItemStack> originals,
                        LinkedHashMap<Integer, ItemStack> staged, int structuralExtractionSlot) {}
}
