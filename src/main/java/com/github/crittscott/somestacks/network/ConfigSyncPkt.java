package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.client.ItemRenderConfig;
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
    private final Map<ResourceLocation, ItemRenderConfig> renderOverrides;

    public ConfigSyncPkt(boolean enableStack, boolean enableSingles, boolean enableBar,
                         Map<ResourceLocation, ItemRenderConfig> renderOverrides) {
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

    public static ConfigSyncPkt decode(FriendlyByteBuf buf) {
        boolean enableStack = buf.readBoolean();
        boolean enableSingles = buf.readBoolean();
        boolean enableBar = buf.readBoolean();

        int size = buf.readInt();
        Map<ResourceLocation, ItemRenderConfig> renderOverrides = new HashMap<>();
        for (int i = 0; i < size; i++) {
            ResourceLocation itemId = buf.readResourceLocation();
            RenderMode mode = buf.readBoolean() ? RenderMode.fromString(buf.readUtf()) : null;
            Float scale = buf.readBoolean() ? buf.readFloat() : null;
            float[] offset = buf.readBoolean()
                    ? new float[]{buf.readFloat(), buf.readFloat(), buf.readFloat()}
                    : null;
            renderOverrides.put(itemId, new ItemRenderConfig(mode, scale, offset));
        }

        return new ConfigSyncPkt(enableStack, enableSingles, enableBar, renderOverrides);
    }

    public static void handle(ConfigSyncPkt msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                StackState.setBlockEnabled(BlockType.STORAGE_STACK, msg.enableStack);
                StackState.setBlockEnabled(BlockType.SINGLES_STACK, msg.enableSingles);
                StackState.setBlockEnabled(BlockType.BAR_STACK, msg.enableBar);

                ItemRenderOverrides.setSyncedServerOverrides(msg.renderOverrides);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
