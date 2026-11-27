package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.util.BlockType;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.BiFunction;

/** Development-client assertions for the loader callback's cancellation result. */
public final class ClientCallbackChecks {
    private ClientCallbackChecks() {}

    /**
     * Observable local callback outputs; none of these performs the server's world action. A loader
     * whose callback returns a single result, with no separate block-use and item-use denial,
     * reports only {@code consumed}; {@code reportsDenials} is then false and the denial fields are
     * not asserted.
     */
    public record Result(
            boolean consumed, boolean reportsDenials, boolean blockDenied, boolean itemDenied) {
        /** A callback that reports only whether it consumed the click. */
        public static Result consumedOnly(boolean consumed) {
            return new Result(consumed, false, false, false);
        }

        /** A callback that also reports separate block-use and item-use denial. */
        public static Result withDenials(
                boolean consumed, boolean blockDenied, boolean itemDenied) {
            return new Result(consumed, true, blockDenied, itemDenied);
        }
    }

    /**
     * To reproduce in-game: launch the loader's runClientGameTests client, join a world, aim at any
     * stack, release Shift and the Stack Modifier, and run /ssclienttest. It invokes the actual
     * loader callback for main/off hand and checks that it consumes the click and, where the loader
     * reports them separately, that it denies both block use and item use. It does not send a
     * vanilla use packet or extract contents. A translated success/failure message reports the
     * assertions; server GameTests cannot exercise this client-only boundary.
     */
    public static int run(
            boolean modifierDown, BiFunction<InteractionHand, BlockHitResult, Result> callback) {
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
            var main = client.player.getMainHandItem().copy();
            var off = client.player.getOffhandItem().copy();
            checkConsumedAndDenied(callback.apply(InteractionHand.MAIN_HAND, hit), "Main-hand");
            checkConsumedAndDenied(callback.apply(InteractionHand.OFF_HAND, hit), "Off-hand");
            GameTestScaffold.check(
                    ItemStack.matches(main, client.player.getMainHandItem()),
                    "Local callback changed main-hand contents");
            GameTestScaffold.check(
                    ItemStack.matches(off, client.player.getOffhandItem()),
                    "Local callback changed off-hand contents");
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

    private static void checkConsumedAndDenied(Result result, String hand) {
        GameTestScaffold.check(result.consumed(), hand + " callback did not consume the click");
        if (result.reportsDenials()) {
            GameTestScaffold.check(result.blockDenied(), hand + " callback allowed block use");
            GameTestScaffold.check(result.itemDenied(), hand + " callback allowed item use");
        }
    }
}
