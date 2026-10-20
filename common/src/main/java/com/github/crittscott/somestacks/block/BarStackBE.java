package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.ItemOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Arrays;
import java.util.Objects;

import javax.annotation.Nullable;

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
     * Set when a cascade removes this block, so its content-removal hook does not start a second
     * walk of the column above.
     */
    private boolean removedByCascade = false;

    public BarStackBE(BlockPos pos, BlockState state) {
        super(CommonRegistry.barStackBe(), pos, state, SLOTS);
    }

    /** Collects this block and its unsupported column before the departing entity is detached. */
    @Override
    protected void dropContentsOnRemoval(ServerLevel serverLevel, BlockPos pos) {
        BarDropBatch drops = new BarDropBatch();
        drops.addAll(pos, items);
        if (!removedByCascade) {
            collapseAbove(serverLevel, pos, drops);
        }
        drops.spawn(serverLevel);
    }

    @Override
    protected boolean isStoredItemValid(ItemStack stack) {
        return isValidBarItem(stack);
    }

    @Override
    protected int localSlotLimit() {
        return 1;
    }

    @Override
    protected void onLocalContentsChanged(int slot) {
        cachedShape = null;
    }

    /** Reloadable data-pack classification of items that belong in Bar rather than Singles. */
    public static final TagKey<Item> INGOTS =
            TagKey.create(Registries.ITEM,
                    Identifier.fromNamespaceAndPath(SomeStacksCommon.MODID, "ingots"));

    /** Accepts nonempty tagged items whose namespaces are not disabled by server policy. */
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
     * The column this block belongs to, or null on the client and for a block being removed.
     *
     * <p>Resolved once a tick and held, because a machine reading this block's item handler resolves
     * the column once per slot it walks. See {@link StorageStackBE#pile()} for the full reasoning.
     */
    @Nullable
    public BarColumn column() {
        return (BarColumn) itemRun();
    }

    @Override
    protected StackRunItemAccess resolveRun(ServerLevel serverLevel) {
        return BarColumn.resolve(serverLevel, getBlockPos());
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
     * @param index the position to fill, in {@code [0, SLOTS)}
     * @return whether the bar moved; the hand shrinks by one only then
     */
    public boolean depositAt(int index, ItemStack fromHand) {
        if (fromHand.isEmpty()) {
            return false;
        }

        if (!isValidBarItem(fromHand)) {
            return false;
        }
        if (!canDepositAt(index)) {
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
     *
     * @param index the position to empty, in {@code [0, SLOTS)}
     * @param actor the player the cascade is attributed to, or null for automation
     */
    public ItemStack extractAt(int index, @Nullable ServerPlayer actor) {
        ServerLevel serverLevel = (ServerLevel) Objects.requireNonNull(level);
        boolean[] topBefore = BarCubeIdx.topLayerOccupancy(items);
        BarDropBatch drops = new BarDropBatch();

        ItemStack extracted;
        beginBatch();
        try {
            extracted = items.extractItem(index, 1, false);

            if (!extracted.isEmpty()) {
                cascadeFrom(serverLevel, this,
                        supportSeamBeneath(level, getBlockPos()), topBefore, drops, actor);
            }
        } finally {
            endBatch();
        }

        drops.spawn(serverLevel);
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
     * Whether {@code index} is a valid, empty, supported position for a new bar. Only a bottom-layer
     * position reads the seam of the Bar Stack below; higher layers rest on this block's own bars.
     */
    public boolean canDepositAt(int index) {
        return index >= 0
                && index < SLOTS
                && items.getStackInSlot(index).isEmpty()
                && BarCubeIdx.isGrounded(index, items, index < BarCubeIdx.LAYER_SIZE
                        ? supportSeamBeneath(level, getBlockPos()) : null);
    }

    /** Whether an empty Bar block at {@code pos} supports its first bar. */
    public static boolean supportsFreshDeposit(Level level, BlockPos pos, int index) {
        return index >= 0
                && index < SLOTS
                && BarCubeIdx.freshBlockSupports(index, supportSeamBeneath(level, pos));
    }

    /**
     * The seam supporting the bottom layer of a Bar Stack at {@code pos}: the top-layer occupancy of
     * the Bar Stack directly below, one entry per position in that top layer, reporting whether a
     * bar is there; {@link BarCubeIdx#seamSupports} matches footprints across it. Null means no Bar
     * Stack is below, so the bottom layer rests on the world and is always grounded.
     */
    @Nullable
    public static boolean[] supportSeamBeneath(@Nullable Level level, BlockPos pos) {
        if (level != null && level.getBlockEntity(pos.below()) instanceof BarStackBE below) {
            return BarCubeIdx.topLayerOccupancy(below.items);
        }
        return null;
    }

    /**
     * Settles {@code start} against the seam beneath it, then carries the result up the column so a
     * vertical run of Bar Stacks behaves as one stack. Support crosses the seam per footprint, so a
     * block above loses only the bars whose support went away rather than collapsing wholesale. Only
     * the top layer can hold up the block above, so a block whose top layer survives intact ends the
     * walk. A block emptied along the way removes itself and passes on its now-empty top layer,
     * which is why an emptied or vanished block needs no case of its own: nothing overlaps an empty
     * seam. {@code topBefore} is {@code start}'s top layer as it stood before the edit that prompted
     * the settle, which the edit itself may already have changed.
     */
    static void cascadeFrom(ServerLevel columnLevel, BarStackBE start,
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
            // The removal hook must know this walk owns the cascade. A veto leaves the block
            // attached, so clear the flag when removal is refused.
            if (be.isEmpty()) {
                be.removedByCascade = true;
                boolean removed = actor == null
                        ? WorldEdits.removeChecked(columnLevel, be.getBlockPos())
                        : WorldEdits.removeChecked(actor, columnLevel, be.getBlockPos());
                if (!removed) {
                    be.removedByCascade = false;
                }
            }

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
     * Propagates an empty support seam above a removed Bar block on the server. Unsupported bars
     * enter the supplied drop batch, and emptied blocks are removed through automation authority.
     */
    private static void collapseAbove(ServerLevel level, BlockPos removed, BarDropBatch drops) {
        if (level.getBlockEntity(removed.above()) instanceof BarStackBE above) {
            cascadeFrom(level, above, BarCubeIdx.emptySeam(),
                    BarCubeIdx.topLayerOccupancy(above.items), drops, null);
        }
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
    protected void loadStackData(ValueInput input) {
        cachedShape = null;
    }
}
