package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.server.WorldEdits;
import com.mojang.authlib.GameProfile;
import eu.pb4.common.protection.api.CommonProtection;
import eu.pb4.common.protection.api.ProtectionProvider;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;

/** Provider-backed checks through the required Common Protection API, with a test provider installed. */
public final class ProtectionApiGameTests {
    static {
        CommonProtection.register(ResourceLocation.fromNamespaceAndPath("somestacks", "test_protection"),
                new ProtectionProvider() {
                    @Override
                    public boolean isProtected(Level level, BlockPos pos) { return false; }

                    @Override
                    public boolean isAreaProtected(Level level, AABB area) { return false; }

                    @Override
                    public boolean canPlaceBlock(Level level, BlockPos pos, GameProfile profile, Player player) {
                        return ProtectionGameTests.consult(level, pos, player);
                    }

                    @Override
                    public boolean canInteractBlock(Level level, BlockPos pos, GameProfile profile, Player player) {
                        return ProtectionGameTests.consult(level, pos, player);
                    }

                    @Override
                    public boolean canBreakBlock(Level level, BlockPos pos, GameProfile profile, Player player) {
                        return true;
                    }
                });
    }

    /** See {@link ProtectionGameTests#automationGrowthConsultsProtectionWithoutSyntheticClick}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void automationGrowthConsultsProtectionWithoutSyntheticClick(GameTestHelper helper) {
        new ProtectionGameTests().automationGrowthConsultsProtectionWithoutSyntheticClick(helper);
    }

    /** See {@link ProtectionGameTests#destinationConsultationRunsBeforePlacement}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void destinationConsultationRunsBeforePlacement(GameTestHelper helper) {
        new ProtectionGameTests().destinationConsultationRunsBeforePlacement(helper);
    }

    /** See {@link ProtectionGameTests#adjacentConsultationHonorsProtectionQuery}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void adjacentConsultationHonorsProtectionQuery(GameTestHelper helper) {
        new ProtectionGameTests().adjacentConsultationHonorsProtectionQuery(helper);
    }

    /** See {@link ProtectionGameTests#allowedAdjacentDepositUsesProtectionQuery}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void allowedAdjacentDepositUsesProtectionQuery(GameTestHelper helper) {
        new ProtectionGameTests().allowedAdjacentDepositUsesProtectionQuery(helper);
    }

    /**
     * To reproduce in-game outside any claim: place a stack against an ordinary block, then
     * modifier-click through empty grounded cells at their support. Both placement and neighboring
     * deposits work when no protection provider objects.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void placementAndAdjacentDepositsWorkWithoutClaims(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(ORIGIN);
        ServerPlayer player = FakePlayer.get(helper.getLevel());
        check(WorldEdits.mayPlace(player, helper.getLevel(), pos), "Unprotected placement was denied");
        check(WorldEdits.mayUseAdjacent(player, pos), "Unprotected neighboring deposit was denied");
        check(WorldEdits.placeChecked(player, helper.getLevel(), pos,
                        Blocks.STONE.defaultBlockState(), Direction.DOWN, net.minecraft.world.phys.shapes.Shapes.block()),
                "Unprotected placement failed");
        helper.assertBlockPresent(Blocks.STONE, ORIGIN);
        helper.getLevel().removeBlock(pos, false);
        ProtectionChecks.checkAdjacentAndDirectDeposits(
                helper, FabricGameTestSupport.playerFactory(helper), true);
        helper.succeed();
    }
}
