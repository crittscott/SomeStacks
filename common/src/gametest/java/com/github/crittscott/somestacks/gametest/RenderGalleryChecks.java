package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/**
 * The render-gallery world builder: that a queued gallery lays out its floor and rows as described
 * and reports the totals it finished with, spread across ticks by the configured placement limit.
 */
public final class RenderGalleryChecks {
    private RenderGalleryChecks() {}

    public static void queuedStorageGalleryBuildsFloorRowsAndCompletionTotals(
            GameTestHelper helper, ServerPlayer player) {
        BlockPos playerPos = helper.absolutePos(ORIGIN);
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        List<Item> firstGroup = List.of(
                Items.STONE, Items.DIRT, Items.COBBLESTONE,
                Items.APPLE, Items.PAPER, Items.STICK,
                Items.GLASS, Items.SAND, Items.GRAVEL, Items.COAL);
        List<Item> secondGroup = List.of(Items.IRON_INGOT);
        RenderGalleryGenerator.Result[] completed = new RenderGalleryGenerator.Result[1];

        RenderGalleryGenerator.Plan plan = RenderGalleryGenerator.enqueue(
                player,
                RenderGalleryGenerator.Kind.STORAGE,
                List.of(firstGroup, secondGroup),
                result -> completed[0] = result);

        checkEquals(3, plan.expectedStacks(), "Planned stack count");
        checkEquals(11, plan.totalItems(), "Planned item count");

        helper.runAfterDelay(60, () -> {
            check(completed[0] != null, "Queued gallery did not complete");
            checkEquals(3, completed[0].totalStacks(), "Placed stack count");
            checkEquals(3, completed[0].expectedStacks(), "Completed expected count");
            checkEquals(11, completed[0].totalItems(), "Completed item count");

            BlockPos base = playerPos.east();
            check(helper.getLevel().getBlockState(base.below()).is(Blocks.SMOOTH_SANDSTONE),
                    "Gallery floor was not built");
            check(helper.getLevel().getBlockEntity(base) instanceof StorageStackBE,
                    "First group first row missing");
            check(helper.getLevel().getBlockEntity(base.north()) instanceof StorageStackBE,
                    "First group second row missing");
            check(helper.getLevel().getBlockEntity(base.east(2)) instanceof StorageStackBE,
                    "Second group row missing");
            helper.succeed();
        });
    }

    /**
     * Gallery placement obeys the configured per-tick budget. To reproduce in-game: lower
     * {@code render_gallery.placements_per_tick} and request a large Storage gallery. Construction
     * visibly progresses across multiple ticks rather than completing in one server tick. The
     * automated check uses two isolated jobs and a budget of four, including both phase boundaries.
     */
    public static void queuedStorageGallerySpreadsWorkAcrossTicks(
            GameTestHelper helper, ServerPlayer player) {
        Path serverConfigDir = helper.getLevel().getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("serverconfig");
        Path original = serverConfigDir.resolve(SomeStacksCommon.MODID + "-server.json");
        Path fixture = serverConfigDir.resolve(SomeStacksCommon.MODID + "-gallery-budget.json");
        BlockPos playerPos = helper.absolutePos(ORIGIN);
        BlockPos[] bases = {playerPos.east(), playerPos.east(8)};
        RenderGalleryGenerator.Result[] completed = new RenderGalleryGenerator.Result[2];
        try {
            // Absent fields keep their loaded values, so the fixture changes only the budget.
            Files.createDirectories(serverConfigDir);
            Files.writeString(fixture, "{\"render_gallery\": {\"placements_per_tick\": 4}}");
            ServerConfig.load(fixture);
            checkEquals(4, ServerConfig.renderGalleryPlacementsPerTick(), "Test placement budget");
            for (int job = 0; job < bases.length; job++) {
                BlockPos base = bases[job];
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        helper.getLevel().setBlock(base.offset(x, -1, z), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                helper.getLevel().setBlock(base, Blocks.AIR.defaultBlockState(), 3);
                player.setPos(base.getX() - 0.5, base.getY(), base.getZ() + 0.5);
                int index = job;
                RenderGalleryGenerator.Plan plan = RenderGalleryGenerator.enqueue(player,
                        RenderGalleryGenerator.Kind.STORAGE, List.of(List.of(Items.STONE)),
                        result -> completed[index] = result);
                checkEquals(1, plan.expectedStacks(), "Planned stack count");
            }

            // Each job has nine floor positions followed by one stack. Tick three crosses
            // both the floor/stack boundary and the job boundary, sharing the same budget.
            for (int tick = 1; tick <= 5; tick++) {
                RenderGalleryGenerator.onServerTick();
                int work = tick * 4;
                for (int job = 0; job < bases.length; job++) {
                    int jobWork = Math.max(0, Math.min(10, work - job * 10));
                    int floorCount = 0;
                    for (int x = -1; x <= 1; x++) {
                        for (int z = -1; z <= 1; z++) {
                            if (helper.getLevel().getBlockState(bases[job].offset(x, -1, z))
                                    .is(Blocks.SMOOTH_SANDSTONE)) floorCount++;
                        }
                    }
                    checkEquals(Math.min(9, jobWork), floorCount, "Floor count after tick " + tick);
                    checkEquals(jobWork == 10,
                            helper.getLevel().getBlockEntity(bases[job]) instanceof StorageStackBE,
                            "Stack placement after tick " + tick);
                    checkEquals(jobWork == 10, completed[job] != null,
                            "Completion after tick " + tick);
                }
            }
            for (RenderGalleryGenerator.Result result : completed) {
                checkEquals(1, result.totalStacks(), "Completed stack count");
                checkEquals(1, result.totalItems(), "Completed item count");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the gallery budget fixture", e);
        } finally {
            // This test runs in its own GameTest batch. Drain only its jobs even if an assertion fails.
            try {
                for (int tick = 0; tick < 5 && (completed[0] == null || completed[1] == null); tick++) {
                    RenderGalleryGenerator.onServerTick();
                }
            } finally {
                ServerConfig.load(original);
                try {
                    Files.deleteIfExists(fixture);
                } catch (IOException e) {
                    SomeStacksCommon.LOGGER.warn("Could not delete GameTest config fixture {}", fixture, e);
                }
            }
        }
        helper.succeed();
    }

    public static void queuedBarGalleryBuildsRowsAndFillsBars(
            GameTestHelper helper, ServerPlayer player) {
        BlockPos playerPos = helper.absolutePos(ORIGIN);
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        Item barItem = GameTestScaffold.firstBarItem();
        RenderGalleryGenerator.Result[] completed = new RenderGalleryGenerator.Result[1];

        RenderGalleryGenerator.Plan plan = RenderGalleryGenerator.enqueue(
                player,
                RenderGalleryGenerator.Kind.BAR,
                List.of(Collections.nCopies(9, barItem)),
                result -> completed[0] = result);
        checkEquals(2, plan.expectedStacks(), "Planned Bar stack count");
        checkEquals(9, plan.totalItems(), "Planned Bar item count");

        helper.runAfterDelay(60, () -> {
            check(completed[0] != null, "Queued Bar gallery did not complete");
            checkEquals(2, completed[0].totalStacks(), "Placed Bar stack count");
            checkEquals(9, completed[0].totalItems(), "Completed Bar item count");

            BlockPos base = playerPos.east();
            check(helper.getLevel().getBlockEntity(base) instanceof BarStackBE,
                    "First Bar gallery row missing");
            check(helper.getLevel().getBlockEntity(base.north()) instanceof BarStackBE,
                    "Second Bar gallery row missing");
            BarStackBE first = (BarStackBE) helper.getLevel().getBlockEntity(base);
            BarStackBE second = (BarStackBE) helper.getLevel().getBlockEntity(base.north());
            checkEquals(8, GameTestScaffold.count(first.getItems(), barItem),
                    "First Bar gallery row contents");
            checkEquals(1, GameTestScaffold.count(second.getItems(), barItem),
                    "Second Bar gallery row contents");
            helper.succeed();
        });
    }
}
