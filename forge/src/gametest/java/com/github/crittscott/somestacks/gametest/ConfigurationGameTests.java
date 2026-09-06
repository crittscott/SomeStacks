package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;

/** Forge delegates for loader-neutral JSON and server-policy checks. */
@GameTestHolder(SomeStacks.MODID)
public final class ConfigurationGameTests {
    private ConfigurationGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes(
            GameTestHelper helper) {
        ConfigurationChecks.overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void overrideJsonUsesInclusiveBoundsForFilesAndNetworkValues(
            GameTestHelper helper) {
        ConfigurationChecks.overrideJsonUsesInclusiveBoundsForFilesAndNetworkValues(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void serverConfigLoadsBoundsListsAndIngotGlobs(GameTestHelper helper) {
        ConfigurationChecks.serverConfigLoadsBoundsListsAndIngotGlobs(helper);
    }
}
