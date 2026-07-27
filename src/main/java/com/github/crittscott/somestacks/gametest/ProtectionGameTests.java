package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.server.RightClickBlockSuppressor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.github.crittscott.somestacks.gametest.GameTestSupport.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.check;

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
}
