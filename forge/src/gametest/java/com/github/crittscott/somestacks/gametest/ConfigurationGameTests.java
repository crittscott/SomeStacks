package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraftforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraftforge.gametest.GameTestNamespace;

/** Forge delegates for loader-neutral JSON and server-policy checks. */
@GameTestNamespace(SomeStacks.MODID)
public final class ConfigurationGameTests {
    public ConfigurationGameTests() {}

    /** See {@link ConfigurationChecks#overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes}. */
    @GameTest(structure = GameTestSupport.TEMPLATE)
    public static void overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes(
            GameTestHelper helper) {
        ConfigurationChecks.overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes(helper);
    }

    /** See {@link ConfigurationChecks#overrideJsonUsesInclusiveBounds}. */
    @GameTest(structure = GameTestSupport.TEMPLATE)
    public static void overrideJsonUsesInclusiveBounds(
            GameTestHelper helper) {
        ConfigurationChecks.overrideJsonUsesInclusiveBounds(helper);
    }

    /** See {@link ConfigurationChecks#serverConfigLoadsBoundsAndLists}. */
    @GameTest(structure = GameTestSupport.TEMPLATE)
    public static void serverConfigLoadsBoundsAndLists(GameTestHelper helper) {
        ConfigurationChecks.serverConfigLoadsBoundsAndLists(helper);
    }

    /** See {@link ConfigurationChecks#denyPoliciesRespectPlayerAutomationAndExistingContents}. */
    @GameTest(structure = GameTestSupport.TEMPLATE)
    public static void denyPoliciesRespectPlayerAutomationAndExistingContents(
            GameTestHelper helper) {
        ConfigurationChecks.denyPoliciesRespectPlayerAutomationAndExistingContents(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link ConfigurationChecks#disabledTypesAndHeightLimitsRefusePlacementAndGrowthAtomically}. */
    @GameTest(structure = GameTestSupport.TEMPLATE)
    public static void disabledTypesAndHeightLimitsRefusePlacementAndGrowthAtomically(
            GameTestHelper helper) {
        ConfigurationChecks.disabledTypesAndHeightLimitsRefusePlacementAndGrowthAtomically(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link ConfigurationChecks#serverCommandsEnforcePermissionsAndPersistEdits}. */
    @GameTest(structure = GameTestSupport.TEMPLATE)
    public static void serverCommandsEnforcePermissionsAndPersistEdits(GameTestHelper helper) {
        ConfigurationChecks.serverCommandsEnforcePermissionsAndPersistEdits(
                helper, GameTestSupport.playerFactory(helper));
    }
    /** See {@link ConfigurationChecks#ingotTagControlsBarAndSinglesAdmission}. */
    @GameTest(structure = GameTestSupport.TEMPLATE)
    public static void ingotTagControlsBarAndSinglesAdmission(GameTestHelper helper) {
        ConfigurationChecks.ingotTagControlsBarAndSinglesAdmission(helper);
    }

    /**
     * To reproduce in-game: use /ss deny item add minecraft:diamond as an operator, inspect the
     * loader-managed TOML, and reopen the world. The saved denial is exposed through common policy.
     */
    @GameTest(structure = GameTestSupport.TEMPLATE)
    public static void nativeServerPolicyPersistsCommandLists(GameTestHelper helper) {
        com.github.crittscott.somestacks.ForgeServerConfig config =
                com.github.crittscott.somestacks.ForgeServerConfig.INSTANCE;
        com.github.crittscott.somestacks.ServerConfig.Settings original = config.reload();
        com.github.crittscott.somestacks.ServerConfig.Backend previous =
                com.github.crittscott.somestacks.ServerConfig.backend();
        try {
            com.github.crittscott.somestacks.ServerConfig.install(config);
            com.github.crittscott.somestacks.ServerConfig.apply(original);
            com.github.crittscott.somestacks.ServerConfig.addListEntry(
                    com.github.crittscott.somestacks.ServerConfig.DISABLE_ITEMS, "minecraft:diamond");
            GameTestScaffold.check(config.reload().disableItems().contains("minecraft:diamond"),
                    "Command edit did not reach native config values");
            GameTestScaffold.check(java.nio.file.Files.readString(config.path()).contains("minecraft:diamond"),
                    "Command edit did not reach the loader-managed TOML");
        } catch (java.io.IOException e) {
            throw helper.assertionException(Component.literal("Could not read native config: " + e));
        } finally {
            config.save(original);
            com.github.crittscott.somestacks.ServerConfig.install(previous);
            com.github.crittscott.somestacks.ServerConfig.reload();
        }
        helper.succeed();
    }

}
