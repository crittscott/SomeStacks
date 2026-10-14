package com.github.crittscott.somestacks.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.util.function.Function;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/** Loader wrappers for the reproduction scenarios documented in InteractionChecks. */
public final class InteractionGameTests implements FabricGameTest {
    /**
     * Loader entry point for {@link InteractionChecks#depositExtractAndPlacement}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void depositExtractAndPlacement(GameTestHelper helper) {
        InteractionChecks.depositExtractAndPlacement(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#extractionFollowsHitPoint}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void extractionFollowsHitPoint(GameTestHelper helper) {
        InteractionChecks.extractionFollowsHitPoint(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#rotationTargetsAimedItemAndConsumesMiss}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void rotationTargetsAimedItemAndConsumesMiss(GameTestHelper helper) {
        InteractionChecks.rotationTargetsAimedItemAndConsumesMiss(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link
     * InteractionChecks#incompatibleAndFullHandsLeaveAllTypesUntouched}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void incompatibleAndFullHandsLeaveAllTypesUntouched(GameTestHelper helper) {
        InteractionChecks.incompatibleAndFullHandsLeaveAllTypesUntouched(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#extractionMissConsumesWithoutMutation}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void extractionMissConsumesWithoutMutation(GameTestHelper helper) {
        InteractionChecks.extractionMissConsumesWithoutMutation(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#handsSneakingAndRotationPrecedence}; see that
     * method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void handsSneakingAndRotationPrecedence(GameTestHelper helper) {
        InteractionChecks.handsSneakingAndRotationPrecedence(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#permanenceTogglesWhileSneaking}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void permanenceTogglesWhileSneaking(GameTestHelper helper) {
        InteractionChecks.permanenceTogglesWhileSneaking(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#fullTopFaceUsesSelectedType}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void fullTopFaceUsesSelectedType(GameTestHelper helper) {
        InteractionChecks.fullTopFaceUsesSelectedType(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#firstDepositFailureLeavesNoBlock}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void firstDepositFailureLeavesNoBlock(GameTestHelper helper) {
        InteractionChecks.firstDepositFailureLeavesNoBlock(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#stateAndLogout}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void stateAndLogout(GameTestHelper helper) {
        InteractionChecks.stateAndLogout(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#successfulUseEmitsBlockChange}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void successfulUseEmitsBlockChange(GameTestHelper helper) {
        InteractionChecks.successfulUseEmitsBlockChange(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link ClientGestureChecks#blockRulesRespectPrecedenceAndHands}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void clientBlockRulesRespectPrecedenceAndHands(GameTestHelper helper) {
        ClientGestureChecks.blockRulesRespectPrecedenceAndHands(helper, factory(helper));
    }

    /**
     * Loader entry point for {@link ClientGestureChecks#fullTopFacesFallThroughToPlacement}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void clientFullTopFacesFallThroughToPlacement(GameTestHelper helper) {
        ClientGestureChecks.fullTopFacesFallThroughToPlacement(helper, factory(helper));
    }

    /**
     * Loader entry point for {@link ClientGestureChecks#gestureSyncSendsChangesAndResets}; see that
     * method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void clientGestureSyncSendsChangesAndResets(GameTestHelper helper) {
        ClientGestureChecks.gestureSyncSendsChangesAndResets(helper);
    }

    /** Runs vanilla's server block use, which invokes Fabric's callback and then calls the block. */
    private static boolean click(ServerPlayer player, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        return player.gameMode.useItemOn(
                player, player.serverLevel(), player.getItemInHand(hand), hand, hit).consumesAction();
    }

    private static Function<ItemStack, ServerPlayer> factory(GameTestHelper helper) {
        return held -> {
            ServerPlayer player = FakePlayer.get(helper.getLevel());
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            return player;
        };
    }
}
