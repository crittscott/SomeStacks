package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.client.ItemRenderConfig;
import com.github.crittscott.somestacks.client.RenderMode;
import com.github.crittscott.somestacks.util.OverrideJsonCodec;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/** Loader-neutral parsing and policy checks for the two JSON-backed configuration surfaces. */
public final class ConfigurationChecks {
    private ConfigurationChecks() {}

    public static void overrideJsonRoundTripsValidFieldsAndSkipsMalformedOnes(
            GameTestHelper helper) {
        JsonObject root = JsonParser.parseString("""
                {
                  "minecraft:stone": {
                    "mode": "block",
                    "scale": 1.25,
                    "offset": [-1.0, 0.0, 1.0]
                  },
                  "minecraft:dirt": {
                    "mode": "not-a-mode",
                    "scale": 2.0
                  },
                  "not an item id": {"mode": "3d"},
                  "minecraft:stick": {"offset": [0.0, 1.5]}
                }
                """).getAsJsonObject();

        Map<ResourceLocation, ItemRenderConfig> parsed =
                OverrideJsonCodec.parse(root, "GameTest fixture");
        checkEquals(2, parsed.size(), "Parsed override count");

        ItemRenderConfig stone = parsed.get(ResourceLocation.parse("minecraft:stone"));
        check(stone != null, "Stone override was skipped");
        checkEquals(RenderMode.BLOCK, stone.mode(), "Stone mode");
        checkEquals(1.25f, stone.scale(), "Stone scale");
        checkArrayEquals(new float[] {-1.0f, 0.0f, 1.0f}, stone.offset(), "Stone offset");

        ItemRenderConfig dirt = parsed.get(ResourceLocation.parse("minecraft:dirt"));
        check(dirt != null, "Valid field in a partially malformed override was skipped");
        checkEquals(null, dirt.mode(), "Invalid mode survived parsing");
        checkEquals(2.0f, dirt.scale(), "Valid scale beside an invalid mode was lost");

        JsonObject serialized = OverrideJsonCodec.toJson(parsed);
        checkEquals(List.of("minecraft:dirt", "minecraft:stone"),
                new ArrayList<>(serialized.keySet()), "Serialized item-id order");
        Map<ResourceLocation, ItemRenderConfig> reparsed =
                OverrideJsonCodec.parse(serialized, "GameTest round trip");
        checkConfigEquals(stone, reparsed.get(ResourceLocation.parse("minecraft:stone")),
                "Stone round trip");
        checkConfigEquals(dirt, reparsed.get(ResourceLocation.parse("minecraft:dirt")),
                "Dirt round trip");
        helper.succeed();
    }

    public static void overrideJsonUsesInclusiveBoundsForFilesAndNetworkValues(
            GameTestHelper helper) {
        check(OverrideJsonCodec.inRange(
                        OverrideJsonCodec.MIN_SCALE,
                        OverrideJsonCodec.MIN_SCALE,
                        OverrideJsonCodec.MAX_SCALE),
                "Minimum scale was excluded");
        check(OverrideJsonCodec.inRange(
                        OverrideJsonCodec.MAX_SCALE,
                        OverrideJsonCodec.MIN_SCALE,
                        OverrideJsonCodec.MAX_SCALE),
                "Maximum scale was excluded");
        check(!OverrideJsonCodec.inRange(
                        Float.NaN, OverrideJsonCodec.MIN_SCALE, OverrideJsonCodec.MAX_SCALE),
                "NaN was accepted");
        check(!OverrideJsonCodec.inRange(
                        Float.POSITIVE_INFINITY,
                        OverrideJsonCodec.MIN_SCALE,
                        OverrideJsonCodec.MAX_SCALE),
                "Infinity was accepted");

        JsonObject root = JsonParser.parseString("""
                {
                  "minecraft:stone": {
                    "scale": 0.01,
                    "offset": [-1.0, 0.0, 1.0]
                  },
                  "minecraft:dirt": {
                    "scale": 20.0,
                    "offset": [1.0, -1.0, 0.0]
                  }
                }
                """).getAsJsonObject();
        Map<ResourceLocation, ItemRenderConfig> parsed =
                OverrideJsonCodec.parse(root, "GameTest bounds fixture");
        checkEquals(OverrideJsonCodec.MIN_SCALE,
                parsed.get(ResourceLocation.parse("minecraft:stone")).scale(),
                "Parsed minimum scale");
        checkEquals(OverrideJsonCodec.MAX_SCALE,
                parsed.get(ResourceLocation.parse("minecraft:dirt")).scale(),
                "Parsed maximum scale");

        ItemRenderConfig sanitized = OverrideJsonCodec.sanitize(new ItemRenderConfig(
                RenderMode.GUI, Float.NaN, new float[] {0.0f, 0.0f}));
        checkEquals(RenderMode.GUI, sanitized.mode(), "Sanitize dropped a valid mode");
        checkEquals(null, sanitized.scale(), "Sanitize retained an invalid scale");
        checkEquals(null, sanitized.offset(), "Sanitize retained an invalid offset");

        ItemRenderConfig edge = OverrideJsonCodec.sanitize(new ItemRenderConfig(
                null,
                OverrideJsonCodec.MAX_SCALE,
                new float[] {
                        OverrideJsonCodec.MIN_OFFSET,
                        0.0f,
                        OverrideJsonCodec.MAX_OFFSET
                }));
        checkEquals(OverrideJsonCodec.MAX_SCALE, edge.scale(), "Sanitize rejected maximum scale");
        checkArrayEquals(new float[] {-1.0f, 0.0f, 1.0f}, edge.offset(),
                "Sanitize rejected endpoint offsets");
        helper.succeed();
    }

    public static void serverConfigLoadsBoundsListsAndIngotGlobs(GameTestHelper helper) {
        Path serverConfigDir = helper.getLevel().getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("serverconfig");
        Path original = serverConfigDir.resolve(SomeStacksCommon.MODID + "-server.json");
        Path fixture = serverConfigDir.resolve(SomeStacksCommon.MODID + "-gametest.json");
        Item knownIngot = GameTestScaffold.firstBarItem();
        String matchingTag = tagContaining(knownIngot);
        String glob = matchingTag.substring(0, matchingTag.length() - 1) + "*";

        try {
            Files.createDirectories(serverConfigDir);
            Files.writeString(fixture, configJson(999, -3, 0, glob));
            ServerConfig.load(fixture);

            checkEquals(64, ServerConfig.maxPileHeight(), "Maximum pile-height clamp");
            checkEquals(0, ServerConfig.galleryRequiredPermissionLevel(),
                    "Minimum gallery-permission clamp");
            checkEquals(1, ServerConfig.renderGalleryPlacementsPerTick(),
                    "Minimum gallery placement budget");
            check(!ServerConfig.enableStorageStackBlock(), "Storage enable flag");
            check(ServerConfig.enableSinglesStackBlock(), "Singles enable flag");
            check(!ServerConfig.enableBarStackBlock(), "Bar enable flag");
            check(ServerConfig.galleryEnabled(), "Gallery enable flag");
            checkEquals(List.of("minecraft"), ServerConfig.GEN_MODS.get(), "Gallery mod list");
            checkEquals(List.of("minecraft:stone"), ServerConfig.GEN_ITEMS.get(),
                    "Gallery item list");
            check(ServerConfig.isModDisabled("MINECRAFT"),
                    "Disabled mod lookup was not case-insensitive");
            check(ServerConfig.isModDisabled("examplemod"), "Disabled mod was not baked");
            check(ServerConfig.isItemDisabled(ResourceLocation.parse("minecraft:stone")),
                    "Disabled item was not baked");
            check(ServerConfig.isIngotItem(knownIngot), "Wildcard ingot tag did not admit its item");
            boolean foundNonIngot = false;
            for (Item item : BuiltInRegistries.ITEM) {
                if (!ServerConfig.isIngotItem(item)) {
                    foundNonIngot = true;
                    break;
                }
            }
            check(foundNonIngot, "Ingot glob unexpectedly admitted every registered item");

            Files.writeString(fixture, configJson(-9, 99, 7, matchingTag));
            ServerConfig.load(fixture);
            checkEquals(1, ServerConfig.maxPileHeight(), "Minimum pile-height clamp");
            checkEquals(4, ServerConfig.galleryRequiredPermissionLevel(),
                    "Maximum gallery-permission clamp");
            checkEquals(7, ServerConfig.renderGalleryPlacementsPerTick(),
                    "Positive gallery placement budget");
            check(ServerConfig.isIngotItem(knownIngot), "Exact ingot tag did not admit its item");
        } catch (IOException e) {
            throw new GameTestAssertException("Could not prepare server-config fixture: " + e);
        } finally {
            ServerConfig.load(original);
            try {
                Files.deleteIfExists(fixture);
            } catch (IOException e) {
                SomeStacksCommon.LOGGER.warn("Could not delete GameTest config fixture {}", fixture, e);
            }
        }
        helper.succeed();
    }

    private static String configJson(int height, int permission, int placements, String ingotTag) {
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
                    "disable_items": ["minecraft:stone", "not an item id"],
                    "ingot_tags": ["%s"]
                  },
                  "render_gallery": {
                    "placements_per_tick": %d,
                    "enabled": true,
                    "required_permission_level": %d,
                    "gen_mods": ["minecraft"],
                    "gen_items": ["minecraft:stone"]
                  }
                }
                """.formatted(height, ingotTag, placements, permission);
    }

    private static String tagContaining(Item item) {
        for (TagKey<Item> tagKey : BuiltInRegistries.ITEM.getTagNames().toList()) {
            var tag = BuiltInRegistries.ITEM.getTag(tagKey).orElse(null);
            if (tag == null) {
                continue;
            }
            for (var holder : tag) {
                if (holder.value() == item) {
                    return tagKey.location().toString();
                }
            }
        }
        throw new GameTestAssertException("Known Bar item belongs to no item tag");
    }

    private static void checkConfigEquals(
            ItemRenderConfig expected, ItemRenderConfig actual, String message) {
        check(actual != null, message + " was absent");
        checkEquals(expected.mode(), actual.mode(), message + " mode");
        checkEquals(expected.scale(), actual.scale(), message + " scale");
        checkArrayEquals(expected.offset(), actual.offset(), message + " offset");
    }

    private static void checkArrayEquals(float[] expected, float[] actual, String message) {
        if (expected == null || actual == null) {
            check(expected == actual, message + ": one offset was null");
            return;
        }
        checkEquals(expected.length, actual.length, message + " length");
        for (int i = 0; i < expected.length; i++) {
            checkEquals(expected[i], actual[i], message + " component " + i);
        }
    }
}
