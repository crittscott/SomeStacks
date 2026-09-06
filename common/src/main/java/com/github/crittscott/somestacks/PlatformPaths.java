package com.github.crittscott.somestacks;

import dev.architectury.injectables.annotations.ExpectPlatform;

import java.nio.file.Path;

/** Loader-specific filesystem locations resolved through the active mod loader. */
public final class PlatformPaths {
    private PlatformPaths() {}

    /** The game's {@code config} directory. */
    @ExpectPlatform
    public static Path configFolder() {
        throw new AssertionError();
    }

    /** This mod's directory beneath the game's {@code config} directory. */
    public static Path modConfigFolder() {
        return configFolder().resolve(SomeStacksCommon.MODID);
    }
}
