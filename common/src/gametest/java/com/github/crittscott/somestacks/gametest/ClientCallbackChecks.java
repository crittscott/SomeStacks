package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.util.BlockType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.BiFunction;

/** Development-client assertions for the loader callback's cancellation result. */
public final class ClientCallbackChecks {
    private ClientCallbackChecks() {}

    /** Observable local callback outputs; none of these performs the server's world action. */
    public record Result(boolean consumed, boolean blockDenied, boolean itemDenied) {}

    /**
     * To reproduce in-game: launch the loader's runClientGameTests client, join a world, aim at any
     * stack, release Shift and the Stack Modifier, and run /ssclienttest. It invokes the actual
     * loader callback for main/off hand and checks consumption plus block/item suppression. It
     * does not send a vanilla use packet or extract contents. A translated success/failure message
     * reports the assertions; server GameTests cannot exercise this client-only boundary.
     */
    public static int run(boolean modifierDown, BiFunction<InteractionHand, BlockHitResult, Result> callback) {
        Minecraft client = Minecraft.getInstance();
        try {
            if (client.player == null || client.level == null
                    || !(client.hitResult instanceof BlockHitResult hit)
                    || BlockType.of(client.level.getBlockState(hit.getBlockPos()).getBlock()) == null) {
                throw new IllegalStateException("Aim at a Some Stacks block in a loaded world");
            }
            if (modifierDown || client.player.isShiftKeyDown()) {
                throw new IllegalStateException("Release Shift and the Stack Modifier");
            }
            var main = client.player.getMainHandItem().copy();
            var off = client.player.getOffhandItem().copy();
            GameTestScaffold.checkEquals(new Result(true, true, true),
                    callback.apply(InteractionHand.MAIN_HAND, hit), "Main-hand callback outputs");
            GameTestScaffold.checkEquals(new Result(false, false, false),
                    callback.apply(InteractionHand.OFF_HAND, hit), "Off-hand callback outputs");
            GameTestScaffold.check(net.minecraft.world.item.ItemStack.matches(main, client.player.getMainHandItem()),
                    "Local callback changed main-hand contents");
            GameTestScaffold.check(net.minecraft.world.item.ItemStack.matches(off, client.player.getOffhandItem()),
                    "Local callback changed off-hand contents");
            client.player.displayClientMessage(Component.translatable("somestacks_gametest.message.client_callback_passed"), false);
            return 1;
        } catch (RuntimeException failure) {
            if (client.player != null) {
                client.player.displayClientMessage(Component.translatable(
                        "somestacks_gametest.message.client_callback_failed", failure.getMessage()), false);
            }
            return 0;
        }
    }
}
