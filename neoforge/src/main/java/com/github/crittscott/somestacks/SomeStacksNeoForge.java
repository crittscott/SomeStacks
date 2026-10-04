package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.NeoForgeItemHandlers;
import com.github.crittscott.somestacks.client.ClientSetup;
import com.github.crittscott.somestacks.command.CommandNetwork;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.github.crittscott.somestacks.command.SsCommand;
import com.github.crittscott.somestacks.neoforge.NeoForgePlatformServices;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.ModNetworking;
import com.github.crittscott.somestacks.server.RotationSoundThrottle;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.server.NeoForgeEditAuthority;
import com.github.crittscott.somestacks.server.AdjacentEdits;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.server.WorldEdits;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge entry point: registers the blocks and block entities, the network payloads, the
 * loader-native item-handler capability, and the listeners behind server config loading, login
 * sync, and commands. Client registration is deferred to {@link ClientSetup} so the
 * dedicated server never touches it.
 */
@Mod(SomeStacksNeoForge.MODID)
public class SomeStacksNeoForge {
    public static final String MODID = SomeStacksCommon.MODID;

    public SomeStacksNeoForge(IEventBus modBus) {
        PlatformServices.install(
                NeoForgePlatformServices::configFolder, NeoForgePlatformServices::modVersion);
        WorldEdits.setAuthority(new NeoForgeEditAuthority());
        AdjacentEdits.setAuthority(new Protection());
        ModRegistry.init(modBus);
        modBus.addListener(ModNetworking::onRegisterPayloadHandlers);
        modBus.addListener(NeoForgeItemHandlers::onRegisterCapabilities);
        CommandNetwork.install(
                SomeStacksNeoForge::syncAllPlayers,
                (player, packet) -> PacketDistributor.sendToPlayer(player, packet));

        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onTagsUpdated);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(modBus);
        }

        SomeStacksCommon.LOGGER.info(
                "Some Stacks v{} initialized for NeoForge", PlatformServices.modVersion(MODID));
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        var configDir = event.getServer().getWorldPath(LevelResource.ROOT).resolve("serverconfig");
        ServerConfig.load(configDir.resolve(MODID + "-server.json"));
    }

    private void onTagsUpdated(TagsUpdatedEvent event) {
        ServerConfig.rebakeIngots();
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        sendConfigSync((ServerPlayer) event.getEntity());
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerGestureState.clear(event.getEntity().getUUID());
        RotationSoundThrottle.clear(event.getEntity().getUUID());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        SsCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    private void onServerTick(ServerTickEvent.Post event) {
        RenderGalleryGenerator.onServerTick();
    }

    private void sendConfigSync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, ConfigSyncPkt.current());
    }

    /**
     * Sends the current server config to every player, reading the override directory once for the
     * whole broadcast.
     *
     * @return the number of players synced
     */
    public static int syncAllPlayers(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(ConfigSyncPkt.current());
        return server.getPlayerList().getPlayerCount();
    }

}
