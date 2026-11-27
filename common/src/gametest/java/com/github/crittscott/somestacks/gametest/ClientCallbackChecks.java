package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.util.BlockType;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Development-client assertions for the loader callback's cancellation result. */
public final class ClientCallbackChecks {
    private ClientCallbackChecks() {}

    /**
     * Observable local callback outputs; none of these performs the server's world action. A
     * loader whose callback reports separate block-use and item-use denial sets {@code
     * denialsReported}; one that returns a single result reports only {@code consumed}.
     */
    public record Result(
            boolean consumed, boolean denialsReported, boolean blockDenied, boolean itemDenied) {
        /** A callback that reports only whether it consumed the click. */
        public static Result consumedOnly(boolean consumed) {
            return new Result(consumed, false, false, false);
        }

        /** A callback that also reports whether it denied block use and item use. */
        public static Result withDenials(boolean consumed, boolean blockDenied, boolean itemDenied) {
            return new Result(consumed, true, blockDenied, itemDenied);
        }
    }

    /**
     * To reproduce in-game: launch the loader's runClientGameTests client, join a world, aim at any
     * stack, release Shift and the Stack Modifier, and run /ssclienttest. It invokes the actual
     * loader callback for main/off hand and checks consumption, plus block/item suppression where
     * the loader's callback reports it separately. It does
     * not send a vanilla use packet or extract contents. A translated success/failure message
     * reports the assertions; server GameTests cannot exercise this client-only boundary. Then
     * runs the loader's own checks against the same hit, and checks render-profile precedence and
     * invalidation through {@link RenderProfileChecks#run()}.
     */
    public static int run(
            boolean modifierDown,
            BiFunction<InteractionHand, BlockHitResult, Result> callback,
            Consumer<BlockHitResult> loaderChecks) {
        Minecraft client = Minecraft.getInstance();
        try {
            if (client.player == null
                    || client.level == null
                    || !(client.hitResult instanceof BlockHitResult hit)
                    || BlockType.of(client.level.getBlockState(hit.getBlockPos()).getBlock())
                            == null) {
                throw new IllegalStateException("Aim at a Some Stacks block in a loaded world");
            }
            if (modifierDown || client.player.isShiftKeyDown()) {
                throw new IllegalStateException("Release Shift and the Stack Modifier");
            }
            RenderProfileChecks.run();
            var main = client.player.getMainHandItem().copy();
            var off = client.player.getOffhandItem().copy();
            checkOutputs(true, callback.apply(InteractionHand.MAIN_HAND, hit), "Main-hand");
            checkOutputs(false, callback.apply(InteractionHand.OFF_HAND, hit), "Off-hand");
            GameTestScaffold.check(
                    ItemStack.matches(main, client.player.getMainHandItem()),
                    "Local callback changed main-hand contents");
            GameTestScaffold.check(
                    ItemStack.matches(off, client.player.getOffhandItem()),
                    "Local callback changed off-hand contents");
            loaderChecks.accept(hit);
            client.player.displayClientMessage(
                    Component.translatable("somestacks_gametest.message.client_callback_passed"),
                    false);
            return 1;
        } catch (RuntimeException failure) {
            if (client.player != null) {
                client.player.displayClientMessage(
                        Component.translatable(
                                "somestacks_gametest.message.client_callback_failed",
                                failure.getMessage()),
                        false);
            }
            return 0;
        }
    }

    /**
     * Checks that a callback consumed the click exactly when {@code expected}, and, where it reports
     * denials, that it denied block use and item use exactly then too.
     */
    private static void checkOutputs(boolean expected, Result result, String hand) {
        GameTestScaffold.checkEquals(expected, result.consumed(), hand + " callback consumption");
        if (result.denialsReported()) {
            GameTestScaffold.checkEquals(
                    expected, result.blockDenied(), hand + " callback block-use denial");
            GameTestScaffold.checkEquals(
                    expected, result.itemDenied(), hand + " callback item-use denial");
        }
    }
}
