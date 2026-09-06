package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacksNeoForge;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** NeoForge delegates for loader-neutral JSON and server-policy checks. */
@GameTestHolder(SomeStacksNeoForge.MODID)
@PrefixGameTestTemplate(false)
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
