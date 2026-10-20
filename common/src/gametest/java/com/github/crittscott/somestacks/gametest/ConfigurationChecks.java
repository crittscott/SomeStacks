package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.SomeStacksServer;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.renderconfig.OverrideJsonCodec;
import com.github.crittscott.somestacks.renderconfig.RenderMode;
import com.github.crittscott.somestacks.renderconfig.RenderOffset;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.server.StackInteractions;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.StackMode;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/** Loader-neutral render-override parsing and server-policy checks. */
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

        Map<Identifier, ItemRenderConfig> parsed =
                OverrideJsonCodec.parse(root, "GameTest fixture");
        checkEquals(2, parsed.size(), "Parsed override count");

        ItemRenderConfig stone = parsed.get(Identifier.parse("minecraft:stone"));
        check(stone != null, "Stone override was skipped");
        checkEquals(RenderMode.BLOCK, stone.mode(), "Stone mode");
        checkEquals(1.25f, stone.scale(), "Stone scale");
        checkOffsetEquals(new RenderOffset(-1.0f, 0.0f, 1.0f), stone.offset(), "Stone offset");

        ItemRenderConfig dirt = parsed.get(Identifier.parse("minecraft:dirt"));
        check(dirt != null, "Valid field in a partially malformed override was skipped");
        checkEquals(null, dirt.mode(), "Invalid mode survived parsing");
        checkEquals(2.0f, dirt.scale(), "Valid scale beside an invalid mode was lost");

        JsonObject serialized = OverrideJsonCodec.toJson(parsed);
        checkEquals(List.of("minecraft:dirt", "minecraft:stone"),
                new ArrayList<>(serialized.keySet()), "Serialized item-id order");
        Map<Identifier, ItemRenderConfig> reparsed =
                OverrideJsonCodec.parse(serialized, "GameTest round trip");
        checkConfigEquals(stone, reparsed.get(Identifier.parse("minecraft:stone")),
                "Stone round trip");
        checkConfigEquals(dirt, reparsed.get(Identifier.parse("minecraft:dirt")),
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
        Map<Identifier, ItemRenderConfig> parsed =
                OverrideJsonCodec.parse(root, "GameTest bounds fixture");
        checkEquals(OverrideJsonCodec.MIN_SCALE,
                parsed.get(Identifier.parse("minecraft:stone")).scale(),
                "Parsed minimum scale");
        checkEquals(OverrideJsonCodec.MAX_SCALE,
                parsed.get(Identifier.parse("minecraft:dirt")).scale(),
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
     * Exact-item denial applies to player deposits but not automation, namespace denial applies to
     * both, and contents already stored remain extractable. To reproduce in-game: deny one item,
     * try it by hand and by a pipe, then deny its namespace after storing it. The pipe can insert
     * through the exact-item rule, the namespace rule refuses new input, and the old contents can
     * still be removed.
     */
    public static void denyPoliciesRespectPlayerAutomationAndExistingContents(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerConfig.Settings original = ServerConfig.settings();
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, GameTestScaffold.ORIGIN);
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.STONE, 4));
        try {
            ServerConfig.apply(policy(8, true, true, true, List.of(), List.of("minecraft:stone")));
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            StackInteractions.handleExistingStack(
                    player, InteractionHand.MAIN_HAND, centerHit(storage.getBlockPos()),
                    BlockType.STORAGE_STACK);
            checkEquals(4, player.getMainHandItem().getCount(),
                    "Exact-item denial changed the player hand");
            checkEquals(0, GameTestScaffold.count(storage.getItems(), Items.STONE),
                    "Exact-item denial admitted a player deposit");

            checkEquals(2, storage.itemRun().insertAt(
                    0, new ItemStack(Items.STONE, 2), false),
                    "Exact-item denial reached automation");
            ServerConfig.apply(policy(8, true, true, true, List.of("minecraft"), List.of()));
            checkEquals(0, storage.itemRun().insertAt(
                    1, new ItemStack(Items.STONE), false),
                    "Namespace denial did not reach automation");
            checkEquals(2, storage.itemRun().extract(0, 64, false).getCount(),
                    "Stored denied contents were not extractable");
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
            ServerConfig.apply(original);
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
        ServerConfig.Settings original = ServerConfig.settings();
        try {
            ServerConfig.apply(policy(1, true, true, false, List.of(), List.of()));

            StorageStackBE storage = GameTestScaffold.placeStorage(helper, GameTestScaffold.ORIGIN);
            for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
                storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64));
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

            ServerConfig.apply(policy(8, true, true, false, List.of(), List.of()));
            BarStackBE bars = GameTestScaffold.placeBar(
                    helper, GameTestScaffold.ORIGIN.east(7));
            for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
                bars.getItems().insertItem(slot, new ItemStack(barItem));
            }
            checkEquals(0, bars.itemRun().insertAt(
                    BarStackBE.SLOTS, new ItemStack(barItem), false),
                    "Disabled Bar type allowed automation growth");

            ServerConfig.apply(policy(8, true, true, true, List.of(), List.of()));
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
        } finally {
            ServerConfig.apply(original);
        }
        helper.succeed();
    }

    /**
     * Server commands enforce permissions and save list edits immediately through whichever
     * configuration backend the loader installed. To reproduce in-game: try the administrative list
     * commands without permission, then as an operator, and inspect the world's server config file.
     * Only the operator edits succeed and they are already on disk.
     */
    public static void serverCommandsEnforcePermissionsAndPersistEdits(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerConfig.Settings original = ServerConfig.settings();
        MinecraftServer server = helper.getLevel().getServer();
        try {
            ServerConfig.apply(policy(8, true, true, true, List.of(), List.of()));
            var commands = server.getCommands();
            var lowPermission = server.createCommandSourceStack().withPermission(LevelBasedPermissionSet.ALL);
            checkEquals(0, execute(commands.getDispatcher(), lowPermission,
                    "ss deny item add minecraft:stone"),
                    "Permission-zero source ran an admin command");
            check(!ServerConfig.ListSetting.DISABLE_ITEMS.get().contains("minecraft:stone"),
                    "Rejected command changed policy");

            var operator = server.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER);
            check(execute(commands.getDispatcher(), operator,
                    "ss deny item add minecraft:stone") > 0,
                    "Operator deny command failed");
            check(execute(commands.getDispatcher(), operator,
                    "ss gen mod add examplemod") > 0,
                    "Operator gallery-list command failed");
            check(ServerConfig.ListSetting.GEN_MODS.get().contains("examplemod"),
                    "Gen command did not update policy");

            // Forget the edits in memory; reloading must bring them back from the saved file.
            ServerConfig.apply(policy(8, true, true, true, List.of(), List.of()));
            // The return value is the synced-player count, which is zero in GameTests.
            execute(commands.getDispatcher(), operator, "ss reload");
            check(ServerConfig.isItemDisabled(Identifier.parse("minecraft:stone")),
                    "Deny command was not saved immediately");
            check(ServerConfig.ListSetting.GEN_MODS.get().contains("examplemod"),
                    "Gen command was not saved immediately");

            ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
            checkEquals(0, execute(commands.getDispatcher(),
                    player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER), "ss gallery all"),
                    "Disabled gallery command ran");
            var baseline = ServerConfig.settings();
            var gallery = commands.getDispatcher().getRoot().getChild("ss").getChild("gallery");
            var ordinaryPlayer = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.ALL);
            ServerConfig.apply(galleryPolicy(baseline, true, 0));
            check(ServerConfig.save(), "Could not save enabled gallery policy");
            ServerConfig.apply(baseline);
            execute(commands.getDispatcher(), operator, "ss reload");
            check(gallery.canUse(ordinaryPlayer), "Command reload did not enable gallery access");
            ServerConfig.apply(galleryPolicy(baseline, true, 4));
            check(ServerConfig.save(), "Could not save restricted gallery policy");
            ServerConfig.apply(galleryPolicy(baseline, true, 0));
            SomeStacksServer.reloadPolicy(server);
            check(!gallery.canUse(ordinaryPlayer), "Policy reload retained the previous gallery permission");
            check(gallery.canUse(player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER)),
                    "Policy reload refused the required owner permission");
            ServerConfig.apply(galleryPolicy(baseline, false, 4));
            check(ServerConfig.save(), "Could not save disabled gallery policy");
            ServerConfig.apply(galleryPolicy(baseline, true, 4));
            SomeStacksServer.reloadPolicy(server);
            check(!gallery.canUse(player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER)),
                    "Policy reload left disabled galleries available");
        } finally {
            ServerConfig.apply(original);
            ServerConfig.save();
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
            check(!SinglesStackBE.isValidSinglesItem(ingot),
                    "Tag item was admitted by Singles");
            ItemStack stick = new ItemStack(Items.STICK);
            check(!stick.is(BarStackBE.INGOTS), "Fixture stick unexpectedly belongs to the ingot tag");
            check(!BarStackBE.isValidBarItem(stick), "Non-tag item was admitted by Bar");
            check(SinglesStackBE.isValidSinglesItem(stick),
                    "Non-tag item was refused by Singles");
        } finally {
            ServerConfig.apply(original);
        }
        helper.succeed();
    }

    private static ServerConfig.Settings galleryPolicy(ServerConfig.Settings policy, boolean enabled, int permission) {
        return new ServerConfig.Settings(policy.maxPileHeight(), policy.enableStorageStackBlock(),
                policy.enableSinglesStackBlock(), policy.enableBarStackBlock(), policy.disableMods(),
                policy.disableItems(), policy.renderGalleryPlacementsPerTick(), enabled, permission,
                policy.genMods(), policy.genItems());
    }

    private static BlockHitResult centerHit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    private static int execute(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandSourceStack source,
            String command) {
        try {
            return dispatcher.execute(command, source);
        } catch (CommandSyntaxException ignored) {
            return 0;
        }
    }

    /** A default policy with the given run limits, enabled types, and deny lists. */
    private static ServerConfig.Settings policy(
            int maxHeight,
            boolean storage,
            boolean singles,
            boolean bars,
            List<String> disabledMods,
            List<String> disabledItems) {
        ServerConfig.Settings defaults = ServerConfig.defaults();
        return new ServerConfig.Settings(maxHeight, storage, singles, bars, disabledMods, disabledItems,
                defaults.renderGalleryPlacementsPerTick(), defaults.galleryEnabled(),
                defaults.galleryPermissionLevel(), defaults.genMods(), defaults.genItems());
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
}
