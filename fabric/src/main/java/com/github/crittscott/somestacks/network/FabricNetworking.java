package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.ServerOverridesLoader;
import com.github.crittscott.somestacks.SomeStacksCommon;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** Fabric channels around the shared packet codecs and server handlers. */
public final class FabricNetworking {
    private FabricNetworking() {}

    public static final ResourceLocation PROTOCOL = id("protocol_2");
    public static final ResourceLocation PLACE_AND_DEPOSIT = id("place_and_deposit");
    public static final ResourceLocation DEPOSIT = id("deposit");
    public static final ResourceLocation TOGGLE_PERMANENT = id("toggle_permanent");
    public static final ResourceLocation CONFIG_SYNC = id("config_sync");
    public static final ResourceLocation RENDER_OVERRIDE = id("render_override");
    public static final ResourceLocation ROTATE_BLOCK = id("rotate_block");
    public static final ResourceLocation ROTATE_ITEM = id("rotate_item");
    public static final ResourceLocation EXTRACT = id("extract");
    public static final ResourceLocation WRITE_OVERRIDES = id("write_overrides");

    public static void initServer() {
        registerServer(PLACE_AND_DEPOSIT, PlaceAndDepositPkt::decode,
                PlaceAndDepositPkt::handleServer);
        registerServer(DEPOSIT, DepositPkt::decode, DepositPkt::handleServer);
        registerServer(TOGGLE_PERMANENT, TogglePermanentPkt::decode,
                TogglePermanentPkt::handleServer);
        registerServer(ROTATE_BLOCK, RotateBlockPkt::decode, RotateBlockPkt::handleServer);
        registerServer(ROTATE_ITEM, RotateItemPkt::decode, RotateItemPkt::handleServer);
        registerServer(EXTRACT, ExtractPkt::decode, ExtractPkt::handleServer);
    }

    public static boolean supportsClient(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, PROTOCOL);
    }

    public static void sendProtocol(ServerPlayer player) {
        ServerPlayNetworking.send(player, PROTOCOL, PacketByteBufs.create());
    }

    public static void sendConfig(ServerPlayer player) {
        ConfigSyncPkt packet = buildConfigSync();
        FriendlyByteBuf buf = PacketByteBufs.create();
        ConfigSyncPkt.encode(packet, buf);
        ServerPlayNetworking.send(player, CONFIG_SYNC, buf);
    }

    public static int syncAllPlayers(MinecraftServer server) {
        ConfigSyncPkt packet = buildConfigSync();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            ConfigSyncPkt.encode(packet, buf);
            ServerPlayNetworking.send(player, CONFIG_SYNC, buf);
        }
        return server.getPlayerList().getPlayerCount();
    }

    public static void sendRenderOverride(ServerPlayer player, RenderOverridePkt packet) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        RenderOverridePkt.encode(packet, buf);
        ServerPlayNetworking.send(player, RENDER_OVERRIDE, buf);
    }

    public static void sendWriteOverrides(ServerPlayer player, WriteOverridesPkt packet) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        WriteOverridesPkt.encode(packet, buf);
        ServerPlayNetworking.send(player, WRITE_OVERRIDES, buf);
    }

    private static ConfigSyncPkt buildConfigSync() {
        return new ConfigSyncPkt(
                ServerConfig.enableStorageStackBlock(),
                ServerConfig.enableSinglesStackBlock(),
                ServerConfig.enableBarStackBlock(),
                ServerOverridesLoader.load());
    }

    private static <T> void registerServer(
            ResourceLocation channel, Function<FriendlyByteBuf, T> decoder,
            BiConsumer<T, ServerPlayer> handler) {
        ServerPlayNetworking.registerGlobalReceiver(channel,
                (server, player, networkHandler, buf, responseSender) -> {
                    T packet = decoder.apply(buf);
                    server.execute(() -> handler.accept(packet, player));
                });
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(SomeStacksCommon.MODID, path);
    }
}
