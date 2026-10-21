package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;

/** Shared connection lifecycle; joining preserves policy received during native configuration. */
public final class SomeStacksClient {
    private SomeStacksClient() {}

    public static void onJoin() { ClientGestures.resetSync(); }

    public static void onDisconnect() {
        ClientGestures.resetSync();
        ClientServerSettings.clear();
        AutoRenderProfiles.saveCache();
    }

    public static void onStopping() { AutoRenderProfiles.saveCache(); }
}
