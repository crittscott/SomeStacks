package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.client.ForgeClientRendering;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.github.crittscott.somestacks.command.SsCommand;
import com.github.crittscott.somestacks.network.ForgeNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * The mod entry point: registers the blocks and block entities, the network channel, and the
 * listeners behind server config loading, sound data, login sync, and commands. Client registration
 * is deferred to {@link ForgeClientRendering} so the dedicated server never touches it.
 *
 * <p>The mod must be present on both sides; there is no client-optional or server-optional mode.
 */
@Mod(SomeStacksCommon.MODID)
public class SomeStacks {
    public SomeStacks() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        PlatformServices.install(new ForgePlatformServices());

        ForgeRegistry.init(modBus);
        ForgeServerConfig.register(modBus);
        ForgeNetworking.init();
        MinecraftForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogout);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ForgeClientRendering.init(modBus));

        SomeStacksCommon.LOGGER.info(
                "Some Stacks {} initialized on Forge (network protocol {})",
                PlatformServices.modVersion(SomeStacksCommon.MODID),
                SomeStacksCommon.PROTOCOL_VERSION);
    }

    /** Starts common session state and publishes Forge's already-loaded world policy. */
    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        ServerLifecycle.starting();
        ForgeServerConfig.start(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        ForgeServerConfig.stop();
        ServerLifecycle.stopped();
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerLifecycle.login((ServerPlayer) event.getEntity());
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerLifecycle.logout(event.getEntity().getUUID());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        SsCommand.register(event.getDispatcher());
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            RenderGalleryGenerator.onServerTick();
        }
    }
}
