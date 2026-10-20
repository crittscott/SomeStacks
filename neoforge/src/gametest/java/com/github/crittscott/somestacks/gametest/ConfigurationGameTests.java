package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.NeoForgeServerConfig;
import com.github.crittscott.somestacks.ServerConfig;
import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/** NeoForge native server-config check and delegates for loader-neutral configuration checks. */
public final class ConfigurationGameTests {
    private ConfigurationGameTests() {}

    /** See {@link ConfigurationChecks#overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes}. */
    public static void overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes(
            GameTestHelper helper) {
        ConfigurationChecks.overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes(helper);
    }

    /** See {@link ConfigurationChecks#overrideJsonUsesInclusiveBounds}. */
    public static void overrideJsonUsesInclusiveBounds(
            GameTestHelper helper) {
        ConfigurationChecks.overrideJsonUsesInclusiveBounds(helper);
    }

    /** See {@link ConfigurationChecks#denyPoliciesRespectPlayerAutomationAndExistingContents}. */
    public static void denyPoliciesRespectPlayerAutomationAndExistingContents(
            GameTestHelper helper) {
        ConfigurationChecks.denyPoliciesRespectPlayerAutomationAndExistingContents(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link ConfigurationChecks#disabledTypesAndHeightLimitsRefusePlacementAndGrowthAtomically}. */
    public static void disabledTypesAndHeightLimitsRefusePlacementAndGrowthAtomically(
            GameTestHelper helper) {
        ConfigurationChecks.disabledTypesAndHeightLimitsRefusePlacementAndGrowthAtomically(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link ConfigurationChecks#serverCommandsEnforcePermissionsAndPersistEdits}. */
    public static void serverCommandsEnforcePermissionsAndPersistEdits(GameTestHelper helper) {
        ConfigurationChecks.serverCommandsEnforcePermissionsAndPersistEdits(
                helper, GameTestSupport.playerFactory(helper));
    }
    /** See {@link ConfigurationChecks#ingotTagControlsBarAndSinglesAdmission}. */
    public static void ingotTagControlsBarAndSinglesAdmission(GameTestHelper helper) {
        ConfigurationChecks.ingotTagControlsBarAndSinglesAdmission(helper);
    }

    /**
     * To reproduce in-game: use /ss deny item add minecraft:diamond as an operator, inspect the
     * loader-managed TOML, and reopen the world. The saved denial is exposed through common policy.
     */
    public static void nativeServerPolicyPersistsCommandLists(GameTestHelper helper) {
        NeoForgeServerConfig config = NeoForgeServerConfig.INSTANCE;
        ServerConfig.Settings original = config.reload();
        ServerConfig.Backend previous = ServerConfig.backend();
        try {
            ServerConfig.install(config);
            ServerConfig.apply(original);
            ServerConfig.addListEntry(
                    ServerConfig.ListSetting.DISABLE_ITEMS, "minecraft:diamond");
            GameTestScaffold.check(config.reload().disableItems().contains("minecraft:diamond"),
                    "Command edit did not reach native config values");
            GameTestScaffold.check(Files.readString(config.path()).contains("minecraft:diamond"),
                    "Command edit did not reach the loader-managed TOML");
        } catch (IOException e) {
            throw helper.assertionException(Component.literal("Could not read native config: " + e));
        } finally {
            config.save(original);
            ServerConfig.install(previous);
            ServerConfig.reload();
        }
        helper.succeed();
    }

}
