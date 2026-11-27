package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.ForgeNetworking;
import com.github.crittscott.somestacks.server.ForgeEditAuthority;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;

import java.nio.file.Path;

/** Forge implementations of the common platform services. */
public final class ForgePlatformServices implements PlatformServices.Backend {
    private final ForgeEditAuthority editAuthority = new ForgeEditAuthority();

    @Override
    public void broadcastConfig(MinecraftServer server, ConfigSyncPkt packet) {
        ForgeNetworking.CHANNEL.send(packet, PacketDistributor.ALL.noArg());
    }

    @Override
    public Path configFolder() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public String modVersion(String namespace) {
        return ModList.get()
                .getModContainerById(namespace)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    @Override
    public void sendConfig(ServerPlayer player, ConfigSyncPkt packet) {
        ForgeNetworking.CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
    }

    @Override
    public ForgeEditAuthority editAuthority() {
        return editAuthority;
    }
}
