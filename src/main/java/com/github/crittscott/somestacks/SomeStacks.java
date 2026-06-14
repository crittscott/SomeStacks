package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.client.ClientSetup;
import com.github.crittscott.somestacks.client.RenderMode;
import com.github.crittscott.somestacks.command.ItemCommand;
import com.github.crittscott.somestacks.command.ModCommand;
import com.github.crittscott.somestacks.command.TestCommand;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.ModNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
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

import java.util.HashMap;
import java.util.Map;

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
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(modBus));
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        boolean enableStack = ServerConfig.ENABLE_STORAGE_STACK_BLOCK.get();
        boolean enableSingles = ServerConfig.ENABLE_SINGLES_STACK_BLOCK.get();
        boolean enableBar = ServerConfig.ENABLE_BAR_STACK_BLOCK.get();

        Map<ResourceLocation, ConfigSyncPkt.RenderConfig> renderOverrides = parseRenderOverrides();

        ConfigSyncPkt packet = new ConfigSyncPkt(enableStack, enableSingles, enableBar, renderOverrides);
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) event.getEntity()), packet);
    }

    private Map<ResourceLocation, ConfigSyncPkt.RenderConfig> parseRenderOverrides() {
        Map<ResourceLocation, ConfigSyncPkt.RenderConfig> map = new HashMap<>();

        for (String entry : ServerConfig.RENDER_MODE_OVERRIDES.get()) {
            try {
                String[] parts = entry.split(",");
                if (parts.length != 6) {
                    LOGGER.warn("Invalid render override entry (expected 6 parts): {}", entry);
                    continue;
                }

                ResourceLocation itemId = new ResourceLocation(parts[0].trim());
                RenderMode mode = RenderMode.fromString(parts[1].trim());
                if (mode == null) {
                    LOGGER.warn("Invalid render mode in override entry: {}", entry);
                    continue;
                }

                float scale = Float.parseFloat(parts[2].trim());
                float x = Float.parseFloat(parts[3].trim());
                float y = Float.parseFloat(parts[4].trim());
                float z = Float.parseFloat(parts[5].trim());

                map.put(itemId, new ConfigSyncPkt.RenderConfig(mode, scale, new float[]{x, y, z}));
            } catch (Exception e) {
                LOGGER.warn("Failed to parse render override entry '{}': {}", entry, e.getMessage());
            }
        }

        return map;
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getType() == ModConfig.Type.SERVER) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.getPlayerList().getPlayers().forEach(player -> {
                    boolean enableStack = ServerConfig.ENABLE_STORAGE_STACK_BLOCK.get();
                    boolean enableSingles = ServerConfig.ENABLE_SINGLES_STACK_BLOCK.get();
                    boolean enableBar = ServerConfig.ENABLE_BAR_STACK_BLOCK.get();
                    Map<ResourceLocation, ConfigSyncPkt.RenderConfig> renderOverrides = parseRenderOverrides();

                    ConfigSyncPkt packet = new ConfigSyncPkt(enableStack, enableSingles, enableBar, renderOverrides);
                    ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
                });
            }
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ItemCommand.register(event.getDispatcher());
        ModCommand.register(event.getDispatcher());
        TestCommand.register(event.getDispatcher());
    }
}
