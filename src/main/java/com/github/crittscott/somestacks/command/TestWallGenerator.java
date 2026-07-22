package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.ServerConfig;
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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Generates walls of Storage Stacks filled with every item of one or more namespaces,
 * for reviewing render settings in the world. One column of stacks per mod, rows
 * running north, over a uniform floor.
 *
 * <p>A wall spanning every loaded mod runs to tens of thousands of placements, so a
 * request is queued and drained a bounded number of placements per server tick rather
 * than built inside the command call.
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

    /** Queued walls. Server thread only: appended by the command, drained by the tick handler. */
    private static final Deque<Job> jobs = new ArrayDeque<>();

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

    /** What a queued wall will consist of, known before any of it is placed. */
    public record Plan(int expectedStacks, int totalItems) {}

    /** What a finished wall actually consists of. */
    public record Result(int totalStacks, int expectedStacks, int totalItems) {}

    /**
     * Queues a wall for the given namespaces and returns what it will contain. The wall is
     * built over the following ticks; {@code onComplete} runs on the server thread once the
     * last stack is placed, and not at all if the player disconnects first.
     */
    public static Plan enqueue(ServerPlayer player, List<String> modIds, Consumer<Result> onComplete) {
        Level level = player.level();
        BlockPos basePos = player.blockPosition().east();

        Map<String, List<Item>> itemsByMod = new LinkedHashMap<>();
        int maxRows = 0;
        int totalItems = 0;
        for (String modId : modIds) {
            List<Item> modItems = collectModItems(modId);
            itemsByMod.put(modId, modItems);
            maxRows = Math.max(maxRows, rowsFor(modItems.size()));
            totalItems += modItems.size();
        }

        Deque<PendingStack> stacks = new ArrayDeque<>();
        int modIndex = 0;
        for (List<Item> modItems : itemsByMod.values()) {
            BlockPos currentPos = basePos.offset(modIndex * MOD_SPACING, 0, 0);
            for (int i = 0; i < modItems.size(); i += ITEMS_PER_STACK) {
                stacks.add(new PendingStack(currentPos,
                        modItems.subList(i, Math.min(i + ITEMS_PER_STACK, modItems.size()))));
                currentPos = currentPos.north();
            }
            modIndex++;
        }

        jobs.add(new Job(player, level, basePos, modIds.size(), maxRows, stacks, totalItems, onComplete));
        return new Plan(stacks.size(), totalItems);
    }

    /** Drains the queued walls, bounded by {@code test_wall.placements_per_tick}. */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || jobs.isEmpty()) {
            return;
        }

        int budget = ServerConfig.TEST_WALL_PLACEMENTS_PER_TICK.get();
        while (budget > 0 && !jobs.isEmpty()) {
            Job job = jobs.peek();

            if (job.player.hasDisconnected()) {
                jobs.poll();
                continue;
            }

            budget = job.advance(budget);

            if (job.isDone()) {
                jobs.poll();
                job.onComplete.accept(new Result(job.placedStacks, job.expectedStacks, job.totalItems));
            }
        }
    }

    private record PendingStack(BlockPos pos, List<Item> batch) {}

    /**
     * One queued wall: a floor laid out by a cursor over its extent, then the stacks. Both
     * draw from the same per-tick budget, since a floor spanning every loaded mod is itself
     * far larger than the wall standing on it.
     */
    private static final class Job {
        private final ServerPlayer player;
        private final Level level;
        private final Deque<PendingStack> stacks;
        private final int expectedStacks;
        private final int totalItems;
        private final Consumer<Result> onComplete;

        private final int floorMinX;
        private final int floorMaxX;
        private final int floorMinZ;
        private final int floorMaxZ;
        private final int floorY;
        private int floorX;
        private int floorZ;

        private int placedStacks;

        private Job(ServerPlayer player, Level level, BlockPos basePos, int modCount, int maxRows,
                    Deque<PendingStack> stacks, int totalItems, Consumer<Result> onComplete) {
            this.player = player;
            this.level = level;
            this.stacks = stacks;
            this.expectedStacks = stacks.size();
            this.totalItems = totalItems;
            this.onComplete = onComplete;

            // The floor extends one block past the stacks on every side, so the whole wall can be
            // walked around and viewed against a uniform background. Rows advance north, which is
            // decreasing Z.
            this.floorMinX = basePos.getX() - 1;
            this.floorMaxX = basePos.getX() + (modCount - 1) * MOD_SPACING + 1;
            this.floorMinZ = basePos.getZ() - maxRows;
            this.floorMaxZ = basePos.getZ() + 1;
            this.floorY = basePos.getY() - 1;
            this.floorX = floorMinX;
            this.floorZ = floorMinZ;
        }

        /** Places at most {@code budget} blocks and returns the unspent remainder. */
        private int advance(int budget) {
            BlockState floor = Blocks.SMOOTH_SANDSTONE.defaultBlockState();

            while (budget > 0 && floorX <= floorMaxX) {
                BlockPos pos = new BlockPos(floorX, floorY, floorZ);
                if (!level.isOutsideBuildHeight(pos)) {
                    level.setBlock(pos, floor, Block.UPDATE_ALL);
                }
                budget--;

                if (++floorZ > floorMaxZ) {
                    floorZ = floorMinZ;
                    floorX++;
                }
            }

            while (budget > 0 && !stacks.isEmpty()) {
                PendingStack next = stacks.poll();
                if (fillStack(level, next.pos(), next.batch())) {
                    placedStacks++;
                }
                budget--;
            }

            return budget;
        }

        private boolean isDone() {
            return floorX > floorMaxX && stacks.isEmpty();
        }
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
}
