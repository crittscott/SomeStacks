package com.github.crittscott.somestacks.client;

/** Loader-specific services used by shared client rendering code. */
public final class ClientRenderPlatform {
    private ClientRenderPlatform() {}

    public interface Backend {
        /**
         * The installed version string of the mod with the given {@code namespace}, or a
         * placeholder if it isn't loaded. Used to detect when a measured item's source mod has
         * updated and cached measurements need to be invalidated.
         */
        String modVersion(String namespace);
    }

    private static Backend backend;

    /** Installs the loader-specific implementation; called once during client setup. */
    public static void setBackend(Backend backend) {
        ClientRenderPlatform.backend = backend;
    }

    /** @see Backend#modVersion */
    public static String modVersion(String namespace) {
        return backend.modVersion(namespace);
    }
}
