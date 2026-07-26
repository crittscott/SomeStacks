package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.ItemOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Arrays;

/**
 * One Bar Stack: 64 bars laid in eight alternating layers of eight.
 *
 * <p>A slot index is a position, not a place in a bag, and every bar must rest on the layer beneath
 * it or on the seam with the Bar Stack below. A vertical run of these is a {@link BarColumn}, which
 * is what automation addresses; this class owns one block's slots, its shape, and the player-facing
 * cascade that drops whatever an extracted bar was holding up.
 */
public class BarStackBE extends BlockEntity {
    /** Positions in one block. The column's flat range is this times its height. */
    public static final int SLOTS = 64;

    private VoxelShape cachedShape = null;
    private boolean suppressSync = false;
    private boolean batchTouched = false;

    /**
     * Set when a cascade is removing this block, so {@link BarStackBlock#onRemove} knows the
     * column above is already being walked and does not start a second collapse of it.
     */
    private boolean removedByCascade = false;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            cachedShape = null;
            if (suppressSync) {
                batchTouched = true;
            } else {
                finalizeAfterBatch();
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return isValidBarItem(stack);
        }
    };
    private LazyOptional<IItemHandler> itemsCap = LazyOptional.of(() -> new BarColumnHandler(this));

    public BarStackBE(BlockPos pos, BlockState state) {
        super(ModRegistry.BAR_STACK_BE.get(), pos, state);
    }

    /**
     * Whether a Bar Stack accepts this item, the single decision point every deposit, column
     * insertion and capability path consults. Ingot-ness is the server's {@code ingot_tags} list
     * resolved against the loaded item tags, so an admin widens or narrows it without a data pack;
     * because the Singles rule is this rule's complement, widening it narrows Singles by as much.
     */
    public static boolean isValidBarItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (ItemOps.isItemFromDisabledMod(stack)) {
            return false;
        }
        return ServerConfig.isIngotItem(stack.getItem());
    }

    /** The column this block belongs to, or null on the client and for a block being removed. */
    @Nullable
    public BarColumn column() {
        return BarColumn.at(level, getBlockPos());
    }

    public VoxelShape computeShape() {
        VoxelShape shape = Shapes.empty();

        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) {
                int[] xyz = BarCubeIdx.xyzFromIndex(i);

                double minX = BarCubeIdx.startPixelX(xyz[0], xyz[1]) / 16.0;
                double minY = BarCubeIdx.startPixelY(xyz[1]) / 16.0;
                double minZ = BarCubeIdx.startPixelZ(xyz[2], xyz[1]) / 16.0;
                double maxX = minX + BarCubeIdx.barWidth(xyz[1]) / 16.0;
                double maxY = minY + BarCubeIdx.barHeight(xyz[1]) / 16.0;
                double maxZ = minZ + BarCubeIdx.barDepth(xyz[1]) / 16.0;

                VoxelShape barShape = Shapes.box(minX, minY, minZ, maxX, maxY, maxZ);
                shape = Shapes.or(shape, barShape);
            }
        }

        return shape;
    }

    public VoxelShape getCachedShape() {
        if (cachedShape == null) {
            cachedShape = computeShape();
        }
        return cachedShape;
    }

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
        if (index < 0 || index >= SLOTS) {
            return ItemStack.EMPTY;
        }

        boolean[] topBefore = BarCubeIdx.topLayerOccupancy(items);

        ItemStack extracted;
        beginBatch();
        try {
            extracted = items.extractItem(index, 1, false);

            if (!extracted.isEmpty() && level != null && !level.isClientSide) {
                cascadeFrom(level, this, seamBeneath(), topBefore);
            }
        } finally {
            endBatch();
        }

        return extracted;
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
     * Settles {@code start} against the seam beneath it, then carries the result up the column so a
     * vertical run of Bar Stacks behaves as one stack. Support crosses the seam per footprint, so a
     * block above loses only the bars whose support went away rather than collapsing wholesale. Only
     * the top layer can hold up the block above, so a block whose top layer survives intact ends the
     * walk. A block emptied along the way removes itself and passes on its now-empty top layer,
     * which is why an emptied or vanished block needs no case of its own: nothing overlaps an empty
     * seam. {@code topBefore} is {@code start}'s top layer as it stood before the edit that prompted
     * the settle, which the edit itself may already have changed.
     */
    static void cascadeFrom(Level columnLevel, BarStackBE start,
                            @Nullable boolean[] seamBelow, boolean[] topBefore) {
        BarStackBE be = start;
        boolean[] seam = seamBelow;
        boolean[] before = topBefore;

        while (true) {
            be.beginBatch();
            try {
                be.dropUnsupported(columnLevel, seam);
            } finally {
                be.endBatch();
            }

            boolean[] after = BarCubeIdx.topLayerOccupancy(be.items);
            BlockPos abovePos = be.getBlockPos().above();

            if (be.isEmpty()) {
                be.removedByCascade = true;
                columnLevel.setBlock(be.getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
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
     * A Bar Stack has gone from beneath {@code removed}, so the column above it has lost the seam it
     * stood on. Carries an empty seam upward, which is the same thing a cascade hands on when it
     * empties a block: nothing overlaps it, so the column comes down.
     */
    static void collapseAbove(Level level, BlockPos removed) {
        if (level.getBlockEntity(removed.above()) instanceof BarStackBE above) {
            cascadeFrom(level, above, BarCubeIdx.emptySeam(), BarCubeIdx.topLayerOccupancy(above.items));
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
    private void dropUnsupported(Level columnLevel, @Nullable boolean[] seamBelow) {
        boolean[] occupancy = BarCubeIdx.occupancyOf(items);

        for (int i = 0; i < SLOTS; i++) {
            if (!occupancy[i] || BarCubeIdx.isGroundedIn(occupancy, i, seamBelow)) {
                continue;
            }

            ItemStack removed = items.extractItem(i, 1, false);
            if (!removed.isEmpty()) {
                occupancy[i] = false;
                Containers.dropItemStack(columnLevel,
                        getBlockPos().getX() + 0.5,
                        getBlockPos().getY() + 0.5,
                        getBlockPos().getZ() + 0.5,
                        removed);
            }
        }
    }

    public boolean isEmpty() {
        return ItemOps.isHandlerEmpty(items);
    }

    public void syncToClients() {
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_ALL);
        }
    }

    /**
     * Opens a run of edits that should publish as one. Per-slot sync is held back and the block
     * remembers whether anything actually changed, so {@link #endBatch()} can settle only the
     * blocks a column-wide pass really touched.
     *
     * <p>Unlike Storage and Singles this does not clear {@code batchTouched}, and must not: batches
     * nest here. {@link #extractAt} opens one, takes a bar — which sets the flag — and then calls
     * {@link #cascadeFrom}, which opens another on this same block. Clearing on the inner open would
     * forget the extraction, and a removal that left nothing unsupported would close its batch with
     * nothing to publish, leaving the taken bar drawn until something else refreshed the block.
     */
    void beginBatch() {
        suppressSync = true;
    }

    void endBatch() {
        suppressSync = false;
        if (batchTouched) {
            batchTouched = false;
            finalizeAfterBatch();
        }
    }

    /**
     * Settles the derived state a content edit leaves behind: pushes contents to clients and
     * recomputes the emitted light level. Callers mark the block changed; this reproduces the rest
     * of the per-edit path as one pass, so a batch that suppresses per-slot sync can finalize once
     * when it finishes.
     */
    private void finalizeAfterBatch() {
        if (level == null || level.isClientSide) {
            return;
        }
        syncToClients();

        int newLight = ItemOps.calculateLightLevelFromItems(items);
        BlockState state = getBlockState();
        if (state.getValue(BarStackBlock.LIGHT_LEVEL) != newLight) {
            level.setBlock(getBlockPos(), state.setValue(BarStackBlock.LIGHT_LEVEL, newLight), Block.UPDATE_ALL);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) {
            items.deserializeNBT(tag.getCompound("Items"));
        }
        cachedShape = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemsCap.cast();
        return super.getCapability(cap, side);
    }

    /** This block's 64 slots, for callers that already hold the block entity. */
    public IItemHandler getItems() {
        return items;
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemsCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemsCap = LazyOptional.of(() -> new BarColumnHandler(this));
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        handleUpdateTag(pkt.getTag());
    }
}
