package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.ServerConfigMigration;
import com.github.crittscott.somestacks.TomlPolicyFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** File-boundary checks; they do not install a backend or change the running world's policy. */
public final class TomlPolicyChecks {
    private TomlPolicyChecks() {}

    /**
     * To reproduce: edit a Fabric TOML using comments, multiline lists and quoted item ids;
     * run /ss reload, then /ss deny item add minecraft:diamond and inspect the saved file.
     * A world JSON from the preceding release becomes a world TOML override with a JSON archive.
     */
    public static void verify(Path directory, ServerConfig.Settings defaults) {
        Path toml = directory.resolve("somestacks-toml-persistence-gametest.toml");
        Path json = directory.resolve("somestacks-toml-persistence-gametest.json");
        Path archive = json.resolveSibling(json.getFileName() + ".migrated");
        try {
            Files.createDirectories(directory);
            Files.deleteIfExists(toml);
            Files.deleteIfExists(json);
            Files.deleteIfExists(archive);
            TomlPolicyFile persistence = new TomlPolicyFile(toml);
            require(persistence.read(defaults).equals(defaults), "Missing TOML did not use defaults");
            String generated = Files.readString(toml);
            require(generated.contains("existing stacks keep working"), "Missing stack explanation");
            require(generated.contains("/ss deny"), "Missing deny-list explanation");
            require(generated.contains("Range: 1 ~ 64"), "Missing height bounds");
            Files.writeString(toml, """
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
            require(parsed.maxPileHeight() == defaults.maxPileHeight(), "Malformed integer did not default");
            require(!parsed.enableStorageStackBlock(), "Valid neighbor field was lost");
            require(parsed.disableItems().equals(List.of("minecraft:diamond")), "Multiline list failed");
            require(parsed.galleryPermissionLevel() == 4 && parsed.renderGalleryPlacementsPerTick() == 1, "Numeric bounds failed");
            require(persistence.write(parsed) && persistence.read(defaults).equals(parsed), "TOML round trip failed");

            Files.delete(toml);
            Files.writeString(json, """
                    {"piles":{"max_pile_height":17},"compatibility":{"disable_items":["minecraft:stone"]}}
                    """);
            ServerConfigMigration.convertJson(json, toml, defaults);
            require(Files.exists(archive) && !Files.exists(json), "JSON migration did not retain its archive");
            require(persistence.read(defaults).maxPileHeight() == 17, "Migration lost the height");
            require(persistence.read(defaults).disableItems().equals(List.of("minecraft:stone")), "Migration lost an explicit deny list");
            Files.writeString(json, "{\"piles\":{\"max_pile_height\":2}}");
            ServerConfigMigration.convertJson(json, toml, defaults);
            require(persistence.read(defaults).maxPileHeight() == 17, "JSON overrode existing TOML");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not prepare TOML persistence fixtures", exception);
        } finally {
            try {
                Files.deleteIfExists(toml);
                Files.deleteIfExists(json);
                Files.deleteIfExists(archive);
            } catch (IOException exception) { throw new IllegalStateException(exception); }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
