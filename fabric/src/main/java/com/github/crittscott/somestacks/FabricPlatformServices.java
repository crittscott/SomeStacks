package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.FabricNetworking;
import com.github.crittscott.somestacks.server.FabricEditAuthority;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Path;

/** Fabric implementations of the common platform services. */
public final class FabricPlatformServices implements PlatformServices.Backend {
    /*
     * Vanilla's block pick range. 1.20.1 has no reach attribute, its only constants for this range
     * live in client-only code, and Fabric API offers no reach query, so the values are restated.
     */
    private static final double CREATIVE_BLOCK_REACH = 5.0;
    private static final double SURVIVAL_BLOCK_REACH = 4.5;

    private final FabricEditAuthority editAuthority = new FabricEditAuthority();

    @Override
    public double blockReach(Player player) {
        return player.getAbilities().instabuild ? CREATIVE_BLOCK_REACH : SURVIVAL_BLOCK_REACH;
    }

    @Override
    public void broadcastConfig(MinecraftServer server, ConfigSyncPkt packet) {
        FabricNetworking.broadcastConfig(server, packet);
    }

    @Override
    public Path configFolder() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public FabricEditAuthority editAuthority() {
        return editAuthority;
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
        FabricNetworking.sendConfig(player, packet);
    }
}
