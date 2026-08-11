package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.network.RenderOverridePkt;
import com.github.crittscott.somestacks.util.OverrideJsonCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** Applies render-related S2C packets to the shared client override layers. */
public final class DefaultClientRenderPacketHandler implements ClientRenderPacketSink.Handler {
    @Override
    public void setServerOverrides(Map<ResourceLocation, ItemRenderConfig> overrides) {
        ItemRenderOverrides.setSyncedServerOverrides(overrides);
    }

    @Override
    public void setUserOverride(RenderOverridePkt packet) {
        if (packet.isReset()) {
            ItemRenderOverrides.removeUser(packet.itemId());
            return;
        }
        RenderMode mode = RenderMode.fromString(packet.renderMode());
        ItemRenderOverrides.putUser(packet.itemId(), OverrideJsonCodec.sanitize(
                new ItemRenderConfig(mode, packet.scale(), packet.offset())));
    }

    @Override
    public void writeOverrides(List<String> namespaces) {
        if (namespaces.isEmpty()) {
            ItemRenderOverrides.handleWriteRequest();
        } else {
            ItemRenderOverrides.handleDumpRequest(namespaces);
        }
    }
}
