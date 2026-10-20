package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.JsonServerConfig;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.SomeStacksCommon;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/** Fabric JSON server-config checks and delegates for loader-neutral configuration checks. */
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

    /**
     * Server policy loads independent fields, clamps numeric bounds, normalizes deny lists, and
     * validates JSON fields independently. To reproduce in-game: edit the world's
     * {@code somestacks-server.json}, run {@code /ss reload}, and verify the height limits, enabled
     * stack modes, denied deposits, gallery pacing, and Bar/Singles admission follow each valid
     * field while malformed fields revert independently to defaults.
     */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void serverConfigLoadsBoundsAndLists(GameTestHelper helper) {
        ServerConfig.Backend original = ServerConfig.backend();
        Path fixture = fixturePath(helper, SomeStacksCommon.MODID + "-gametest.json");

        try {
            Files.createDirectories(fixture.getParent());
            Files.deleteIfExists(fixture);
            loadFixture(fixture);

            Files.writeString(fixture, configJson(999, -3, 0));
            loadFixture(fixture);

            checkEquals(64, ServerConfig.maxPileHeight(), "Maximum pile-height clamp");
            checkEquals(0, ServerConfig.settings().galleryPermissionLevel(),
                    "Minimum gallery-permission clamp");
            checkEquals(1, ServerConfig.renderGalleryPlacementsPerTick(),
                    "Minimum gallery placement budget");
            check(!ServerConfig.enableStorageStackBlock(), "Storage enable flag");
            check(ServerConfig.enableSinglesStackBlock(), "Singles enable flag");
            check(!ServerConfig.enableBarStackBlock(), "Bar enable flag");
            check(ServerConfig.galleryEnabled(), "Gallery enable flag");
            checkEquals(List.of("minecraft"), ServerConfig.ListSetting.GEN_MODS.get(), "Gallery mod list");
            checkEquals(List.of("minecraft:stone"), ServerConfig.ListSetting.GEN_ITEMS.get(),
                    "Gallery item list");
            check(ServerConfig.isModDisabled("MINECRAFT"),
                    "Disabled mod lookup was not case-insensitive");
            check(ServerConfig.isModDisabled("examplemod"), "Disabled mod was not baked");
            check(ServerConfig.isItemDisabled(Identifier.parse("minecraft:stone")),
                    "Disabled item was not baked");
            Files.writeString(fixture, configJson(-9, 99, 7));
            loadFixture(fixture);
            checkEquals(1, ServerConfig.maxPileHeight(), "Minimum pile-height clamp");
            checkEquals(4, ServerConfig.settings().galleryPermissionLevel(),
                    "Maximum gallery-permission clamp");
            checkEquals(7, ServerConfig.renderGalleryPlacementsPerTick(),
                    "Positive gallery placement budget");
            Files.writeString(fixture, """
                    {
                      "piles": {"max_pile_height": "not-a-number"},
                      "stacks": {"enable_storage_stack_block": false},
                      "compatibility": {"disable_mods": ["minecraft", 7]}
                    }
                    """);
            check(ServerConfig.reload(), "Server config did not reload its current file");
            checkEquals(8, ServerConfig.maxPileHeight(),
                    "Malformed height did not fall back independently");
            check(!ServerConfig.enableStorageStackBlock(),
                    "Valid field beside a malformed field was lost");
            check(ServerConfig.enableSinglesStackBlock(),
                    "Missing field retained the previous file's value");
            checkEquals(List.of("minecraft"), ServerConfig.ListSetting.DISABLE_MODS.get(),
                    "Malformed list member invalidated valid members");
            checkEquals(ServerConfig.DEFAULT_DISABLE_ITEMS, ServerConfig.ListSetting.DISABLE_ITEMS.get(),
                    "Missing list did not fall back to its default");
        } catch (IOException e) {
            throw helper.assertionException(Component.literal("Could not prepare server-config fixture: " + e));
        } finally {
            restore(original, fixture);
        }
        helper.succeed();
    }

    /**
     * To reproduce in-game: use /ss deny item add minecraft:diamond as an operator and inspect the
     * world's {@code somestacks-server.json}. The denial is already written there.
     */
    @GameTest(structure = TEMPLATE, maxTicks = 100)
    public void jsonServerPolicyPersistsCommandLists(GameTestHelper helper) {
        ServerConfig.Backend original = ServerConfig.backend();
        Path fixture = fixturePath(helper, SomeStacksCommon.MODID + "-commands-gametest.json");
        try {
            Files.createDirectories(fixture.getParent());
            Files.deleteIfExists(fixture);
            loadFixture(fixture);
            var operator = helper.getLevel().getServer().createCommandSourceStack()
                    .withPermission(LevelBasedPermissionSet.OWNER);
            helper.getLevel().getServer().getCommands().performPrefixedCommand(
                    operator, "ss deny item add minecraft:diamond");
            JsonObject written = JsonParser.parseString(Files.readString(fixture)).getAsJsonObject();
            check(written.getAsJsonObject("compatibility").getAsJsonArray("disable_items")
                            .contains(new JsonPrimitive("minecraft:diamond")),
                    "Deny command was not written to the JSON file");
        } catch (IOException e) {
            throw helper.assertionException(Component.literal("Could not read JSON config: " + e));
        } finally {
            restore(original, fixture);
        }
        helper.succeed();
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

    private static void loadFixture(Path fixture) {
        JsonServerConfig backend = new JsonServerConfig();
        ServerConfig.Backend previousBackend = ServerConfig.backend();
        ServerConfig.Settings previousSettings = ServerConfig.settings();
        ServerConfig.Settings loaded = backend.load(fixture);
        check(ServerConfig.backend() == previousBackend, "JSON load installed itself");
        check(ServerConfig.settings() == previousSettings, "JSON load published policy");
        ServerConfig.install(backend);
        ServerConfig.apply(loaded);
    }

    private static Path fixturePath(GameTestHelper helper, String name) {
        return helper.getLevel().getServer().getWorldPath(LevelResource.ROOT)
                .resolve("serverconfig").resolve(name);
    }

    /** Reinstalls the world's own backend, rereads its file, and deletes the fixture. */
    private static void restore(ServerConfig.Backend original, Path fixture) {
        ServerConfig.install(original);
        ServerConfig.reload();
        try {
            Files.deleteIfExists(fixture);
        } catch (IOException e) {
            SomeStacksCommon.LOGGER.warn("Could not delete GameTest config fixture {}", fixture, e);
        }
    }

    private static String configJson(int height, int permission, int placements) {
        return """
                {
                  "piles": {"max_pile_height": %d},
                  "stacks": {
                    "enable_storage_stack_block": false,
                    "enable_singles_stack_block": true,
                    "enable_bar_stack_block": false
                  },
                  "compatibility": {
                    "disable_mods": [" MineCraft ", "ExampleMod"],
                    "disable_items": ["minecraft:stone", "not an item id"]
                  },
                  "render_gallery": {
                    "placements_per_tick": %d,
                    "enabled": true,
                    "required_permission_level": %d,
                    "gen_mods": ["minecraft"],
                    "gen_items": ["minecraft:stone"]
                  }
                }
                """.formatted(height, placements, permission);
    }
}
