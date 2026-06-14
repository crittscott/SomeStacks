package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetworking {
    private ModNetworking() {}

    private static final String PROTOCOL = "1";
    public static SimpleChannel CHANNEL;

    public static void init() {
        CHANNEL = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(SomeStacks.MODID, "main"))
                .networkProtocolVersion(() -> PROTOCOL)
                .clientAcceptedVersions(PROTOCOL::equals)
                .serverAcceptedVersions(PROTOCOL::equals)
                .simpleChannel();

        int id = 0;
        CHANNEL.registerMessage(id++, PlaceAndDepositPkt.class, PlaceAndDepositPkt::encode,
                PlaceAndDepositPkt::decode, PlaceAndDepositPkt::handle);
        CHANNEL.registerMessage(id++, DepositPkt.class, DepositPkt::encode,
                DepositPkt::decode, DepositPkt::handle);
        CHANNEL.registerMessage(id++, TogglePermanentPkt.class, TogglePermanentPkt::encode,
                TogglePermanentPkt::decode, TogglePermanentPkt::handle);
        CHANNEL.registerMessage(id++, ConfigSyncPkt.class, ConfigSyncPkt::encode,
                ConfigSyncPkt::decode, ConfigSyncPkt::handle);
        CHANNEL.registerMessage(id++, RenderOverridePkt.class, RenderOverridePkt::encode,
                RenderOverridePkt::decode, RenderOverridePkt::handle);
        CHANNEL.registerMessage(id++, RotateBlockPkt.class, RotateBlockPkt::encode,
                RotateBlockPkt::decode, RotateBlockPkt::handle);
        CHANNEL.registerMessage(id++, RotateItemPkt.class, RotateItemPkt::encode,
                RotateItemPkt::decode, RotateItemPkt::handle);
        CHANNEL.registerMessage(id++, ExtractPkt.class, ExtractPkt::encode,
                ExtractPkt::decode, ExtractPkt::handle);
    }
}
