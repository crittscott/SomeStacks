package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.client.FabricClientEvents;
import com.github.crittscott.somestacks.client.FabricKeyMappings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;

/** Fabric development-client registration for {@link ClientCallbackChecks#run}. */
public final class ClientCallbackGameTests implements ClientModInitializer {
    /** Registers the local /ssclienttest smoke command in a client running the test mod. */
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                dispatcher.register(ClientCommandManager.literal("ssclienttest").executes(ctx ->
                        ClientCallbackChecks.run(FabricKeyMappings.STACK_MODE_KEY.isDown(), (hand, hit) -> {
                            Minecraft client = Minecraft.getInstance();
                            InteractionResult result = FabricClientEvents.onUseBlock(client.player, client.level, hand, hit);
                            boolean consumed = result == InteractionResult.SUCCESS;
                            return new ClientCallbackChecks.Result(consumed, consumed, consumed);
                        }))));
    }
}
