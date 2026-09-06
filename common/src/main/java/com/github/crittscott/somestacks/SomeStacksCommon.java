package com.github.crittscott.somestacks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Shared mod identity for common code. Loader entry points use these constants so registry names,
 * logging, and metadata remain identical on Forge, NeoForge, and Fabric.
 */
public final class SomeStacksCommon {
    private SomeStacksCommon() {
    }

    public static final String MODID = "somestacks";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
}
