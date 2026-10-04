package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.FabricItemStorage;
import com.github.crittscott.somestacks.command.CommandNetwork;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.github.crittscott.somestacks.command.SsCommand;
import com.github.crittscott.somestacks.fabric.FabricPlatformServices;
import com.github.crittscott.somestacks.network.FabricNetworking;
import com.github.crittscott.somestacks.server.FabricEditAuthority;
import com.github.crittscott.somestacks.server.FabricAdjacentEditAuthority;
import com.github.crittscott.somestacks.server.FabricStackInteractionEvents;
import com.github.crittscott.somestacks.server.RotationSoundThrottle;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.server.AdjacentEdits;
import com.github.crittscott.somestacks.server.WorldEdits;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/** Fabric's common entry point and server lifecycle wiring. */
public final class SomeStacksFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PlatformServices.install(
                FabricPlatformServices::configFolder, FabricPlatformServices::modVersion);
        FabricRegistry.init();
        FabricItemStorage.init();
        WorldEdits.setAuthority(new FabricEditAuthority());
        AdjacentEdits.setAuthority(new FabricAdjacentEditAuthority());
        FabricStackInteractionEvents.init();
        FabricNetworking.registerPayloads();
        FabricNetworking.initServer();
        CommandNetwork.install(FabricNetworking::syncAllPlayers, FabricNetworking::send);

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) ->
                        SsCommand.register(dispatcher, registryAccess));
        ServerTickEvents.END_SERVER_TICK.register(server -> RenderGalleryGenerator.onServerTick());

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            var configDir = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig");
            ServerConfig.load(configDir.resolve(SomeStacksCommon.MODID + "-server.json"));
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
                (server, resourceManager, success) -> {
                    if (success) {
                        ServerConfig.rebakeIngots();
                    }
                });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (!FabricNetworking.supportsClient(player)) {
                SomeStacksCommon.LOGGER.warn(
                        "Disconnected {}: compatible Some Stacks client protocol was not advertised",
                        player.getGameProfile().getName());
                handler.disconnect(Component.translatable("somestacks.disconnect.protocol"));
                return;
            }
            FabricNetworking.sendConfig(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerGestureState.clear(handler.player.getUUID());
            RotationSoundThrottle.clear(handler.player.getUUID());
        });

        SomeStacksCommon.LOGGER.info(
                "Some Stacks v{} initialized for Fabric",
                PlatformServices.modVersion(SomeStacksCommon.MODID));
    }
}
