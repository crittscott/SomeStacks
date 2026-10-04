package com.github.crittscott.somestacks.gametest;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

/**
 * Fabric delegates for loader-neutral interaction and validation checks.
 */
public final class InteractionGameTests implements FabricGameTest {
    private static final String TEMPLATE = FabricGameTestSupport.TEMPLATE;

    @GameTest(template = TEMPLATE)
    public void malformedGestureStateFailsDuringDecoding(GameTestHelper helper) {
        InteractionChecks.malformedGestureStateFailsDuringDecoding(helper);
    }

    @GameTest(template = TEMPLATE)
    public void gestureStateTracksModeAndModifier(GameTestHelper helper) {
        InteractionChecks.gestureStateTracksModeAndModifier(
                helper, playerFactory(helper));
    }

    @GameTest(template = TEMPLATE)
    public void interactionReadsOnlyTheMainHand(GameTestHelper helper) {
        InteractionChecks.interactionReadsOnlyTheMainHand(helper, playerFactory(helper));
    }

    @GameTest(template = TEMPLATE)
    public void rotationValidatesHeldItemAndBlockType(GameTestHelper helper) {
        InteractionChecks.rotationValidatesHeldItemAndBlockType(
                helper, playerFactory(helper));
    }

    @GameTest(template = TEMPLATE)
    public void sneakingRotationsReachTheLoaderHook(GameTestHelper helper) {
        InteractionChecks.sneakingRotationsReachTheLoaderHook(
                helper, playerFactory(helper), (player, hit) ->
                        UseBlockCallback.EVENT.invoker().interact(
                                player, player.level(), InteractionChecks.HAND, hit)
                                == InteractionResult.SUCCESS);
    }

    @GameTest(template = TEMPLATE)
    public void vanillaInteractionReachesDepositExtractAndPlacement(GameTestHelper helper) {
        InteractionChecks.vanillaInteractionReachesDepositExtractAndPlacement(
                helper, playerFactory(helper));
    }

    @GameTest(template = TEMPLATE)
    public void itemRotationTargetsOnlyAnOccupiedCell(GameTestHelper helper) {
        InteractionChecks.itemRotationTargetsOnlyAnOccupiedCell(
                helper, playerFactory(helper));
    }

    @GameTest(template = TEMPLATE)
    public void permanenceRequiresEmptyHandAndHonorsProtection(GameTestHelper helper) {
        InteractionChecks.permanenceRequiresEmptyHandAndHonorsProtection(
                helper, playerFactory(helper));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void rotationSoundThrottleSuppressesSameTickAndClears(GameTestHelper helper) {
        InteractionChecks.rotationSoundThrottleSuppressesSameTickAndClears(
                helper, playerFactory(helper));
    }

    @GameTest(template = TEMPLATE)
    public void extractionRefusesIncompatibleOrFullHands(GameTestHelper helper) {
        InteractionChecks.extractionRefusesIncompatibleOrFullHands(
                helper, playerFactory(helper));
    }

    /** Each test needs its own mock player so the hand it means to test with starts empty. */
    private static Function<ItemStack, ServerPlayer> playerFactory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return mainHand -> {
            ServerPlayer player = FakePlayer.get(level);
            player.setItemInHand(InteractionChecks.HAND, mainHand);
            return player;
        };
    }
}
