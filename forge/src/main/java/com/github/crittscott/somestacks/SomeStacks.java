package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.client.ClientSetup;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.github.crittscott.somestacks.command.CommandNetwork;
import com.github.crittscott.somestacks.command.ForgeCommandNetwork;
import com.github.crittscott.somestacks.command.SsCommand;
import com.github.crittscott.somestacks.forge.ForgePlatformServices;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.ModNetworking;
import com.github.crittscott.somestacks.server.ForgeEditAuthority;
import com.github.crittscott.somestacks.server.RotationSoundThrottle;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.server.AdjacentEdits;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.server.WorldEdits;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;

/**
 * The mod entry point: registers the blocks and block entities, the network channel, and the
 * listeners behind server config loading, login sync, and commands. Client
 * registration is deferred to {@link ClientSetup} so the dedicated server never touches it.
 *
 * <p>The mod must be present on both sides; there is no client-optional or server-optional mode.
 */
@Mod(SomeStacks.MODID)
public class SomeStacks {
    public static final String MODID = SomeStacksCommon.MODID;

    private final ForgeEditAuthority editAuthority = new ForgeEditAuthority();

    public SomeStacks() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        PlatformServices.install(
                ForgePlatformServices::configFolder, ForgePlatformServices::modVersion);
        WorldEdits.setAuthority(editAuthority);
        AdjacentEdits.setAuthority(new Protection());

        ModRegistry.init(modBus);
        ModNetworking.init();
        CommandNetwork.setHandler(new ForgeCommandNetwork());
        MinecraftForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogout);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onTagsUpdated);
        MinecraftForge.EVENT_BUS.addListener(this::onLevelUnload);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(modBus));

        SomeStacksCommon.LOGGER.info(
                "Some Stacks v{} initialized for Forge", PlatformServices.modVersion(MODID));
    }

    /** Loads the world-specific server config; {@code /ss reload} can reread it later. */
    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        var configDir = event.getServer().getWorldPath(LevelResource.ROOT).resolve("serverconfig");
        ServerConfig.load(configDir.resolve(MODID + "-server.json"));
    }

    private void onTagsUpdated(TagsUpdatedEvent event) {
        ServerConfig.rebakeIngots();
    }

    private void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            editAuthority.unload(level);
        }
    }

    private void onServerStopped(ServerStoppedEvent event) {
        editAuthority.clear();
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

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            RenderGalleryGenerator.onServerTick();
        }
    }

    private void sendConfigSync(ServerPlayer player) {
        ModNetworking.CHANNEL.send(ConfigSyncPkt.current(), PacketDistributor.PLAYER.with(player));
    }

    /**
     * Sends the current server config to every player, reading the override directory once
     * for the whole broadcast.
     *
     * @return the number of players synced
     */
    public static int syncAllPlayers(MinecraftServer server) {
        ModNetworking.CHANNEL.send(ConfigSyncPkt.current(), PacketDistributor.ALL.noArg());
        return server.getPlayerList().getPlayerCount();
    }

}
