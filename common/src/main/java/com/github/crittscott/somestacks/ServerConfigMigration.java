package com.github.crittscott.somestacks;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One-time migration of the preceding release's world JSON policy into documented TOML, run by
 * every loader before it reads the world's TOML.
 */
public final class ServerConfigMigration {
    private ServerConfigMigration() {}

    private static final Set<String> DEFAULT_INGOTS =
            Set.of("#forge:ingots*", "#c:ingots*", "#somestacks:ingots");

    /**
     * Converts the world policy before the loader selects its TOML override.
     *
     * @return the world's TOML override path, which may not exist
     * @throws IllegalStateException when a JSON policy exists but could not be converted
     */
    public static Path prepareWorld(MinecraftServer server) {
        Path directory = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig");
        Path json = directory.resolve(SomeStacksCommon.MODID + "-server.json");
        Path toml = directory.resolve(SomeStacksCommon.MODID + "-server.toml");
        convertJson(json, toml, ServerConfig.defaults());
        if (Files.exists(json) && !Files.exists(toml)) {
            throw new IllegalStateException(
                    "Repair the JSON server policy before migration: " + json);
        }
        return toml;
    }

    /**
     * Converts the JSON and keeps it as an archive. An existing TOML file is authoritative; a JSON
     * retained beside it is only inspected for custom ingot entries.
     */
    public static void convertJson(Path json, Path toml, ServerConfig.Settings defaults) {
        if (!Files.exists(json)) return;
        if (Files.exists(toml)) {
            reportRetainedIngots(json);
            return;
        }
        ServerConfig.Settings migrated = readJson(json, defaults);
        if (migrated != null && new TomlPolicyFile(toml).write(migrated)) archive(json);
    }

    /** Reads the shipped JSON schema, reporting fields that now belong in an item tag. */
    private static ServerConfig.Settings readJson(Path file, ServerConfig.Settings defaults) {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            CommentedConfig converted = CommentedConfig.of(TomlFormat.instance());
            for (ServerConfig.Section section : ServerConfig.Section.values()) {
                JsonElement element = root.get(section.key());
                if (element == null) continue;
                if (!element.isJsonObject()) {
                    SomeStacksCommon.LOGGER.warn(
                            "Ignoring non-object section {} in {}", section.key(), file);
                    continue;
                }
                for (var entry : element.getAsJsonObject().entrySet()) {
                    String key = section.key() + "." + entry.getKey();
                    if (section == ServerConfig.Section.COMPATIBILITY
                            && entry.getKey().equals("ingots")) {
                        reportIngots(file, entry.getValue());
                        continue;
                    }
                    Object value = jsonValue(entry.getValue());
                    if (value != null) converted.set(key, value);
                    else
                        SomeStacksCommon.LOGGER.warn(
                                "Ignoring invalid JSON policy field {} in {}", key, file);
                }
            }
            return TomlPolicyFile.decode(converted, defaults, file);
        } catch (IOException | RuntimeException exception) {
            SomeStacksCommon.LOGGER.warn(
                    "Cannot migrate server policy {}; leaving it intact for repair",
                    file,
                    exception);
            return null;
        }
    }

    private static Object jsonValue(JsonElement value) {
        if (value.isJsonArray()) {
            List<String> result = new ArrayList<>();
            for (JsonElement entry : value.getAsJsonArray()) {
                if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString())
                    result.add(entry.getAsString());
                else
                    SomeStacksCommon.LOGGER.warn(
                            "Ignoring non-string JSON policy list entry {}", entry);
            }
            return result;
        }
        if (!value.isJsonPrimitive()) return null;
        var primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (primitive.isString()) return primitive.getAsString();
        try {
            return primitive.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }

    private static void reportIngots(Path file, JsonElement value) {
        List<String> custom = new ArrayList<>();
        if (value.isJsonArray()) {
            for (JsonElement entry : value.getAsJsonArray()) {
                String text = entry.isJsonPrimitive() ? entry.getAsString() : entry.toString();
                if (!DEFAULT_INGOTS.contains(text)) custom.add(text);
            }
        } else custom.add(value.toString());
        if (!custom.isEmpty())
            SomeStacksCommon.LOGGER.warn(
                    "{} has custom compatibility.ingots entries {}. Add them to the somestacks:ingots item tag in a data pack; the original list is retained in the JSON file or its migration archive",
                    file,
                    custom);
    }

    /** Reports custom ingot entries in a JSON policy kept beside an authoritative TOML file. */
    private static void reportRetainedIngots(Path file) {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            JsonElement section = root.get(ServerConfig.Section.COMPATIBILITY.key());
            if (section != null && section.isJsonObject()) {
                JsonElement ingots = section.getAsJsonObject().get("ingots");
                if (ingots != null) reportIngots(file, ingots);
            }
        } catch (IOException | RuntimeException exception) {
            SomeStacksCommon.LOGGER.warn(
                    "Could not inspect retained ingot policy {}", file, exception);
        }
    }

    /** Called only after TOML persistence has succeeded. */
    private static void archive(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".migrated"));
            SomeStacksCommon.LOGGER.info(
                    "Migrated server policy {}; retained the original as .migrated", file);
        } catch (IOException exception) {
            SomeStacksCommon.LOGGER.warn(
                    "TOML saved but could not archive JSON policy {}", file, exception);
        }
    }
}
