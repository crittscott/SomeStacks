package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The three stack types, as a value that can be looked up in the registry or the server config,
 * and what differs between them before a block of the type exists: admission, enablement, sounds,
 * and the shape of a new block's first deposit.
 */
public enum BlockType {
    STORAGE_STACK,
    SINGLES_STACK,
    BAR_STACK;

    /** The type whose block this is, or null when it names none of the three. */
    @Nullable
    public static BlockType of(Block block) {
        for (BlockType type : values()) {
            if (type.getBlock() == block) {
                return type;
            }
        }
        return null;
    }

    /**
     * Whether this type accepts {@code stack} as a new deposit, the rule its block entities apply
     * as {@code admits}.
     */
    public boolean admits(ItemStack stack) {
        return switch (this) {
            case STORAGE_STACK -> StorageStackBE.isValidStorageItem(stack);
            case SINGLES_STACK -> SinglesStackBE.isValidSinglesItem(stack);
            case BAR_STACK -> BarStackBE.isValidBarItem(stack);
        };
    }

    public SoundEvent depositSound() {
        return switch (this) {
            case STORAGE_STACK -> CommonRegistry.Sound.STORAGE_DEPOSIT.get();
            case SINGLES_STACK -> CommonRegistry.Sound.SINGLES_DEPOSIT.get();
            case BAR_STACK -> CommonRegistry.Sound.BAR_DEPOSIT.get();
        };
    }

    public SoundEvent extractSound() {
        return switch (this) {
            case STORAGE_STACK -> CommonRegistry.Sound.STORAGE_EXTRACT.get();
            case SINGLES_STACK -> CommonRegistry.Sound.SINGLES_EXTRACT.get();
            case BAR_STACK -> CommonRegistry.Sound.BAR_EXTRACT.get();
        };
    }

    /**
     * The collision shape a new block of this type in {@code state} at {@code pos} would have once
     * it holds the first deposit along {@code ray}, or null when that deposit would find no
     * supported cell. Changes nothing. A new block starts empty and takes its real shape only with
     * its first deposit, so placement tests obstruction against this shape. New blocks are
     * unrotated, so Singles cells are traced in their storage frame.
     */
    @Nullable
    public VoxelShape firstDepositShape(
            Level level, BlockPos pos, BlockState state, ViewRay ray) {
        return switch (this) {
            case STORAGE_STACK -> state.getCollisionShape(level, pos, CollisionContext.empty());
            case SINGLES_STACK -> {
                int index =
                        ray.lastEmptyBeforeOccupied(
                                SinglesStackBE.SLOTS,
                                null,
                                slot -> SinglesCubeIdx.GRID.worldBox(slot, 0, pos));
                yield index >= 0
                                && SinglesCubeIdx.freshBlockSupports(
                                        index, SinglesStackBE.supportSeamBeneath(level, pos))
                        ? SinglesCubeIdx.shapeFor(index, 0)
                        : null;
            }
            case BAR_STACK -> {
                int index =
                        ray.lastEmptyBeforeOccupied(
                                BarStackBE.SLOTS,
                                null,
                                slot ->
                                        BarCubeIdx.localBox(slot)
                                                .move(pos.getX(), pos.getY(), pos.getZ()));
                yield index >= 0
                                && BarCubeIdx.freshBlockSupports(
                                        index, BarStackBE.supportSeamBeneath(level, pos))
                        ? BarCubeIdx.shapeFor(index)
                        : null;
            }
        };
    }

    public Block getBlock() {
        return switch (this) {
            case STORAGE_STACK -> CommonRegistry.storageStackBlock();
            case SINGLES_STACK -> CommonRegistry.singlesStackBlock();
            case BAR_STACK -> CommonRegistry.barStackBlock();
        };
    }

    /**
     * Whether the server policy enables this type. Server-side only; the client's copy is {@code
     * StackState}.
     */
    public boolean isEnabled() {
        ServerConfig.Settings policy = ServerConfig.settings();
        return switch (this) {
            case STORAGE_STACK -> policy.enableStorageStackBlock();
            case SINGLES_STACK -> policy.enableSinglesStackBlock();
            case BAR_STACK -> policy.enableBarStackBlock();
        };
    }
}
