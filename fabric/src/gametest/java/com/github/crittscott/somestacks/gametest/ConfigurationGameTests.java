package com.github.crittscott.somestacks.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric delegates for loader-neutral JSON and server-policy checks. */
public final class ConfigurationGameTests implements FabricGameTest {
    private static final String TEMPLATE = FabricGameTestSupport.TEMPLATE;

    /** See {@link ConfigurationChecks#overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes}. */
    @GameTest(template = TEMPLATE)
    public void overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes(GameTestHelper helper) {
        ConfigurationChecks.overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes(helper);
    }

    /** See {@link ConfigurationChecks#overrideJsonUsesInclusiveBounds}. */
    @GameTest(template = TEMPLATE)
    public void overrideJsonUsesInclusiveBounds(GameTestHelper helper) {
        ConfigurationChecks.overrideJsonUsesInclusiveBounds(helper);
    }

    /** See {@link ConfigurationChecks#serverConfigLoadsBoundsAndLists}. */
    @GameTest(template = TEMPLATE)
    public void serverConfigLoadsBoundsAndLists(GameTestHelper helper) {
        ConfigurationChecks.serverConfigLoadsBoundsAndLists(helper);
    }

    /** See {@link ConfigurationChecks#denyPoliciesRespectPlayerAutomationAndExistingContents}. */
    @GameTest(template = TEMPLATE)
    public void denyPoliciesRespectPlayerAutomationAndExistingContents(GameTestHelper helper) {
        ConfigurationChecks.denyPoliciesRespectPlayerAutomationAndExistingContents(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ConfigurationChecks#disabledTypesAndHeightLimitsRefusePlacementAndGrowthAtomically}. */
    @GameTest(template = TEMPLATE)
    public void disabledTypesAndHeightLimitsRefusePlacementAndGrowthAtomically(
            GameTestHelper helper) {
        ConfigurationChecks.disabledTypesAndHeightLimitsRefusePlacementAndGrowthAtomically(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ConfigurationChecks#serverCommandsEnforcePermissionsAndPersistEdits}. */
    @GameTest(template = TEMPLATE)
    public void serverCommandsEnforcePermissionsAndPersistEdits(GameTestHelper helper) {
        ConfigurationChecks.serverCommandsEnforcePermissionsAndPersistEdits(
                helper, FabricGameTestSupport.playerFactory(helper));
    }
    /** See {@link ConfigurationChecks#ingotTagControlsBarAndSinglesAdmission}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void ingotTagControlsBarAndSinglesAdmission(GameTestHelper helper) {
        ConfigurationChecks.ingotTagControlsBarAndSinglesAdmission(helper);
    }

}
