package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacksNeoForge;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.function.Function;

/**
 * NeoForge delegates for loader-neutral packet entry-point, validation, and pacing checks.
 */
@GameTestHolder(SomeStacksNeoForge.MODID)
@PrefixGameTestTemplate(false)
public final class PacketBoundaryGameTests {
    private PacketBoundaryGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void reachCheckAcceptsNearTargetAndRejectsFarTarget(GameTestHelper helper) {
        PacketBoundaryChecks.reachCheckAcceptsNearTargetAndRejectsFarTarget(
                helper, playerFactory(helper));
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void gestureChecksReadOnlyTheMainHand(GameTestHelper helper) {
        PacketBoundaryChecks.gestureChecksReadOnlyTheMainHand(helper, playerFactory(helper));
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void rotateBlockHandlerValidatesTheHeldItemAndBlockType(GameTestHelper helper) {
        PacketBoundaryChecks.rotateBlockHandlerValidatesTheHeldItemAndBlockType(
                helper, playerFactory(helper));
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void mutationPacketEntryPointsReachTheirValidatedOperations(
            GameTestHelper helper) {
        PacketBoundaryChecks.mutationPacketEntryPointsReachTheirValidatedOperations(
                helper, playerFactory(helper));
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void rotateItemHandlerLeavesEmptyCellsUnoriented(GameTestHelper helper) {
        PacketBoundaryChecks.rotateItemHandlerLeavesEmptyCellsUnoriented(
                helper, playerFactory(helper));
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void togglePermanentHandlerRequiresAnEmptyHandAndStoragePile(
            GameTestHelper helper) {
        PacketBoundaryChecks.togglePermanentHandlerRequiresAnEmptyHandAndStoragePile(
                helper, playerFactory(helper));
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = 20)
    public static void gestureThrottleEnforcesTickAndRotationSoundIntervals(
            GameTestHelper helper) {
        PacketBoundaryChecks.gestureThrottleEnforcesTickAndRotationSoundIntervals(
                helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void extractIgnoresAnEmptyCell(GameTestHelper helper) {
        PacketBoundaryChecks.extractIgnoresAnEmptyCell(helper, playerFactory(helper));
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void extractRefusesAHandHoldingSomethingElse(GameTestHelper helper) {
        PacketBoundaryChecks.extractRefusesAHandHoldingSomethingElse(helper, playerFactory(helper));
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void extractRefusesAFullHandOfTheSameItem(GameTestHelper helper) {
        PacketBoundaryChecks.extractRefusesAFullHandOfTheSameItem(helper, playerFactory(helper));
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void refusedBarExtractionLeavesTheBlockStanding(GameTestHelper helper) {
        PacketBoundaryChecks.refusedBarExtractionLeavesTheBlockStanding(
                helper, playerFactory(helper));
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void extractIgnoresAnIndexOutsideTheBlocksOwnSlots(GameTestHelper helper) {
        PacketBoundaryChecks.extractIgnoresAnIndexOutsideTheBlocksOwnSlots(
                helper, playerFactory(helper));
    }

    /** The fake player is shared per level, so every test sets the hand it means to test with. */
    private static Function<ItemStack, ServerPlayer> playerFactory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return mainHand -> {
            ServerPlayer player = GameTestSupport.fakePlayer(level);
            player.setItemInHand(PacketBoundaryChecks.HAND, mainHand);
            return player;
        };
    }
}
