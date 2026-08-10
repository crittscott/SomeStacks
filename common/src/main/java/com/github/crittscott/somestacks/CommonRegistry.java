package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

/**
 * Loader-neutral access to the registered blocks and block entity types. The loader's own
 * registration glue (Forge's {@code ModRegistry}, or its Fabric equivalent) assigns these once
 * registration is set up; common code never registers anything itself, only reads these back.
 */
public final class CommonRegistry {
    private CommonRegistry() {
    }

    public static Supplier<Block> STORAGE_STACK_BLOCK;
    public static Supplier<Block> SINGLES_STACK_BLOCK;
    public static Supplier<Block> BAR_STACK_BLOCK;

    public static Supplier<BlockEntityType<StorageStackBE>> STORAGE_STACK_BE;
    public static Supplier<BlockEntityType<SinglesStackBE>> SINGLES_STACK_BE;
    public static Supplier<BlockEntityType<BarStackBE>> BAR_STACK_BE;
}
