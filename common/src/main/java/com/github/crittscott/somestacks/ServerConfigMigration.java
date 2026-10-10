package com.github.crittscott.somestacks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reports server config the 1.21.1 release saved that the current mod no longer reads.
 *
 * <p>1.21.1 kept the Bar Stack ingot list in {@code compatibility.ingots} of
 * {@code <world>/serverconfig/somestacks-server.json} on every loader. Bar classification is now the
 * {@code somestacks:ingots} item tag, which already covers the released defaults; any other entry
 * is logged so the admin can move it into a data pack. The file is left untouched.
 */
public final class ServerConfigMigration {
    private ServerConfigMigration() {}

    private static final String CONFIG_DIRECTORY = "serverconfig";
    private static final String LEGACY_FILE_SUFFIX = "-server.json";
    private static final String SECTION_COMPATIBILITY = "compatibility";
    private static final String KEY_INGOTS = "ingots";

    /** The 1.21.1 default entries, all covered by the shipped {@code somestacks:ingots} tag. */
    private static final Set<String> RELEASED_DEFAULT_INGOTS =
            Set.of("#forge:ingots*", "#c:ingots*", "#somestacks:ingots");

    /** Logs ingot entries from a 1.21.1 world config. Call once at server start. */
    public static void reportLegacyIngots(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve(CONFIG_DIRECTORY)
                .resolve(SomeStacksCommon.MODID + LEGACY_FILE_SUFFIX);
        if (!Files.exists(file)) return;

        JsonArray ingots;
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file));
            if (!root.isJsonObject()) return;
            JsonElement compatibility = root.getAsJsonObject().get(SECTION_COMPATIBILITY);
            if (compatibility == null || !compatibility.isJsonObject()) return;
            JsonElement entries = ((JsonObject) compatibility).get(KEY_INGOTS);
            if (entries == null || !entries.isJsonArray()) return;
            ingots = entries.getAsJsonArray();
        } catch (Exception e) {
            SomeStacksCommon.LOGGER.warn("Could not check {} for a 1.21.1 ingot list: {}",
                    file.toAbsolutePath().normalize(), e.getMessage());
            return;
        }

        List<String> leftover = new ArrayList<>();
        for (JsonElement entry : ingots) {
            String text = entry.isJsonPrimitive() ? entry.getAsString() : entry.toString();
            if (!RELEASED_DEFAULT_INGOTS.contains(text)) leftover.add(text);
        }
        if (leftover.isEmpty()) return;

        SomeStacksCommon.LOGGER.warn(
                "{} lists Bar Stack ingots in compatibility.ingots, which is no longer read: {}. "
                        + "Add them to the somestacks:ingots item tag in a data pack "
                        + "(data/somestacks/tags/item/ingots.json), then remove that field.",
                file.toAbsolutePath().normalize(), leftover);
    }
}
