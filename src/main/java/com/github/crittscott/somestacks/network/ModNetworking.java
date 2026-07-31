package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class ModNetworking {
    private ModNetworking() {}

    private static final String PROTOCOL = "2";
    public static SimpleChannel CHANNEL;

    public static void init() {
        CHANNEL = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(SomeStacks.MODID, "main"))
                .networkProtocolVersion(() -> PROTOCOL)
                .clientAcceptedVersions(PROTOCOL::equals)
                .serverAcceptedVersions(PROTOCOL::equals)
                .simpleChannel();

        Optional<NetworkDirection> toServer = Optional.of(NetworkDirection.PLAY_TO_SERVER);
        Optional<NetworkDirection> toClient = Optional.of(NetworkDirection.PLAY_TO_CLIENT);

        int id = 0;
        CHANNEL.registerMessage(id++, PlaceAndDepositPkt.class, PlaceAndDepositPkt::encode,
                PlaceAndDepositPkt::decode, PlaceAndDepositPkt::handle, toServer);
        CHANNEL.registerMessage(id++, DepositPkt.class, DepositPkt::encode,
                DepositPkt::decode, DepositPkt::handle, toServer);
        CHANNEL.registerMessage(id++, TogglePermanentPkt.class, TogglePermanentPkt::encode,
                TogglePermanentPkt::decode, TogglePermanentPkt::handle, toServer);
        CHANNEL.registerMessage(id++, ConfigSyncPkt.class, ConfigSyncPkt::encode,
                ConfigSyncPkt::decode, ConfigSyncPkt::handle, toClient);
        CHANNEL.registerMessage(id++, RenderOverridePkt.class, RenderOverridePkt::encode,
                RenderOverridePkt::decode, RenderOverridePkt::handle, toClient);
        CHANNEL.registerMessage(id++, RotateBlockPkt.class, RotateBlockPkt::encode,
                RotateBlockPkt::decode, RotateBlockPkt::handle, toServer);
        CHANNEL.registerMessage(id++, RotateItemPkt.class, RotateItemPkt::encode,
                RotateItemPkt::decode, RotateItemPkt::handle, toServer);
        CHANNEL.registerMessage(id++, ExtractPkt.class, ExtractPkt::encode,
                ExtractPkt::decode, ExtractPkt::handle, toServer);
        CHANNEL.registerMessage(id++, WriteOverridesPkt.class, WriteOverridesPkt::encode,
                WriteOverridesPkt::decode, WriteOverridesPkt::handle, toClient);
    }
}
