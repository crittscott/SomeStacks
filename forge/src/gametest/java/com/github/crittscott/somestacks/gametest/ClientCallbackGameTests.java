package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.client.ClientGestures;
import com.github.crittscott.somestacks.client.ForgeClientEvents;
import com.github.crittscott.somestacks.client.ForgeClientSetup;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Forge development-client registration for {@link ClientCallbackChecks#run}. */
@Mod.EventBusSubscriber(
        modid = SomeStacksGameTests.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientCallbackGameTests {
    private ClientCallbackGameTests() {}

    /** Registers the local /ssclienttest smoke command in a client running the test mod. */
    // spotless:off
    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ssclienttest").executes(ctx ->
                ClientCallbackChecks.run(ForgeClientSetup.STACK_MODE_KEY.isDown(), (hand, hit) -> {
                    var click = new PlayerInteractEvent.RightClickBlock(
                            Minecraft.getInstance().player, hand, hit.getBlockPos(), hit);
                    ForgeClientEvents.onRightClickSync(click);
                    boolean canceled = ForgeClientEvents.onRightClick(click);
                    return ClientCallbackChecks.Result.withDenials(canceled
                            && click.getCancellationResult() == InteractionResult.SUCCESS,
                            click.getUseBlock() == Result.DENY, click.getUseItem() == Result.DENY);
                }, (be, tag, initial) -> {
                    var registries = be.getLevel().registryAccess();
                    var input = TagValueInput.create(ProblemReporter.DISCARDING, registries, tag);
                    if (initial) be.handleUpdateTag(input, registries);
                    else be.onDataPacket(null, input, registries);
                }, ClientCallbackGameTests::earlierCancellationSurvives)));
    }
    // spotless:on

    /**
     * Posts a main-hand click on the event bus behind a temporary default-priority listener that
     * cancels it, as a claim mod does. The gesture listener runs last, so the earlier result must
     * survive with nothing denied, while the separate first-running sync still sends gesture state
     * for the block-use packet that follows.
     */
    private static void earlierCancellationSurvives(BlockHitResult hit) {
        Predicate<PlayerInteractEvent.RightClickBlock> canceler =
                click -> {
                    click.setCancellationResult(InteractionResult.FAIL);
                    return true;
                };
        List<CustomPacketPayload> sent = new ArrayList<>();
        Consumer<CustomPacketPayload> previousSender = ClientGestures.setSender(sent::add);
        ClientGestures.resetSync();
        var registration = PlayerInteractEvent.RightClickBlock.BUS.addListener(canceler);
        try {
            var click =
                    new PlayerInteractEvent.RightClickBlock(
                            Minecraft.getInstance().player,
                            InteractionHand.MAIN_HAND,
                            hit.getBlockPos(),
                            hit);
            boolean canceled = PlayerInteractEvent.RightClickBlock.BUS.post(click);
            GameTestScaffold.check(
                    canceled && click.getCancellationResult() == InteractionResult.FAIL,
                    "Gesture listener replaced an earlier listener's result");
            GameTestScaffold.check(
                    click.getUseBlock() != Result.DENY && click.getUseItem() != Result.DENY,
                    "Gesture listener denied a click an earlier listener canceled");
            GameTestScaffold.checkEquals(1, sent.size(), "Gesture state sent for a canceled click");
        } finally {
            PlayerInteractEvent.RightClickBlock.BUS.removeListener(registration);
            ClientGestures.setSender(previousSender);
            ClientGestures.resetSync();
        }
    }
}
