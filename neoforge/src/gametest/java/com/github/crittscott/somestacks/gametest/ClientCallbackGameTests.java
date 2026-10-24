package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.client.ClientEvents;
import com.github.crittscott.somestacks.client.KeyMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** NeoForge development-client registration for {@link ClientCallbackChecks#run}. */
@EventBusSubscriber(modid = SomeStacksGameTests.MOD_ID, value = Dist.CLIENT)
public final class ClientCallbackGameTests {
    private ClientCallbackGameTests() {}

    /** Registers the local /ssclienttest smoke command in a client running the test mod. */
    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ssclienttest").executes(ctx ->
                ClientCallbackChecks.run(KeyMappings.STACK_MODE_KEY.isDown(), (hand, hit) -> {
                    var click = new PlayerInteractEvent.RightClickBlock(
                            Minecraft.getInstance().player, hand, hit.getBlockPos(), hit);
                    ClientEvents.onRightClick(click);
                    return new ClientCallbackChecks.Result(click.isCanceled()
                            && click.getCancellationResult() == InteractionResult.SUCCESS,
                            click.getUseBlock() == TriState.FALSE, click.getUseItem() == TriState.FALSE);
                })));
    }
}
