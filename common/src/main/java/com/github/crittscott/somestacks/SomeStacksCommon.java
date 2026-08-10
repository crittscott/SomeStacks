package com.github.crittscott.somestacks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Shared mod identity for common code. The Forge entry point ({@code SomeStacks}, forge-only)
 * aliases its own {@code MODID}/{@code LOGGER} to these so loader-specific code that already
 * references them (including {@code @Mod.EventBusSubscriber(modid = ...)} annotations, which need a
 * compile-time constant) needs no changes.
 */
public final class SomeStacksCommon {
    private SomeStacksCommon() {
    }

    public static final String MODID = "somestacks";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
}
