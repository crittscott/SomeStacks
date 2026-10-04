package com.github.crittscott.somestacks;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** Loader-specific paths and installed-mod information. */
public final class PlatformServices {
    private static Supplier<Path> configFolderSupplier;
    private static Function<String, String> modVersionResolver;

    private PlatformServices() {}

    /** Installs the active loader's services during mod initialization. */
    public static void install(
            Supplier<Path> configFolderSupplier,
            Function<String, String> modVersionResolver) {
        PlatformServices.configFolderSupplier = Objects.requireNonNull(configFolderSupplier);
        PlatformServices.modVersionResolver = Objects.requireNonNull(modVersionResolver);
    }

    /** The game's {@code config} directory. */
    public static Path configFolder() {
        return Objects.requireNonNull(
                configFolderSupplier, "Platform services have not been installed").get();
    }

    /** This mod's directory beneath the game's {@code config} directory. */
    public static Path modConfigFolder() {
        return configFolder().resolve(SomeStacksCommon.MODID);
    }

    /** The installed version of {@code namespace}, or {@code "unknown"} when it is not loaded. */
    public static String modVersion(String namespace) {
        return Objects.requireNonNull(
                modVersionResolver, "Platform services have not been installed").apply(namespace);
    }
}
