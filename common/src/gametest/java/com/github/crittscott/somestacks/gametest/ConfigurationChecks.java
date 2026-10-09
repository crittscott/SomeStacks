package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.JsonServerConfig;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.renderconfig.OverrideJsonCodec;
import com.github.crittscott.somestacks.renderconfig.RenderMode;
import com.github.crittscott.somestacks.renderconfig.RenderOffset;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.server.StackInteractions;
import com.github.crittscott.somestacks.util.StackMode;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/** Loader-neutral parsing and policy checks for the two JSON-backed configuration surfaces. */
public final class ConfigurationChecks {
    private ConfigurationChecks() {}

    /**
     * Valid render-override fields round-trip in stable item-id order while malformed entries and
     * fields are skipped independently. No in-game reproduction applies: this directly verifies
     * the JSON codec rather than a player-visible action.
     */
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
        checkOffsetEquals(new RenderOffset(-1.0f, 0.0f, 1.0f), stone.offset(), "Stone offset");

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

    /**
     * Render scale and offset bounds are inclusive, finite, and shared by authored file values.
     * No in-game reproduction applies: this directly verifies validation at the serialization
     * boundary.
     */
    public static void overrideJsonUsesInclusiveBounds(
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
                RenderMode.GUI, Float.NaN, new RenderOffset(Float.NaN, 0.0f, 0.0f)));
        checkEquals(RenderMode.GUI, sanitized.mode(), "Sanitize dropped a valid mode");
        checkEquals(null, sanitized.scale(), "Sanitize retained an invalid scale");
        checkEquals(null, sanitized.offset(), "Sanitize retained an invalid offset");

        ItemRenderConfig edge = OverrideJsonCodec.sanitize(new ItemRenderConfig(
                null,
                OverrideJsonCodec.MAX_SCALE,
                new RenderOffset(
                        OverrideJsonCodec.MIN_OFFSET,
                        0.0f,
                        OverrideJsonCodec.MAX_OFFSET
                )));
        checkEquals(OverrideJsonCodec.MAX_SCALE, edge.scale(), "Sanitize rejected maximum scale");
        checkOffsetEquals(new RenderOffset(-1.0f, 0.0f, 1.0f), edge.offset(),
                "Sanitize rejected endpoint offsets");
        helper.succeed();
    }

    /**
     * Server policy loads independent fields, clamps numeric bounds, normalizes deny lists, and
     * validates Fabric JSON fields independently. To reproduce in-game on Fabric: edit the world's
     * {@code somestacks-server.json}, run {@code /ss reload}, and verify the height limits, enabled
     * stack modes, denied deposits, gallery pacing, and Bar/Singles admission follow each valid
     * field while malformed fields revert independently to defaults.
     */
    public static void serverConfigLoadsBoundsAndLists(GameTestHelper helper) {
        Path serverConfigDir = helper.getLevel().getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("serverconfig");
        ServerConfig.Backend original = ServerConfig.backend();
        Path fixture = serverConfigDir.resolve(SomeStacksCommon.MODID + "-gametest.json");

        try {
            Files.createDirectories(serverConfigDir);
            Files.deleteIfExists(fixture);
            new JsonServerConfig().load(fixture);

            Files.writeString(fixture, configJson(999, -3, 0));
            new JsonServerConfig().load(fixture);

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
            Files.writeString(fixture, configJson(-9, 99, 7));
            new JsonServerConfig().load(fixture);
            checkEquals(1, ServerConfig.maxPileHeight(), "Minimum pile-height clamp");
            checkEquals(4, ServerConfig.galleryRequiredPermissionLevel(),
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
            checkEquals(List.of("minecraft"), ServerConfig.DISABLE_MODS.get(),
                    "Malformed list member invalidated valid members");
            checkEquals(List.of(), ServerConfig.DISABLE_ITEMS.get(),
                    "Missing list retained the previous file's value");
        } catch (IOException e) {
            throw new GameTestAssertException("Could not prepare server-config fixture: " + e);
        } finally {
            ServerConfig.install(original);
            ServerConfig.reload();
            try {
                Files.deleteIfExists(fixture);
            } catch (IOException e) {
                SomeStacksCommon.LOGGER.warn("Could not delete GameTest config fixture {}", fixture, e);
            }
        }
        helper.succeed();
    }

    /**
     * Exact-item denial applies to player deposits but not automation, namespace denial applies to
     * both, and contents already stored remain extractable. To reproduce in-game: deny one item,
     * try it by hand and by a pipe, then deny its namespace after storing it. The pipe can insert
     * through the exact-item rule, the namespace rule refuses new input, and the old contents can
     * still be removed.
     */
    public static void denyPoliciesRespectPlayerAutomationAndExistingContents(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerConfig.Backend original = ServerConfig.backend();
        Path fixture = serverConfigPath(helper, SomeStacksCommon.MODID + "-policy-gametest.json");
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, GameTestScaffold.ORIGIN);
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.STONE, 4));
        try {
            Files.createDirectories(fixture.getParent());
            Files.writeString(fixture, policyJson(8, true, true, true,
                    List.of(), List.of("minecraft:stone")));
            new JsonServerConfig().load(fixture);
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            StackInteractions.handleExistingStack(
                    player, InteractionHand.MAIN_HAND, centerHit(storage.getBlockPos()),
                    com.github.crittscott.somestacks.util.BlockType.STORAGE_STACK);
            checkEquals(4, player.getMainHandItem().getCount(),
                    "Exact-item denial changed the player hand");
            checkEquals(0, GameTestScaffold.count(storage.getItems(), Items.STONE),
                    "Exact-item denial admitted a player deposit");

            checkEquals(2, storage.itemRun().insertAt(
                    0, new ItemStack(Items.STONE, 2), false),
                    "Exact-item denial reached automation");
            Files.writeString(fixture, policyJson(8, true, true, true,
                    List.of("minecraft"), List.of()));
            new JsonServerConfig().load(fixture);
            checkEquals(0, storage.itemRun().insertAt(
                    1, new ItemStack(Items.STONE), false),
                    "Namespace denial did not reach automation");
            checkEquals(2, storage.itemRun().extract(0, 64, false).getCount(),
                    "Stored denied contents were not extractable");
        } catch (IOException e) {
            throw new GameTestAssertException("Could not prepare policy fixture: " + e);
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
            restoreConfig(original, fixture);
        }
        helper.succeed();
    }

    /**
     * Disabled types and the maximum run height refuse placement and growth without spending the
     * input. To reproduce in-game: disable Bar placement, cap runs at one block, and try placement,
     * player growth, and automation growth. No second block appears and every held item remains.
     */
    public static void disabledTypesAndHeightLimitsRefusePlacementAndGrowthAtomically(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerConfig.Backend original = ServerConfig.backend();
        Path fixture = serverConfigPath(helper, SomeStacksCommon.MODID + "-limits-gametest.json");
        try {
            Files.createDirectories(fixture.getParent());
            Files.writeString(fixture, policyJson(1, true, true, false, List.of(), List.of()));
            new JsonServerConfig().load(fixture);

            StorageStackBE storage = GameTestScaffold.placeStorage(helper, GameTestScaffold.ORIGIN);
            for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
                storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
            }
            checkEquals(StorageStackBE.SLOTS, storage.itemRun().advertisedSlots(),
                    "Height-capped run advertised headroom");
            checkEquals(0, storage.itemRun().insertAt(
                    StorageStackBE.SLOTS, new ItemStack(Items.STONE), false),
                    "Height-capped automation grew the run");
            ItemStack deposit = new ItemStack(Items.STONE);
            checkEquals(0, storage.deposit(deposit, playerFactory.apply(ItemStack.EMPTY)),
                    "Height-capped player deposit grew the run");
            checkEquals(1, deposit.getCount(), "Rejected deposit spent its input");
            helper.assertBlockNotPresent(CommonRegistry.storageStackBlock(),
                    GameTestScaffold.ORIGIN.above());

            BlockPos supportRelative = GameTestScaffold.ORIGIN.east(4);
            BlockPos support = helper.absolutePos(supportRelative);
            helper.setBlock(supportRelative, Blocks.STONE);
            Item barItem = GameTestScaffold.firstBarItem();
            ServerPlayer player = playerFactory.apply(new ItemStack(barItem, 3));
            ServerGestureState.set(player, StackMode.BAR_STACK, true);
            try {
                StackInteractions.handleAdjacentClick(
                        player,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(
                                new Vec3(support.getX() + 0.5, support.getY() + 1.0,
                                        support.getZ() + 0.5),
                                Direction.UP,
                                support,
                                false),
                        true,
                        true);
                checkEquals(3, player.getMainHandItem().getCount(),
                        "Disabled Bar placement spent the hand");
                helper.assertBlockNotPresent(CommonRegistry.barStackBlock(),
                        supportRelative.above());
            } finally {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                ServerGestureState.clear(player.getUUID());
            }

            Files.writeString(fixture, policyJson(8, true, true, false, List.of(), List.of()));
            new JsonServerConfig().load(fixture);
            BarStackBE bars = GameTestScaffold.placeBar(
                    helper, GameTestScaffold.ORIGIN.east(7));
            for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
                bars.getItems().insertItem(slot, new ItemStack(barItem), false);
            }
            checkEquals(0, bars.itemRun().insertAt(
                    BarStackBE.SLOTS, new ItemStack(barItem), false),
                    "Disabled Bar type allowed automation growth");

            Files.writeString(fixture, policyJson(8, true, true, true, List.of(), List.of()));
            new JsonServerConfig().load(fixture);
            ServerPlayer invalidPlayer = playerFactory.apply(new ItemStack(Items.STICK, 2));
            ServerGestureState.set(invalidPlayer, StackMode.BAR_STACK, true);
            try {
                StackInteractions.handleAdjacentClick(
                        invalidPlayer,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false),
                        true,
                        true);
                checkEquals(2, invalidPlayer.getMainHandItem().getCount(),
                        "Invalid first Bar deposit spent the hand");
                helper.assertBlockNotPresent(CommonRegistry.barStackBlock(),
                        supportRelative.above());
            } finally {
                invalidPlayer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                ServerGestureState.clear(invalidPlayer.getUUID());
            }
        } catch (IOException e) {
            throw new GameTestAssertException("Could not prepare limit fixture: " + e);
        } finally {
            restoreConfig(original, fixture);
        }
        helper.succeed();
    }

    /**
     * Server commands enforce permissions and save list edits immediately. To reproduce in-game:
     * try the administrative list commands without permission, then as an operator, and inspect
     * {@code somestacks-server.json}. Only the operator edits succeed and they are already on disk.
     */
    public static void serverCommandsEnforcePermissionsAndPersistEdits(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerConfig.Backend original = ServerConfig.backend();
        Path fixture = serverConfigPath(helper, SomeStacksCommon.MODID + "-commands-gametest.json");
        MinecraftServer server = helper.getLevel().getServer();
        try {
            Files.createDirectories(fixture.getParent());
            Files.writeString(fixture, policyJson(8, true, true, true, List.of(), List.of()));
            new JsonServerConfig().load(fixture);
            var commands = server.getCommands();
            var lowPermission = server.createCommandSourceStack().withPermission(0);
            checkEquals(0, execute(commands.getDispatcher(), lowPermission,
                    "ss deny item add minecraft:stone"),
                    "Permission-zero source ran an admin command");
            check(!ServerConfig.DISABLE_ITEMS.get().contains("minecraft:stone"),
                    "Rejected command changed policy");

            var operator = server.createCommandSourceStack().withPermission(4);
            check(execute(commands.getDispatcher(), operator,
                    "ss deny item add minecraft:stone") > 0,
                    "Operator deny command failed");
            check(execute(commands.getDispatcher(), operator,
                    "ss gen mod add examplemod") > 0,
                    "Operator gallery-list command failed");
            JsonObject written = JsonParser.parseString(Files.readString(fixture)).getAsJsonObject();
            check(written.getAsJsonObject("compatibility")
                            .getAsJsonArray("disable_items").contains(
                                    JsonParser.parseString("\"minecraft:stone\"")),
                    "Deny command was not saved immediately");
            check(ServerConfig.GEN_MODS.get().contains("examplemod"),
                    "Gen command did not update policy");

            Files.writeString(fixture, policyJson(8, true, true, true,
                    List.of(), List.of("minecraft:dirt")));
            execute(commands.getDispatcher(), operator, "ss reload");
            check(ServerConfig.isItemDisabled(ResourceLocation.parse("minecraft:dirt")),
                    "Reload command did not apply the edited file");

            ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
            checkEquals(0, execute(commands.getDispatcher(),
                    player.createCommandSourceStack().withPermission(4), "ss gallery all"),
                    "Disabled gallery command ran");
        } catch (IOException e) {
            throw new GameTestAssertException("Could not prepare command fixture: " + e);
        } finally {
            restoreConfig(original, fixture);
        }
        helper.succeed();
    }

    /**
     * To reproduce in-game: add an item to somestacks:ingots with a data pack and reload. Bar accepts
     * that item and Singles refuses it; items outside the tag follow the opposite admission rule.
     */
    public static void ingotTagControlsBarAndSinglesAdmission(GameTestHelper helper) {
        ServerConfig.Settings original = ServerConfig.settings();
        try {
            ServerConfig.apply(ServerConfig.defaults());
            ItemStack ingot = new ItemStack(GameTestScaffold.firstBarItem());
            check(ingot.is(BarStackBE.INGOTS), "Bar item was not in the ingot tag");
            check(BarStackBE.isValidBarItem(ingot), "Tag item was refused by Bar");
            check(!com.github.crittscott.somestacks.block.SinglesStackBE.isValidSinglesItem(ingot),
                    "Tag item was admitted by Singles");
            ItemStack stick = new ItemStack(Items.STICK);
            check(!stick.is(BarStackBE.INGOTS), "Fixture stick unexpectedly belongs to the ingot tag");
            check(!BarStackBE.isValidBarItem(stick), "Non-tag item was admitted by Bar");
            check(com.github.crittscott.somestacks.block.SinglesStackBE.isValidSinglesItem(stick),
                    "Non-tag item was refused by Singles");
        } finally {
            ServerConfig.apply(original);
        }
        helper.succeed();
    }

    private static BlockHitResult centerHit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    private static int execute(
            com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher,
            CommandSourceStack source,
            String command) {
        try {
            return dispatcher.execute(command, source);
        } catch (CommandSyntaxException ignored) {
            return 0;
        }
    }

    private static Path serverConfigPath(GameTestHelper helper, String name) {
        return helper.getLevel().getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("serverconfig")
                .resolve(name);
    }

    private static String policyJson(
            int maxHeight,
            boolean storage,
            boolean singles,
            boolean bars,
            List<String> disabledMods,
            List<String> disabledItems) {
        JsonObject compatibility = new JsonObject();
        compatibility.add("disable_mods", stringArray(disabledMods));
        compatibility.add("disable_items", stringArray(disabledItems));
        JsonObject stacks = new JsonObject();
        stacks.addProperty("enable_storage_stack_block", storage);
        stacks.addProperty("enable_singles_stack_block", singles);
        stacks.addProperty("enable_bar_stack_block", bars);
        JsonObject piles = new JsonObject();
        piles.addProperty("max_pile_height", maxHeight);
        JsonObject root = new JsonObject();
        root.add("piles", piles);
        root.add("stacks", stacks);
        root.add("compatibility", compatibility);
        return root.toString();
    }

    private static com.google.gson.JsonArray stringArray(List<String> entries) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        entries.forEach(array::add);
        return array;
    }

    private static void restoreConfig(ServerConfig.Backend original, Path fixture) {
        ServerConfig.install(original);
            ServerConfig.reload();
        try {
            Files.deleteIfExists(fixture);
        } catch (IOException e) {
            SomeStacksCommon.LOGGER.warn("Could not delete GameTest config fixture {}", fixture, e);
        }
    }

    private static void checkOffsetEquals(RenderOffset expected, RenderOffset actual, String message) {
        checkEquals(expected, actual, message);
    }

    private static void checkConfigEquals(ItemRenderConfig expected, ItemRenderConfig actual, String message) {
        check(actual != null, message + ": missing override");
        checkEquals(expected.mode(), actual.mode(), message + " mode");
        checkEquals(expected.scale(), actual.scale(), message + " scale");
        checkOffsetEquals(expected.offset(), actual.offset(), message + " offset");
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
