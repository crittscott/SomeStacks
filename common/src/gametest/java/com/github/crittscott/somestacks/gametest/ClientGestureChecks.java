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
import com.github.crittscott.somestacks.client.interaction.InteractionContext;
import com.github.crittscott.somestacks.client.interaction.InteractionRuleRegistry;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.GestureStatePkt;
import com.github.crittscott.somestacks.util.StackMode;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
            boolean claimed) {}

    /**
     * The client claims exactly the block clicks a gesture uses, for either hand; the server acts
     * only for the main hand. To reproduce in-game: try plain extraction, modified deposit, Shift
     * torch rotation, and empty-hand permanence on each stack type, then repeat with an off-hand
     * item and on an ordinary block, including one beside a Singles Stack. Claimed clicks neither
     * empty a held bucket nor place a held block; unclaimed clicks keep ordinary use. Toggle
     * Permanent reserves modified clicks against deposit and placement.
     */
    public static void blockRulesRespectPrecedenceAndHands(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        var storage = placeStorage(helper, ORIGIN);
        var singles = placeSingles(helper, ORIGIN.east(3));
        var bars = placeBar(helper, ORIGIN.east(6));
        BlockPos ordinary = helper.absolutePos(ORIGIN.south(3));
        helper.getLevel().setBlock(ordinary, Blocks.STONE.defaultBlockState(), 3);
        // A stack beside the ordinary block, where the server may deposit; the client claims the
        // click exactly as it would a placement.
        placeSingles(helper, ORIGIN.south(3).north());
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
                    InteractionContext ctx =
                            InteractionContext.forBlockClick(
                                    player,
                                    helper.getLevel(),
                                    hand,
                                    targets[click.target()],
                                    click.mode(),
                                    click.modifier());
                    InteractionRuleRegistry.processBlockRules(ctx);
                    checkEquals(
                            click.claimed(),
                            ctx.shouldCancel(),
                            "Cancellation for " + click + " / " + hand);
                }
            }
        } finally {
            player.setShiftKeyDown(originalShift);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, originalOffhand);
        }
        helper.succeed();
    }

    /**
     * A modifier-plus-item click on a stack's top face is claimed locally whether the server will
     * deposit into the stack or place a new block above it. To reproduce in-game: holding an
     * eligible item and the Stack Modifier, click the top of an empty, a partly filled, and a full
     * Singles or Bar Stack, and of a full Storage Stack. The held item is never used locally; the
     * server deposits, or places the selected type above.
     */
    public static void fullTopFacesFallThroughToPlacement(
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
                assertClaimed(player, be.getBlockPos(), "Empty " + be.getBlockPos());
                be.getItems().setStackInSlot(0, new ItemStack(item));
                assertClaimed(player, be.getBlockPos(), "Partly filled " + be.getBlockPos());
                for (int slot = 0; slot < be.getItems().getSlots(); slot++) {
                    be.getItems().setStackInSlot(slot, new ItemStack(item, be.slotLimit()));
                }
                assertClaimed(player, be.getBlockPos(), "Full " + be.getBlockPos());
            }
        } finally {
            player.setShiftKeyDown(originalShift);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        helper.succeed();
    }

    private static void assertClaimed(ServerPlayer player, BlockPos pos, String message) {
        var ctx =
                InteractionContext.forBlockClick(
                        player,
                        player.level(),
                        InteractionHand.MAIN_HAND,
                        pos,
                        StackMode.STORAGE_STACK,
                        true);
        InteractionRuleRegistry.processBlockRules(ctx);
        check(ctx.shouldCancel(), message + ": modified click not consumed");
    }

    /**
     * While the Stack Modifier is held, the client claims every main-hand item use, including the
     * one that follows a block click no block gesture claimed; that is the use the server refuses.
     * To reproduce in-game: hold food or a bow and the Stack Modifier, sneak, and right-click a
     * block; then select Toggle Permanent and right-click a block without sneaking. Nothing is
     * eaten or drawn, on either loader. Release the modifier and the same clicks eat or draw as
     * usual. An off-hand item use is never claimed.
     */
    public static void modifierItemUseIsClaimedOnBlocks(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.APPLE));
        boolean originalShift = player.isShiftKeyDown();
        try {
            for (boolean shift : new boolean[] {false, true}) {
                player.setShiftKeyDown(shift);
                for (StackMode mode : StackMode.values()) {
                    for (boolean modifier : new boolean[] {false, true}) {
                        for (InteractionHand hand : InteractionHand.values()) {
                            InteractionContext ctx =
                                    InteractionContext.forAirClick(player, hand, mode, modifier)
                                            .withHittingBlock(true);
                            InteractionRuleRegistry.processAirRules(ctx);
                            checkEquals(
                                    modifier && hand == InteractionHand.MAIN_HAND,
                                    ctx.shouldCancel(),
                                    "Item use claim for shift="
                                            + shift
                                            + " mode="
                                            + mode
                                            + " modifier="
                                            + modifier
                                            + " / "
                                            + hand);
                        }
                    }
                }
            }
        } finally {
            player.setShiftKeyDown(originalShift);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        helper.succeed();
    }

    /**
     * The mode cycle runs Storage, Singles, Bar, Toggle Permanent, and back, skipping types the
     * server disables, and a policy update that disables the selected type moves the selection on
     * at once. To reproduce in-game: cycle through every mode with the Stack Modifier at open air.
     * Then select Storage Stack, have an admin disable Storage Stack and reload: the hotbar shows
     * the next enabled mode, and the next modifier click places that type instead of doing nothing.
     */
    public static void modeCycleFollowsOrderAndPolicy(GameTestHelper helper) {
        try (GestureStateScope scope = new GestureStateScope()) {
            selectMode(StackMode.STORAGE_STACK);
            List<StackMode> walked = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                ClientGestures.cycleMode();
                walked.add(ClientGestures.currentMode());
            }
            checkEquals(
                    List.of(
                            StackMode.SINGLES_STACK,
                            StackMode.BAR_STACK,
                            StackMode.TOGGLE_PERMANENT,
                            StackMode.STORAGE_STACK),
                    walked,
                    "Cycle order");

            applyFlags(true, false, true);
            ClientGestures.cycleMode();
            checkEquals(StackMode.BAR_STACK, ClientGestures.currentMode(), "Disabled type kept");

            applyFlags(true, true, true);
            selectMode(StackMode.STORAGE_STACK);
            applyFlags(true, false, false);
            checkEquals(
                    StackMode.STORAGE_STACK,
                    ClientGestures.currentMode(),
                    "Enabled selection moved");
            applyFlags(false, true, true);
            checkEquals(
                    StackMode.SINGLES_STACK,
                    ClientGestures.currentMode(),
                    "Disabled selection kept");
            applyFlags(false, false, false);
            checkEquals(
                    StackMode.TOGGLE_PERMANENT, ClientGestures.currentMode(), "All types disabled");
        }
        helper.succeed();
    }

    private static void applyFlags(boolean storage, boolean singles, boolean bars) {
        ConfigSyncPkt current = ClientServerSettings.snapshot();
        ClientServerSettings.apply(
                new ConfigSyncPkt(
                        storage, singles, bars, current.genMods(), current.disabledMods()),
                null);
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
        private final List<GestureStatePkt> sent = new ArrayList<>();
        private final Consumer<GestureStatePkt> previousSender;
        private final StackMode previousMode = ClientGestures.currentMode();
        private final ConfigSyncPkt previousSettings = ClientServerSettings.snapshot();

        private GestureStateScope() {
            enableAllTypes();
            previousSender = ClientGestures.setSender(sent::add);
            ClientGestures.resetSync();
        }

        private void enableAllTypes() {
            ClientServerSettings.apply(
                    new ConfigSyncPkt(
                            true,
                            true,
                            true,
                            previousSettings.genMods(),
                            previousSettings.disabledMods()),
                    null);
        }

        @Override
        public void close() {
            enableAllTypes();
            selectMode(previousMode);
            ClientServerSettings.apply(previousSettings, null);
            ClientGestures.setSender(previousSender);
            ClientGestures.resetSync();
        }
    }
}
