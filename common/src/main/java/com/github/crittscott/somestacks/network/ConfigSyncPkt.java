package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.client.ItemRenderConfig;
import com.github.crittscott.somestacks.client.RenderMode;
import com.github.crittscott.somestacks.util.OverrideJsonCodec;
import net.minecraft.network.FriendlyByteBuf;
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
    public static final StreamCodec<FriendlyByteBuf, ConfigSyncPkt> STREAM_CODEC =
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
        this.renderOverrides = renderOverrides;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(ConfigSyncPkt msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enableStack);
        buf.writeBoolean(msg.enableSingles);
        buf.writeBoolean(msg.enableBar);

        buf.writeInt(msg.renderOverrides.size());
        for (Map.Entry<ResourceLocation, ItemRenderConfig> entry : msg.renderOverrides.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            ItemRenderConfig config = entry.getValue();

            buf.writeBoolean(config.mode() != null);
            if (config.mode() != null) {
                buf.writeUtf(config.mode().getId());
            }
            buf.writeBoolean(config.scale() != null);
            if (config.scale() != null) {
                buf.writeFloat(config.scale());
            }
            buf.writeBoolean(config.offset() != null);
            if (config.offset() != null) {
                buf.writeFloat(config.offset()[0]);
                buf.writeFloat(config.offset()[1]);
                buf.writeFloat(config.offset()[2]);
            }
        }
    }

    /**
     * Decodes an invalid entry count to a null-map sentinel for {@link #isValid()} to reject. An empty
     * map is valid and would clear the client's server override layer, so it cannot represent
     * failure.
     */
    public static ConfigSyncPkt decode(FriendlyByteBuf buf) {
        boolean enableStack = buf.readBoolean();
        boolean enableSingles = buf.readBoolean();
        boolean enableBar = buf.readBoolean();

        int size = buf.readInt();
        if (size < 0 || size > MAX_OVERRIDE_ENTRIES) {
            SomeStacksCommon.LOGGER.warn("Ignoring config sync claiming {} render overrides", size);
            return new ConfigSyncPkt(enableStack, enableSingles, enableBar, null);
        }

        Map<ResourceLocation, ItemRenderConfig> renderOverrides = new HashMap<>();
        for (int i = 0; i < size; i++) {
            ResourceLocation itemId = buf.readResourceLocation();
            RenderMode mode = buf.readBoolean() ? RenderMode.fromString(buf.readUtf()) : null;
            Float scale = buf.readBoolean() ? buf.readFloat() : null;
            float[] offset = buf.readBoolean()
                    ? new float[]{buf.readFloat(), buf.readFloat(), buf.readFloat()}
                    : null;
            renderOverrides.put(itemId,
                    OverrideJsonCodec.sanitize(new ItemRenderConfig(mode, scale, offset)));
        }

        return new ConfigSyncPkt(enableStack, enableSingles, enableBar, renderOverrides);
    }

    public boolean isValid() {
        return renderOverrides != null;
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
