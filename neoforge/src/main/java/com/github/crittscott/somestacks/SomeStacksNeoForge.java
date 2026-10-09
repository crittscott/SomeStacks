package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.NeoForgeItemHandlers;
import com.github.crittscott.somestacks.client.ClientSetup;
import com.github.crittscott.somestacks.neoforge.NeoForgePlatformServices;
import com.github.crittscott.somestacks.network.ModNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NeoForge entry point: registers the blocks and block entities, the network payloads, the
 * loader-native item-handler capability, and the listeners behind server config loading, login
 * sync, and commands. Client registration is deferred to {@link ClientSetup} so the
 * dedicated server never touches it.
 */
@Mod(SomeStacksNeoForge.MODID)
public class SomeStacksNeoForge {
    public static final String MODID = SomeStacksCommon.MODID;

    public SomeStacksNeoForge(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        PlatformServices.install(new NeoForgePlatformServices());
        NeoForgeServerConfig config = NeoForgeServerConfig.INSTANCE;
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, config.spec);
        ServerConfig.install(config);
        modBus.addListener(config::onLoading);
        modBus.addListener(config::onReloading);

        ModRegistry.init(modBus);
        modBus.addListener(ModNetworking::onRegisterPayloadHandlers);
        modBus.addListener(NeoForgeItemHandlers::onRegisterCapabilities);

        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(modBus);
        }

        SomeStacksCommon.LOGGER.info(
                "Some Stacks v{} initialized for NeoForge", PlatformServices.modVersion(MODID));
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        SomeStacksServer.onServerStarting(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        SomeStacksServer.onServerStopped();
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        SomeStacksServer.onPlayerJoined((ServerPlayer) event.getEntity());
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
