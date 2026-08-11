package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.client.FabricClientEvents;
import com.github.crittscott.somestacks.client.FabricKeyMappings;
import com.github.crittscott.somestacks.network.FabricClientNetworking;
import net.fabricmc.api.ClientModInitializer;

/** Fabric's client entry point. Rendering registration is added in the next port stage. */
public final class SomeStacksFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricKeyMappings.init();
        FabricClientNetworking.init();
        FabricClientEvents.init();
    }
}
