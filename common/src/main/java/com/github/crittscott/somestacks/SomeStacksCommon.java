package com.github.crittscott.somestacks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared mod identity for common code. Loader entry points use these constants so registry names,
 * logging, and metadata remain identical on Forge, NeoForge, and Fabric.
 */
public final class SomeStacksCommon {
    private SomeStacksCommon() {
    }

    public static final String MODID = "somestacks";
    public static final int PROTOCOL_VERSION = 3;
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
}
