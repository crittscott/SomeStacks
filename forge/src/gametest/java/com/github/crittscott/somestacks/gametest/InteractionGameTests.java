package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.server.StackInteractionEvents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;


/**
 * Forge delegates for loader-neutral interaction and validation checks.
 */
@GameTestHolder(SomeStacks.MODID)
public final class InteractionGameTests {
    private InteractionGameTests() {}

    /** See {@link InteractionChecks#malformedGestureStateFailsDuringDecoding}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void malformedGestureStateFailsDuringDecoding(GameTestHelper helper) {
        InteractionChecks.malformedGestureStateFailsDuringDecoding(helper);
    }

    /** See {@link InteractionChecks#gestureStateTracksModeAndModifier}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void gestureStateTracksModeAndModifier(GameTestHelper helper) {
        InteractionChecks.gestureStateTracksModeAndModifier(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#interactionReadsOnlyTheMainHand}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void interactionReadsOnlyTheMainHand(GameTestHelper helper) {
        InteractionChecks.interactionReadsOnlyTheMainHand(helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#rotationValidatesHeldItemAndBlockType}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void rotationValidatesHeldItemAndBlockType(GameTestHelper helper) {
        InteractionChecks.rotationValidatesHeldItemAndBlockType(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#sneakingRotationsReachTheLoaderHook}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void sneakingRotationsReachTheLoaderHook(GameTestHelper helper) {
        InteractionChecks.sneakingRotationsReachTheLoaderHook(
                helper, GameTestSupport.playerFactory(helper), (player, hit) -> {
                    var event = new PlayerInteractEvent.RightClickBlock(
                            player, InteractionChecks.HAND, hit.getBlockPos(), hit);
                    StackInteractionEvents.onRightClickBlock(event);
                    return event.isCanceled();
                });
    }

    /** See {@link InteractionChecks#vanillaInteractionReachesDepositExtractAndPlacement}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void vanillaInteractionReachesDepositExtractAndPlacement(
            GameTestHelper helper) {
        InteractionChecks.vanillaInteractionReachesDepositExtractAndPlacement(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#itemRotationTargetsOnlyAnOccupiedCell}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void itemRotationTargetsOnlyAnOccupiedCell(GameTestHelper helper) {
        InteractionChecks.itemRotationTargetsOnlyAnOccupiedCell(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#permanenceRequiresEmptyHandAndHonorsProtection}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void permanenceRequiresEmptyHandAndHonorsProtection(
            GameTestHelper helper) {
        InteractionChecks.permanenceRequiresEmptyHandAndHonorsProtection(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#rotationSoundThrottleSuppressesSameTickAndClears}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = 20)
    public static void rotationSoundThrottleSuppressesSameTickAndClears(
            GameTestHelper helper) {
        InteractionChecks.rotationSoundThrottleSuppressesSameTickAndClears(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#playerMutationEmitsBlockChange}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = 20)
    public static void playerMutationEmitsBlockChange(GameTestHelper helper) {
        InteractionChecks.playerMutationEmitsBlockChange(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#extractionRefusesIncompatibleOrFullHands}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void extractionRefusesIncompatibleOrFullHands(GameTestHelper helper) {
        InteractionChecks.extractionRefusesIncompatibleOrFullHands(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#fullTopFacePlacesTheSelectedStackType}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void fullTopFacePlacesTheSelectedStackType(GameTestHelper helper) {
        InteractionChecks.fullTopFacePlacesTheSelectedStackType(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#everyStackTypeBlocksPistons}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void everyStackTypeBlocksPistons(GameTestHelper helper) {
        InteractionChecks.everyStackTypeBlocksPistons(helper);
    }

    /** See {@link InteractionChecks#extractionEventsFollowBlockSurvival}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void extractionEventsFollowBlockSurvival(GameTestHelper helper) {
        InteractionChecks.extractionEventsFollowBlockSurvival(helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link SynchronizationChecks#configDecoderEnforcesPacketBudget}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void configDecoderEnforcesPacketBudget(GameTestHelper helper) {
        SynchronizationChecks.configDecoderEnforcesPacketBudget(helper);
    }

    /** See {@link SynchronizationChecks#configStagingRejectsAndClearsInvalidGenerations}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void configStagingRejectsAndClearsInvalidGenerations(GameTestHelper helper) {
        SynchronizationChecks.configStagingRejectsAndClearsInvalidGenerations(helper);
    }

    /** See {@link SynchronizationChecks#configSnapshotsRoundTripAndPublishAtomically}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void configSnapshotsRoundTripAndPublishAtomically(GameTestHelper helper) {
        SynchronizationChecks.configSnapshotsRoundTripAndPublishAtomically(helper);
    }

    /** See {@link SynchronizationChecks#configStagingChecksSequencesAndTotals}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void configStagingChecksSequencesAndTotals(GameTestHelper helper) {
        SynchronizationChecks.configStagingChecksSequencesAndTotals(helper);
    }

    /** See {@link SynchronizationChecks#configDecoderRejectsMalformedCountsAndNamespaces}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void configDecoderRejectsMalformedCountsAndNamespaces(GameTestHelper helper) {
        SynchronizationChecks.configDecoderRejectsMalformedCountsAndNamespaces(helper);
    }

    /** See {@link ClientGestureChecks#blockRulesRespectPrecedenceAndHands}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void blockRulesRespectPrecedenceAndHands(GameTestHelper helper) {
        ClientGestureChecks.blockRulesRespectPrecedenceAndHands(helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link ClientGestureChecks#fullTopFacesFallThroughToPlacement}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void fullTopFacesFallThroughToPlacement(GameTestHelper helper) {
        ClientGestureChecks.fullTopFacesFallThroughToPlacement(helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link ClientGestureChecks#airRulesAndDisabledModeCycling}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void airRulesAndDisabledModeCycling(GameTestHelper helper) {
        ClientGestureChecks.airRulesAndDisabledModeCycling(helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link ClientGestureChecks#gestureSyncSendsChangesAndResets}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void gestureSyncSendsChangesAndResets(GameTestHelper helper) {
        ClientGestureChecks.gestureSyncSendsChangesAndResets(helper);
    }

}
