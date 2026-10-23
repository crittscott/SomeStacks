package com.github.crittscott.somestacks;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** One-time migration of the preceding release's world JSON policy into documented TOML. */
public final class ServerConfigMigration {
    private ServerConfigMigration() {}
    private static final Set<String> DEFAULT_INGOTS = Set.of("#forge:ingots*", "#c:ingots*", "#somestacks:ingots");

    /** Reports a retained JSON ingot list on native loaders that already use TOML. */
    public static void reportLegacyIngots(net.minecraft.server.MinecraftServer server) {
        Path file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("serverconfig").resolve(SomeStacksCommon.MODID + "-server.json");
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            JsonElement section = root.get("compatibility");
            if (section != null && section.isJsonObject()) {
                JsonElement ingots = section.getAsJsonObject().get("ingots");
                if (ingots != null) reportIngots(file, ingots);
            }
        } catch (IOException | RuntimeException exception) {
            SomeStacksCommon.LOGGER.warn("Could not inspect retained ingot policy {}", file, exception);
        }
    }

    /** Keeps the JSON as an archive; an existing TOML file is authoritative. */
    public static void convertJson(Path json, Path toml, ServerConfig.Settings defaults) {
        if (Files.exists(toml) || !Files.exists(json)) return;
        ServerConfig.Settings migrated = readJson(json, defaults);
        if (migrated != null && new TomlPolicyFile(toml).write(migrated)) archive(json);
    }

    /** Reads the shipped JSON schema, reporting fields that now belong in an item tag. */
    public static ServerConfig.Settings readJson(Path file, ServerConfig.Settings defaults) {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            CommentedConfig converted = CommentedConfig.of(TomlFormat.instance());
            for (String section : List.of("piles", "stacks", "compatibility", "render_gallery")) {
                JsonElement element = root.get(section);
                if (element == null) continue;
                if (!element.isJsonObject()) {
                    SomeStacksCommon.LOGGER.warn("Ignoring non-object section {} in {}", section, file);
                    continue;
                }
                for (var entry : element.getAsJsonObject().entrySet()) {
                    String key = section + "." + entry.getKey();
                    if (key.equals("compatibility.ingots")) {
                        reportIngots(file, entry.getValue());
                        continue;
                    }
                    Object value = jsonValue(entry.getValue());
                    if (value != null) converted.set(key, value);
                    else SomeStacksCommon.LOGGER.warn("Ignoring invalid JSON policy field {} in {}", key, file);
                }
            }
            return TomlPolicyFile.decode(converted, defaults);
        } catch (IOException | RuntimeException exception) {
            SomeStacksCommon.LOGGER.warn("Cannot migrate server policy {}; leaving it intact for repair", file, exception);
            return null;
        }
    }

    private static Object jsonValue(JsonElement value) {
        if (value.isJsonArray()) {
            List<String> result = new ArrayList<>();
            for (JsonElement entry : value.getAsJsonArray()) {
                if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) result.add(entry.getAsString());
                else SomeStacksCommon.LOGGER.warn("Ignoring non-string JSON policy list entry {}", entry);
            }
            return result;
        }
        if (!value.isJsonPrimitive()) return null;
        var primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (primitive.isString()) return primitive.getAsString();
        try { return primitive.getAsBigDecimal().longValueExact(); }
        catch (ArithmeticException | NumberFormatException exception) { return null; }
    }

    private static void reportIngots(Path file, JsonElement value) {
        List<String> custom = new ArrayList<>();
        if (value.isJsonArray()) {
            for (JsonElement entry : value.getAsJsonArray()) {
                String text = entry.isJsonPrimitive() ? entry.getAsString() : entry.toString();
                if (!DEFAULT_INGOTS.contains(text)) custom.add(text);
            }
        } else custom.add(value.toString());
        if (!custom.isEmpty()) SomeStacksCommon.LOGGER.warn(
                "{} has custom compatibility.ingots entries {}. Add them to the somestacks:ingots item tag in a data pack; the original list is retained in the JSON file or its migration archive", file, custom);
    }

    /** Called only after TOML persistence has succeeded. */
    public static void archive(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".migrated"));
            SomeStacksCommon.LOGGER.info("Migrated server policy {}; retained the original as .migrated", file);
        } catch (IOException exception) {
            SomeStacksCommon.LOGGER.warn("TOML saved but could not archive JSON policy {}", file, exception);
        }
    }
}
