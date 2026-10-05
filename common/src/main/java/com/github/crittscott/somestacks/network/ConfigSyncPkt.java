package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.ServerOverridesLoader;
import com.github.crittscott.somestacks.renderconfig.ItemRenderConfig;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-to-client synchronization of the settings the client must agree with the server about:
 * which stack types are enabled, and the server's item render overrides. Sent on login and on
 * an explicit server resynchronization such as {@code /ss reload}.
 *
 * <p>Blacklists and pile limits are not sent. They gate server-side decisions only, and the client
 * never needs to predict them.
 */
public class ConfigSyncPkt implements CustomPacketPayload {
    public static final Type<ConfigSyncPkt> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "config_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPkt> STREAM_CODEC =
            StreamCodec.ofMember(ConfigSyncPkt::encode, ConfigSyncPkt::decode);

    /** Defensive upper bound on synchronized override entries. */
    private static final int MAX_OVERRIDE_ENTRIES = 65536;

    private final boolean enableStack;
    private final boolean enableSingles;
    private final boolean enableBar;
    private final Map<ResourceLocation, ItemRenderConfig> renderOverrides;

    public ConfigSyncPkt(boolean enableStack, boolean enableSingles, boolean enableBar,
                         Map<ResourceLocation, ItemRenderConfig> renderOverrides) {
        this.enableStack = enableStack;
        this.enableSingles = enableSingles;
        this.enableBar = enableBar;
        this.renderOverrides = Map.copyOf(renderOverrides);
    }

    /** Builds one snapshot of the server state clients need. */
    public static ConfigSyncPkt current() {
        return new ConfigSyncPkt(
                ServerConfig.enableStorageStackBlock(),
                ServerConfig.enableSinglesStackBlock(),
                ServerConfig.enableBarStackBlock(),
                ServerOverridesLoader.current());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(ConfigSyncPkt msg, RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(msg.enableStack);
        buf.writeBoolean(msg.enableSingles);
        buf.writeBoolean(msg.enableBar);

        buf.writeVarInt(msg.renderOverrides.size());
        for (Map.Entry<ResourceLocation, ItemRenderConfig> entry : msg.renderOverrides.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            ItemRenderConfig.STREAM_CODEC.encode(buf, entry.getValue());
        }
    }

    public static ConfigSyncPkt decode(RegistryFriendlyByteBuf buf) {
        boolean enableStack = buf.readBoolean();
        boolean enableSingles = buf.readBoolean();
        boolean enableBar = buf.readBoolean();

        int size = buf.readVarInt();
        if (size < 0 || size > MAX_OVERRIDE_ENTRIES) {
            throw new DecoderException("Invalid render-override count: " + size);
        }

        Map<ResourceLocation, ItemRenderConfig> renderOverrides = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation itemId = buf.readResourceLocation();
            renderOverrides.put(itemId, ItemRenderConfig.STREAM_CODEC.decode(buf));
        }

        return new ConfigSyncPkt(enableStack, enableSingles, enableBar, renderOverrides);
    }

    public boolean enableStack() {
        return enableStack;
    }

    public boolean enableSingles() {
        return enableSingles;
    }

    public boolean enableBar() {
        return enableBar;
    }

    public Map<ResourceLocation, ItemRenderConfig> renderOverrides() {
        return renderOverrides;
    }
}
