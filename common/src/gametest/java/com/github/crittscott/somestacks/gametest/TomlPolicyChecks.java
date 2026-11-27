package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.policy;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.ServerConfigMigration;
import com.github.crittscott.somestacks.TomlPolicyFile;

import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** File-boundary checks; they do not install a backend or change the running world's policy. */
public final class TomlPolicyChecks {
    private TomlPolicyChecks() {}

    /**
     * The TOML policy file is written with explanations and ranges, parses malformed fields
     * independently, clamps numbers, round-trips a custom policy, and a world JSON policy from the
     * preceding release migrates to TOML with an archive. To reproduce in-game: on Fabric, edit
     * {@code config/somestacks-server.toml} using comments, multiline lists, and quoted item ids,
     * run /ss reload, then /ss deny item add minecraft:diamond and inspect the saved file. On any
     * loader, put the preceding release's {@code <world>/serverconfig/somestacks-server.json} in a
     * world and start it: a world TOML with the same values appears and the JSON is kept as {@code
     * somestacks-server.json.migrated}.
     */
    public static void tomlPolicyParsesRoundTripsAndMigrates(GameTestHelper helper) {
        ServerConfig.Settings defaults = ServerConfig.defaults();
        Path directory =
                helper.getLevel()
                        .getServer()
                        .getWorldPath(LevelResource.ROOT)
                        .resolve("serverconfig");
        Path toml = directory.resolve("somestacks-toml-persistence-gametest.toml");
        Path json = directory.resolve("somestacks-toml-persistence-gametest.json");
        Path archive = json.resolveSibling(json.getFileName() + ".migrated");
        try {
            Files.createDirectories(directory);
            Files.deleteIfExists(toml);
            Files.deleteIfExists(json);
            Files.deleteIfExists(archive);
            TomlPolicyFile persistence = new TomlPolicyFile(toml);
            checkEquals(defaults, persistence.read(defaults), "Missing TOML did not use defaults");
            String generated = Files.readString(toml);
            check(generated.contains("existing stacks keep working"), "Missing stack explanation");
            check(generated.contains("/ss deny"), "Missing deny-list explanation");
            check(generated.contains("Range: 1 ~ 64"), "Missing height bounds");
            check(generated.contains("Range: 0 ~ 4"), "Missing permission bounds");
            check(generated.contains("Range: 1 ~ 2147483647"), "Missing placement bounds");

            ServerConfig.Settings custom =
                    policy(
                            defaults,
                            Map.ofEntries(
                                    Map.entry(ServerConfig.Setting.MAX_PILE_HEIGHT, 17),
                                    Map.entry(
                                            ServerConfig.Setting.ENABLE_STORAGE_STACK_BLOCK,
                                            false),
                                    Map.entry(
                                            ServerConfig.Setting.ENABLE_SINGLES_STACK_BLOCK,
                                            true),
                                    Map.entry(ServerConfig.Setting.ENABLE_BAR_STACK_BLOCK, false),
                                    Map.entry(
                                            ServerConfig.Setting.DISABLE_MODS,
                                            List.of("examplemod")),
                                    Map.entry(
                                            ServerConfig.Setting.DISABLE_ITEMS,
                                            List.of("minecraft:stone", "minecraft:dirt")),
                                    Map.entry(ServerConfig.Setting.PLACEMENTS_PER_TICK, 5),
                                    Map.entry(ServerConfig.Setting.GALLERY_ENABLED, true),
                                    Map.entry(ServerConfig.Setting.REQUIRED_PERMISSION_LEVEL, 1),
                                    Map.entry(ServerConfig.Setting.GEN_MODS, List.of("minecraft")),
                                    Map.entry(
                                            ServerConfig.Setting.GEN_ITEMS,
                                            List.of("minecraft:diamond"))));
            check(persistence.write(custom), "Custom policy was not written");
            checkEquals(custom, persistence.read(defaults), "Custom policy did not read back");

            Files.writeString(
                    toml,
                    """
                    # A valid TOML file with a malformed field beside valid values.
                    [piles]
                    max_pile_height = "bad"
                    [stacks]
                    enable_storage_stack_block = false
                    [compatibility]
                    disable_items = [
                        'minecraft:diamond', # literal string and trailing comma
                    ]
                    [render_gallery]
                    required_permission_level = 99
                    placements_per_tick = 0
                    """);
            ServerConfig.Settings parsed = persistence.read(defaults);
            checkEquals(
                    defaults.maxPileHeight(),
                    parsed.maxPileHeight(),
                    "Malformed integer did not default");
            check(!parsed.enableStorageStackBlock(), "Valid neighbor field was lost");
            checkEquals(
                    List.of("minecraft:diamond"), parsed.disableItems(), "Multiline list failed");
            checkEquals(4, parsed.galleryPermissionLevel(), "High permission was not corrected");
            checkEquals(
                    1, parsed.renderGalleryPlacementsPerTick(), "Low placements were not corrected");
            check(persistence.write(parsed), "Parsed policy was not written");
            checkEquals(parsed, persistence.read(defaults), "TOML round trip failed");

            Files.writeString(
                    toml,
                    """
                    [piles]
                    max_pile_height = 0
                    [render_gallery]
                    required_permission_level = -1
                    """);
            ServerConfig.Settings low = persistence.read(defaults);
            checkEquals(1, low.maxPileHeight(), "Low height was not corrected");
            checkEquals(0, low.galleryPermissionLevel(), "Low permission was not corrected");
            Files.writeString(toml, "[piles]\nmax_pile_height = 65\n");
            checkEquals(
                    64, persistence.read(defaults).maxPileHeight(), "High height was not corrected");

            Files.delete(toml);
            Files.writeString(
                    json,
                    """
                    {"piles":{"max_pile_height":17},"compatibility":{"disable_items":["minecraft:stone"]}}
                    """);
            ServerConfigMigration.convertJson(json, toml, defaults);
            check(
                    Files.exists(archive) && !Files.exists(json),
                    "JSON migration did not retain its archive");
            checkEquals(17, persistence.read(defaults).maxPileHeight(), "Migration lost the height");
            checkEquals(
                    List.of("minecraft:stone"),
                    persistence.read(defaults).disableItems(),
                    "Migration lost an explicit deny list");
            Files.writeString(json, "{\"piles\":{\"max_pile_height\":2}}");
            ServerConfigMigration.convertJson(json, toml, defaults);
            checkEquals(
                    17, persistence.read(defaults).maxPileHeight(), "JSON overrode existing TOML");
        } catch (IOException exception) {
            throw new GameTestAssertException(
                    "Could not prepare TOML persistence fixtures: " + exception);
        } finally {
            try {
                Files.deleteIfExists(toml);
                Files.deleteIfExists(json);
                Files.deleteIfExists(archive);
            } catch (IOException exception) {
                throw new GameTestAssertException(
                        "Could not delete TOML persistence fixtures: " + exception);
            }
        }
        helper.succeed();
    }
}
