package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.server.RightClickBlockSuppressor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import static com.github.crittscott.somestacks.gametest.GameTestSupport.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.check;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.checkEquals;

@GameTestHolder(SomeStacks.MODID)
@PrefixGameTestTemplate(false)
public final class ProtectionGameTests {
    private ProtectionGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        BlockPos valid = helper.absolutePos(ORIGIN);
        BlockPos invalid = new BlockPos(
                valid.getX(), level.getMinBuildHeight() - 1, valid.getZ());

        check(Protection.placeChecked(
                        player, level, valid, Blocks.STONE.defaultBlockState(), Direction.DOWN),
                "Valid checked placement was rejected");
        check(level.getBlockState(valid).is(Blocks.STONE),
                "Valid checked placement did not change the world");
        check(!Protection.placeChecked(
                        player, level, invalid, Blocks.STONE.defaultBlockState(), Direction.DOWN),
                "Out-of-height checked placement was accepted");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void sameTickSuppressionVetoesOnlyTheMarkedPosition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        BlockPos marked = helper.absolutePos(ORIGIN);
        BlockPos other = marked.east();

        RightClickBlockSuppressor.suppress(player, marked, level);

        check(!Protection.mayInteract(player, marked, InteractionHand.MAIN_HAND),
                "Marked same-tick interaction was not suppressed");
        check(Protection.mayInteract(player, other, InteractionHand.MAIN_HAND),
                "Different position was suppressed");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = 20)
    public static void suppressionExpiresOnTheNextTick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        BlockPos marked = helper.absolutePos(ORIGIN);

        RightClickBlockSuppressor.suppress(player, marked, level);
        helper.runAfterDelay(1, () -> {
            check(Protection.mayInteract(player, marked, InteractionHand.MAIN_HAND),
                    "Expired suppression still vetoed interaction");
            helper.succeed();
        });
    }

    // Growth under protection
    //
    // A capability insertion aimed at a slot past what the run holds is the one that grows it, and it
    // weighs the position above the run before promising the caller anything, so a simulation and the
    // commit that follows agree about a position growth cannot have. The three tests below stage that
    // with the world border. Spawn protection, the other half of the same predicate, cannot be staged
    // here: it is implemented on DedicatedServer, and the server running these tests is not one.

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void storageGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        StorageStackBE storage = GameTestSupport.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
        }
        IItemHandler capability = GameTestSupport.capability(storage);
        ItemStack offered = new ItemStack(Items.STONE, 4);

        int headroom = StorageStackBE.SLOTS;
        check(capability.insertItem(headroom, offered, true).isEmpty(),
                "A full pile with free headroom did not credit growth");

        outsideTheBorder(helper, () -> {
            checkEquals(4, capability.insertItem(headroom, offered, true).getCount(),
                    "Simulated remainder");
            checkEquals(4, capability.insertItem(headroom, offered, false).getCount(),
                    "Committed remainder");
        });

        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                ModRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void singlesGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        SinglesStackBE singles = GameTestSupport.placeSingles(helper, ORIGIN);
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            singles.getItems().insertItem(slot, new ItemStack(Items.STONE, 1), false);
        }
        IItemHandler capability = GameTestSupport.capability(singles);
        ItemStack offered = new ItemStack(Items.STONE, 4);

        // A cell takes one item, so a credited growth leaves three of the four behind.
        int headroom = SinglesStackBE.SLOTS;
        checkEquals(3, capability.insertItem(headroom, offered, true).getCount(),
                "A full column with free headroom did not credit growth");

        outsideTheBorder(helper, () -> {
            checkEquals(4, capability.insertItem(headroom, offered, true).getCount(),
                    "Simulated remainder");
            checkEquals(4, capability.insertItem(headroom, offered, false).getCount(),
                    "Committed remainder");
        });

        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                ModRegistry.SINGLES_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void barGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        BarStackBE bars = GameTestSupport.placeBar(helper, ORIGIN);
        Item bar = GameTestSupport.firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            bars.getItems().insertItem(slot, new ItemStack(bar, 1), false);
        }
        IItemHandler capability = GameTestSupport.capability(bars);
        ItemStack offered = new ItemStack(bar, 4);

        // A position takes one bar, so a credited growth leaves three of the four behind.
        int headroom = BarStackBE.SLOTS;
        checkEquals(3, capability.insertItem(headroom, offered, true).getCount(),
                "A full column with free headroom did not credit growth");

        outsideTheBorder(helper, () -> {
            checkEquals(4, capability.insertItem(headroom, offered, true).getCount(),
                    "Simulated remainder");
            checkEquals(4, capability.insertItem(headroom, offered, false).getCount(),
                    "Committed remainder");
        });

        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                ModRegistry.BAR_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    /**
     * Runs {@code action} with the world border moved off the test structure, and restores it
     * before returning. The border is level-wide state, but the move and the restore both happen
     * inside this one synchronous call, so no other test observes it moved.
     */
    private static void outsideTheBorder(GameTestHelper helper, Runnable action) {
        WorldBorder border = helper.getLevel().getWorldBorder();
        BlockPos above = helper.absolutePos(ORIGIN.above());
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();
        double size = border.getSize();
        try {
            border.setCenter(above.getX() + 1000.0, above.getZ());
            border.setSize(16.0);
            check(!border.isWithinBounds(above),
                    "Test setup left the position inside the world border");
            action.run();
        } finally {
            border.setCenter(centerX, centerZ);
            border.setSize(size);
        }
    }
}
