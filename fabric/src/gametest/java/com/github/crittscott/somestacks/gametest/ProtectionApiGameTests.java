package com.github.crittscott.somestacks.gametest;

import com.mojang.authlib.GameProfile;
import eu.pb4.common.protection.api.CommonProtection;
import eu.pb4.common.protection.api.ProtectionProvider;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Provider-backed checks loaded only in development runs with Common Protection API installed. */
public final class ProtectionApiGameTests {
    static {
        CommonProtection.register(Identifier.fromNamespaceAndPath("somestacks", "test_protection"),
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
}
