package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.client.FabricClientEvents;
import com.github.crittscott.somestacks.client.FabricClientRendering;
import com.github.crittscott.somestacks.client.FabricKeyMappings;
import com.github.crittscott.somestacks.client.ClientRenderCommands;
import com.github.crittscott.somestacks.network.FabricClientNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** Fabric's client entry point. */
public final class SomeStacksFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricClientRendering.init();
        FabricKeyMappings.init();
        FabricClientNetworking.init();
        FabricClientEvents.init();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                ClientRenderCommands.register(
                        dispatcher, new ClientRenderCommands.Feedback<FabricClientCommandSource>() {
                            @Override
                            public void success(
                                    FabricClientCommandSource source,
                                    net.minecraft.network.chat.Component message) {
                                source.sendFeedback(message);
                            }

                            @Override
                            public void failure(
                                    FabricClientCommandSource source,
                                    net.minecraft.network.chat.Component message) {
                                source.sendError(message);
                            }
                        }));
    }
}
