package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.firstBarItem;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

import com.github.crittscott.somestacks.client.ClientGestures;
import com.github.crittscott.somestacks.client.ClientServerSettings;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.GestureStatePkt;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.StackGesture;
import com.github.crittscott.somestacks.util.StackMode;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Client decision and state checks with explicit hit classification, runnable without a client
 * singleton.
 */
public final class ClientGestureChecks {
    private ClientGestureChecks() {}

    private record Click(
            int target,
            Item held,
            boolean shift,
            boolean modifier,
            StackMode mode,
            boolean consumed) {}

    /**
     * Only main-hand clicks that name a gesture are consumed. To reproduce in-game: try plain
     * extraction, modified deposit, Shift torch rotation, and empty-hand permanence on each stack
     * type, then repeat with an off-hand item and on an ordinary block. Unclaimed clicks retain
     * ordinary use. Toggle Permanent reserves modified clicks against deposit and placement.
     */
    public static void blockRulesRespectPrecedenceAndHands(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        var storage = placeStorage(helper, ORIGIN);
        var singles = placeSingles(helper, ORIGIN.east(3));
        var bars = placeBar(helper, ORIGIN.east(6));
        BlockPos ordinary = helper.absolutePos(ORIGIN.south(3));
        helper.getLevel().setBlock(ordinary, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos[] targets = {
            storage.getBlockPos(), singles.getBlockPos(), bars.getBlockPos(), ordinary
        };
        List<Click> cases = new ArrayList<>();
        for (int target = 0; target < 4; target++) {
            for (Item held : new Item[] {Items.AIR, Items.APPLE}) {
                cases.add(
                        new Click(target, held, false, false, StackMode.STORAGE_STACK, target < 3));
                cases.add(new Click(target, held, true, false, StackMode.STORAGE_STACK, false));
                cases.add(
                        new Click(
                                target,
                                held,
                                false,
                                true,
                                StackMode.STORAGE_STACK,
                                held != Items.AIR));
                cases.add(
                        new Click(
                                target,
                                held,
                                false,
                                true,
                                StackMode.TOGGLE_PERMANENT,
                                held == Items.AIR && target == 0));
                cases.add(new Click(target, held, true, true, StackMode.STORAGE_STACK, false));
            }
            for (boolean modifier : new boolean[] {false, true}) {
                cases.add(
                        new Click(
                                target,
                                Items.REDSTONE_TORCH,
                                true,
                                modifier,
                                StackMode.STORAGE_STACK,
                                target < 2));
                cases.add(
                        new Click(
                                target,
                                Items.SOUL_TORCH,
                                true,
                                modifier,
                                StackMode.STORAGE_STACK,
                                target == 1));
            }
        }
        cases.add(new Click(0, Items.AIR, true, true, StackMode.TOGGLE_PERMANENT, true));
        cases.add(new Click(0, Items.REDSTONE_TORCH, true, true, StackMode.TOGGLE_PERMANENT, true));
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        ItemStack originalOffhand = player.getOffhandItem().copy();
        boolean originalShift = player.isShiftKeyDown();
        try {
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT));
            for (Click click : cases) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(click.held()));
                player.setShiftKeyDown(click.shift());
                for (InteractionHand hand : InteractionHand.values()) {
                    StackGesture gesture =
                            StackGesture.classify(
                                    BlockType.of(
                                            helper.getLevel()
                                                    .getBlockState(targets[click.target()])
                                                    .getBlock()),
                                    click.mode(),
                                    click.modifier(),
                                    click.shift(),
                                    player.getMainHandItem(),
                                    true,
                                    hand);
                    boolean expected = hand == InteractionHand.MAIN_HAND && click.consumed();
                    checkEquals(
                            expected,
                            gesture.consumesLocalUse(player.getMainHandItem()),
                            "Cancellation for " + click + " / " + hand);
                }
            }
            // Both clicked and adjacent destinations consume the same client gesture.
            placeSingles(helper, ORIGIN.north());
            player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
            assertConsumed(player, storage.getBlockPos());
            placeSingles(helper, ORIGIN.south(3).north());
            assertConsumed(player, ordinary);
        } finally {
            player.setShiftKeyDown(originalShift);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, originalOffhand);
        }
        helper.succeed();
    }

    /**
     * A modified top-face click on any stack is consumed whether its cells are empty, partly
     * occupied, or full; the server deposits or places the selected type above. To reproduce
     * in-game: modifier-click the top face of each stack type while holding an eligible item, with
     * its cells empty and then full. The held item is never used against the block.
     */
    public static void topFaceModifiedClicksAreConsumed(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        var storage = placeStorage(helper, ORIGIN);
        var singles = placeSingles(helper, ORIGIN.east(3));
        var bars = placeBar(helper, ORIGIN.east(6));
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.APPLE));
        boolean originalShift = player.isShiftKeyDown();
        try {
            player.setShiftKeyDown(false);
            for (var be : List.of(storage, singles, bars)) {
                Item item = be == bars ? firstBarItem() : Items.APPLE;
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
                assertConsumed(player, be.getBlockPos());
                be.getItems().setStackInSlot(0, new ItemStack(item));
                assertConsumed(player, be.getBlockPos());
                GameTestScaffold.fill(be, item);
                assertConsumed(player, be.getBlockPos());
            }
        } finally {
            player.setShiftKeyDown(originalShift);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        helper.succeed();
    }

    private static void assertConsumed(ServerPlayer player, BlockPos pos) {
        StackGesture gesture =
                StackGesture.classify(
                        BlockType.of(player.level().getBlockState(pos).getBlock()),
                        StackMode.STORAGE_STACK,
                        true,
                        player.isShiftKeyDown(),
                        player.getMainHandItem(),
                        true,
                        InteractionHand.MAIN_HAND);
        check(
                gesture.consumesLocalUse(player.getMainHandItem()),
                "Modified click at " + pos + " was not consumed");
    }

    /**
     * Air cycling distinguishes hit type and hand, skips disabled types, and always retains
     * permanence; a published server policy moves the selection off a type it disables. To
     * reproduce in-game: hold the modifier and click air with empty and occupied main hands, also
     * holding an off-hand item. Modes cycle only from the main hand, and disabled types are
     * skipped. Clicking a block must not cycle the mode. Then select Storage, have an operator
     * disable Storage and run /ss reload: the hotbar shows Singles mode.
     */
    public static void airRulesAndDisabledModeCycling(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        ItemStack originalOffhand = player.getOffhandItem().copy();
        List<String> originalGallery = ClientServerSettings.genMods();
        List<String> originalDenied = List.copyOf(ClientServerSettings.disabledMods());
        try (GestureStateScope scope = new GestureStateScope()) {
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT));
            for (boolean occupied : new boolean[] {false, true}) {
                player.setItemInHand(
                        InteractionHand.MAIN_HAND,
                        occupied ? new ItemStack(Items.APPLE) : ItemStack.EMPTY);
                for (boolean hittingBlock : new boolean[] {false, true}) {
                    for (boolean modifier : new boolean[] {false, true}) {
                        for (InteractionHand hand : InteractionHand.values()) {
                            var before = ClientGestures.currentMode();
                            var gesture =
                                    hittingBlock
                                            ? StackGesture.NONE
                                            : ClientGestures.processClick(
                                                    player, hand, null, false, modifier);
                            boolean claimed =
                                    hand == InteractionHand.MAIN_HAND
                                            && modifier
                                            && !hittingBlock;
                            checkEquals(
                                    claimed ? StackGesture.CYCLE_MODE : StackGesture.NONE,
                                    gesture,
                                    "Air intent");
                            checkEquals(
                                    claimed && occupied,
                                    gesture.consumesLocalUse(player.getMainHandItem()),
                                    "Air cancellation");
                            checkEquals(
                                    claimed,
                                    before != ClientGestures.currentMode(),
                                    "Air mode change");
                        }
                    }
                }
            }
            for (int mask = 0; mask < 8; mask++) {
                for (BlockType type : BlockType.values())
                    ClientServerSettings.setBlockEnabled(type, true);
                selectMode(StackMode.STORAGE_STACK);
                BlockType[] types = {
                    BlockType.STORAGE_STACK, BlockType.SINGLES_STACK, BlockType.BAR_STACK
                };
                for (int i = 0; i < types.length; i++)
                    ClientServerSettings.setBlockEnabled(types[i], (mask & (1 << i)) != 0);
                List<StackMode> expected = new ArrayList<>();
                if ((mask & 2) != 0) expected.add(StackMode.SINGLES_STACK);
                if ((mask & 4) != 0) expected.add(StackMode.BAR_STACK);
                expected.add(StackMode.TOGGLE_PERMANENT);
                if ((mask & 1) != 0) expected.add(StackMode.STORAGE_STACK);
                for (StackMode mode : expected) {
                    ClientGestures.cycleMode();
                    checkEquals(mode, ClientGestures.currentMode(), "Disabled-mode cycle " + mask);
                }
                ClientGestures.cycleMode();
                checkEquals(expected.get(0), ClientGestures.currentMode(), "Cycle wrap " + mask);
            }
            for (BlockType type : BlockType.values())
                ClientServerSettings.setBlockEnabled(type, true);
            selectMode(StackMode.STORAGE_STACK);
            ClientServerSettings.apply(
                    new ConfigSyncPkt(false, true, true, List.of(), List.of()), null);
            checkEquals(
                    StackMode.SINGLES_STACK,
                    ClientGestures.currentMode(),
                    "Published policy kept a disabled mode");
            ClientServerSettings.apply(
                    new ConfigSyncPkt(true, true, true, List.of(), List.of()), null);
            checkEquals(
                    StackMode.SINGLES_STACK,
                    ClientGestures.currentMode(),
                    "Published policy changed an enabled mode");
        } finally {
            ClientServerSettings.replace(originalGallery, originalDenied);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, originalOffhand);
        }
        helper.succeed();
    }

    /**
     * Only changed mode/modifier state is sent; reconnect reset forces the next state packet. To
     * reproduce in-game: cycle mode and use modified/unmodified gestures, reconnect, and use the
     * same mode again. The server interprets the first new click correctly. Packet suppression
     * itself requires observing the sender, as this test does.
     */
    public static void gestureSyncSendsChangesAndResets(GameTestHelper helper) {
        try (GestureStateScope scope = new GestureStateScope()) {
            selectMode(StackMode.STORAGE_STACK);
            ClientGestures.syncState(false);
            ClientGestures.syncState(false);
            ClientGestures.syncState(true);
            ClientGestures.syncState(true);
            ClientGestures.cycleMode();
            ClientGestures.syncState(true);
            ClientGestures.syncState(true);
            ClientGestures.resetSync();
            ClientGestures.syncState(true);
            ClientGestures.syncState(true);
            ClientGestures.syncState(false);
            checkEquals(
                    List.of(
                            new GestureStatePkt(StackMode.STORAGE_STACK, false),
                            new GestureStatePkt(StackMode.STORAGE_STACK, true),
                            new GestureStatePkt(StackMode.SINGLES_STACK, true),
                            new GestureStatePkt(StackMode.SINGLES_STACK, true),
                            new GestureStatePkt(StackMode.SINGLES_STACK, false)),
                    scope.sent,
                    "Changed-only/reset packets");
        }
        helper.succeed();
    }

    private static void selectMode(StackMode mode) {
        for (int i = 0;
                i < StackMode.values().length && ClientGestures.currentMode() != mode;
                i++) {
            ClientGestures.cycleMode();
        }
        checkEquals(mode, ClientGestures.currentMode(), "Could not select enabled mode");
    }

    private static final class GestureStateScope implements AutoCloseable {
        private final List<CustomPacketPayload> sent = new ArrayList<>();
        private final Consumer<CustomPacketPayload> previousSender;
        private final StackMode previousMode = ClientGestures.currentMode();
        private final boolean[] previousFlags = new boolean[BlockType.values().length];

        private GestureStateScope() {
            for (BlockType type : BlockType.values()) {
                previousFlags[type.ordinal()] = ClientServerSettings.isBlockTypeEnabled(type);
                ClientServerSettings.setBlockEnabled(type, true);
            }
            previousSender = ClientGestures.setSender(sent::add);
            ClientGestures.resetSync();
        }

        @Override
        public void close() {
            for (BlockType type : BlockType.values())
                ClientServerSettings.setBlockEnabled(type, true);
            selectMode(previousMode);
            for (BlockType type : BlockType.values())
                ClientServerSettings.setBlockEnabled(type, previousFlags[type.ordinal()]);
            ClientGestures.setSender(previousSender);
            ClientGestures.resetSync();
        }
    }
}
