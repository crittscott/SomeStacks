package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.fabric.FabricPlatformServices;
import com.github.crittscott.somestacks.network.FabricNetworking;
import com.github.crittscott.somestacks.network.ProtocolPkt;
import com.github.crittscott.somestacks.server.FabricStackInteractionEvents;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

/**
 * Fabric's common entry point, run on both physical sides: installs the platform services and the
 * server config, registers content, item storage, gesture callbacks, and payloads, and wires the
 * server lifecycle, commands, ticking, the configuration-phase protocol check, and player join and
 * leave to {@link SomeStacksServer}.
 */
public final class SomeStacksFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricPlatformServices platform = new FabricPlatformServices();
        PlatformServices.install(platform);
        ServerConfig.install(
                new FabricServerConfig(
                        FabricLoader.getInstance().getConfigDir()));
        FabricRegistry.init();
        FabricStackInteractionEvents.init();
        FabricNetworking.registerPayloads();
        FabricNetworking.initServer();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                SomeStacksServer.registerCommands(dispatcher, registryAccess));
        ServerTickEvents.END_SERVER_TICK.register(server -> SomeStacksServer.onServerTickEnd());

        ServerLifecycleEvents.SERVER_STARTING.register(SomeStacksServer::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SomeStacksServer.onServerStopped();
            platform.editAuthority().clear();
        });
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (!ServerConfigurationNetworking.canSend(handler, ProtocolPkt.TYPE)) {
                handler.disconnect(Component.translatable("somestacks.disconnect.protocol"));
            }
        });
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> SomeStacksServer.onPlayerJoined(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> SomeStacksServer.onPlayerLeft(handler.player.getUUID()));

        PlatformServices.logStartup("Fabric");
    }
}
