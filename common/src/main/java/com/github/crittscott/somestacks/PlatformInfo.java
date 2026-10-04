package com.github.crittscott.somestacks;

import java.util.Objects;
import java.util.function.Function;

/** Loader-specific information about installed mods. */
public final class PlatformInfo {
    private PlatformInfo() {}

    private static Function<String, String> resolver;

    /** Installs the active loader's version lookup during mod initialization. */
    public static void setResolver(Function<String, String> resolver) {
        PlatformInfo.resolver = Objects.requireNonNull(resolver);
    }

    /** The installed version of {@code namespace}, or {@code "unknown"} when it is not loaded. */
    public static String modVersion(String namespace) {
        return Objects.requireNonNull(
                resolver, "Platform version resolver has not been installed").apply(namespace);
    }
}
