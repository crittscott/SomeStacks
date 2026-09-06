package com.github.crittscott.somestacks.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric delegates for loader-neutral JSON and server-policy checks. */
public final class ConfigurationGameTests implements FabricGameTest {
    private static final String TEMPLATE = FabricGameTestSupport.TEMPLATE;

    @GameTest(template = TEMPLATE)
    public void overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes(GameTestHelper helper) {
        ConfigurationChecks.overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes(helper);
    }

    @GameTest(template = TEMPLATE)
    public void overrideJsonUsesInclusiveBoundsForFilesAndNetworkValues(GameTestHelper helper) {
        ConfigurationChecks.overrideJsonUsesInclusiveBoundsForFilesAndNetworkValues(helper);
    }

    @GameTest(template = TEMPLATE)
    public void serverConfigLoadsBoundsListsAndIngotGlobs(GameTestHelper helper) {
        ConfigurationChecks.serverConfigLoadsBoundsListsAndIngotGlobs(helper);
    }
}
