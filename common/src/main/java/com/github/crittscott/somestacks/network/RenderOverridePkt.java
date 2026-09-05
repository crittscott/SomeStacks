package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: applies or resets one entry in the receiving client's user render
 * override layer, as directed by the {@code ss item} command.
 */
public class RenderOverridePkt implements CustomPacketPayload {
    public static final Type<RenderOverridePkt> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "render_override"));
    public static final StreamCodec<FriendlyByteBuf, RenderOverridePkt> STREAM_CODEC =
            StreamCodec.ofMember(RenderOverridePkt::encode, RenderOverridePkt::decode);

    private final ResourceLocation itemId;
    private final boolean reset;
    private final String renderMode;
    private final float scale;
    private final float[] offset;

    private RenderOverridePkt(ResourceLocation itemId, boolean reset, String renderMode, float scale, float[] offset) {
        this.itemId = itemId;
        this.reset = reset;
        this.renderMode = renderMode;
        this.scale = scale;
        this.offset = offset;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static RenderOverridePkt set(ResourceLocation itemId, String renderMode, float scale, float[] offset) {
        return new RenderOverridePkt(itemId, false, renderMode, scale, offset);
    }

    public static RenderOverridePkt reset(ResourceLocation itemId) {
        return new RenderOverridePkt(itemId, true, "", 0.0f, new float[3]);
    }

    public static void encode(RenderOverridePkt msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.itemId);
        buf.writeBoolean(msg.reset);
        if (!msg.reset) {
            buf.writeUtf(msg.renderMode);
            buf.writeFloat(msg.scale);
            buf.writeFloat(msg.offset[0]);
            buf.writeFloat(msg.offset[1]);
            buf.writeFloat(msg.offset[2]);
        }
    }

    public static RenderOverridePkt decode(FriendlyByteBuf buf) {
        ResourceLocation itemId = buf.readResourceLocation();
        boolean reset = buf.readBoolean();
        if (reset) {
            return reset(itemId);
        }
        String renderMode = buf.readUtf();
        float scale = buf.readFloat();
        float[] offset = new float[]{buf.readFloat(), buf.readFloat(), buf.readFloat()};
        return set(itemId, renderMode, scale, offset);
    }

    public ResourceLocation itemId() {
        return itemId;
    }

    public boolean isReset() {
        return reset;
    }

    public String renderMode() {
        return renderMode;
    }

    public float scale() {
        return scale;
    }

    public float[] offset() {
        return offset;
    }
}
