package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.client.ClientGestures;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Fabric delegates for loader-neutral interaction and validation checks. */
public final class InteractionGameTests {
    private static final String TEMPLATE = FabricGameTestSupport.TEMPLATE;

    /** See {@link InteractionChecks#fullMaxHeightDepositExplainsRefusal}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void fullMaxHeightDepositExplainsRefusal(GameTestHelper helper) {
        InteractionChecks.fullMaxHeightDepositExplainsRefusal(helper);
    }

    /** See {@link InteractionChecks#adventurePlacementAndGrowthShareTheBuildCheck}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void adventurePlacementAndGrowthShareTheBuildCheck(GameTestHelper helper) {
        InteractionChecks.adventurePlacementAndGrowthShareTheBuildCheck(helper);
    }

    /** See {@link InteractionChecks#barGestureDepositRefusesNonIngots}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void barGestureDepositRefusesNonIngots(GameTestHelper helper) {
        InteractionChecks.barGestureDepositRefusesNonIngots(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#malformedGestureStateFailsDuringDecoding}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void malformedGestureStateFailsDuringDecoding(GameTestHelper helper) {
        InteractionChecks.malformedGestureStateFailsDuringDecoding(helper);
    }

    /** See {@link InteractionChecks#gestureStateTracksModeAndModifier}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void gestureStateTracksModeAndModifier(GameTestHelper helper) {
        InteractionChecks.gestureStateTracksModeAndModifier(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#interactionReadsOnlyTheMainHand}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void interactionReadsOnlyTheMainHand(GameTestHelper helper) {
        InteractionChecks.interactionReadsOnlyTheMainHand(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /**
     * To reproduce in-game: leave the main hand empty, hold an item in the offhand, and use the
     * empty-hand air gesture; the SomeStacks interaction mode still changes.
     */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void emptyMainHandAirClickIgnoresOccupiedOffhand(GameTestHelper helper) {
        ServerPlayer player = FakePlayer.get(helper.getLevel());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        BlockHitResult miss = BlockHitResult.miss(Vec3.ZERO, Direction.NORTH, BlockPos.ZERO);

        GameTestScaffold.check(
                ClientGestures.isEmptyMainHandAirClick(player, miss),
                "Occupied offhand blocked empty-main-hand mode cycling");
        helper.succeed();
    }

    /** See {@link InteractionChecks#rotationValidatesHeldItemAndBlockType}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void rotationValidatesHeldItemAndBlockType(GameTestHelper helper) {
        InteractionChecks.rotationValidatesHeldItemAndBlockType(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#vanillaInteractionReachesDepositExtractAndPlacement}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void vanillaInteractionReachesDepositExtractAndPlacement(GameTestHelper helper) {
        InteractionChecks.vanillaInteractionReachesDepositExtractAndPlacement(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#itemRotationTargetsOnlyAnOccupiedCell}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void itemRotationTargetsOnlyAnOccupiedCell(GameTestHelper helper) {
        InteractionChecks.itemRotationTargetsOnlyAnOccupiedCell(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#permanenceRequiresEmptyHandAndHonorsProtection}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void permanenceRequiresEmptyHandAndHonorsProtection(GameTestHelper helper) {
        InteractionChecks.permanenceRequiresEmptyHandAndHonorsProtection(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#rotationSoundThrottleSuppressesSameTickAndClears}. */
    @GameTest(structure = TEMPLATE, maxTicks = 20)
    public void rotationSoundThrottleSuppressesSameTickAndClears(GameTestHelper helper) {
        InteractionChecks.rotationSoundThrottleSuppressesSameTickAndClears(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#playerMutationEmitsBlockChange}. */
    @GameTest(structure = TEMPLATE, maxTicks = 20)
    public void playerMutationEmitsBlockChange(GameTestHelper helper) {
        InteractionChecks.playerMutationEmitsBlockChange(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#extractionRefusesIncompatibleOrFullHands}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void extractionRefusesIncompatibleOrFullHands(GameTestHelper helper) {
        InteractionChecks.extractionRefusesIncompatibleOrFullHands(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#fullTopFacePlacesTheSelectedStackType}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void fullTopFacePlacesTheSelectedStackType(GameTestHelper helper) {
        InteractionChecks.fullTopFacePlacesTheSelectedStackType(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#everyStackTypeBlocksPistons}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void everyStackTypeBlocksPistons(GameTestHelper helper) {
        InteractionChecks.everyStackTypeBlocksPistons(helper);
    }

    /** See {@link InteractionChecks#extractionEventsFollowBlockSurvival}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void extractionEventsFollowBlockSurvival(GameTestHelper helper) {
        InteractionChecks.extractionEventsFollowBlockSurvival(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link SynchronizationChecks#configSnapshotsRoundTripAndPublishAtomically}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void configSnapshotsRoundTripAndPublishAtomically(GameTestHelper helper) {
        SynchronizationChecks.configSnapshotsRoundTripAndPublishAtomically(helper);
    }

    /** See {@link SynchronizationChecks#configDecoderRejectsMalformedCountsAndNamespaces}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void configDecoderRejectsMalformedCountsAndNamespaces(GameTestHelper helper) {
        SynchronizationChecks.configDecoderRejectsMalformedCountsAndNamespaces(helper);
    }

    /** See {@link ClientGestureChecks#blockRulesRespectPrecedenceAndHands}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void blockRulesRespectPrecedenceAndHands(GameTestHelper helper) {
        ClientGestureChecks.blockRulesRespectPrecedenceAndHands(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ClientGestureChecks#topFaceModifiedClicksAreConsumed}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void topFaceModifiedClicksAreConsumed(GameTestHelper helper) {
        ClientGestureChecks.topFaceModifiedClicksAreConsumed(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ClientGestureChecks#airRulesAndDisabledModeCycling}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void airRulesAndDisabledModeCycling(GameTestHelper helper) {
        ClientGestureChecks.airRulesAndDisabledModeCycling(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ClientGestureChecks#gestureSyncSendsChangesAndResets}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void gestureSyncSendsChangesAndResets(GameTestHelper helper) {
        ClientGestureChecks.gestureSyncSendsChangesAndResets(helper);
    }

    /** See {@link InteractionChecks#disabledTypesRefuseNewBlocksButKeepExistingOnes}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void disabledTypesRefuseNewBlocksButKeepExistingOnes(GameTestHelper helper) {
        InteractionChecks.disabledTypesRefuseNewBlocksButKeepExistingOnes(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link InteractionChecks#maximumHeightRefusesGrowthAndHidesHeadroom}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void maximumHeightRefusesGrowthAndHidesHeadroom(GameTestHelper helper) {
        InteractionChecks.maximumHeightRefusesGrowthAndHidesHeadroom(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link InteractionChecks#deniedItemsRefuseDepositsAndOnlyModDenialStopsAutomation}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void deniedItemsRefuseDepositsAndOnlyModDenialStopsAutomation(GameTestHelper helper) {
        InteractionChecks.deniedItemsRefuseDepositsAndOnlyModDenialStopsAutomation(
                helper, FabricGameTestSupport::automation);
    }
}
