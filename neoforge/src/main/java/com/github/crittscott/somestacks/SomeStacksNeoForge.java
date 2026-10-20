package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.NeoForgeItemHandlers;
import com.github.crittscott.somestacks.client.NeoForgeClientSetup;
import com.github.crittscott.somestacks.network.NeoForgeNetworking;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NeoForge entry point: registers the blocks and block entities, the network payloads, the
 * loader-native item-handler capability, and the listeners behind server config loading, native
 * config sync, and commands. Client registration is deferred to {@link NeoForgeClientSetup} so the
 * dedicated server never touches it.
 */
@Mod(SomeStacksCommon.MODID)
public class SomeStacksNeoForge {

    public SomeStacksNeoForge(IEventBus modBus, ModContainer container) {
        PlatformServices.install(new NeoForgePlatformServices());
        NeoForgeServerConfig config = NeoForgeServerConfig.INSTANCE;
        container.registerConfig(ModConfig.Type.SERVER, config.spec);
        ServerConfig.install(config);
        modBus.addListener(config::onLoading);
        modBus.addListener(config::onReloading);

        NeoForgeRegistry.init(modBus);
        modBus.addListener(NeoForgeNetworking::onRegisterPayloadHandlers);
        modBus.addListener(NeoForgeItemHandlers::onRegisterCapabilities);

        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            NeoForgeClientSetup.init(modBus);
        }

        SomeStacksCommon.LOGGER.info(
                "Some Stacks v{} initialized for NeoForge", PlatformServices.modVersion(SomeStacksCommon.MODID));
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        SomeStacksServer.onServerStarting(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        SomeStacksServer.onServerStopped();
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SomeStacksServer.onPlayerLeft(event.getEntity().getUUID());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        SomeStacksServer.registerCommands(event.getDispatcher(), event.getBuildContext());
    }

    private void onServerTick(ServerTickEvent.Post event) {
        SomeStacksServer.onServerTickEnd();
    }
}
