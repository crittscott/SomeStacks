package com.github.crittscott.somestacks.fabric;

import com.github.crittscott.somestacks.PlatformServices;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.FabricNetworking;
import com.github.crittscott.somestacks.server.AdjacentEditAuthority;
import com.github.crittscott.somestacks.server.FabricAdjacentEditAuthority;
import com.github.crittscott.somestacks.server.FabricEditAuthority;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

/** Fabric implementations of the common platform services. */
public final class FabricPlatformServices implements PlatformServices.Backend {
    private final FabricEditAuthority editAuthority = new FabricEditAuthority();
    private final AdjacentEditAuthority adjacentEditAuthority = new FabricAdjacentEditAuthority();

    @Override
    public Path configFolder() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public String modVersion(String namespace) {
        return FabricLoader.getInstance().getModContainer(namespace)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public void sendConfig(ServerPlayer player, ConfigSyncPkt packet) {
        FabricNetworking.send(player, packet);
    }

    @Override
    public FabricEditAuthority editAuthority() {
        return editAuthority;
    }

    @Override
    public AdjacentEditAuthority adjacentEditAuthority() {
        return adjacentEditAuthority;
    }
}
