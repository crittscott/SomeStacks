package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;

import com.github.crittscott.somestacks.ServerConfig;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/** Fabric TOML server-config checks plus delegates for loader-neutral configuration checks. */
public final class ConfigurationGameTests {
    private static final String TEMPLATE = FabricGameTestSupport.TEMPLATE;

    /** See {@link PolicyParityChecks#commandHelpAndNamespaceLimits}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void commandHelpAndNamespaceLimits(GameTestHelper helper) {
        PolicyParityChecks.commandHelpAndNamespaceLimits(helper);
    }

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

    /**
     * To reproduce in-game: use /ss deny item add minecraft:diamond as an operator and inspect the
     * active {@code somestacks-server.toml}. The denial is already saved there.
     */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void tomlServerPolicyPersistsCommandLists(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerConfig.Backend backend = ServerConfig.backend();
        ServerConfig.Settings original = ServerConfig.settings();
        try {
            check(
                    server.getCommands()
                                    .getDispatcher()
                                    .execute(
                                            "ss deny item add minecraft:diamond",
                                            server.createCommandSourceStack().withPermission(4))
                            > 0,
                    "Operator deny command failed");
            check(
                    backend.reload().disableItems().contains("minecraft:diamond"),
                    "Deny command was not saved to the active TOML");
        } catch (CommandSyntaxException e) {
            throw helper.assertionException(
                    Component.literal("Could not run or read the deny command: " + e));
        } finally {
            ServerConfig.apply(original);
            ServerConfig.save();
        }
        helper.succeed();
    }

    /** See {@link ConfigurationChecks#denyPoliciesRespectPlayerAutomationAndExistingContents}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void denyPoliciesRespectPlayerAutomationAndExistingContents(GameTestHelper helper) {
        ConfigurationChecks.denyPoliciesRespectPlayerAutomationAndExistingContents(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /**
     * See {@link
     * ConfigurationChecks#disabledTypesAndHeightLimitsRefusePlacementAndGrowthAtomically}.
     */
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

    /** See {@link TomlPolicyChecks#tomlPolicyParsesRoundTripsAndMigrates}. */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void tomlPolicyParsesRoundTripsAndMigrates(GameTestHelper helper) {
        TomlPolicyChecks.tomlPolicyParsesRoundTripsAndMigrates(helper);
    }
}
