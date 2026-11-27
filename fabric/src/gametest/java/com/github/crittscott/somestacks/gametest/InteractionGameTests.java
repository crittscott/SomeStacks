package com.github.crittscott.somestacks.gametest;

import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Loader wrappers for the reproduction scenarios documented in InteractionChecks. */
public final class InteractionGameTests implements FabricGameTest {
    private static final GameProfile MESSAGE_PROFILE =
            new GameProfile(
                    UUID.fromString("5c0e5f2a-6b1d-4c39-9a57-2f7d1e0b8c41"),
                    "[SomeStacksMessages]");

    /**
     * Loader entry point for {@link InteractionChecks#depositExtractAndPlacement}; see that method
     * for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void depositExtractAndPlacement(GameTestHelper helper) {
        InteractionChecks.depositExtractAndPlacement(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#extractionFollowsHitPoint}; see that method
     * for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void extractionFollowsHitPoint(GameTestHelper helper) {
        InteractionChecks.extractionFollowsHitPoint(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#rotationTargetsAimedItemAndConsumesMiss}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void rotationTargetsAimedItemAndConsumesMiss(GameTestHelper helper) {
        InteractionChecks.rotationTargetsAimedItemAndConsumesMiss(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link
     * InteractionChecks#incompatibleAndFullHandsLeaveAllTypesUntouched}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void incompatibleAndFullHandsLeaveAllTypesUntouched(GameTestHelper helper) {
        InteractionChecks.incompatibleAndFullHandsLeaveAllTypesUntouched(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#extractionMissConsumesWithoutMutation}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void extractionMissConsumesWithoutMutation(GameTestHelper helper) {
        InteractionChecks.extractionMissConsumesWithoutMutation(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#handsSneakingAndRotationPrecedence}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void handsSneakingAndRotationPrecedence(GameTestHelper helper) {
        InteractionChecks.handsSneakingAndRotationPrecedence(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#permanenceTogglesWhileSneaking}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void permanenceTogglesWhileSneaking(GameTestHelper helper) {
        InteractionChecks.permanenceTogglesWhileSneaking(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#fullTopFaceUsesSelectedType}; see that method
     * for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void fullTopFaceUsesSelectedType(GameTestHelper helper) {
        InteractionChecks.fullTopFaceUsesSelectedType(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#firstDepositFailureLeavesNoBlock}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void firstDepositFailureLeavesNoBlock(GameTestHelper helper) {
        InteractionChecks.firstDepositFailureLeavesNoBlock(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#modifierAirUseLeavesItemUnused}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void modifierAirUseLeavesItemUnused(GameTestHelper helper) {
        InteractionChecks.modifierAirUseLeavesItemUnused(
                helper, FabricGameTestSupport.players(helper));
    }

    /**
     * Loader entry point for {@link InteractionChecks#stateAndLogout}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void stateAndLogout(GameTestHelper helper) {
        InteractionChecks.stateAndLogout(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#successfulUseEmitsBlockChange}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void successfulUseEmitsBlockChange(GameTestHelper helper) {
        InteractionChecks.successfulUseEmitsBlockChange(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link ClientGestureChecks#blockRulesRespectPrecedenceAndHands}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void clientBlockRulesRespectPrecedenceAndHands(GameTestHelper helper) {
        ClientGestureChecks.blockRulesRespectPrecedenceAndHands(
                helper, FabricGameTestSupport.players(helper));
    }

    /**
     * Loader entry point for {@link ClientGestureChecks#fullTopFacesFallThroughToPlacement}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void clientFullTopFacesFallThroughToPlacement(GameTestHelper helper) {
        ClientGestureChecks.fullTopFacesFallThroughToPlacement(
                helper, FabricGameTestSupport.players(helper));
    }

    /**
     * Loader entry point for {@link ClientGestureChecks#gestureSyncSendsChangesAndResets}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void clientGestureSyncSendsChangesAndResets(GameTestHelper helper) {
        ClientGestureChecks.gestureSyncSendsChangesAndResets(helper);
    }

    /**
     * Loader entry point for {@link
     * InteractionChecks#disabledTypesRefuseNewBlocksButKeepExistingOnes}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void disabledTypesRefuseNewBlocksButKeepExistingOnes(GameTestHelper helper) {
        List<Component> messages = new ArrayList<>();
        InteractionChecks.disabledTypesRefuseNewBlocksButKeepExistingOnes(
                helper,
                messagePlayer(helper, messages),
                messages,
                FabricGameTestSupport::click,
                FabricGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link InteractionChecks#maximumHeightRefusesGrowthAndHidesHeadroom};
     * see that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void maximumHeightRefusesGrowthAndHidesHeadroom(GameTestHelper helper) {
        InteractionChecks.maximumHeightRefusesGrowthAndHidesHeadroom(
                helper,
                FabricGameTestSupport.players(helper),
                FabricGameTestSupport::click,
                FabricGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * InteractionChecks#deniedItemsRefuseDepositsAndOnlyModDenialStopsAutomation}; see that method
     * for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void deniedItemsRefuseDepositsAndOnlyModDenialStopsAutomation(GameTestHelper helper) {
        List<Component> messages = new ArrayList<>();
        InteractionChecks.deniedItemsRefuseDepositsAndOnlyModDenialStopsAutomation(
                helper,
                messagePlayer(helper, messages),
                messages,
                FabricGameTestSupport::click,
                FabricGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link InteractionChecks#fullMaxHeightDepositExplainsRefusal}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void fullMaxHeightDepositExplainsRefusal(GameTestHelper helper) {
        List<Component> messages = new ArrayList<>();
        InteractionChecks.fullMaxHeightDepositExplainsRefusal(
                helper, messagePlayer(helper, messages), messages, FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link
     * InteractionChecks#adventurePlacementAndGrowthShareTheBuildCheck}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void adventurePlacementAndGrowthShareTheBuildCheck(GameTestHelper helper) {
        InteractionChecks.adventurePlacementAndGrowthShareTheBuildCheck(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#barGestureDepositRefusesNonIngots}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void barGestureDepositRefusesNonIngots(GameTestHelper helper) {
        InteractionChecks.barGestureDepositRefusesNonIngots(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link ClientGestureChecks#modifierItemUseIsClaimedOnBlocks}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void clientModifierItemUseIsClaimedOnBlocks(GameTestHelper helper) {
        ClientGestureChecks.modifierItemUseIsClaimedOnBlocks(
                helper, FabricGameTestSupport.players(helper));
    }

    /**
     * Loader entry point for {@link ClientGestureChecks#modeCycleFollowsOrderAndPolicy}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void clientModeCycleFollowsOrderAndPolicy(GameTestHelper helper) {
        ClientGestureChecks.modeCycleFollowsOrderAndPolicy(helper);
    }

    /**
     * Loader entry point for {@link NetworkPolicyChecks#gestureStatePacketValidatesOnDecode}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void gestureStatePacketValidatesOnDecode(GameTestHelper helper) {
        NetworkPolicyChecks.gestureStatePacketValidatesOnDecode(helper);
    }

    /** A fake player that records the hotbar and chat messages the mod shows it. */
    private static ServerPlayer messagePlayer(GameTestHelper helper, List<Component> messages) {
        return new FakePlayer(helper.getLevel(), MESSAGE_PROFILE) {
            @Override
            public void displayClientMessage(Component message, boolean overlay) {
                messages.add(message);
            }
        };
    }
}
