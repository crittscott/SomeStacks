package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.client.ClientEvents;
import com.github.crittscott.somestacks.client.ClientGestures;
import com.github.crittscott.somestacks.client.ClientSetup;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** NeoForge development-client registration for {@link ClientCallbackChecks#run}. */
@EventBusSubscriber(modid = SomeStacksGameTests.MOD_ID, value = Dist.CLIENT)
public final class ClientCallbackGameTests {
    private ClientCallbackGameTests() {}

    /** Registers the local /ssclienttest smoke command in a client running the test mod. */
    // spotless:off
    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ssclienttest").executes(ctx ->
                ClientCallbackChecks.run(ClientSetup.STACK_MODE_KEY.isDown(), (hand, hit) -> {
                    var click = new PlayerInteractEvent.RightClickBlock(
                            Minecraft.getInstance().player, hand, hit.getBlockPos(), hit);
                    ClientEvents.onRightClick(click);
                    return ClientCallbackChecks.Result.withDenials(click.isCanceled()
                            && click.getCancellationResult() == InteractionResult.SUCCESS,
                            click.getUseBlock() == TriState.FALSE, click.getUseItem() == TriState.FALSE);
                }, ClientCallbackGameTests::earlierCancellationSurvives)));
    }
    // spotless:on

    /**
     * Posts a main-hand click on the event bus behind a temporary highest-priority listener that
     * cancels it. The gesture listener runs last, so the earlier result must survive with nothing
     * denied, while gesture state is still sent for the block-use packet that follows.
     */
    private static void earlierCancellationSurvives(BlockHitResult hit) {
        Consumer<PlayerInteractEvent.RightClickBlock> canceler =
                click -> {
                    click.setCanceled(true);
                    click.setCancellationResult(InteractionResult.FAIL);
                };
        List<CustomPacketPayload> sent = new ArrayList<>();
        Consumer<CustomPacketPayload> previousSender = ClientGestures.setSender(sent::add);
        ClientGestures.resetSync();
        NeoForge.EVENT_BUS.addListener(
                EventPriority.HIGHEST, false, PlayerInteractEvent.RightClickBlock.class, canceler);
        try {
            var click =
                    new PlayerInteractEvent.RightClickBlock(
                            Minecraft.getInstance().player,
                            InteractionHand.MAIN_HAND,
                            hit.getBlockPos(),
                            hit);
            NeoForge.EVENT_BUS.post(click);
            GameTestScaffold.check(
                    click.isCanceled() && click.getCancellationResult() == InteractionResult.FAIL,
                    "Gesture listener replaced an earlier listener's result");
            GameTestScaffold.check(
                    click.getUseBlock() != TriState.FALSE
                            && click.getUseItem() != TriState.FALSE,
                    "Gesture listener denied a click an earlier listener canceled");
            GameTestScaffold.checkEquals(1, sent.size(), "Gesture state sent for a canceled click");
        } finally {
            NeoForge.EVENT_BUS.unregister(canceler);
            ClientGestures.setSender(previousSender);
            ClientGestures.resetSync();
        }
    }
}
