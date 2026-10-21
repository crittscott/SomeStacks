package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.server.ForgeStackInteractionEvents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;


/**
 * Forge delegates for loader-neutral interaction and validation checks.
 */
public final class InteractionGameTests {
    public InteractionGameTests() {}

    /** See {@link InteractionChecks#playerPlacementAwardsCriterionAfterDeposit}. */
    public static void playerPlacementAwardsCriterionAfterDeposit(GameTestHelper helper) {
        InteractionChecks.playerPlacementAwardsCriterionAfterDeposit(helper);
    }

    /** See {@link InteractionChecks#malformedGestureStateFailsDuringDecoding}. */
    public static void malformedGestureStateFailsDuringDecoding(GameTestHelper helper) {
        InteractionChecks.malformedGestureStateFailsDuringDecoding(helper);
    }

    /** See {@link InteractionChecks#gestureStateTracksModeAndModifier}. */
    public static void gestureStateTracksModeAndModifier(GameTestHelper helper) {
        InteractionChecks.gestureStateTracksModeAndModifier(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#interactionReadsOnlyTheMainHand}. */
    public static void interactionReadsOnlyTheMainHand(GameTestHelper helper) {
        InteractionChecks.interactionReadsOnlyTheMainHand(helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#rotationValidatesHeldItemAndBlockType}. */
    public static void rotationValidatesHeldItemAndBlockType(GameTestHelper helper) {
        InteractionChecks.rotationValidatesHeldItemAndBlockType(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#sneakingRotationsReachTheLoaderHook}. */
    public static void sneakingRotationsReachTheLoaderHook(GameTestHelper helper) {
        InteractionChecks.sneakingRotationsReachTheLoaderHook(
                helper, GameTestSupport.playerFactory(helper), (player, hit) -> {
                    var event = new PlayerInteractEvent.RightClickBlock(
                            player, InteractionChecks.HAND, hit.getBlockPos(), hit);
                    return ForgeStackInteractionEvents.onRightClickBlock(event);
                });
    }

    /** See {@link InteractionChecks#vanillaInteractionReachesDepositExtractAndPlacement}. */
    public static void vanillaInteractionReachesDepositExtractAndPlacement(
            GameTestHelper helper) {
        InteractionChecks.vanillaInteractionReachesDepositExtractAndPlacement(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#itemRotationTargetsOnlyAnOccupiedCell}. */
    public static void itemRotationTargetsOnlyAnOccupiedCell(GameTestHelper helper) {
        InteractionChecks.itemRotationTargetsOnlyAnOccupiedCell(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#permanenceRequiresEmptyHandAndHonorsProtection}. */
    public static void permanenceRequiresEmptyHandAndHonorsProtection(
            GameTestHelper helper) {
        InteractionChecks.permanenceRequiresEmptyHandAndHonorsProtection(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#rotationSoundThrottleSuppressesSameTickAndClears}. */
    public static void rotationSoundThrottleSuppressesSameTickAndClears(
            GameTestHelper helper) {
        InteractionChecks.rotationSoundThrottleSuppressesSameTickAndClears(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#playerMutationEmitsBlockChange}. */
    public static void playerMutationEmitsBlockChange(GameTestHelper helper) {
        InteractionChecks.playerMutationEmitsBlockChange(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#extractionRefusesIncompatibleOrFullHands}. */
    public static void extractionRefusesIncompatibleOrFullHands(GameTestHelper helper) {
        InteractionChecks.extractionRefusesIncompatibleOrFullHands(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#fullTopFacePlacesTheSelectedStackType}. */
    public static void fullTopFacePlacesTheSelectedStackType(GameTestHelper helper) {
        InteractionChecks.fullTopFacePlacesTheSelectedStackType(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#everyStackTypeBlocksPistons}. */
    public static void everyStackTypeBlocksPistons(GameTestHelper helper) {
        InteractionChecks.everyStackTypeBlocksPistons(helper);
    }

    /** See {@link InteractionChecks#extractionEventsFollowBlockSurvival}. */
    public static void extractionEventsFollowBlockSurvival(GameTestHelper helper) {
        InteractionChecks.extractionEventsFollowBlockSurvival(helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link SynchronizationChecks#configSnapshotsRoundTripAndPublishAtomically}. */
    public static void configSnapshotsRoundTripAndPublishAtomically(GameTestHelper helper) {
        SynchronizationChecks.configSnapshotsRoundTripAndPublishAtomically(helper);
    }

    /** See {@link SynchronizationChecks#configDecoderRejectsMalformedCountsAndNamespaces}. */
    public static void configDecoderRejectsMalformedCountsAndNamespaces(GameTestHelper helper) {
        SynchronizationChecks.configDecoderRejectsMalformedCountsAndNamespaces(helper);
    }

    /** See {@link ClientGestureChecks#blockRulesRespectPrecedenceAndHands}. */
    public static void blockRulesRespectPrecedenceAndHands(GameTestHelper helper) {
        ClientGestureChecks.blockRulesRespectPrecedenceAndHands(helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link ClientGestureChecks#fullTopFacesFallThroughToPlacement}. */
    public static void fullTopFacesFallThroughToPlacement(GameTestHelper helper) {
        ClientGestureChecks.fullTopFacesFallThroughToPlacement(helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link ClientGestureChecks#airRulesAndDisabledModeCycling}. */
    public static void airRulesAndDisabledModeCycling(GameTestHelper helper) {
        ClientGestureChecks.airRulesAndDisabledModeCycling(helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link ClientGestureChecks#gestureSyncSendsChangesAndResets}. */
    public static void gestureSyncSendsChangesAndResets(GameTestHelper helper) {
        ClientGestureChecks.gestureSyncSendsChangesAndResets(helper);
    }

}
