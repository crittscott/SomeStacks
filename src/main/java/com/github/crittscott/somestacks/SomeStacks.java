package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.client.ClientSetup;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.github.crittscott.somestacks.command.SsCommand;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.ModNetworking;
import com.github.crittscott.somestacks.server.StackSoundData;
import net.minecraft.server.MinecraftServer;
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
        modBus.addListener(this::onConfigLoad);
        modBus.addListener(this::onConfigReload);
        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(RenderGalleryGenerator::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ServerConfig::onTagsUpdated);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(modBus));
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new StackSoundData());
    }

    private void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getType() == ModConfig.Type.SERVER) {
            ServerConfig.bakeServerLists();
        }
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getType() != ModConfig.Type.SERVER) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            // No level, so no item tags to walk and no players to tell.
            ServerConfig.bakeServerLists();
            return;
        }

        // Config events fire on the file-watcher thread. Baking reads the item tags, which are data
        // pack state the server thread rewrites on reload, so the whole bake goes there along with
        // the player list it feeds.
        server.execute(() -> {
            ServerConfig.bakeServerLists();
            syncAllPlayers(server);
        });
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        sendConfigSync((ServerPlayer) event.getEntity());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        SsCommand.register(event.getDispatcher());
    }

    private void sendConfigSync(ServerPlayer player) {
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), buildConfigSync());
    }

    /**
     * Sends the current server config to every player, reading the override directory once
     * for the whole broadcast.
     *
     * @return the number of players synced
     */
    public static int syncAllPlayers(MinecraftServer server) {
        ConfigSyncPkt packet = buildConfigSync();
        ModNetworking.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        return server.getPlayerList().getPlayerCount();
    }

    private static ConfigSyncPkt buildConfigSync() {
        return new ConfigSyncPkt(
                ServerConfig.ENABLE_STORAGE_STACK_BLOCK.get(),
                ServerConfig.ENABLE_SINGLES_STACK_BLOCK.get(),
                ServerConfig.ENABLE_BAR_STACK_BLOCK.get(),
                ServerOverridesLoader.load());
    }
}
