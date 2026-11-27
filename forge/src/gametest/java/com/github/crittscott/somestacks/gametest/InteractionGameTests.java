package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Loader wrappers for the reproduction scenarios documented in InteractionChecks. */
@GameTestHolder(SomeStacksCommon.MODID)
@PrefixGameTestTemplate(false)
public final class InteractionGameTests {
    private static final GameProfile MESSAGE_PROFILE =
            new GameProfile(
                    UUID.fromString("5c0e5f2a-6b1d-4c39-9a57-2f7d1e0b8c41"),
                    "[SomeStacksMessages]");

    /**
     * Loader entry point for {@link InteractionChecks#depositExtractAndPlacement}; see that method
     * for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void depositExtractAndPlacement(GameTestHelper helper) {
        InteractionChecks.depositExtractAndPlacement(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#extractionFollowsHitPoint}; see that method
     * for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void extractionFollowsHitPoint(GameTestHelper helper) {
        InteractionChecks.extractionFollowsHitPoint(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#rotationTargetsAimedItemAndConsumesMiss}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void rotationTargetsAimedItemAndConsumesMiss(GameTestHelper helper) {
        InteractionChecks.rotationTargetsAimedItemAndConsumesMiss(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link
     * InteractionChecks#incompatibleAndFullHandsLeaveAllTypesUntouched}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void incompatibleAndFullHandsLeaveAllTypesUntouched(GameTestHelper helper) {
        InteractionChecks.incompatibleAndFullHandsLeaveAllTypesUntouched(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#extractionMissConsumesWithoutMutation}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void extractionMissConsumesWithoutMutation(GameTestHelper helper) {
        InteractionChecks.extractionMissConsumesWithoutMutation(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#handsSneakingAndRotationPrecedence}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void handsSneakingAndRotationPrecedence(GameTestHelper helper) {
        InteractionChecks.handsSneakingAndRotationPrecedence(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#permanenceTogglesWhileSneaking}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void permanenceTogglesWhileSneaking(GameTestHelper helper) {
        InteractionChecks.permanenceTogglesWhileSneaking(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#fullTopFaceUsesSelectedType}; see that method
     * for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void fullTopFaceUsesSelectedType(GameTestHelper helper) {
        InteractionChecks.fullTopFaceUsesSelectedType(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#firstDepositFailureLeavesNoBlock}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void firstDepositFailureLeavesNoBlock(GameTestHelper helper) {
        InteractionChecks.firstDepositFailureLeavesNoBlock(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#modifierAirUseLeavesItemUnused}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void modifierAirUseLeavesItemUnused(GameTestHelper helper) {
        InteractionChecks.modifierAirUseLeavesItemUnused(
                helper, ForgeGameTestSupport.players(helper));
    }

    /**
     * Loader entry point for {@link InteractionChecks#stateAndLogout}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void stateAndLogout(GameTestHelper helper) {
        InteractionChecks.stateAndLogout(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#successfulUseEmitsBlockChange}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void successfulUseEmitsBlockChange(GameTestHelper helper) {
        InteractionChecks.successfulUseEmitsBlockChange(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link ClientGestureChecks#blockRulesRespectPrecedenceAndHands}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void clientBlockRulesRespectPrecedenceAndHands(GameTestHelper helper) {
        ClientGestureChecks.blockRulesRespectPrecedenceAndHands(
                helper, ForgeGameTestSupport.players(helper));
    }

    /**
     * Loader entry point for {@link ClientGestureChecks#fullTopFacesFallThroughToPlacement}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void clientFullTopFacesFallThroughToPlacement(GameTestHelper helper) {
        ClientGestureChecks.fullTopFacesFallThroughToPlacement(
                helper, ForgeGameTestSupport.players(helper));
    }

    /**
     * Loader entry point for {@link ClientGestureChecks#gestureSyncSendsChangesAndResets}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void clientGestureSyncSendsChangesAndResets(GameTestHelper helper) {
        ClientGestureChecks.gestureSyncSendsChangesAndResets(helper);
    }

    /**
     * Loader entry point for {@link
     * InteractionChecks#disabledTypesRefuseNewBlocksButKeepExistingOnes}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void disabledTypesRefuseNewBlocksButKeepExistingOnes(GameTestHelper helper) {
        List<Component> messages = new ArrayList<>();
        InteractionChecks.disabledTypesRefuseNewBlocksButKeepExistingOnes(
                helper,
                messagePlayer(helper, messages),
                messages,
                ForgeGameTestSupport::click,
                ForgeGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link InteractionChecks#maximumHeightRefusesGrowthAndHidesHeadroom};
     * see that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void maximumHeightRefusesGrowthAndHidesHeadroom(GameTestHelper helper) {
        InteractionChecks.maximumHeightRefusesGrowthAndHidesHeadroom(
                helper,
                ForgeGameTestSupport.players(helper),
                ForgeGameTestSupport::click,
                ForgeGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * InteractionChecks#deniedItemsRefuseDepositsAndOnlyModDenialStopsAutomation}; see that method
     * for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void deniedItemsRefuseDepositsAndOnlyModDenialStopsAutomation(
            GameTestHelper helper) {
        List<Component> messages = new ArrayList<>();
        InteractionChecks.deniedItemsRefuseDepositsAndOnlyModDenialStopsAutomation(
                helper,
                messagePlayer(helper, messages),
                messages,
                ForgeGameTestSupport::click,
                ForgeGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link InteractionChecks#fullMaxHeightDepositExplainsRefusal}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void fullMaxHeightDepositExplainsRefusal(GameTestHelper helper) {
        List<Component> messages = new ArrayList<>();
        InteractionChecks.fullMaxHeightDepositExplainsRefusal(
                helper, messagePlayer(helper, messages), messages, ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link
     * InteractionChecks#adventurePlacementAndGrowthShareTheBuildCheck}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void adventurePlacementAndGrowthShareTheBuildCheck(GameTestHelper helper) {
        InteractionChecks.adventurePlacementAndGrowthShareTheBuildCheck(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#barGestureDepositRefusesNonIngots}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void barGestureDepositRefusesNonIngots(GameTestHelper helper) {
        InteractionChecks.barGestureDepositRefusesNonIngots(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link ClientGestureChecks#modifierItemUseIsClaimedOnBlocks}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void clientModifierItemUseIsClaimedOnBlocks(GameTestHelper helper) {
        ClientGestureChecks.modifierItemUseIsClaimedOnBlocks(
                helper, ForgeGameTestSupport.players(helper));
    }

    /**
     * Loader entry point for {@link ClientGestureChecks#modeCycleFollowsOrderAndPolicy}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void clientModeCycleFollowsOrderAndPolicy(GameTestHelper helper) {
        ClientGestureChecks.modeCycleFollowsOrderAndPolicy(helper);
    }

    /**
     * Loader entry point for {@link NetworkPolicyChecks#gestureStatePacketValidatesOnDecode}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void gestureStatePacketValidatesOnDecode(GameTestHelper helper) {
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
