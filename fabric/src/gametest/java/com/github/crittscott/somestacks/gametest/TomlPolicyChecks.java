package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.policy;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.ServerConfigMigration;
import com.github.crittscott.somestacks.TomlPolicyFile;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Fabric policy file checks on scratch files beside the world's serverconfig folder; they do not
 * install a backend or change the running world's policy. Run from {@link
 * CapabilityAndPersistenceGameTests#tomlPolicyParsesRoundTripsAndMigrates}, which describes the
 * in-game check.
 */
public final class TomlPolicyChecks {
    private TomlPolicyChecks() {}

    public static void verify(GameTestHelper helper) {
        ServerConfig.Settings defaults = ServerConfig.Settings.defaults();
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
            check(persistence.read(defaults).equals(defaults), "Missing TOML did not use defaults");
            String generated = Files.readString(toml);
            check(generated.contains("existing stacks keep working"), "Missing stack explanation");
            check(generated.contains("/ss deny"), "Missing deny-list explanation");
            check(generated.contains("Range: 1 ~ 64"), "Missing height bounds");
            check(generated.contains("Range: 0 ~ 4"), "Missing permission bounds");
            check(generated.contains("8 alternating layers"), "Missing Bar layer count");

            ServerConfig.Settings custom =
                    policy(
                            defaults,
                            Map.ofEntries(
                                    Map.entry(ServerConfig.Setting.MAX_PILE_HEIGHT, 5),
                                    Map.entry(
                                            ServerConfig.Setting.ENABLE_STORAGE_STACK_BLOCK, false),
                                    Map.entry(
                                            ServerConfig.Setting.ENABLE_SINGLES_STACK_BLOCK, true),
                                    Map.entry(ServerConfig.Setting.ENABLE_BAR_STACK_BLOCK, false),
                                    Map.entry(
                                            ServerConfig.Setting.DISABLE_MODS,
                                            List.of("examplemod")),
                                    Map.entry(
                                            ServerConfig.Setting.DISABLE_ITEMS,
                                            List.of("minecraft:stone")),
                                    Map.entry(ServerConfig.Setting.PLACEMENTS_PER_TICK, 17),
                                    Map.entry(ServerConfig.Setting.GALLERY_ENABLED, true),
                                    Map.entry(ServerConfig.Setting.REQUIRED_PERMISSION_LEVEL, 2),
                                    Map.entry(ServerConfig.Setting.GEN_MODS, List.of("minecraft")),
                                    Map.entry(
                                            ServerConfig.Setting.GEN_ITEMS,
                                            List.of("minecraft:dirt"))));
            check(persistence.write(custom), "Custom policy was not written");
            checkEquals(custom, persistence.read(defaults), "Written policy did not read back");

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
            checkEquals(defaults.maxPileHeight(), parsed.maxPileHeight(), "Malformed integer");
            check(!parsed.enableStorageStackBlock(), "Valid neighbor field was lost");
            checkEquals(List.of("minecraft:diamond"), parsed.disableItems(), "Multiline list");
            checkEquals(4, parsed.galleryPermissionLevel(), "High permission correction");
            checkEquals(1, parsed.renderGalleryPlacementsPerTick(), "Low speed correction");
            check(
                    persistence.write(parsed) && persistence.read(defaults).equals(parsed),
                    "TOML round trip failed");

            Files.writeString(
                    toml,
                    """
                    [piles]
                    max_pile_height = 99
                    [render_gallery]
                    required_permission_level = -1
                    """);
            parsed = persistence.read(defaults);
            checkEquals(64, parsed.maxPileHeight(), "High height correction");
            checkEquals(0, parsed.galleryPermissionLevel(), "Low permission correction");
            Files.writeString(toml, "[piles]\nmax_pile_height = 0\n");
            checkEquals(1, persistence.read(defaults).maxPileHeight(), "Low height correction");

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
            checkEquals(17, persistence.read(defaults).maxPileHeight(), "Migrated height");
            checkEquals(
                    List.of("minecraft:stone"),
                    persistence.read(defaults).disableItems(),
                    "Migrated deny list");
            Files.writeString(json, "{\"piles\":{\"max_pile_height\":2}}");
            ServerConfigMigration.convertJson(json, toml, defaults);
            checkEquals(
                    17, persistence.read(defaults).maxPileHeight(), "JSON overrode existing TOML");
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Could not prepare TOML persistence fixtures", exception);
        } finally {
            try {
                Files.deleteIfExists(toml);
                Files.deleteIfExists(json);
                Files.deleteIfExists(archive);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
        helper.succeed();
    }
}
