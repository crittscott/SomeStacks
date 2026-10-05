package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;

/** Forge delegates for loader-neutral JSON and server-policy checks. */
@GameTestHolder(SomeStacks.MODID)
public final class ConfigurationGameTests {
    private ConfigurationGameTests() {}

    /** See {@link ConfigurationChecks#overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes(
            GameTestHelper helper) {
        ConfigurationChecks.overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes(helper);
    }

    /** See {@link ConfigurationChecks#overrideJsonUsesInclusiveBoundsForFilesAndNetworkValues}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void overrideJsonUsesInclusiveBoundsForFilesAndNetworkValues(
            GameTestHelper helper) {
        ConfigurationChecks.overrideJsonUsesInclusiveBoundsForFilesAndNetworkValues(helper);
    }

    /** See {@link ConfigurationChecks#serverConfigLoadsBoundsListsAndIngotGlobs}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void serverConfigLoadsBoundsListsAndIngotGlobs(GameTestHelper helper) {
        ConfigurationChecks.serverConfigLoadsBoundsListsAndIngotGlobs(helper);
    }

    /** See {@link ConfigurationChecks#denyPoliciesRespectPlayerAutomationAndExistingContents}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void denyPoliciesRespectPlayerAutomationAndExistingContents(
            GameTestHelper helper) {
        ConfigurationChecks.denyPoliciesRespectPlayerAutomationAndExistingContents(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link ConfigurationChecks#disabledTypesAndHeightLimitsRefusePlacementAndGrowthAtomically}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void disabledTypesAndHeightLimitsRefusePlacementAndGrowthAtomically(
            GameTestHelper helper) {
        ConfigurationChecks.disabledTypesAndHeightLimitsRefusePlacementAndGrowthAtomically(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link ConfigurationChecks#serverCommandsEnforcePermissionsAndPersistEdits}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void serverCommandsEnforcePermissionsAndPersistEdits(GameTestHelper helper) {
        ConfigurationChecks.serverCommandsEnforcePermissionsAndPersistEdits(
                helper, GameTestSupport.playerFactory(helper));
    }
}
