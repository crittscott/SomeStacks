package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkProtocol;
import net.minecraftforge.network.SimpleChannel;

/**
 * The mod's single channel and the payload registrations on it. Client and server must agree on the
 * protocol version exactly, so both accept only their own, and the mod is required on both sides.
 *
 * <p>Registration order is positional per direction. Adding a payload anywhere but the end of its
 * {@code serverbound}/{@code clientbound} block renumbers the ones after it, which is a protocol
 * change.
 */
public final class ModNetworking {
    private ModNetworking() {}

    private static final int PROTOCOL = 2;
    public static SimpleChannel CHANNEL;

    public static void init() {
        CHANNEL = ChannelBuilder
                .named(ResourceLocation.fromNamespaceAndPath(SomeStacks.MODID, "main"))
                .networkProtocolVersion(PROTOCOL)
                .clientAcceptedVersions(Channel.VersionTest.exact(PROTOCOL))
                .serverAcceptedVersions(Channel.VersionTest.exact(PROTOCOL))
                .simpleChannel();

        CHANNEL.protocol(NetworkProtocol.PLAY)
                .serverbound()
                .addMain(PlaceAndDepositPkt.class, registryCodec(PlaceAndDepositPkt.STREAM_CODEC),
                        ForgePacketHandlers::handlePlaceAndDeposit)
                .addMain(DepositPkt.class, registryCodec(DepositPkt.STREAM_CODEC),
                        ForgePacketHandlers::handleDeposit)
                .addMain(TogglePermanentPkt.class, registryCodec(TogglePermanentPkt.STREAM_CODEC),
                        ForgePacketHandlers::handleTogglePermanent)
                .addMain(RotateBlockPkt.class, registryCodec(RotateBlockPkt.STREAM_CODEC),
                        ForgePacketHandlers::handleRotateBlock)
                .addMain(RotateItemPkt.class, registryCodec(RotateItemPkt.STREAM_CODEC),
                        ForgePacketHandlers::handleRotateItem)
                .addMain(ExtractPkt.class, registryCodec(ExtractPkt.STREAM_CODEC),
                        ForgePacketHandlers::handleExtract)
                .clientbound()
                .addMain(ConfigSyncPkt.class, registryCodec(ConfigSyncPkt.STREAM_CODEC),
                        ForgePacketHandlers::handleConfigSync)
                .addMain(RenderOverridePkt.class, registryCodec(RenderOverridePkt.STREAM_CODEC),
                        ForgePacketHandlers::handleRenderOverride)
                .addMain(WriteOverridesPkt.class, registryCodec(WriteOverridesPkt.STREAM_CODEC),
                        ForgePacketHandlers::handleWriteOverrides)
                .build();
    }

    /**
     * The shared payload codecs are declared over {@link FriendlyByteBuf}; Forge's {@code addMain}
     * wants one over {@link RegistryFriendlyByteBuf}. The channel only ever feeds them a
     * {@code RegistryFriendlyByteBuf}, which every one of these codecs already reads and writes as a
     * plain buffer, so the widening is safe.
     */
    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> StreamCodec<RegistryFriendlyByteBuf, T> registryCodec(
            StreamCodec<FriendlyByteBuf, T> codec) {
        return (StreamCodec<RegistryFriendlyByteBuf, T>) (StreamCodec<?, T>) codec;
    }
}
