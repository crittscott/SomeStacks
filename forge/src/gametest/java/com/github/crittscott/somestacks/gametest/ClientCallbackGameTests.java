package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.client.ClientEvents;
import com.github.crittscott.somestacks.client.KeyMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge development-client registration for {@link ClientCallbackChecks#run}. */
@Mod.EventBusSubscriber(modid = SomeStacksGameTests.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientCallbackGameTests {
    private ClientCallbackGameTests() {}

    /** Registers the local /ssclienttest smoke command in a client running the test mod. */
    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ssclienttest").executes(ctx ->
                ClientCallbackChecks.run(KeyMappings.STACK_MODE_KEY.isDown(), (hand, hit) -> {
                    var click = new PlayerInteractEvent.RightClickBlock(
                            Minecraft.getInstance().player, hand, hit.getBlockPos(), hit);
                    boolean canceled = ClientEvents.onRightClick(click);
                    return new ClientCallbackChecks.Result(canceled
                            && click.getCancellationResult() == InteractionResult.SUCCESS,
                            click.getUseBlock() == Result.DENY, click.getUseItem() == Result.DENY);
                })));
    }
}
