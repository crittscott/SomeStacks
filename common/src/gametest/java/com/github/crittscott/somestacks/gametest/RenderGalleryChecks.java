package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

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

    public static void queuedStorageGallerySpreadsWorkAcrossTicks(
            GameTestHelper helper, ServerPlayer player) {
        BlockPos playerPos = helper.absolutePos(ORIGIN);
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        List<Item> group = Collections.nCopies(28, Items.STONE);
        RenderGalleryGenerator.Result[] completed = new RenderGalleryGenerator.Result[1];

        RenderGalleryGenerator.Plan plan = RenderGalleryGenerator.enqueue(
                player,
                RenderGalleryGenerator.Kind.STORAGE,
                List.of(group, group, group, group),
                result -> completed[0] = result);

        // Four groups by four rows require 54 floor placements and 16 stack placements. The
        // default budget of 64 can start the stacks on the first tick but cannot finish the job.
        checkEquals(16, plan.expectedStacks(), "Planned multi-tick stack count");
        checkEquals(112, plan.totalItems(), "Planned multi-tick item count");
        check(completed[0] == null, "Gallery completed synchronously instead of queueing");

        helper.runAfterDelay(1, () -> {
            check(completed[0] == null, "Gallery ignored the per-tick placement budget");
            check(helper.getLevel().getBlockState(playerPos.east().below())
                            .is(Blocks.SMOOTH_SANDSTONE),
                    "First gallery tick made no floor progress");
        });
        helper.runAfterDelay(20, () -> {
            check(completed[0] != null, "Multi-tick gallery did not complete");
            checkEquals(16, completed[0].totalStacks(), "Multi-tick placed stack count");
            checkEquals(112, completed[0].totalItems(), "Multi-tick completed item count");
            helper.succeed();
        });
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
