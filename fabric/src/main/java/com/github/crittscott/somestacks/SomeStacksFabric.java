package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.FabricItemStorage;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.github.crittscott.somestacks.command.SsCommand;
import com.github.crittscott.somestacks.network.FabricNetworking;
import com.github.crittscott.somestacks.server.FabricStackInteractionEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/** Fabric's common entry point and server lifecycle wiring. */
public final class SomeStacksFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PlatformServices.install(new FabricPlatformServices());
        FabricRegistry.init();
        FabricItemStorage.init();
        FabricNetworking.initServer();
        FabricStackInteractionEvents.init();

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> SsCommand.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(server -> RenderGalleryGenerator.onServerTick());

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ServerLifecycle.starting();
            FabricServerConfig.start(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerLifecycle.stopped());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ServerLifecycle.login(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ServerLifecycle.logout(handler.player.getUUID()));
    }
}
