package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.server.StackInteractionEvents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.function.Function;

/**
 * Forge delegates for loader-neutral interaction and validation checks.
 */
@GameTestHolder(SomeStacks.MODID)
public final class InteractionGameTests {
    private InteractionGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void malformedGestureStateFailsDuringDecoding(GameTestHelper helper) {
        InteractionChecks.malformedGestureStateFailsDuringDecoding(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void gestureStateTracksModeAndModifier(GameTestHelper helper) {
        InteractionChecks.gestureStateTracksModeAndModifier(
                helper, playerFactory(helper));
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void interactionReadsOnlyTheMainHand(GameTestHelper helper) {
        InteractionChecks.interactionReadsOnlyTheMainHand(helper, playerFactory(helper));
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void rotationValidatesHeldItemAndBlockType(GameTestHelper helper) {
        InteractionChecks.rotationValidatesHeldItemAndBlockType(
                helper, playerFactory(helper));
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void sneakingRotationsReachTheLoaderHook(GameTestHelper helper) {
        InteractionChecks.sneakingRotationsReachTheLoaderHook(
                helper, playerFactory(helper), (player, hit) -> {
                    var event = new PlayerInteractEvent.RightClickBlock(
                            player, InteractionChecks.HAND, hit.getBlockPos(), hit);
                    StackInteractionEvents.onRightClickBlock(event);
                    return event.isCanceled();
                });
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void vanillaInteractionReachesDepositExtractAndPlacement(
            GameTestHelper helper) {
        InteractionChecks.vanillaInteractionReachesDepositExtractAndPlacement(
                helper, playerFactory(helper));
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void itemRotationTargetsOnlyAnOccupiedCell(GameTestHelper helper) {
        InteractionChecks.itemRotationTargetsOnlyAnOccupiedCell(
                helper, playerFactory(helper));
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void permanenceRequiresEmptyHandAndHonorsProtection(
            GameTestHelper helper) {
        InteractionChecks.permanenceRequiresEmptyHandAndHonorsProtection(
                helper, playerFactory(helper));
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = 20)
    public static void rotationSoundThrottleSuppressesSameTickAndClears(
            GameTestHelper helper) {
        InteractionChecks.rotationSoundThrottleSuppressesSameTickAndClears(
                helper, playerFactory(helper));
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void extractionRefusesIncompatibleOrFullHands(GameTestHelper helper) {
        InteractionChecks.extractionRefusesIncompatibleOrFullHands(
                helper, playerFactory(helper));
    }

    /** The fake player is shared per level, so every test sets the hand it means to test with. */
    private static Function<ItemStack, ServerPlayer> playerFactory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return mainHand -> {
            ServerPlayer player = GameTestSupport.fakePlayer(level);
            player.setItemInHand(InteractionChecks.HAND, mainHand);
            return player;
        };
    }
}
