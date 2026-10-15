package com.github.crittscott.somestacks.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric delegates for loader-neutral JSON and server-policy checks. */
public final class ConfigurationGameTests {
    private static final String TEMPLATE = FabricGameTestSupport.TEMPLATE;

    /** See {@link ConfigurationChecks#overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes(GameTestHelper helper) {
        ConfigurationChecks.overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes(helper);
    }

    /** See {@link ConfigurationChecks#overrideJsonUsesInclusiveBounds}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void overrideJsonUsesInclusiveBounds(GameTestHelper helper) {
        ConfigurationChecks.overrideJsonUsesInclusiveBounds(helper);
    }

    /** See {@link ConfigurationChecks#serverConfigLoadsBoundsAndLists}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void serverConfigLoadsBoundsAndLists(GameTestHelper helper) {
        ConfigurationChecks.serverConfigLoadsBoundsAndLists(helper);
    }

    /** See {@link ConfigurationChecks#denyPoliciesRespectPlayerAutomationAndExistingContents}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void denyPoliciesRespectPlayerAutomationAndExistingContents(GameTestHelper helper) {
        ConfigurationChecks.denyPoliciesRespectPlayerAutomationAndExistingContents(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ConfigurationChecks#disabledTypesAndHeightLimitsRefusePlacementAndGrowthAtomically}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void disabledTypesAndHeightLimitsRefusePlacementAndGrowthAtomically(
            GameTestHelper helper) {
        ConfigurationChecks.disabledTypesAndHeightLimitsRefusePlacementAndGrowthAtomically(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ConfigurationChecks#serverCommandsEnforcePermissionsAndPersistEdits}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void serverCommandsEnforcePermissionsAndPersistEdits(GameTestHelper helper) {
        ConfigurationChecks.serverCommandsEnforcePermissionsAndPersistEdits(
                helper, FabricGameTestSupport.playerFactory(helper));
    }
    /** See {@link ConfigurationChecks#ingotTagControlsBarAndSinglesAdmission}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void ingotTagControlsBarAndSinglesAdmission(GameTestHelper helper) {
        ConfigurationChecks.ingotTagControlsBarAndSinglesAdmission(helper);
    }

}
