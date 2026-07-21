package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.client.ClientSetup;
import com.github.crittscott.somestacks.command.SsCommand;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.ModNetworking;
import com.github.crittscott.somestacks.server.StackSoundData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SomeStacks.MODID)
public class SomeStacks {
    public static final String MODID = "somestacks";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public SomeStacks() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SERVER_CONFIG);

        ModRegistry.init(modBus);
        ModNetworking.init();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onConfigReload);
        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(modBus));
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new StackSoundData());
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        sendConfigSync((ServerPlayer) event.getEntity());
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getType() == ModConfig.Type.SERVER) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.getPlayerList().getPlayers().forEach(this::sendConfigSync);
            }
        }
    }

    private void sendConfigSync(ServerPlayer player) {
        ConfigSyncPkt packet = new ConfigSyncPkt(
                ServerConfig.ENABLE_STORAGE_STACK_BLOCK.get(),
                ServerConfig.ENABLE_SINGLES_STACK_BLOCK.get(),
                ServerConfig.ENABLE_BAR_STACK_BLOCK.get(),
                ServerOverridesLoader.load());
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        SsCommand.register(event.getDispatcher());
    }
}
