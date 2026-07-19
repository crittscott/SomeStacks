package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generates walls of Storage Stacks filled with every item of one or more namespaces,
 * for reviewing render settings in the world. One column of stacks per mod, rows
 * running north, over a uniform floor.
 */
public final class TestWallGenerator {
    /** Columns between the rows of adjacent mods. */
    private static final int MOD_SPACING = 2;

    public static final int ITEMS_PER_STACK = 9;

    private TestWallGenerator() {}

    /**
     * Item registry contents grouped by namespace. The registry is frozen before any
     * command or suggestion request can arrive, so one pass serves every later query;
     * without this cache, every suggestion keystroke rescans the full registry on the
     * server thread.
     */
    private static Map<String, List<Item>> itemsByNamespace;

    private static Map<String, List<Item>> itemsByNamespace() {
        if (itemsByNamespace == null) {
            Map<String, List<Item>> map = new TreeMap<>();
            ForgeRegistries.ITEMS.getEntries().forEach(entry -> map
                    .computeIfAbsent(entry.getKey().location().getNamespace(), ns -> new ArrayList<>())
                    .add(entry.getValue()));
            itemsByNamespace = map;
        }
        return itemsByNamespace;
    }

    public static List<String> getModIdsWithItems() {
        return List.copyOf(itemsByNamespace().keySet());
    }

    public static List<Item> collectModItems(String modId) {
        return itemsByNamespace().getOrDefault(modId, List.of());
    }

    /** Rows a mod's items occupy, one stack per row running north. */
    public static int rowsFor(int itemCount) {
        return (itemCount + ITEMS_PER_STACK - 1) / ITEMS_PER_STACK;
    }

    public record Result(int totalStacks, int expectedStacks, int totalItems) {}

    public static Result generate(ServerPlayer player, List<String> modIds) {
        Level level = player.level();
        BlockPos basePos = player.blockPosition().east();

        Map<String, List<Item>> itemsByMod = new LinkedHashMap<>();
        int maxRows = 0;
        for (String modId : modIds) {
            List<Item> modItems = collectModItems(modId);
            itemsByMod.put(modId, modItems);
            maxRows = Math.max(maxRows, rowsFor(modItems.size()));
        }

        placeFloor(level, basePos, modIds.size(), maxRows);

        int totalStacks = 0;
        int expectedStacks = 0;
        int totalItems = 0;
        int modIndex = 0;

        for (List<Item> modItems : itemsByMod.values()) {
            BlockPos modStartPos = basePos.offset(modIndex * MOD_SPACING, 0, 0);
            totalStacks += createStorageStacks(level, modStartPos, modItems);
            expectedStacks += rowsFor(modItems.size());
            totalItems += modItems.size();
            modIndex++;
        }

        return new Result(totalStacks, expectedStacks, totalItems);
    }

    public static int createStorageStacks(Level level, BlockPos startPos, List<Item> items) {
        int stacksCreated = 0;
        BlockPos currentPos = startPos;

        for (int i = 0; i < items.size(); i += ITEMS_PER_STACK) {
            List<Item> batch = items.subList(i, Math.min(i + ITEMS_PER_STACK, items.size()));

            if (fillStack(level, currentPos, batch)) {
                stacksCreated++;
            }

            currentPos = currentPos.north();
        }

        return stacksCreated;
    }

    private static boolean fillStack(Level level, BlockPos pos, List<Item> batch) {
        if (level.isOutsideBuildHeight(pos)) {
            SomeStacks.LOGGER.warn("Test stack skipped at {}: outside build height", pos);
            return false;
        }

        BlockState stackState = ModRegistry.STORAGE_STACK_BLOCK.get().defaultBlockState();
        if (!level.setBlock(pos, stackState, Block.UPDATE_ALL)) {
            SomeStacks.LOGGER.warn("Test stack placement rejected at {}, block there is {}",
                    pos, level.getBlockState(pos));
            return false;
        }

        var blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof StorageStackBE sbe)) {
            SomeStacks.LOGGER.warn("Test stack at {} has no StorageStackBE after placement (found {})",
                    pos, blockEntity);
            return false;
        }

        for (Item item : batch) {
            sbe.deposit(new ItemStack(item, 1));
        }
        return true;
    }

    /**
     * Lays a smooth sandstone surface one level below the stacks, extending one block
     * past them on every side so the whole wall can be walked around and viewed against
     * a uniform background.
     */
    private static void placeFloor(Level level, BlockPos basePos, int modCount, int maxRows) {
        BlockState floor = Blocks.SMOOTH_SANDSTONE.defaultBlockState();

        int minX = basePos.getX() - 1;
        int maxX = basePos.getX() + (modCount - 1) * MOD_SPACING + 1;
        // Rows advance north, which is decreasing Z.
        int minZ = basePos.getZ() - maxRows;
        int maxZ = basePos.getZ() + 1;
        int y = basePos.getY() - 1;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.isOutsideBuildHeight(pos)) {
                    level.setBlock(pos, floor, Block.UPDATE_ALL);
                }
            }
        }
    }
}
