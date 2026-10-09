package com.github.crittscott.somestacks.neoforge;

import com.github.crittscott.somestacks.PlatformServices;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.server.NeoForgeEditAuthority;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Path;

/** NeoForge implementations of the common platform services. */
public final class NeoForgePlatformServices implements PlatformServices.Backend {
    private final NeoForgeEditAuthority editAuthority = new NeoForgeEditAuthority();

    @Override
    public Path configFolder() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public String modVersion(String namespace) {
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    @Override
    public void sendConfig(ServerPlayer player, ConfigSyncPkt packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    @Override
    public NeoForgeEditAuthority editAuthority() {
        return editAuthority;
    }
}
