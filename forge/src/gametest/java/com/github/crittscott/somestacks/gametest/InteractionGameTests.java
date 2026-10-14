package com.github.crittscott.somestacks.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.util.function.Function;
import com.github.crittscott.somestacks.SomeStacks;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.InteractionResult;

/** Loader wrappers for the reproduction scenarios documented in InteractionChecks. */
@GameTestHolder(SomeStacks.MODID)
@PrefixGameTestTemplate(false)
public final class InteractionGameTests {
    /**
     * Loader entry point for {@link InteractionChecks#depositExtractAndPlacement}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void depositExtractAndPlacement(GameTestHelper helper) {
        InteractionChecks.depositExtractAndPlacement(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#extractionUsesServerRay}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void extractionUsesServerRay(GameTestHelper helper) {
        InteractionChecks.extractionUsesServerRay(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#rotationUsesServerRayAndConsumesMiss}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void rotationUsesServerRayAndConsumesMiss(GameTestHelper helper) {
        InteractionChecks.rotationUsesServerRayAndConsumesMiss(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link
     * InteractionChecks#incompatibleAndFullHandsLeaveAllTypesUntouched}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void incompatibleAndFullHandsLeaveAllTypesUntouched(GameTestHelper helper) {
        InteractionChecks.incompatibleAndFullHandsLeaveAllTypesUntouched(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#extractionMissConsumesWithoutMutation}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void extractionMissConsumesWithoutMutation(GameTestHelper helper) {
        InteractionChecks.extractionMissConsumesWithoutMutation(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#handsSneakingAndRotationPrecedence}; see that
     * method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void handsSneakingAndRotationPrecedence(GameTestHelper helper) {
        InteractionChecks.handsSneakingAndRotationPrecedence(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#permanenceAndSameTickPacing}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void permanenceAndSameTickPacing(GameTestHelper helper) {
        InteractionChecks.permanenceAndSameTickPacing(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#fullTopFaceUsesSelectedType}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void fullTopFaceUsesSelectedType(GameTestHelper helper) {
        InteractionChecks.fullTopFaceUsesSelectedType(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#firstDepositFailureLeavesNoBlock}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void firstDepositFailureLeavesNoBlock(GameTestHelper helper) {
        InteractionChecks.firstDepositFailureLeavesNoBlock(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link InteractionChecks#stateAndLogout}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void stateAndLogout(GameTestHelper helper) {
        InteractionChecks.stateAndLogout(helper, factory(helper), InteractionGameTests::click);
    }

    /**
     * Loader entry point for {@link ClientGestureChecks#blockRulesRespectPrecedenceAndHands}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void clientBlockRulesRespectPrecedenceAndHands(GameTestHelper helper) {
        ClientGestureChecks.blockRulesRespectPrecedenceAndHands(helper, factory(helper));
    }

    /**
     * Loader entry point for {@link ClientGestureChecks#fullTopFacesFallThroughToPlacement}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void clientFullTopFacesFallThroughToPlacement(GameTestHelper helper) {
        ClientGestureChecks.fullTopFacesFallThroughToPlacement(helper, factory(helper));
    }

    /**
     * Loader entry point for {@link ClientGestureChecks#gestureSyncSendsChangesAndResets}; see that
     * method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void clientGestureSyncSendsChangesAndResets(GameTestHelper helper) {
        ClientGestureChecks.gestureSyncSendsChangesAndResets(helper);
    }

    private static boolean click(ServerPlayer player, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        var event = ForgeHooks.onRightClickBlock(player, hand, hit.getBlockPos(), hit);
        return event.isCanceled() && event.getCancellationResult() == InteractionResult.SUCCESS;
    }

    private static Function<ItemStack, ServerPlayer> factory(GameTestHelper helper) {
        return held -> {
            ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            return player;
        };
    }
}
