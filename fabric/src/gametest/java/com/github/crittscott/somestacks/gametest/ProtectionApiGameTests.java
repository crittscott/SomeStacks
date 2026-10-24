package com.github.crittscott.somestacks.gametest;

import com.mojang.authlib.GameProfile;
import eu.pb4.common.protection.api.CommonProtection;
import eu.pb4.common.protection.api.ProtectionProvider;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Provider-backed checks loaded only in development runs with Common Protection API installed. */
public final class ProtectionApiGameTests implements FabricGameTest {
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
                });
    }

    /** See {@link ProtectionGameTests#automationGrowthConsultsProtectionWithoutSyntheticClick}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void automationGrowthConsultsProtectionWithoutSyntheticClick(GameTestHelper helper) {
        new ProtectionGameTests().automationGrowthConsultsProtectionWithoutSyntheticClick(helper);
    }

    /** See {@link ProtectionGameTests#destinationConsultationRunsBeforePlacement}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void destinationConsultationRunsBeforePlacement(GameTestHelper helper) {
        new ProtectionGameTests().destinationConsultationRunsBeforePlacement(helper);
    }

    /** See {@link ProtectionGameTests#adjacentConsultationHonorsProtectionQuery}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void adjacentConsultationHonorsProtectionQuery(GameTestHelper helper) {
        new ProtectionGameTests().adjacentConsultationHonorsProtectionQuery(helper);
    }

    /** See {@link ProtectionGameTests#allowedAdjacentDepositUsesProtectionQuery}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void allowedAdjacentDepositUsesProtectionQuery(GameTestHelper helper) {
        new ProtectionGameTests().allowedAdjacentDepositUsesProtectionQuery(helper);
    }
}
