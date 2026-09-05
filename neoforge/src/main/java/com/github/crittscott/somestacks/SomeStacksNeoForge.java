package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.NeoForgeItemHandlers;
import com.github.crittscott.somestacks.client.ClientSetup;
import com.github.crittscott.somestacks.command.CommandNetwork;
import com.github.crittscott.somestacks.command.NeoForgeCommandNetwork;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.github.crittscott.somestacks.command.SsCommand;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.ModNetworking;
import com.github.crittscott.somestacks.server.GestureThrottle;
import com.github.crittscott.somestacks.server.NeoForgeEditAuthority;
import com.github.crittscott.somestacks.server.NeoForgePlayerEditAuthority;
import com.github.crittscott.somestacks.server.PlayerEdits;
import com.github.crittscott.somestacks.server.StackSoundData;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.PlayerReach;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.logging.log4j.Logger;

/**
 * NeoForge entry point: registers the blocks and block entities, the network payloads, the
 * loader-native item-handler capability, and the listeners behind server config loading, sound
 * data, login sync, and commands. Client registration is deferred to {@link ClientSetup} so the
 * dedicated server never touches it.
 */
@Mod(SomeStacksNeoForge.MODID)
public class SomeStacksNeoForge {
    public static final String MODID = SomeStacksCommon.MODID;
    public static final Logger LOGGER = SomeStacksCommon.LOGGER;

    public SomeStacksNeoForge(IEventBus modBus) {
        WorldEdits.setAuthority(new NeoForgeEditAuthority());
        PlayerEdits.setAuthority(new NeoForgePlayerEditAuthority());
        PlayerReach.setProvider(player -> player.blockInteractionRange());
        ServerConfig.useCommonIngotTagDefaults();

        ModRegistry.init(modBus);
        modBus.addListener(ModNetworking::onRegisterPayloadHandlers);
        modBus.addListener(NeoForgeItemHandlers::onRegisterCapabilities);
        CommandNetwork.setHandler(new NeoForgeCommandNetwork());

        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onTagsUpdated);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(modBus);
        }
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        var configDir = event.getServer().getWorldPath(LevelResource.ROOT).resolve("serverconfig");
        ServerConfig.load(configDir.resolve(MODID + "-server.json"));
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new StackSoundData());
    }

    private void onTagsUpdated(TagsUpdatedEvent event) {
        ServerConfig.rebakeIngotTags();
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        sendConfigSync((ServerPlayer) event.getEntity());
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        GestureThrottle.clear(event.getEntity().getUUID());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        SsCommand.register(event.getDispatcher());
    }

    private void onServerTick(ServerTickEvent.Post event) {
        RenderGalleryGenerator.onServerTick();
    }

    private void sendConfigSync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, buildConfigSync());
    }

    /**
     * Sends the current server config to every player, reading the override directory once for the
     * whole broadcast.
     *
     * @return the number of players synced
     */
    public static int syncAllPlayers(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(buildConfigSync());
        return server.getPlayerList().getPlayerCount();
    }

    private static ConfigSyncPkt buildConfigSync() {
        return new ConfigSyncPkt(
                ServerConfig.enableStorageStackBlock(),
                ServerConfig.enableSinglesStackBlock(),
                ServerConfig.enableBarStackBlock(),
                ServerOverridesLoader.load());
    }
}
