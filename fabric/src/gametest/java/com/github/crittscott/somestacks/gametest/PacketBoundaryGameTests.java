package com.github.crittscott.somestacks.gametest;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

/**
 * What the server refuses at the packet boundary: out-of-reach targets, hands other than the main
 * one, cells holding nothing, and extractions into a hand that cannot take what is offered.
 */
public final class PacketBoundaryGameTests implements FabricGameTest {
    private static final String TEMPLATE = FabricGameTestSupport.TEMPLATE;

    @GameTest(template = TEMPLATE)
    public void reachCheckAcceptsNearTargetAndRejectsFarTarget(GameTestHelper helper) {
        PacketBoundaryChecks.reachCheckAcceptsNearTargetAndRejectsFarTarget(
                helper, playerFactory(helper));
    }

    @GameTest(template = TEMPLATE)
    public void gestureChecksReadOnlyTheMainHand(GameTestHelper helper) {
        PacketBoundaryChecks.gestureChecksReadOnlyTheMainHand(helper, playerFactory(helper));
    }

    @GameTest(template = TEMPLATE)
    public void extractIgnoresAnEmptyCell(GameTestHelper helper) {
        PacketBoundaryChecks.extractIgnoresAnEmptyCell(helper, playerFactory(helper));
    }

    @GameTest(template = TEMPLATE)
    public void extractRefusesAHandHoldingSomethingElse(GameTestHelper helper) {
        PacketBoundaryChecks.extractRefusesAHandHoldingSomethingElse(helper, playerFactory(helper));
    }

    @GameTest(template = TEMPLATE)
    public void extractRefusesAFullHandOfTheSameItem(GameTestHelper helper) {
        PacketBoundaryChecks.extractRefusesAFullHandOfTheSameItem(helper, playerFactory(helper));
    }

    @GameTest(template = TEMPLATE)
    public void refusedBarExtractionLeavesTheBlockStanding(GameTestHelper helper) {
        PacketBoundaryChecks.refusedBarExtractionLeavesTheBlockStanding(
                helper, playerFactory(helper));
    }

    @GameTest(template = TEMPLATE)
    public void extractIgnoresAnIndexOutsideTheBlocksOwnSlots(GameTestHelper helper) {
        PacketBoundaryChecks.extractIgnoresAnIndexOutsideTheBlocksOwnSlots(
                helper, playerFactory(helper));
    }

    /** Each test needs its own mock player so the hand it means to test with starts empty. */
    private static Function<ItemStack, ServerPlayer> playerFactory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return mainHand -> {
            ServerPlayer player = FakePlayer.get(level);
            player.setItemInHand(PacketBoundaryChecks.HAND, mainHand);
            return player;
        };
    }
}
