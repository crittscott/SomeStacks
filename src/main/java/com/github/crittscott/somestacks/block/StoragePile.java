package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.util.StackSort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A maximal contiguous vertical run of Storage Stacks, treated as one inventory.
 *
 * <p>Every operation on a Storage Stack resolves the pile it belongs to and acts on the whole
 * column: deposits fill from the base upward whatever block or slot they were aimed at, the
 * capability exposes every block's slots as one flat range, and settling consolidates, sorts and
 * packs the entire height down. The base block's {@code permanent} flag is the pile's, and
 * settling propagates it, so a pile that is split or joined heals to one answer.
 *
 * <p>Instances are resolved fresh per operation and are not held across world edits.
 */
public final class StoragePile {
    private final Level level;
    private final BlockPos base;
    private final List<StorageStackBE> blocks;

    private StoragePile(Level level, BlockPos base, List<StorageStackBE> blocks) {
        this.level = level;
        this.base = base;
        this.blocks = blocks;
    }

    /**
     * Resolves the pile containing {@code pos}, or {@code null} when that position holds no
     * Storage Stack. Client-side levels never resolve a pile: the pile is server state, and
     * reading its bounds would consult the server config.
     */
    @Nullable
    public static StoragePile at(@Nullable Level level, BlockPos pos) {
        if (level == null || level.isClientSide) {
            return null;
        }
        if (!(level.getBlockEntity(pos) instanceof StorageStackBE sbe)) {
            return null;
        }
        return sbe.pile();
    }

    /**
     * Walks the world for the run containing {@code pos}. Every caller reaches this through the
     * cache {@link StorageStackBE#pile()} keeps, because a capability read resolves the run once per
     * slot and a machine reading the whole handler does that hundreds of times a tick.
     */
    static StoragePile resolve(Level level, BlockPos pos) {
        BlockPos base = pos;
        while (level.getBlockEntity(base.below()) instanceof StorageStackBE) {
            base = base.below();
        }

        List<StorageStackBE> blocks = new ArrayList<>();
        BlockPos current = base;
        while (level.getBlockEntity(current) instanceof StorageStackBE sbe) {
            blocks.add(sbe);
            current = current.above();
        }

        return new StoragePile(level, base, blocks);
    }

    /**
     * Drops the cached run held by every block a change at {@code pos} could have altered: the run
     * {@code pos} belongs to when it still holds a Storage Stack, and the runs above and below it
     * when it no longer does.
     *
     * <p>The cache expires on its own at the end of the tick, which covers anything that edits the
     * world without telling us. This is what covers the same tick, and the block's own place and
     * remove hooks are where every structural change passes, including the growth and trimming this
     * class does itself.
     */
    static void invalidateAround(Level level, BlockPos pos) {
        invalidateRun(level, pos, Direction.UP);
        invalidateRun(level, pos.above(), Direction.UP);
        invalidateRun(level, pos.below(), Direction.DOWN);
    }

    private static void invalidateRun(Level level, BlockPos from, Direction direction) {
        BlockPos current = from;
        while (level.getBlockEntity(current) instanceof StorageStackBE sbe) {
            sbe.invalidatePile();
            current = current.relative(direction);
        }
    }

    /** Marks the pile at {@code pos} for settling, if one is there. */
    public static void markDirtyAt(@Nullable Level level, BlockPos pos) {
        StoragePile pile = at(level, pos);
        if (pile != null) {
            pile.markDirty();
        }
    }

    /** The configured ceiling on pile height. Server-side only. */
    public static int maxHeight() {
        return ServerConfig.MAX_PILE_HEIGHT.get();
    }

    /**
     * Whether a Storage Stack may be created at {@code pos}: the contiguous column it would form,
     * counting the runs both below and above it, must fit the configured maximum. Testing the
     * whole resulting column rather than one neighbouring pile is what stops a block placed into
     * the gap between two piles from joining them into an over-tall one.
     */
    public static boolean columnHasRoomFor(Level level, BlockPos pos) {
        return 1 + runLength(level, pos, Direction.DOWN) + runLength(level, pos, Direction.UP) <= maxHeight();
    }

    private static int runLength(Level level, BlockPos from, Direction direction) {
        Block storage = ModRegistry.STORAGE_STACK_BLOCK.get();
        int length = 0;
        BlockPos current = from.relative(direction);
        while (level.getBlockState(current).is(storage)) {
            length++;
            current = current.relative(direction);
        }
        return length;
    }

    /**
     * Gives a Storage Stack that has just been placed the mode of the pile it joins, taking it
     * from the block below when there is one and otherwise from the block above. Placing a block
     * beneath a permanent pile makes it the new base, so without this the pile would silently
     * become temporary.
     */
    public static void adoptNeighbourState(Level level, BlockPos pos, StorageStackBE placed) {
        StorageStackBE neighbour = null;
        if (level.getBlockEntity(pos.below()) instanceof StorageStackBE below) {
            neighbour = below;
        } else if (level.getBlockEntity(pos.above()) instanceof StorageStackBE above) {
            neighbour = above;
        }
        if (neighbour != null) {
            placed.adoptPileState(neighbour.isPermanent(), neighbour.getRotation());
        }
    }

    public BlockPos basePos() {
        return base;
    }

    public int height() {
        return blocks.size();
    }

    /** Whether this pile's blocks survive being emptied. The base block's flag is authoritative. */
    public boolean isPermanent() {
        return blocks.get(0).isPermanent();
    }

    public void setPermanent(boolean permanent) {
        for (StorageStackBE sbe : blocks) {
            sbe.inheritPermanent(permanent);
        }
        markDirty();
    }

    /** Slots the pile actually holds: 27 per block, indexed from the base upward. */
    public int totalSlots() {
        return blocks.size() * StorageStackBE.SLOTS;
    }

    /**
     * Slots the pile advertises to automation: the ones it holds, plus one block's worth of
     * headroom while the configured height allows another block. See
     * {@link SinglesColumn#advertisedSlots()} for why it is neither the potential height nor the
     * real one.
     */
    public int advertisedSlots() {
        int levels = blocks.size() < maxHeight() ? blocks.size() + 1 : blocks.size();
        return StorageStackBE.SLOTS * levels;
    }

    public ItemStack getSlot(int flatSlot) {
        if (flatSlot < 0 || flatSlot >= totalSlots()) {
            return ItemStack.EMPTY;
        }
        return handlerOf(flatSlot).getStackInSlot(flatSlot % StorageStackBE.SLOTS);
    }

    /** Extracts from one pile slot and schedules the settle that packs the rest down over it. */
    public ItemStack extract(int flatSlot, int amount) {
        if (flatSlot < 0 || flatSlot >= totalSlots()) {
            return ItemStack.EMPTY;
        }
        ItemStack extracted = handlerOf(flatSlot).extractItem(flatSlot % StorageStackBE.SLOTS, amount, false);
        if (!extracted.isEmpty()) {
            markDirty();
        }
        return extracted;
    }

    private IItemHandler handlerOf(int flatSlot) {
        return blocks.get(flatSlot / StorageStackBE.SLOTS).getItems();
    }

    /**
     * The comparator output for the whole pile, which is what every block of it reports. Held in one
     * place so the value a block answers with and the value a settle decides to publish cannot drift
     * apart.
     *
     * <p>This is vanilla's container conversion, over a fill level computed the way vanilla computes
     * it. The bottom of the range is reserved rather than proportional: any nonempty pile reads at
     * least 1, so signal 0 means empty and nothing else, which is what the standard emptiness
     * circuit tests. A pile is large enough that this matters — 216 slots at full height, where a
     * proportional conversion would leave hundreds of items reading 0.
     */
    public int comparatorSignal() {
        double fill = fillLevel();
        return fill > 0.0 ? Mth.floor(fill * 14.0) + 1 : 0;
    }

    /**
     * Tells the pile's neighbours to read the comparator output again, but only when that output has
     * actually changed.
     *
     * <p>Every block of the pile reports the whole pile's fill, so an edit anywhere in it changes the
     * value every block answers with — including blocks whose own slots the edit never touched, and
     * which therefore publish nothing of their own. Those are exactly the blocks a comparator may be
     * sitting against, so the whole run is notified rather than the one block that changed. The
     * guard is what keeps that from being a run-length worth of neighbour updates per item moved.
     *
     * <p>The base block holds the last published value, because the base is what identifies a pile.
     */
    private void publishComparatorSignal() {
        if (blocks.isEmpty()) {
            return;
        }
        int signal = comparatorSignal();
        if (!blocks.get(0).exchangePublishedSignal(signal)) {
            return;
        }

        Block block = ModRegistry.STORAGE_STACK_BLOCK.get();
        for (StorageStackBE sbe : blocks) {
            // The comparator-aware update vanilla containers use: it reaches a comparator sitting one
            // block further away behind a solid block, which the plain neighbour update does not.
            level.updateNeighbourForOutputSignal(sbe.getBlockPos(), block);
        }
    }

    public double fillLevel() {
        double sum = 0.0;
        for (int i = 0; i < totalSlots(); i++) {
            ItemStack stack = getSlot(i);
            if (!stack.isEmpty()) {
                sum += (double) stack.getCount() / stack.getMaxStackSize();
            }
        }
        return sum / totalSlots();
    }

    // Deposit

    /**
     * Fills the pile from the base upward, ignoring where the deposit was aimed, and grows the
     * column while items remain and the configured height allows it.
     *
     * @param placer the player responsible for any block this deposit creates, or null for
     *               automation, whose growth is attributed to the level's fake player and checked
     *               against the same protections.
     * @return how many items were taken from {@code fromHand}, which is shrunk by that amount
     */
    public int deposit(ItemStack fromHand, @Nullable ServerPlayer placer) {
        if (fromHand.isEmpty() || !StorageStackBE.isValidStorageItem(fromHand)) {
            return 0;
        }

        int moved = 0;
        for (StorageStackBE sbe : blocks) {
            sbe.beginBatch();
        }
        try {
            moved = fillExisting(fromHand);
            while (!fromHand.isEmpty() && grow(placer)) {
                moved += fillExisting(fromHand);
            }
        } finally {
            for (StorageStackBE sbe : blocks) {
                sbe.endBatch();
            }
        }

        if (moved > 0) {
            markDirty();
        }
        return moved;
    }

    /**
     * Puts what it can of {@code stack} into the one slot at {@code flatSlot}, growing the pile when
     * that slot lies in the block above it.
     *
     * <p>A pile is genuinely a bag, so unlike the two structures its capability insertion is an
     * ordinary positional one: the slot named takes what a slot takes, and the settle scheduled
     * behind it packs the pile down from the base on the next tick. Filling from the base is
     * therefore still what a pile ends up doing, just a tick later than a player's own deposit does
     * it. Answering for the slot given rather than for the pile is what makes the handler's slot
     * range mean something: a caller that walks the range and sums what each slot accepts gets the
     * pile's real capacity, where a whole-pile answer repeated at every slot multiplied it.
     *
     * <p>A capability insertion carries no player, so growth is weighed against the fake player that
     * would place the block.
     *
     * @param simulate when true, nothing is stored and the pile does not grow
     * @return how many items were, or would be, taken from {@code stack}
     */
    public int insertAt(int flatSlot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !StorageStackBE.isValidStorageItem(stack)) {
            return 0;
        }
        if (flatSlot < 0 || flatSlot >= advertisedSlots()) {
            return 0;
        }

        if (simulate) {
            return Math.min(stack.getCount(), roomAt(flatSlot, stack));
        }

        int moved;
        for (StorageStackBE sbe : blocks) {
            sbe.beginBatch();
        }
        try {
            // Growth hands back a block with a batch already open, which the close below pairs with.
            if (flatSlot >= totalSlots() && !grow(null)) {
                return 0;
            }
            ItemStack offer = stack.copy();
            ItemStack rejected = handlerOf(flatSlot).insertItem(flatSlot % StorageStackBE.SLOTS, offer, false);
            moved = stack.getCount() - rejected.getCount();
        } finally {
            for (StorageStackBE sbe : blocks) {
                sbe.endBatch();
            }
        }

        if (moved > 0) {
            markDirty();
        }
        return moved;
    }

    /**
     * How much of {@code stack} the one slot at {@code flatSlot} has room for. A slot in the block
     * above the pile is empty, so it has room for a whole stack — but only where growth would
     * actually be permitted, which {@link #canGrow} answers without side effects, so a simulation
     * cannot promise a slot the commit would refuse.
     */
    private int roomAt(int flatSlot, ItemStack stack) {
        if (flatSlot >= totalSlots()) {
            return canGrow(null) ? stack.getMaxStackSize() : 0;
        }

        ItemStack inSlot = getSlot(flatSlot);
        if (inSlot.isEmpty()) {
            return stack.getMaxStackSize();
        }
        if (!ItemStack.isSameItemSameTags(inSlot, stack)) {
            return 0;
        }
        return Math.max(0, inSlot.getMaxStackSize() - inSlot.getCount());
    }

    /** Compatible partials first, then empty slots, both walked from the base upward. */
    private int fillExisting(ItemStack from) {
        int moved = 0;

        for (int i = 0; i < totalSlots() && !from.isEmpty(); i++) {
            ItemStack slot = getSlot(i);
            if (slot.isEmpty() || !ItemStack.isSameItemSameTags(slot, from)) {
                continue;
            }
            int room = slot.getMaxStackSize() - slot.getCount();
            if (room > 0) {
                moved += fillSlot(i, from, Math.min(from.getCount(), room));
            }
        }

        for (int i = 0; i < totalSlots() && !from.isEmpty(); i++) {
            if (getSlot(i).isEmpty()) {
                moved += fillSlot(i, from, Math.min(from.getCount(), from.getMaxStackSize()));
            }
        }

        return moved;
    }

    /** Moves up to {@code amount} from {@code from} into one slot, shrinking {@code from} by what it took. */
    private int fillSlot(int flatSlot, ItemStack from, int amount) {
        ItemStack offer = from.copy();
        offer.setCount(amount);
        ItemStack rejected = handlerOf(flatSlot).insertItem(flatSlot % StorageStackBE.SLOTS, offer, false);
        int used = amount - rejected.getCount();
        from.shrink(used);
        return used;
    }

    /**
     * Whether growth is permitted and the space above the pile could take a block: height,
     * enablement, build height, replaceability, and everything protection can answer without side
     * effects. Planning and committing share this predicate so that a simulated deposit cannot
     * promise a block the deposit itself would refuse.
     *
     * <p>Spawn protection exempts operators, so the answer depends on who is growing the pile: a
     * player's own deposit is weighed against that player, and automation against the level's fake
     * player, which is never exempt.
     */
    private boolean canGrow(@Nullable ServerPlayer placer) {
        if (blocks.size() >= maxHeight() || !ServerConfig.ENABLE_STORAGE_STACK_BLOCK.get()
                || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        BlockPos above = topPos().above();
        if (level.isOutsideBuildHeight(above) || !level.getBlockState(above).canBeReplaced()) {
            return false;
        }
        return !Protection.isProtected(editor(serverLevel, placer), above);
    }

    /** The player a growth is attributed to: the depositing player, or the fake player. */
    private static ServerPlayer editor(ServerLevel serverLevel, @Nullable ServerPlayer placer) {
        return placer != null ? placer : FakePlayerFactory.getMinecraft(serverLevel);
    }

    /** Adds one block on top, inheriting the pile's mode and rotation. */
    private boolean grow(@Nullable ServerPlayer placer) {
        if (!canGrow(placer) || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        BlockPos above = topPos().above();
        ServerPlayer editor = editor(serverLevel, placer);
        BlockState newStack = ModRegistry.STORAGE_STACK_BLOCK.get().defaultBlockState();
        if (!Protection.placeChecked(editor, serverLevel, above, newStack, Direction.DOWN)) {
            return false;
        }
        StorageStackBE grown = (StorageStackBE) level.getBlockEntity(above);

        grown.adoptPileState(isPermanent(), blocks.get(0).getRotation());
        grown.beginBatch();
        blocks.add(grown);
        return true;
    }

    private BlockPos topPos() {
        return blocks.get(blocks.size() - 1).getBlockPos();
    }

    // Settling

    /**
     * Schedules the settle for the next tick, on the base so that every edit anywhere in the pile
     * coalesces into one pass. Ticking there also means the pile settles once for a burst of
     * automation traffic rather than once per item moved.
     */
    public void markDirty() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Block block = ModRegistry.STORAGE_STACK_BLOCK.get();
        if (!serverLevel.getBlockTicks().hasScheduledTick(base, block)) {
            serverLevel.scheduleTick(base, block, 1);
        }
    }

    /**
     * Consolidates and sorts the whole pile, writes it back from the base upward, propagates the
     * base's mode, and removes the empty blocks that packing leaves at the top.
     *
     * <p>Slots that already hold what they should are left alone, so an edit that disturbs a few
     * stacks resyncs a few blocks rather than the whole column.
     */
    public void settle() {
        List<ItemStack> contents = new ArrayList<>();
        for (int i = 0; i < totalSlots(); i++) {
            ItemStack stack = getSlot(i);
            if (!stack.isEmpty()) {
                contents.add(stack.copy());
            }
        }

        List<ItemStack> packed = consolidate(contents);
        boolean permanent = isPermanent();

        for (StorageStackBE sbe : blocks) {
            sbe.beginBatch();
        }
        try {
            int index = 0;
            for (StorageStackBE sbe : blocks) {
                for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
                    ItemStack desired = index < packed.size() ? packed.get(index) : ItemStack.EMPTY;
                    index++;
                    sbe.setSlotIfChanged(slot, desired);
                }
                sbe.inheritPermanent(permanent);
            }
        } finally {
            for (StorageStackBE sbe : blocks) {
                sbe.endBatch();
            }
        }

        trimEmptyTop();
        publishComparatorSignal();
    }

    /**
     * Removes empty blocks from the top of the pile, stopping at the first block that still holds
     * something or is permanent. Packing has already pushed every item as far down as it goes, so
     * the empty blocks are exactly the run at the top: nothing below can be stranded by this.
     */
    private void trimEmptyTop() {
        int keep = blocks.size();
        while (keep > 0) {
            StorageStackBE sbe = blocks.get(keep - 1);
            if (!sbe.isEmpty() || sbe.isPermanent()) {
                break;
            }
            keep--;
        }

        for (int i = blocks.size() - 1; i >= keep; i--) {
            level.setBlock(blocks.get(i).getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        blocks.subList(keep, blocks.size()).clear();
    }

    /**
     * Totals the input by exact item identity, then re-cuts each total into whole stacks plus at
     * most one remainder. Grouping by identity rather than by adjacency means two compatible
     * stacks always merge, wherever they sat in the pile.
     */
    private static List<ItemStack> consolidate(List<ItemStack> stacks) {
        Map<StackKey, ItemStack> models = new LinkedHashMap<>();
        Map<StackKey, Integer> totals = new LinkedHashMap<>();

        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;

            StackKey key = new StackKey(stack.getItem(), stack.getDamageValue(), stack.getTag());
            models.putIfAbsent(key, stack);
            totals.merge(key, stack.getCount(), Integer::sum);
        }

        List<ItemStack> out = new ArrayList<>();

        for (Map.Entry<StackKey, Integer> entry : totals.entrySet()) {
            ItemStack model = models.get(entry.getKey());
            int max = Math.max(1, model.getMaxStackSize());
            int remaining = entry.getValue();

            while (remaining > 0) {
                ItemStack piece = model.copy();
                piece.setCount(Math.min(remaining, max));
                out.add(piece);
                remaining -= piece.getCount();
            }
        }

        out.sort(StackSort.COMPARATOR);
        return out;
    }

    /** Exact stack identity: two stacks merge if and only if their keys are equal. */
    private record StackKey(Item item, int damage, @Nullable CompoundTag tag) {
    }
}
