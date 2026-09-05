package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.FabricItemStorage;
import com.github.crittscott.somestacks.network.FabricNetworking;
import com.github.crittscott.somestacks.command.CommandNetwork;
import com.github.crittscott.somestacks.command.FabricCommandNetwork;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.github.crittscott.somestacks.command.SsCommand;
import com.github.crittscott.somestacks.server.FabricEditAuthority;
import com.github.crittscott.somestacks.server.FabricPlayerEditAuthority;
import com.github.crittscott.somestacks.server.FabricStackSoundData;
import com.github.crittscott.somestacks.server.GestureThrottle;
import com.github.crittscott.somestacks.server.PlayerEdits;
import com.github.crittscott.somestacks.server.WorldEdits;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.storage.LevelResource;

/** Fabric's common entry point and server lifecycle wiring. */
public final class SomeStacksFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricRegistry.init();
        FabricItemStorage.init();
        ServerConfig.useCommonIngotTagDefaults();
        WorldEdits.setAuthority(new FabricEditAuthority());
        PlayerEdits.setAuthority(new FabricPlayerEditAuthority());
        FabricNetworking.registerPayloads();
        FabricNetworking.initServer();
        CommandNetwork.setHandler(new FabricCommandNetwork());

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> SsCommand.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(server -> RenderGalleryGenerator.onServerTick());

        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(new FabricStackSoundData());

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            var configDir = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig");
            ServerConfig.load(configDir.resolve(SomeStacksCommon.MODID + "-server.json"));
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
                (server, resourceManager, success) -> {
                    if (success) {
                        ServerConfig.rebakeIngotTags();
                    }
                });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (!FabricNetworking.supportsClient(player)) {
                handler.disconnect(Component.translatable("somestacks.disconnect.protocol"));
                return;
            }
            FabricNetworking.sendProtocol(player);
            FabricNetworking.sendConfig(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                GestureThrottle.clear(handler.player.getUUID()));
    }
}
