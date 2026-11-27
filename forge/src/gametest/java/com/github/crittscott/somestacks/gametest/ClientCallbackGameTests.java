package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.client.ClientGestures;
import com.github.crittscott.somestacks.client.ForgeClientEvents;
import com.github.crittscott.somestacks.client.ForgeClientRendering;
import com.github.crittscott.somestacks.network.GestureStatePkt;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Forge development-client registration for {@link ClientCallbackChecks#run}. */
@Mod.EventBusSubscriber(modid = SomeStacksGameTests.MOD_ID, value = Dist.CLIENT)
public final class ClientCallbackGameTests {
    private ClientCallbackGameTests() {}

    /**
     * Registers the local /ssclienttest smoke command in a client running the test mod. Besides
     * {@link ClientCallbackChecks#run}, each hand's click is also posted through Forge's bus behind
     * a higher-priority listener that takes it. To reproduce in-game: with another mod that
     * consumes right-clicks on a block at normal priority, plain-click a stack holding a bucket.
     * That mod's action happens on both sides, the stack is untouched, and the next modifier click
     * still reaches the server with the current mode.
     */
    @SubscribeEvent
    // spotless:off
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ssclienttest").executes(ctx ->
                ClientCallbackChecks.run(ForgeClientRendering.STACK_MODE_KEY.isDown(), (hand, hit) -> {
                    var click = new PlayerInteractEvent.RightClickBlock(
                            Minecraft.getInstance().player, hand, hit.getBlockPos(), hit);
                    ForgeClientEvents.onRightClick(click);
                    checkEarlierListenerKeepsClick(hand, hit);
                    return ClientCallbackChecks.Result.withDenials(click.isCanceled()
                            && click.getCancellationResult() == InteractionResult.SUCCESS,
                            click.getUseBlock() == Event.Result.DENY, click.getUseItem() == Event.Result.DENY);
                })));
    }
    // spotless:on

    /**
     * Posts the click through Forge's bus behind a highest-priority listener that cancels it. The
     * gesture listener must leave that listener's outcome alone, yet still send the gesture state
     * the server needs to read the block-use packet vanilla sends anyway.
     */
    private static void checkEarlierListenerKeepsClick(InteractionHand hand, BlockHitResult hit) {
        var click =
                new PlayerInteractEvent.RightClickBlock(
                        Minecraft.getInstance().player, hand, hit.getBlockPos(), hit);
        Consumer<PlayerInteractEvent.RightClickBlock> earlier =
                event -> {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.FAIL);
                };
        List<GestureStatePkt> sent = new ArrayList<>();
        Consumer<GestureStatePkt> previousSender = ClientGestures.setSender(sent::add);
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.HIGHEST, false, PlayerInteractEvent.RightClickBlock.class, earlier);
        try {
            MinecraftForge.EVENT_BUS.post(click);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(earlier);
            ClientGestures.setSender(previousSender);
        }
        GameTestScaffold.check(
                click.isCanceled() && click.getCancellationResult() == InteractionResult.FAIL,
                "Gesture overrode an earlier listener's result");
        GameTestScaffold.check(
                click.getUseBlock() != Event.Result.DENY && click.getUseItem() != Event.Result.DENY,
                "Gesture denied use after an earlier listener took the click");
        GameTestScaffold.checkEquals(1, sent.size(), "Gesture state not synced behind a canceler");
    }
}
