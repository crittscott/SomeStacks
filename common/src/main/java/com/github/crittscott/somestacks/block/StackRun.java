package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.SlotAccess;
import com.github.crittscott.somestacks.util.StackPlacement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

/**
 * Mechanics shared by every maximal vertical run of one stack type, including the one gate on
 * growing a run one block taller. Concrete runs retain their distinct insertion, extraction,
 * settlement, and collapse rules, and what a grown block must hold clear.
 */
public abstract class StackRun<BE extends StackBlockEntity> implements StackRunItemAccess {
    protected final ServerLevel level;
    protected final List<BE> blocks;

    private final Block block;
    private final int slotsPerBlock;
    private final BlockType type;

    /**
     * Creates a server-only run over a bottom-to-top list of same-type block entities, empty when
     * the resolving block's entity is gone.
     */
    protected StackRun(ServerLevel level, List<BE> blocks, int slotsPerBlock, BlockType type) {
        this.level = level;
        this.blocks = blocks;
        this.slotsPerBlock = slotsPerBlock;
        this.type = type;
        this.block = type.getBlock();
    }

    /** Walks the maximal run containing {@code pos}, from its bottom block upward. */
    protected static <BE extends StackBlockEntity> List<BE> resolveBlocks(
            ServerLevel level, BlockPos pos, Class<BE> blockEntityClass) {
        BlockPos base = pos;
        while (blockEntityClass.isInstance(level.getBlockEntity(base.below()))) {
            base = base.below();
        }

        List<BE> blocks = new ArrayList<>();
        BlockPos current = base;
        while (blockEntityClass.isInstance(level.getBlockEntity(current))) {
            blocks.add(blockEntityClass.cast(level.getBlockEntity(current)));
            current = current.above();
        }
        return blocks;
    }

    /** Invalidates every cached run a structural change at {@code pos} could have altered. */
    protected static <BE extends StackBlockEntity> void invalidateAround(
            Level level, BlockPos pos, Class<BE> blockEntityClass) {
        invalidateRun(level, pos, Direction.UP, blockEntityClass);
        invalidateRun(level, pos.above(), Direction.UP, blockEntityClass);
        invalidateRun(level, pos.below(), Direction.DOWN, blockEntityClass);
    }

    private static <BE extends StackBlockEntity> void invalidateRun(
            Level level, BlockPos from, Direction direction, Class<BE> blockEntityClass) {
        BlockPos current = from;
        while (blockEntityClass.isInstance(level.getBlockEntity(current))) {
            blockEntityClass.cast(level.getBlockEntity(current)).invalidateRunCache();
            current = current.relative(direction);
        }
    }

    /**
     * Resolves and schedules a run's deferred pass; a null resolution, including client use, does
     * nothing.
     */
    protected static <R extends StackRunItemAccess> void markDirtyAt(
            Level level, BlockPos pos, BiFunction<Level, BlockPos, R> resolver) {
        R run = resolver.apply(level, pos);
        if (run != null) {
            run.markDirty();
        }
    }

    /**
     * Publishes comparator changes for runs at and beside a structural edit after cache
     * invalidation. Null resolutions are ignored; this does not publish inventories or settle
     * Storage.
     */
    protected static <R extends StackRun<?>> void publishAround(
            Level level, BlockPos pos, BiFunction<Level, BlockPos, R> resolver) {
        publishAt(level, pos, resolver);
        publishAt(level, pos.below(), resolver);
        publishAt(level, pos.above(), resolver);
    }

    private static <R extends StackRun<?>> void publishAt(
            Level level, BlockPos pos, BiFunction<Level, BlockPos, R> resolver) {
        R run = resolver.apply(level, pos);
        if (run != null) {
            run.publishComparatorSignal();
        }
    }

    /**
     * Whether a block of {@code type} may be created at {@code pos}: the contiguous column it would
     * form, counting the runs both below and above it, must fit the configured maximum. Testing the
     * whole resulting column rather than one neighboring run is what stops a block placed into the
     * gap between two runs from joining them into an over-tall one.
     */
    public static boolean columnHasRoomFor(Level level, BlockPos pos, BlockType type) {
        Block block = type.getBlock();
        return 1
                        + runLength(level, pos, Direction.DOWN, block)
                        + runLength(level, pos, Direction.UP, block)
                <= ServerConfig.settings().maxPileHeight();
    }

    private static int runLength(Level level, BlockPos from, Direction direction, Block block) {
        int length = 0;
        BlockPos current = from.relative(direction);
        while (level.getBlockState(current).is(block)) {
            length++;
            current = current.relative(direction);
        }
        return length;
    }

    /** Bottom block position of this resolved run. */
    public final BlockPos basePos() {
        return blocks.get(0).getBlockPos();
    }

    /** Top existing block position, excluding advertised headroom. */
    protected final BlockPos topPos() {
        return blocks.get(blocks.size() - 1).getBlockPos();
    }

    /** Number of existing blocks in this resolved run. */
    public final int height() {
        return blocks.size();
    }

    /** Existing slot count; valid stored flat slots satisfy {@code 0 <= slot < totalSlots()}. */
    public final int totalSlots() {
        return blocks.size() * slotsPerBlock;
    }

    @Override
    public final int advertisedSlots() {
        int levels = isBelowMaximumHeight() ? blocks.size() + 1 : blocks.size();
        return slotsPerBlock * levels;
    }

    /**
     * Whether the run may grow one block taller as far as anything without side effects can tell:
     * the height limit, the type's enablement, and {@link WorldEdits#mayPlace} for the space above,
     * checked against {@code actor} or, when null, the automation identity. Simulating and
     * committing share this predicate with {@link #placeOnTop}, so a simulated insertion cannot
     * promise a block the insertion itself would refuse.
     */
    protected final boolean canGrowSpace(@Nullable ServerPlayer actor) {
        return mayGrowTaller()
                && WorldEdits.mayPlace(actor, level, topPos().above(), Direction.DOWN);
    }

    /**
     * Places a new block of this run's type on top through {@link WorldEdits#placeChecked}, which
     * applies the placement gate and obstruction by {@code finalCollision}. The caller adds the new
     * block entity to the run.
     *
     * @param actor the player responsible for the new block, or null for automation
     */
    protected final boolean placeOnTop(@Nullable ServerPlayer actor, VoxelShape finalCollision) {
        BlockPos above = topPos().above();
        return mayGrowTaller()
                && WorldEdits.placeChecked(
                        actor,
                        level,
                        above,
                        StackPlacement.stateFor(block, level, above),
                        Direction.DOWN,
                        finalCollision);
    }

    private boolean isBelowMaximumHeight() {
        return blocks.size() < ServerConfig.settings().maxPileHeight();
    }

    private boolean mayGrowTaller() {
        return isBelowMaximumHeight() && type.isEnabled();
    }

    @Override
    public final ItemStack getSlot(int flatSlot) {
        if (flatSlot < 0 || flatSlot >= totalSlots()) {
            return ItemStack.EMPTY;
        }
        return handlerOf(flatSlot).getStackInSlot(localSlot(flatSlot));
    }

    /**
     * Owning block for a validated stored flat slot; headroom and invalid indices are not accepted.
     */
    protected final BE blockOf(int flatSlot) {
        return blocks.get(flatSlot / slotsPerBlock);
    }

    /** Mutable local storage for a validated stored flat slot. */
    protected final SlotAccess handlerOf(int flatSlot) {
        return blockOf(flatSlot).getItems();
    }

    /** Local index for a validated stored flat slot, with blocks ordered from the base upward. */
    protected final int localSlot(int flatSlot) {
        return flatSlot % slotsPerBlock;
    }

    /** Whole-run fill computed from each block's maintained local contribution. */
    public final double fillLevel() {
        double sum = 0.0;
        for (BE blockEntity : blocks) {
            sum += blockEntity.comparatorContribution();
        }
        return blocks.isEmpty() ? 0.0 : sum / totalSlots();
    }

    /**
     * Vanilla-style run-wide comparator strength: zero when empty, otherwise floor(fill * 14) + 1.
     */
    public final int comparatorSignal() {
        double fill = fillLevel();
        return fill > 0.0 ? Mth.floor(fill * 14.0) + 1 : 0;
    }

    /**
     * Notifies every block's comparator neighbors only when the base's last published signal
     * changes.
     */
    final void publishComparatorSignal() {
        if (blocks.isEmpty()) {
            return;
        }
        int signal = comparatorSignal();
        if (!blocks.get(0).exchangePublishedSignal(signal)) {
            return;
        }
        for (BE blockEntity : blocks) {
            level.updateNeighbourForOutputSignal(blockEntity.getBlockPos(), block);
        }
    }

    /**
     * Coalesces server changes into one next-tick pass on the base; no immediate publication
     * occurs.
     */
    @Override
    public final void markDirty() {
        if (blocks.isEmpty()) {
            return;
        }
        BlockPos bottom = basePos();
        if (!level.getBlockTicks().hasScheduledTick(bottom, block)) {
            level.scheduleTick(bottom, block, 1);
        }
    }

    /** Publishes changed local contents and derived light, returning whether anything changed. */
    protected final boolean publishPendingBlocks() {
        boolean published = false;
        for (BE blockEntity : blocks) {
            published |= blockEntity.publishIfPending(level);
        }
        return published;
    }

    /** Publishes deferred local changes and then the run-wide comparator signal. */
    public final void publishPending() {
        if (publishPendingBlocks()) {
            publishComparatorSignal();
        }
    }
}
