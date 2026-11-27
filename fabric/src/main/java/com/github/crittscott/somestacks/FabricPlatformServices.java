package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.server.FabricEditAuthority;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

/** Fabric implementations of the common platform services. */
public final class FabricPlatformServices implements PlatformServices.Backend {
    private final FabricEditAuthority editAuthority = new FabricEditAuthority();

    /** Builds one vanilla packet for the payload and sends that same packet to every player. */
    @Override
    public void broadcastConfig(MinecraftServer server, ConfigSyncPkt packet) {
        server.getPlayerList().broadcastAll(ServerPlayNetworking.createS2CPacket(packet));
    }

    @Override
    public Path configFolder() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public String modVersion(String namespace) {
        return FabricLoader.getInstance()
                .getModContainer(namespace)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public void sendConfig(ServerPlayer player, ConfigSyncPkt packet) {
        ServerPlayNetworking.send(player, packet);
    }

    @Override
    public FabricEditAuthority editAuthority() {
        return editAuthority;
    }
}
