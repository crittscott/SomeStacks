package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.client.ClientSetup;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.github.crittscott.somestacks.command.CommandNetwork;
import com.github.crittscott.somestacks.command.ForgeCommandNetwork;
import com.github.crittscott.somestacks.command.SsCommand;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.ModNetworking;
import com.github.crittscott.somestacks.server.ForgeEditAuthority;
import com.github.crittscott.somestacks.server.ForgePlayerEditAuthority;
import com.github.crittscott.somestacks.server.GestureThrottle;
import com.github.crittscott.somestacks.server.PlayerEdits;
import com.github.crittscott.somestacks.server.StackSoundData;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.PlayerReach;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;
import org.apache.logging.log4j.Logger;

/**
 * The mod entry point: registers the blocks and block entities, the network channel, and the
 * listeners behind server config loading, sound data, login sync, and commands. Client
 * registration is deferred to {@link ClientSetup} so the dedicated server never touches it.
 *
 * <p>The mod must be present on both sides; there is no client-optional or server-optional mode.
 */
@Mod(SomeStacks.MODID)
public class SomeStacks {
    public static final String MODID = SomeStacksCommon.MODID;
    public static final Logger LOGGER = SomeStacksCommon.LOGGER;

    public SomeStacks() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        WorldEdits.setAuthority(new ForgeEditAuthority());
        PlayerEdits.setAuthority(new ForgePlayerEditAuthority());
        PlayerReach.setProvider(player -> player.getBlockReach());

        ModRegistry.init(modBus);
        ModNetworking.init();
        CommandNetwork.setHandler(new ForgeCommandNetwork());
        MinecraftForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogout);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onTagsUpdated);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(modBus));
    }

    /**
     * Loads the world-specific server config, the way Forge's own per-world {@code ModConfig} used
     * to. There is no automatic file-watch reload behind this hand-rolled reader/writer; {@code
     * /ss reload} is the supported way to pick up a manual edit while the server runs.
     */
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

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            RenderGalleryGenerator.onServerTick();
        }
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
                ServerConfig.enableStorageStackBlock(),
                ServerConfig.enableSinglesStackBlock(),
                ServerConfig.enableBarStackBlock(),
                ServerOverridesLoader.load());
    }
}
