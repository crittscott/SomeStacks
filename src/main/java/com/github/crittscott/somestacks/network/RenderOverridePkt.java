package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.client.ItemRenderOverrides;
import com.github.crittscott.somestacks.client.RenderMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RenderOverridePkt {
    private final ResourceLocation itemId;
    private final String renderMode;
    private final float scale;
    private final float[] offset;

    public RenderOverridePkt(ResourceLocation itemId, String renderMode, float scale, float[] offset) {
        this.itemId = itemId;
        this.renderMode = renderMode;
        this.scale = scale;
        this.offset = offset;
    }

    public static void encode(RenderOverridePkt msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.itemId);
        buf.writeUtf(msg.renderMode);
        buf.writeFloat(msg.scale);
        buf.writeFloat(msg.offset[0]);
        buf.writeFloat(msg.offset[1]);
        buf.writeFloat(msg.offset[2]);
    }

    public static RenderOverridePkt decode(FriendlyByteBuf buf) {
        ResourceLocation itemId = buf.readResourceLocation();
        String renderMode = buf.readUtf();
        float scale = buf.readFloat();
        float[] offset = new float[]{buf.readFloat(), buf.readFloat(), buf.readFloat()};
        return new RenderOverridePkt(itemId, renderMode, scale, offset);
    }

    public static void handle(RenderOverridePkt msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            RenderMode mode = RenderMode.fromString(msg.renderMode);
            ItemRenderOverrides.ItemRenderConfig config =
                    new ItemRenderOverrides.ItemRenderConfig(mode, msg.scale, msg.offset);
            ItemRenderOverrides.CONFIG_MAP.put(msg.itemId, config);
        });
        ctx.get().setPacketHandled(true);
    }
}
