package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.client.ItemRenderConfig;
import com.github.crittscott.somestacks.client.RenderMode;
import com.github.crittscott.somestacks.util.OverrideJsonCodec;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/** Loader-neutral render JSON parsing and server TOML policy checks. */
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

    /**
     * To reproduce: edit server TOML, run /ss reload, and verify bounds and deny lists.
     * Bar admission follows the somestacks:ingots data-pack tag; the config has no ingot list.
     */
    public static void serverConfigLoadsBoundsAndLists(GameTestHelper helper) {
        TomlPolicyChecks.verify(helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("serverconfig"), ServerConfig.defaults());
        ServerConfig.Backend original = ServerConfig.backend();
        ServerConfig.Settings policy = ServerConfig.settings();
        Path fixture = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT)
                .resolve("serverconfig/somestacks-gametest.toml");
        try {
            Files.createDirectories(fixture.getParent());
            Files.writeString(fixture, """
                    [piles]
                    max_pile_height = 999
                    [compatibility]
                    disable_mods = ["ExampleMod"]
                    [render_gallery]
                    placements_per_tick = 0
                    required_permission_level = -3
                    """);
            com.github.crittscott.somestacks.TomlServerConfig backend = new com.github.crittscott.somestacks.TomlServerConfig();
            ServerConfig.install(backend);
            ServerConfig.apply(backend.load(fixture));
            checkEquals(64, ServerConfig.maxPileHeight(), "Maximum height clamp");
            checkEquals(1, ServerConfig.renderGalleryPlacementsPerTick(), "Minimum placement budget");
            checkEquals(0, ServerConfig.galleryRequiredPermissionLevel(), "Minimum permission clamp");
            check(ServerConfig.isModDisabled("EXAMPLEMOD"), "Case-insensitive namespace denial");
            check(ServerConfig.DISABLE_ITEMS.get().contains("evilcraft:broom_part"), "Broom-part default missing");
            net.minecraft.world.item.ItemStack ingot = new net.minecraft.world.item.ItemStack(GameTestScaffold.firstBarItem());
            check(ingot.is(com.github.crittscott.somestacks.block.BarStackBE.INGOTS), "Bar fixture is not in the ingot tag");
            check(com.github.crittscott.somestacks.block.BarStackBE.isValidBarItem(ingot), "Tagged item was refused");
            ServerConfig.addListEntry(ServerConfig.DISABLE_ITEMS, "minecraft:diamond");
            check(Files.readString(fixture).contains("minecraft:diamond"), "List edit was not saved");
            check(!Files.readString(fixture).contains("compatibility.ingots"), "Retired ingot setting was written");
        } catch (IOException e) {
            throw new GameTestAssertException("Could not prepare TOML fixture: " + e);
        } finally {
            ServerConfig.install(original);
            ServerConfig.apply(policy);
            try { Files.deleteIfExists(fixture); }
            catch (IOException e) { throw new IllegalStateException(e); }
        }
        helper.succeed();
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
