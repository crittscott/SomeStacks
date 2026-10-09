package com.github.crittscott.somestacks.forge;

import com.github.crittscott.somestacks.PlatformServices;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.ModNetworking;
import com.github.crittscott.somestacks.server.AdjacentEditAuthority;
import com.github.crittscott.somestacks.server.ForgeEditAuthority;
import com.github.crittscott.somestacks.server.Protection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;

import java.nio.file.Path;

/** Forge implementations of the common platform services. */
public final class ForgePlatformServices implements PlatformServices.Backend {
    private final ForgeEditAuthority editAuthority = new ForgeEditAuthority();
    private final AdjacentEditAuthority adjacentEditAuthority = new Protection();

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
        ModNetworking.CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
    }

    @Override
    public ForgeEditAuthority editAuthority() {
        return editAuthority;
    }

    @Override
    public AdjacentEditAuthority adjacentEditAuthority() {
        return adjacentEditAuthority;
    }
}
