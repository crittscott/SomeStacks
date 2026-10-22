package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.github.crittscott.somestacks.command.SsCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Deque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.lang.reflect.Field;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/**
 * The render-gallery world builder: that a queued gallery lays out its floor and rows as described
 * and reports the totals it finished with, spread across ticks by the configured placement limit.
 */
public final class RenderGalleryChecks {
    private static final int GALLERY_PERMISSION = 3;
    private RenderGalleryChecks() {}

    /**
     * To reproduce: as a nonoperator, try /ss deny item add minecraft:stone and both gallery
     * commands; none may execute. As an operator, try galleries with enabled=false, then with
     * enabled=true and required_permission_level=3 at levels 2 and 3. Only the level-3 player may
     * start construction; a console source may not. At level 2, add/remove stone from the deny-item
     * list and confirm player deposits are refused/allowed respectively; an unknown item must not
     * alter policy. Use a clear gallery site and restore the original settings after checking.
     *
     * <p>The fixture executes a registered dispatcher, uses copied command sources rather than
     * changing operator records, and intercepts policy saves. The Bar gallery covers the
     * {@code minecraft} ingots, which fit one Bar Stack. Accepted jobs are drained through the generator's tick entry point in this scenario;
     * asynchronous construction is covered separately by the queued-gallery test.
     */
    public static void commandsEnforcePolicyAndGalleryPermissions(GameTestHelper helper, ServerPlayer player) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        SsCommand.register(dispatcher);
        try (InteractionChecks.PlayerScope playerScope = new InteractionChecks.PlayerScope(player);
             CommandScope scope = new CommandScope()) {
            check(scope.jobs.isEmpty(), "Command batch started with unrelated gallery work");
            ServerConfig.start(() -> commandSettings(false, List.of()), scope.saved::add, () -> {});
            check(BlockType.BAR_STACK.admits(new ItemStack(Items.IRON_INGOT), null), "Iron ingot is not Bar-valid");
            BlockPos playerPos = helper.absolutePos(ORIGIN);
            player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
            CommandSourceStack ordinary = player.createCommandSourceStack().withPermission(0).withSuppressedOutput();
            CommandSourceStack admin = ordinary.withPermission(2);
            CommandSourceStack authorized = ordinary.withPermission(GALLERY_PERMISSION);
            CommandSourceStack console = ordinary.getServer().createCommandSourceStack()
                    .withPermission(4).withSuppressedOutput();
            check(console.getEntity() == null, "Console fixture unexpectedly has an entity");
            BlockPos storageBase = playerPos.east();
            var floorBefore = helper.getLevel().getBlockState(storageBase.below());
            var stackBefore = helper.getLevel().getBlockState(storageBase);

            assertRefused(dispatcher, ordinary, "ss deny item add minecraft:stone", scope);
            for (String command : List.of("ss gallery items", "ss ingotgallery list")) {
                assertRefused(dispatcher, ordinary, command, scope);
                assertRefused(dispatcher, authorized.withPermission(4), command, scope);
            }
            ServerConfig.publish(commandSettings(true, List.of()));
            for (String command : List.of("ss gallery items", "ss ingotgallery list")) {
                assertRefused(dispatcher, ordinary, command, scope);
                assertRefused(dispatcher, admin, command, scope);
                assertRefused(dispatcher, console, command, scope);
            }
            RenderGalleryGenerator.onServerTick();
            checkEquals(floorBefore, helper.getLevel().getBlockState(storageBase.below()), "Refusal changed floor");
            checkEquals(stackBefore, helper.getLevel().getBlockState(storageBase), "Refusal placed a stack");

            ServerConfig.Settings clean = ServerConfig.settings();
            checkEquals(1, execute(dispatcher, admin, "ss deny item add minecraft:stone", scope), "Authorized add");
            checkEquals(commandSettings(true, List.of("minecraft:stone")), ServerConfig.settings(),
                    "Deny command changed the wrong policy fields");
            check(!BlockType.permitsPlayerItemAndNotify(new ItemStack(Items.STONE), player), "Player admission ignored deny");
            check(BlockType.STORAGE_STACK.admits(new ItemStack(Items.STONE), null), "Player-only deny reached automation");
            checkEquals(1, scope.saved.size(), "Authorized add did not save once");
            checkEquals(ServerConfig.settings(), scope.saved.get(0), "Saved policy differs from publication");

            ServerConfig.Settings denied = ServerConfig.settings();
            checkEquals(0, execute(dispatcher, admin, "ss deny item add somestacks:missing_command_test_item", scope),
                    "Unknown item was accepted");
            checkEquals(denied, ServerConfig.settings(), "Unknown item changed policy");
            checkEquals(1, scope.saved.size(), "Unknown item caused a save");
            check(!ServerConfig.isItemDisabled(new ResourceLocation("somestacks", "missing_command_test_item")),
                    "Unknown item reached admission state");
            checkEquals(1, execute(dispatcher, admin, "ss deny item remove minecraft:stone", scope), "Authorized remove");
            checkEquals(clean, ServerConfig.settings(), "Add/remove did not restore the intended policy");
            check(BlockType.permitsPlayerItemAndNotify(new ItemStack(Items.STONE), player), "Removal retained denial");
            checkEquals(2, scope.saved.size(), "Authorized remove did not save once");
            checkEquals(clean, scope.saved.get(1), "Removal saved the wrong policy");

            int queued = scope.jobs.size();
            checkEquals(1, execute(dispatcher, authorized, "ss gallery items", scope), "Authorized Storage gallery");
            checkEquals(queued + 1, scope.jobs.size(), "Storage command did not enqueue one job");
            checkEquals(stackBefore, helper.getLevel().getBlockState(storageBase), "Gallery built inside command");
            BlockPos barPlayerPos = playerPos.east(4);
            player.setPos(barPlayerPos.getX() + 0.5, barPlayerPos.getY(), barPlayerPos.getZ() + 0.5);
            CommandSourceStack barSource = player.createCommandSourceStack()
                    .withPermission(GALLERY_PERMISSION).withSuppressedOutput();
            checkEquals(1, execute(dispatcher, barSource, "ss ingotgallery list", scope), "Authorized Bar gallery");
            checkEquals(queued + 2, scope.jobs.size(), "Bar command did not enqueue one job");
            RenderGalleryGenerator.onServerTick();
            check(scope.jobs.isEmpty(), "Small command galleries did not complete within their budget");
            check(helper.getLevel().getBlockEntity(storageBase) instanceof StorageStackBE, "Storage gallery missing");
            checkEquals(1, GameTestScaffold.heldAt(helper, storageBase, Items.STONE), "Storage gallery contents");
            BlockPos barBase = barPlayerPos.east();
            check(helper.getLevel().getBlockEntity(barBase) instanceof BarStackBE, "Bar gallery missing");
            checkEquals(1, GameTestScaffold.heldAt(helper, barBase, Items.IRON_INGOT), "Bar gallery contents");
            check(helper.getLevel().getBlockState(storageBase.below()).is(Blocks.SMOOTH_SANDSTONE), "Storage floor");
            check(helper.getLevel().getBlockState(barBase.below()).is(Blocks.SMOOTH_SANDSTONE), "Bar floor");
        } catch (ReflectiveOperationException | CommandSyntaxException exception) {
            throw new AssertionError("Command scenario failed", exception);
        }
        helper.succeed();
    }

    /**
     * To reproduce: enable galleries, run /ss deny item add minecraft:stone, then /ss gallery
     * minecraft on a clear site. The items read in id order and stone does not appear. Restore the
     * deny list afterward.
     */
    public static void galleryItemsAreInIdOrderWithoutDeniedItems(GameTestHelper helper) {
        ServerConfig.Settings policy = ServerConfig.settings();
        try {
            ServerConfig.publish(commandSettings(true, List.of("minecraft:stone")));
            List<ResourceLocation> ids = RenderGalleryGenerator.Kind.STORAGE.itemsIn("minecraft").stream()
                    .map(BuiltInRegistries.ITEM::getKey)
                    .toList();
            check(!ids.contains(BuiltInRegistries.ITEM.getKey(Items.STONE)), "Denied item stayed in the gallery");
            check(ids.contains(BuiltInRegistries.ITEM.getKey(Items.DIRT)), "Allowed item left the gallery");
            List<ResourceLocation> sorted = new ArrayList<>(ids);
            Collections.sort(sorted);
            checkEquals(sorted, ids, "Gallery items out of id order");
        } finally {
            ServerConfig.publish(policy);
        }
        helper.succeed();
    }

    private static ServerConfig.Settings commandSettings(boolean galleryEnabled, List<String> disabledItems) {
        return new ServerConfig.Settings(8, true, true, true, List.of(), disabledItems,
                64, galleryEnabled, GALLERY_PERMISSION,
                List.of("minecraft"), List.of("minecraft:stone"));
    }

    private static void assertRefused(CommandDispatcher<CommandSourceStack> dispatcher,
                                      CommandSourceStack source, String command, CommandScope scope) {
        ServerConfig.Settings before = ServerConfig.settings();
        int savesBefore = scope.saved.size();
        int jobsBefore = scope.jobs.size();
        try {
            execute(dispatcher, source, command, scope);
            check(false, "Permission gate accepted " + command);
        } catch (CommandSyntaxException expected) {
            // Brigadier excludes command nodes whose source requirements fail.
        }
        checkEquals(before, ServerConfig.settings(), "Refused command changed policy");
        checkEquals(savesBefore, scope.saved.size(), "Refused command saved policy");
        checkEquals(jobsBefore, scope.jobs.size(), "Refused command queued a gallery");
    }

    private static int execute(CommandDispatcher<CommandSourceStack> dispatcher,
                               CommandSourceStack source, String command, CommandScope scope)
            throws CommandSyntaxException {
        Set<Object> before = new HashSet<>(scope.jobs);
        try {
            return dispatcher.execute(command, source);
        } finally {
            for (Object job : scope.jobs) {
                if (!before.contains(job)) scope.ownedJobs.add(job);
            }
        }
    }

    /**
     * Saves are recorded in memory, and the world's policy reader and saver are restored on close;
     * only jobs created by this scenario are removed on failure.
     */
    private static final class CommandScope implements AutoCloseable {
        private final ServerConfig.Settings policy = ServerConfig.settings();
        private final Field readerField;
        private final Object reader;
        private final Field saverField;
        private final Object saver;
        private final Field syncClientsField;
        private final Object syncClients;
        private final Deque<Object> jobs;
        private final Set<Object> ownedJobs = new HashSet<>();
        private final List<ServerConfig.Settings> saved = new ArrayList<>();

        @SuppressWarnings("unchecked")
        private CommandScope() throws ReflectiveOperationException {
            readerField = ServerConfig.class.getDeclaredField("reader");
            readerField.setAccessible(true);
            reader = readerField.get(null);
            saverField = ServerConfig.class.getDeclaredField("saver");
            saverField.setAccessible(true);
            saver = saverField.get(null);
            syncClientsField = ServerConfig.class.getDeclaredField("syncClients");
            syncClientsField.setAccessible(true);
            syncClients = syncClientsField.get(null);
            Field jobsField = RenderGalleryGenerator.class.getDeclaredField("jobs");
            jobsField.setAccessible(true);
            jobs = (Deque<Object>) jobsField.get(null);
        }

        @Override
        public void close() throws IllegalAccessException {
            try {
                jobs.removeAll(ownedJobs);
                ServerConfig.publish(policy);
            } finally {
                readerField.set(null, reader);
                saverField.set(null, saver);
                syncClientsField.set(null, syncClients);
            }
        }
    }

    /**
     * Internal gallery plan: enqueue ten ordinary items as one group and iron ingot as another;
     * after 60 ticks assert three Storage blocks, eleven total items, and smooth-sandstone flooring
     * east of the player, with rows extending north. Exact grouping and completion-callback values
     * have no direct command reproduction.
     *
     * <p>Nearest manual check: enable render_gallery.enabled and use a player with the configured
     * gallery permission. Set gen_items with /ss gen item add, then run /ss gallery items on a
     * clear site. Watch construction proceed across ticks according to placements_per_tick and
     * inspect the eastward floor and northward rows.
     */
    public static void queuedStorageGalleryBuildsFloorRowsAndCompletionTotals(
            GameTestHelper helper, ServerPlayer player) {
        BlockPos playerPos = helper.absolutePos(ORIGIN);
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        List<Item> firstGroup = List.of(
                Items.STONE, Items.DIRT, Items.COBBLESTONE,
                Items.APPLE, Items.PAPER, Items.STICK,
                Items.GLASS, Items.SAND, Items.GRAVEL, Items.COAL);
        List<Item> secondGroup = List.of(Items.IRON_INGOT);
        RenderGalleryGenerator.Result[] completed = new RenderGalleryGenerator.Result[1];

        RenderGalleryGenerator.Plan plan = RenderGalleryGenerator.enqueue(
                player,
                RenderGalleryGenerator.Kind.STORAGE,
                List.of(firstGroup, secondGroup),
                result -> completed[0] = result);

        checkEquals(3, plan.expectedStacks(), "Planned stack count");
        checkEquals(11, plan.totalItems(), "Planned item count");

        helper.runAfterDelay(60, () -> {
            check(completed[0] != null, "Queued gallery did not complete");
            checkEquals(3, completed[0].totalStacks(), "Placed stack count");
            checkEquals(3, completed[0].expectedStacks(), "Completed expected count");
            checkEquals(11, completed[0].totalItems(), "Completed item count");

            BlockPos base = playerPos.east();
            check(helper.getLevel().getBlockState(base.below()).is(Blocks.SMOOTH_SANDSTONE),
                    "Gallery floor was not built");
            check(helper.getLevel().getBlockEntity(base) instanceof StorageStackBE,
                    "First group first row missing");
            check(helper.getLevel().getBlockEntity(base.north()) instanceof StorageStackBE,
                    "First group second row missing");
            check(helper.getLevel().getBlockEntity(base.east(2)) instanceof StorageStackBE,
                    "Second group row missing");
            helper.succeed();
        });
    }
}
