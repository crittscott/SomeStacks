package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.ItemOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * One Bar Stack: 64 bars laid in eight alternating layers of eight.
 *
 * <p>A slot index is a position, not a place in a bag, and every bar must rest on the layer beneath
 * it or on the seam with the Bar Stack below. A vertical run of these is a {@link BarColumn}, which
 * is what automation addresses; this class owns one block's slots, its shape, and the player-facing
 * cascade that drops whatever an extracted bar was holding up.
 *
 * <p>Capability exposure is loader-specific and lives outside this class; {@link #getItems()} is
 * what a loader-specific capability view adapts.
 */
public class BarStackBE extends StackBlockEntity {
    /** Positions in one block. The column's flat range is this times its height. */
    public static final int SLOTS = BarCubeIdx.CELLS;

    private VoxelShape cachedShape = null;

    /**
     * Set when a cascade is removing this block, so {@link BarStackBlock#onRemove} knows the
     * column above is already being walked and does not start a second collapse of it.
     */
    private boolean removedByCascade = false;

    public BarStackBE(BlockPos pos, BlockState state) {
        super(CommonRegistry.BAR_STACK_BE.get(), pos, state, SLOTS, BarStackBlock.LIGHT_LEVEL);
    }

    /** The item tag defining what a Bar Stack holds; data packs add to it. */
    public static final TagKey<Item> INGOTS =
            TagKey.create(Registries.ITEM, new ResourceLocation(SomeStacksCommon.MODID, "ingots"));

    /**
     * Whether a Bar Stack accepts this item, the single decision point every deposit, column
     * insertion and capability path consults. Ingot-ness is membership in {@link #INGOTS}; because
     * the Singles rule is this rule's complement, widening the tag narrows Singles by as much.
     */
    public static boolean isValidBarItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (ItemOps.isItemFromDisabledMod(stack)) {
            return false;
        }
        return stack.is(INGOTS);
    }

    /**
     * Returns the server column containing this block, or null on the client or when removed.
     * Delegates to {@link StackBlockEntity#itemRun()}, whose per-tick cache is invalidated by
     * structural edits.
     */
    @Nullable
    public BarColumn column() {
        return (BarColumn) itemRun();
    }

    /** Builds the union of the occupied bars' boxes. */
    public VoxelShape computeShape() {
        VoxelShape shape = Shapes.empty();

        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) {
                shape = Shapes.or(shape, BarCubeIdx.shapeFor(i));
            }
        }

        return shape;
    }

    /**
     * Returns the occupied-bar shape, rebuilding it only after contents invalidate the cache. Shape
     * queries occur frequently enough that they must not rebuild it unconditionally.
     */
    public VoxelShape getCachedShape() {
        if (cachedShape == null) {
            cachedShape = computeShape();
        }
        return cachedShape;
    }

    /**
     * Puts one bar from the given stack into the named position, if that position is empty, valid,
     * and overlapping a bar below. Refuses rather than redirecting the item elsewhere, so a caller
     * walking positions from the bottom fills the supported ones in order.
     *
     * @return whether the bar moved; the hand shrinks by one only then
     */
    public boolean depositAt(int index, ItemStack fromHand) {
        if (fromHand.isEmpty()) {
            return false;
        }

        if (index < 0 || index >= SLOTS) {
            return false;
        }

        if (!items.getStackInSlot(index).isEmpty()) {
            return false;
        }

        if (!isValidBarItem(fromHand)) {
            return false;
        }

        if (!BarCubeIdx.isGrounded(index, items, seamBeneath())) {
            return false;
        }

        ItemStack toInsert = fromHand.copy();
        toInsert.setCount(1);
        ItemStack remainder = items.insertItem(index, toInsert, false);

        if (remainder.isEmpty()) {
            fromHand.shrink(1);
            return true;
        }

        return false;
    }

    /**
     * The player's extraction: takes one bar and lets go of whatever it was holding up. Automation
     * takes a different path — see {@link BarColumn#extract}, which backfills instead.
     */
    public ItemStack extractAt(int index) {
        return extractAt(index, null);
    }

    /**
     * Player-facing extraction variant that preserves the actor through cascade cleanup.
     *
     * @param actor the player responsible for any block the cascade empties away, or null for
     *              automation
     */
    public ItemStack extractAt(int index, @Nullable ServerPlayer actor) {
        if (index < 0 || index >= SLOTS) {
            return ItemStack.EMPTY;
        }

        boolean[] topBefore = BarCubeIdx.topLayerOccupancy(items);
        BarDropBatch drops = new BarDropBatch();

        ItemStack extracted;
        beginBatch();
        try {
            extracted = items.extractItem(index, 1, false);

            if (!extracted.isEmpty() && level != null && !level.isClientSide) {
                cascadeFrom(level, this, seamBeneath(), topBefore, drops, actor);
            }
        } finally {
            endBatch();
        }

        if (level != null) {
            drops.spawn(level);
        }
        return extracted;
    }

    /**
     * Writes a bar the column already holds into an empty position, without the validity test an
     * insertion runs. The caller owns the bar and the position it goes to; see
     * {@link BarColumn#extract}, the one place a bar changes position in place, for why validity has
     * no say in it.
     */
    void relocateInto(int index, ItemStack bar) {
        items.setStackInSlot(index, bar);
    }

    /**
     * The support this block's bottom layer rests on: the top-layer occupancy of the Bar Stack
     * directly below, or null when this block stands on the world instead of on another Bar Stack.
     */
    @Nullable
    private boolean[] seamBeneath() {
        if (level != null && level.getBlockEntity(getBlockPos().below()) instanceof BarStackBE below) {
            return BarCubeIdx.topLayerOccupancy(below.items);
        }
        return null;
    }

    /**
     * Drops unsupported bars from {@code start} and dependent blocks above, collecting drops in
     * {@code drops}. Footprint support crosses seams; supported bars remain. Empty blocks are
     * removed only when edit authority permits, and a refused removal still leaves an empty seam.
     *
     * @param seamBelow occupancy immediately below start, or null when start is grounded
     * @param topBefore start's top-layer occupancy before the triggering edit
     * @param actor the player removals are attributed to, or null for automation
     */
    static void cascadeFrom(Level columnLevel, BarStackBE start,
                            @Nullable boolean[] seamBelow, boolean[] topBefore,
                            BarDropBatch drops, @Nullable ServerPlayer actor) {
        BarStackBE be = start;
        boolean[] seam = seamBelow;
        boolean[] before = topBefore;

        while (true) {
            be.beginBatch();
            try {
                be.dropUnsupported(seam, drops);
            } finally {
                be.endBatch();
            }

            boolean[] after = BarCubeIdx.topLayerOccupancy(be.items);
            BlockPos abovePos = be.getBlockPos().above();

            // A refused removal leaves an empty block standing, and the walk carries on regardless:
            // an empty seam holds nothing up either way, so the bars above still come down. Only
            // the block's going is protection's to decide.
            //
            // The flag is raised first because the removal runs onRemove, which reads it to know
            // the cascade is already walking its own way up, and lowered again if nothing went.
            if (be.isEmpty() && columnLevel instanceof ServerLevel serverLevel) {
                be.removedByCascade = true;
                boolean removed = actor == null
                        ? WorldEdits.removeChecked(serverLevel, be.getBlockPos())
                        : WorldEdits.removeChecked(actor, serverLevel, be.getBlockPos());
                if (!removed) {
                    be.removedByCascade = false;
                }
            }

            // An unchanged top layer cannot alter support for the next block.
            if (Arrays.equals(before, after)) {
                return;
            }

            if (!(columnLevel.getBlockEntity(abovePos) instanceof BarStackBE above)) {
                return;
            }

            be = above;
            seam = after;
            before = BarCubeIdx.topLayerOccupancy(above.items);
        }
    }

    /**
     * A Bar Stack has gone from beneath {@code removed}, so the column above it has lost the seam it
     * stood on. Carries an empty seam upward, which is the same thing a cascade hands on when it
     * empties a block: nothing overlaps it, so the column comes down.
     *
     * <p>Server only, as the extraction route into the cascade is. A client reaches this while
     * applying the server's own removal, and would go on to empty block entities and set blocks to
     * air on its own authority — deciding a collapse the server has already decided and is sending.
     * The re-entrancy flag a cascade sets is runtime state that is never synced, so a client could
     * not even tell it was inside one.
     */
    static void collapseAbove(Level level, BlockPos removed, BarDropBatch drops) {
        if (level.isClientSide) {
            return;
        }
        if (level.getBlockEntity(removed.above()) instanceof BarStackBE above) {
            cascadeFrom(level, above, BarCubeIdx.emptySeam(),
                    BarCubeIdx.topLayerOccupancy(above.items), drops, null);
        }
    }

    boolean wasRemovedByCascade() {
        return removedByCascade;
    }

    /**
     * Drops every bar this block leaves without support. Slot index is layer-major and a bar is
     * supported only by the layer directly beneath it, so one ascending pass settles the block: by
     * the time a layer is reached, the layer it rests on is final, whether that is the layer below
     * it here or the seam. Callers batch; this does not publish.
     */
    private void dropUnsupported(@Nullable boolean[] seamBelow, BarDropBatch drops) {
        boolean[] occupancy = BarCubeIdx.occupancyOf(items);

        for (int i = 0; i < SLOTS; i++) {
            if (!occupancy[i] || BarCubeIdx.isGroundedIn(occupancy, i, seamBelow)) {
                continue;
            }

            ItemStack removed = items.extractItem(i, 1, false);
            if (!removed.isEmpty()) {
                occupancy[i] = false;
                drops.add(getBlockPos(), removed);
            }
        }
    }

    @Override
    protected void loadStackData(CompoundTag tag) {
        cachedShape = null;
    }

    @Override
    protected boolean isStoredItemValid(ItemStack stack) {
        return isValidBarItem(stack);
    }

    @Override
    protected StackRunItemAccess resolveRun(ServerLevel serverLevel) {
        return BarColumn.resolve(serverLevel, getBlockPos());
    }

    @Override
    protected int localSlotLimit() {
        return 1;
    }

    @Override
    protected void onLocalContentsChanged(int slot) {
        cachedShape = null;
    }
}
