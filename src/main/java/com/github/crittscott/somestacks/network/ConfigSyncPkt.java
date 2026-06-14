package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.client.ItemRenderOverrides;
import com.github.crittscott.somestacks.client.RenderMode;
import com.github.crittscott.somestacks.client.StackState;
import com.github.crittscott.somestacks.util.BlockType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ConfigSyncPkt {
    private final boolean enableStack;
    private final boolean enableSingles;
    private final boolean enableBar;
    private final Map<ResourceLocation, RenderConfig> renderOverrides;

    public ConfigSyncPkt(boolean enableStack, boolean enableSingles, boolean enableBar,
                         Map<ResourceLocation, RenderConfig> renderOverrides) {
        this.enableStack = enableStack;
        this.enableSingles = enableSingles;
        this.enableBar = enableBar;
        this.renderOverrides = renderOverrides;
    }

    public static void encode(ConfigSyncPkt msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enableStack);
        buf.writeBoolean(msg.enableSingles);
        buf.writeBoolean(msg.enableBar);

        buf.writeInt(msg.renderOverrides.size());
        for (Map.Entry<ResourceLocation, RenderConfig> entry : msg.renderOverrides.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            buf.writeUtf(entry.getValue().mode().getId());
            buf.writeFloat(entry.getValue().scale());
            buf.writeFloat(entry.getValue().offset()[0]);
            buf.writeFloat(entry.getValue().offset()[1]);
            buf.writeFloat(entry.getValue().offset()[2]);
        }
    }

    public static ConfigSyncPkt decode(FriendlyByteBuf buf) {
        boolean enableStack = buf.readBoolean();
        boolean enableSingles = buf.readBoolean();
        boolean enableBar = buf.readBoolean();

        int size = buf.readInt();
        Map<ResourceLocation, RenderConfig> renderOverrides = new HashMap<>();
        for (int i = 0; i < size; i++) {
            ResourceLocation itemId = buf.readResourceLocation();
            RenderMode mode = RenderMode.fromString(buf.readUtf());
            float scale = buf.readFloat();
            float[] offset = new float[]{buf.readFloat(), buf.readFloat(), buf.readFloat()};
            renderOverrides.put(itemId, new RenderConfig(mode, scale, offset));
        }

        return new ConfigSyncPkt(enableStack, enableSingles, enableBar, renderOverrides);
    }

    public static void handle(ConfigSyncPkt msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                StackState.setBlockEnabled(BlockType.STORAGE_STACK, msg.enableStack);
                StackState.setBlockEnabled(BlockType.SINGLES_STACK, msg.enableSingles);
                StackState.setBlockEnabled(BlockType.BAR_STACK, msg.enableBar);

                Map<ResourceLocation, ItemRenderOverrides.ItemRenderConfig> converted = new HashMap<>();
                for (Map.Entry<ResourceLocation, RenderConfig> entry : msg.renderOverrides.entrySet()) {
                    converted.put(entry.getKey(), entry.getValue().toItemRenderConfig());
                }
                ItemRenderOverrides.setSyncedServerOverrides(converted);
            });
        });
        ctx.get().setPacketHandled(true);
    }

    public record RenderConfig(RenderMode mode, float scale, float[] offset) {
        public ItemRenderOverrides.ItemRenderConfig toItemRenderConfig() {
            return new ItemRenderOverrides.ItemRenderConfig(mode, scale, offset);
        }
    }
}
