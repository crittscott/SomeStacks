package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.client.ClientGestures;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;


/**
 * Fabric delegates for loader-neutral interaction and validation checks.
 */
public final class InteractionGameTests implements FabricGameTest {
    private static final String TEMPLATE = FabricGameTestSupport.TEMPLATE;

    /** See {@link InteractionChecks#malformedGestureStateFailsDuringDecoding}. */
    @GameTest(template = TEMPLATE)
    public void malformedGestureStateFailsDuringDecoding(GameTestHelper helper) {
        InteractionChecks.malformedGestureStateFailsDuringDecoding(helper);
    }

    /** See {@link InteractionChecks#gestureStateTracksModeAndModifier}. */
    @GameTest(template = TEMPLATE)
    public void gestureStateTracksModeAndModifier(GameTestHelper helper) {
        InteractionChecks.gestureStateTracksModeAndModifier(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#interactionReadsOnlyTheMainHand}. */
    @GameTest(template = TEMPLATE)
    public void interactionReadsOnlyTheMainHand(GameTestHelper helper) {
        InteractionChecks.interactionReadsOnlyTheMainHand(helper, FabricGameTestSupport.playerFactory(helper));
    }

    /**
     * To reproduce in-game: leave the main hand empty, hold an item in the offhand, and use the
     * empty-hand air gesture; the SomeStacks interaction mode still changes.
     */
    @GameTest(template = TEMPLATE)
    public void emptyMainHandAirClickIgnoresOccupiedOffhand(GameTestHelper helper) {
        ServerPlayer player = FakePlayer.get(helper.getLevel());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        BlockHitResult miss = BlockHitResult.miss(Vec3.ZERO, Direction.NORTH, BlockPos.ZERO);

        GameTestScaffold.check(ClientGestures.isEmptyMainHandAirClick(player, miss),
                "Occupied offhand blocked empty-main-hand mode cycling");
        helper.succeed();
    }

    /** See {@link InteractionChecks#rotationValidatesHeldItemAndBlockType}. */
    @GameTest(template = TEMPLATE)
    public void rotationValidatesHeldItemAndBlockType(GameTestHelper helper) {
        InteractionChecks.rotationValidatesHeldItemAndBlockType(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#sneakingRotationsReachTheLoaderHook}. */
    @GameTest(template = TEMPLATE)
    public void sneakingRotationsReachTheLoaderHook(GameTestHelper helper) {
        InteractionChecks.sneakingRotationsReachTheLoaderHook(
                helper, FabricGameTestSupport.playerFactory(helper), (player, hit) ->
                        UseBlockCallback.EVENT.invoker().interact(
                                player, player.level(), InteractionChecks.HAND, hit)
                                == InteractionResult.SUCCESS);
    }

    /** See {@link InteractionChecks#vanillaInteractionReachesDepositExtractAndPlacement}. */
    @GameTest(template = TEMPLATE)
    public void vanillaInteractionReachesDepositExtractAndPlacement(GameTestHelper helper) {
        InteractionChecks.vanillaInteractionReachesDepositExtractAndPlacement(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#itemRotationTargetsOnlyAnOccupiedCell}. */
    @GameTest(template = TEMPLATE)
    public void itemRotationTargetsOnlyAnOccupiedCell(GameTestHelper helper) {
        InteractionChecks.itemRotationTargetsOnlyAnOccupiedCell(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#permanenceRequiresEmptyHandAndHonorsProtection}. */
    @GameTest(template = TEMPLATE)
    public void permanenceRequiresEmptyHandAndHonorsProtection(GameTestHelper helper) {
        InteractionChecks.permanenceRequiresEmptyHandAndHonorsProtection(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#rotationSoundThrottleSuppressesSameTickAndClears}. */
    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void rotationSoundThrottleSuppressesSameTickAndClears(GameTestHelper helper) {
        InteractionChecks.rotationSoundThrottleSuppressesSameTickAndClears(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#playerMutationEmitsBlockChange}. */
    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void playerMutationEmitsBlockChange(GameTestHelper helper) {
        InteractionChecks.playerMutationEmitsBlockChange(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#extractionRefusesIncompatibleOrFullHands}. */
    @GameTest(template = TEMPLATE)
    public void extractionRefusesIncompatibleOrFullHands(GameTestHelper helper) {
        InteractionChecks.extractionRefusesIncompatibleOrFullHands(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#fullTopFacePlacesTheSelectedStackType}. */
    @GameTest(template = TEMPLATE)
    public void fullTopFacePlacesTheSelectedStackType(GameTestHelper helper) {
        InteractionChecks.fullTopFacePlacesTheSelectedStackType(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link InteractionChecks#everyStackTypeBlocksPistons}. */
    @GameTest(template = TEMPLATE)
    public void everyStackTypeBlocksPistons(GameTestHelper helper) {
        InteractionChecks.everyStackTypeBlocksPistons(helper);
    }

}
