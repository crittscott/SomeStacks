package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.github.crittscott.somestacks.gametest.GameTestSupport.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.check;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.checkEquals;

/**
 * The render-gallery world builder: that a queued gallery lays out its floor and rows as described
 * and reports the totals it finished with, spread across ticks by the configured placement limit.
 */
@GameTestHolder(SomeStacks.MODID)
@PrefixGameTestTemplate(false)
public final class RenderGalleryGameTests {
    private RenderGalleryGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = 100)
    public static void queuedStorageGalleryBuildsFloorRowsAndCompletionTotals(
            GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
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
}
