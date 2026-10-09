package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.server.WorldEdits;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;

/** Checks the Fabric edit policy when no protection query provider is installed. */
public final class NoProtectionApiGameTests implements FabricGameTest {
    /**
     * To reproduce in-game without Common Protection API: place a stack against an ordinary block,
     * then modifier-click through empty grounded cells at their support. Both placement and
     * neighboring deposits work when destination block-use callbacks permit them.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void placementAndAdjacentDepositsWorkWithoutProtectionApi(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(ORIGIN);
        ServerPlayer player = FakePlayer.get(helper.getLevel());
        check(WorldEdits.mayPlace(player, pos), "Unprotected placement was denied");
        check(WorldEdits.mayUseAdjacent(player, pos), "Unprotected neighboring deposit was denied");
        check(WorldEdits.placeChecked(player, helper.getLevel(), pos,
                        Blocks.STONE.defaultBlockState(), Direction.DOWN),
                "Unprotected placement failed");
        helper.assertBlockPresent(Blocks.STONE, ORIGIN);
        helper.getLevel().removeBlock(pos, false);
        ProtectionChecks.checkAdjacentAndDirectDeposits(
                helper, FabricGameTestSupport.playerFactory(helper), true);
        helper.succeed();
    }
}
