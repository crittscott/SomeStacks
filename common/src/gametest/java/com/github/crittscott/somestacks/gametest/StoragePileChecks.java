package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.count;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.signalAt;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.withPolicy;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.StorageStackBlock;
import com.github.crittscott.somestacks.gametest.GameTestScaffold.Automation;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.StackMode;
import com.github.crittscott.somestacks.util.StackPlacement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Storage pile behavior in a live level: deposits filling partial stacks before empty ones, growth
 * when a pile fills, automation insertion addressing the slot it names, obstruction refusing
 * growth, and settling consolidating, packing, and sorting. Player scenarios take the loader's
 * click; automation scenarios take the loader's {@link Automation} view.
 */
public final class StoragePileChecks {
    private StoragePileChecks() {}

    /**
     * To reproduce: create a permanent three-block Storage pile and use /data merge block to seed
     * sparse glowstone contents in its end blocks and diamonds in its middle block. Place
     * comparators beside both ends and break the middle before another inventory mutation. Seed two
     * full glowstone stacks below, seven diamonds in the middle, and two single glowstone items
     * above. Only seven diamonds drop. The lower pile packs to two slots (signal 2, light 6); the
     * upper pile merges to one slot (signal 1, light 3). The fixture first drains placement ticks,
     * then seeds sparse local slots and makes a pending upper edit in the removal tick, so it
     * specifically checks rescheduling at the new upper base. That same-tick pending-publication
     * assertion is internal; ordinary deposits pack from the base and cannot create this sparse
     * starting layout. Fixture contents carry a per-run tag so drops from other tests or earlier
     * runs are excluded.
     */
    public static void middleRemovalSettlesAndPublishesBothSurvivingPiles(GameTestHelper helper) {
        String marker = UUID.randomUUID().toString();
        StorageStackBE lower = placeStorage(helper, ORIGIN);
        StorageStackBE middle = placeStorage(helper, ORIGIN.above());
        StorageStackBE upper = placeStorage(helper, ORIGIN.above(2));
        StoragePile original = lower.pile();
        check(original != null, "Initial pile missing");
        original.setPermanent(true, null);
        BlockPos lowerComparator = GameTestScaffold.placeComparator(helper, ORIGIN);
        BlockPos upperComparator = GameTestScaffold.placeComparator(helper, ORIGIN.above(2));
        helper.runAfterDelay(
                6,
                () -> {
                    check(
                            !helper.getLevel()
                                    .getBlockTicks()
                                    .hasScheduledTick(
                                            upper.getBlockPos(), upper.getBlockState().getBlock()),
                            "Fixture retained an upper placement tick");
                    StoragePile cached = lower.pile();
                    check(
                            cached != null && cached == upper.pile(),
                            "Ends did not share the initial cache");
                    checkEquals(3, cached.height(), "Initial height");
                    lower.getItems()
                            .setStackInSlot(
                                    17, GameTestScaffold.markedStack(Items.GLOWSTONE, 64, marker));
                    lower.getItems()
                            .setStackInSlot(
                                    25, GameTestScaffold.markedStack(Items.GLOWSTONE, 64, marker));
                    middle.getItems()
                            .setStackInSlot(
                                    7, GameTestScaffold.markedStack(Items.DIAMOND, 7, marker));
                    upper.getItems()
                            .setStackInSlot(
                                    12, GameTestScaffold.markedStack(Items.GLOWSTONE, 1, marker));
                    upper.getItems()
                            .setStackInSlot(
                                    24, GameTestScaffold.markedStack(Items.GLOWSTONE, 1, marker));
                    checkEquals(
                            0,
                            upper.getBlockState().getValue(StorageStackBlock.LIGHT_LEVEL),
                            "Upper edit published before the split");
                    check(
                            helper.getLevel()
                                    .setBlock(
                                            middle.getBlockPos(),
                                            Blocks.AIR.defaultBlockState(),
                                            Block.UPDATE_ALL),
                            "Middle removal failed");
                    checkEquals(
                            7,
                            GameTestScaffold.droppedInColumn(
                                            helper, lower.getBlockPos(), upper.getBlockPos())
                                    .stream()
                                    .map(entity -> entity.getItem())
                                    .filter(stack ->
                                            GameTestScaffold.hasMarker(stack, marker)
                                                    && stack.is(Items.DIAMOND))
                                    .mapToInt(ItemStack::getCount)
                                    .sum(),
                            "Removed block drops");
                    checkEquals(
                            0L,
                            GameTestScaffold.droppedInColumn(
                                            helper, lower.getBlockPos(), upper.getBlockPos())
                                    .stream()
                                    .filter(entity ->
                                            GameTestScaffold.hasMarker(entity.getItem(), marker)
                                                    && !entity.getItem().is(Items.DIAMOND))
                                    .count(),
                            "A surviving block dropped contents");
                    helper.runAfterDelay(
                            6,
                            () -> {
                                StoragePile below = lower.pile();
                                StoragePile above = upper.pile();
                                check(
                                        below != null && above != null && below != above,
                                        "Split piles not independent");
                                checkEquals(lower.getBlockPos(), below.basePos(), "Lower base");
                                checkEquals(upper.getBlockPos(), above.basePos(), "Upper base");
                                checkEquals(1, below.height(), "Lower height");
                                checkEquals(1, above.height(), "Upper height");
                                checkEquals(
                                        64, below.getSlot(0).getCount(), "Lower packed first slot");
                                checkEquals(
                                        64,
                                        below.getSlot(1).getCount(),
                                        "Lower packed second slot");
                                checkEquals(
                                        2,
                                        above.getSlot(0).getCount(),
                                        "Upper merged and packed contents");
                                checkEquals(
                                        2,
                                        GameTestScaffold.occupied(lower.getItems()),
                                        "Lower occupied slots");
                                checkEquals(
                                        1,
                                        GameTestScaffold.occupied(upper.getItems()),
                                        "Upper occupied slots");
                                checkEquals(
                                        128,
                                        GameTestScaffold.count(lower.getItems(), Items.GLOWSTONE),
                                        "Lower conservation");
                                checkEquals(
                                        2,
                                        GameTestScaffold.count(upper.getItems(), Items.GLOWSTONE),
                                        "Upper conservation");
                                checkEquals(
                                        7,
                                        GameTestScaffold.droppedInColumn(
                                                        helper,
                                                        lower.getBlockPos(),
                                                        upper.getBlockPos())
                                                .stream()
                                                .map(entity -> entity.getItem())
                                                .filter(stack ->
                                                        GameTestScaffold.hasMarker(stack, marker)
                                                                && stack.is(Items.DIAMOND))
                                                .mapToInt(ItemStack::getCount)
                                                .sum(),
                                        "Dropped contents after settlement");
                                checkEquals(
                                        6,
                                        lower.getBlockState()
                                                .getValue(StorageStackBlock.LIGHT_LEVEL),
                                        "Lower light");
                                checkEquals(
                                        3,
                                        upper.getBlockState()
                                                .getValue(StorageStackBlock.LIGHT_LEVEL),
                                        "Upper light");
                                checkEquals(
                                        2,
                                        GameTestScaffold.comparatorOutput(helper, lowerComparator),
                                        "Lower comparator");
                                checkEquals(
                                        1,
                                        GameTestScaffold.comparatorOutput(helper, upperComparator),
                                        "Upper comparator");
                                helper.succeed();
                            });
                });
    }

    /**
     * Internal deposit-placement check: the fixture seeds 60 stone in local slot 5 and deposits
     * eight more through the block entity, with no player; slot 5 reaches 64 and slot 0 receives
     * four. It pins which slots a deposit fills before settlement, which no click shows.
     *
     * <p>The nearest real-trigger check is {@link #fullPileGrowsByOneBlock}. To see the visible
     * part, V-deposit 60 stone into Storage, then eight more in Survival: all eight leave the hand,
     * and one full stack and a four-item remainder occupy the pile.
     */
    public static void depositFillsCompatiblePartialBeforeEmptySlot(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        storage.getItems().insertItem(5, new ItemStack(Items.STONE, 60), false);
        ItemStack offered = new ItemStack(Items.STONE, 8);

        int moved = storage.deposit(offered, null);

        checkEquals(8, moved, "Moved count");
        checkEquals(0, offered.getCount(), "Offered remainder");
        checkEquals(64, storage.getItems().getStackInSlot(5).getCount(), "Compatible partial");
        checkEquals(4, storage.getItems().getStackInSlot(0).getCount(), "First empty slot");
        helper.succeed();
    }

    /**
     * To reproduce: allow pile height of at least two, fill all 27 slots of a Storage block with
     * full dirt stacks, and leave replaceable, unobstructed space above. Select Storage and
     * V-deposit one stone into its side. A second Storage block appears and stores it; the hand is
     * emptied in Survival.
     */
    public static void fullPileGrowsByOneBlock(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            InteractionChecks.LoaderClick click) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
        }
        withPolicy(
                Map.of(ServerConfig.Setting.MAX_PILE_HEIGHT, 2),
                () -> {
                    try (InteractionChecks.PlayerScope scope =
                            new InteractionChecks.PlayerScope(
                                    factory.apply(new ItemStack(Items.STONE)))) {
                        var player = scope.player;
                        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
                        click.click(
                                player,
                                InteractionChecks.HAND,
                                InteractionChecks.aim(player, storage.getBlockPos(), 0.2, 0.2));
                        check(player.getMainHandItem().isEmpty(), "Growth deposit kept the stone");
                    }
                });
        StoragePile pile = storage.pile();
        check(pile != null, "Pile did not resolve");
        checkEquals(2, pile.height(), "Pile height");
        checkEquals(Items.STONE, pile.getSlot(27).getItem(), "First slot in grown block");
        helper.succeed();
    }

    /**
     * To reproduce: build a two-block Storage pile, give its base one redstone-torch turn and its
     * top block three, fill both blocks with full dirt stacks, then select Storage and V-deposit
     * one stone into the base. The new third block shows the base's layout, not the top block's.
     */
    public static void grownBlockTakesTheBaseFacing(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            InteractionChecks.LoaderClick click) {
        growThreeBlockPileFromRotatedBase(helper, factory, click);
        helper.succeed();
    }

    /**
     * The scenario of {@link #grownBlockTakesTheBaseFacing} without its success call, for a loader
     * wrapper that asserts more about the same growth afterward.
     */
    public static void growThreeBlockPileFromRotatedBase(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            InteractionChecks.LoaderClick click) {
        StorageStackBE lower = placeStorage(helper, ORIGIN);
        StorageStackBE upper = placeStorage(helper, ORIGIN.above());
        lower.setRotation(1);
        upper.setRotation(3);
        for (StorageStackBE block : new StorageStackBE[] {lower, upper}) {
            for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
                block.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
            }
        }
        withPolicy(
                Map.of(ServerConfig.Setting.MAX_PILE_HEIGHT, 3),
                () -> {
                    try (InteractionChecks.PlayerScope scope =
                            new InteractionChecks.PlayerScope(
                                    factory.apply(new ItemStack(Items.STONE)))) {
                        var player = scope.player;
                        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
                        click.click(
                                player,
                                InteractionChecks.HAND,
                                InteractionChecks.aim(player, lower.getBlockPos(), 0.2, 0.2));
                        check(player.getMainHandItem().isEmpty(), "Growth deposit kept the stone");
                    }
                });

        StorageStackBE grown =
                (StorageStackBE) helper.getLevel().getBlockEntity(upper.getBlockPos().above());
        check(grown != null, "Pile did not grow");
        checkEquals(1, grown.getRotation(), "Grown block rotation");
        checkEquals(3, upper.getRotation(), "Old top rotation changed");
    }

    /**
     * To reproduce: fill a Storage block in Survival, switch to Adventure, and V-deposit again.
     * Existing room accepts items, but a full pile cannot grow and rejected items stay in hand.
     * Switch back to Survival or Creative to permit growth. Automation can still grow the pile.
     */
    public static void growthRequiresBuildPermission(GameTestHelper helper, ServerPlayer player) {
        boolean mayBuild = player.getAbilities().mayBuild;
        boolean creative = player.getAbilities().instabuild;
        try {
            StorageStackBE storage = placeStorage(helper, ORIGIN);
            player.getAbilities().mayBuild = false;
            ItemStack existing = new ItemStack(Items.DIRT);
            checkEquals(
                    1,
                    storage.deposit(existing, player),
                    "Existing room refused Adventure deposit");
            for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
                storage.getItems().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
            }
            ItemStack offered = new ItemStack(Items.STONE);
            BlockPos above = storage.getBlockPos().above();
            try (var events = new WorldEditChecks.EventProbe(helper.getLevel(), above)) {
                checkEquals(0, storage.deposit(offered, player), "Adventure growth accepted");
                checkEquals(1, offered.getCount(), "Rejected item consumed");
                check(
                        helper.getLevel().getBlockState(above).isAir(),
                        "Denied growth placed a block");
                events.expectNone();
            }
            player.getAbilities().mayBuild = true;
            player.getAbilities().instabuild = false;
            checkEquals(1, storage.deposit(offered, player), "Survival growth refused");
            for (boolean automation : new boolean[] {false, true}) {
                StorageStackBE other = placeStorage(helper, ORIGIN.east(automation ? 4 : 2));
                for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
                    other.getItems().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
                }
                player.getAbilities().instabuild = true;
                checkEquals(
                        1,
                        other.deposit(new ItemStack(Items.STONE), automation ? null : player),
                        automation ? "Automation growth refused" : "Creative actor growth refused");
            }
        } finally {
            player.getAbilities().mayBuild = mayBuild;
            player.getAbilities().instabuild = creative;
        }
        helper.succeed();
    }

    /**
     * To reproduce: rotate the top of a Storage pile, then V-place Storage above it or beneath an
     * overhanging pile. New joins inherit the adjacent facing and permanence. Water placement keeps
     * waterlogging. A /setblock with an explicit facing keeps that facing even beside another pile.
     * The fixture also checks that a gap between different facings prefers the block below.
     */
    public static void placementChoosesFacingBeforeJoining(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = CommonRegistry.STORAGE_STACK_BLOCK.get();
        StorageStackBE below = placeStorage(helper, ORIGIN);
        below.setRotation(1);
        below.pile().setPermanent(true, null);
        BlockPos target = below.getBlockPos().above();
        level.setBlock(target, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        var joinedState = StackPlacement.stateFor(block, level, target);
        checkEquals(
                Direction.WEST,
                joinedState.getValue(StorageStackBlock.HORIZONTAL_FACING),
                "Join facing");
        check(joinedState.getValue(StorageStackBlock.WATERLOGGED), "Join lost waterlogging");
        check(
                WorldEdits.placeChecked(
                        null, level, target, joinedState, Direction.DOWN, Shapes.block()),
                "Join placement refused");
        StorageStackBE joined = (StorageStackBE) level.getBlockEntity(target);
        checkEquals(joinedState, joined.getBlockState(), "Placement hook changed final state");
        check(joined.isPermanent(), "Join lost permanence");

        BlockPos gap = helper.absolutePos(ORIGIN.east(3).above());
        StorageStackBE upper = placeStorage(helper, ORIGIN.east(3).above(2));
        upper.setRotation(3);
        upper.pile().setPermanent(true, null);
        var beneathState = StackPlacement.stateFor(block, level, gap);
        checkEquals(
                Direction.EAST,
                beneathState.getValue(StorageStackBlock.HORIZONTAL_FACING),
                "Below-pile facing");
        check(
                WorldEdits.placeChecked(
                        null, level, gap, beneathState, Direction.UP, Shapes.block()),
                "Below-pile placement refused");
        StorageStackBE beneath = (StorageStackBE) level.getBlockEntity(gap);
        check(beneath.isPermanent(), "New base lost upper pile permanence");
        checkEquals(beneathState, beneath.getBlockState(), "Below-pile placement changed facing");
        check(level.removeBlock(gap, false), "Could not reopen gap");
        StorageStackBE lower = placeStorage(helper, ORIGIN.east(3));
        lower.setRotation(1);
        checkEquals(
                Direction.WEST,
                StackPlacement.stateFor(block, level, gap)
                        .getValue(StorageStackBlock.HORIZONTAL_FACING),
                "Gap did not prefer lower facing");
        var explicitState =
                block.defaultBlockState()
                        .setValue(StorageStackBlock.HORIZONTAL_FACING, Direction.SOUTH);
        check(level.setBlock(gap, explicitState, Block.UPDATE_ALL), "Explicit placement failed");
        StorageStackBE explicit = (StorageStackBE) level.getBlockEntity(gap);
        checkEquals(2, explicit.getRotation(), "Placement hook overrode explicit facing");
        checkEquals(
                explicitState, level.getBlockState(gap), "Rotation read changed explicit state");
        helper.succeed();
    }

    /**
     * Internal settlement fixture: directly seed stone stacks of 40 and 30 with {variant:1} and two
     * stacks of five with {variant:2} in separated slots, plus dirt, untagged stone, stone with an
     * explicit empty tag, and two wooden pickaxes with different damage. Settlement orders dirt,
     * untagged stone, tagged stone stacks of 64, 6, and 10, the empty-tag stone (its tag text "{}"
     * sorts after "{variant:...}", and it stays apart from untagged stone), then the pickaxes in
     * ascending damage order.
     *
     * <p>For a visible check, /give stone with those tags and deposit the same totals into Storage.
     * Also deposit dirt, untagged stone, and wooden pickaxes with different damage. After
     * settlement, matching tags merge into legal stacks, distinct tags stay separate, and occupied
     * cells pack toward the base in that order. The test's initial gaps require direct fixture
     * writes; source counts and independent output NBT are internal ownership checks.
     */
    public static void settleConsolidatesExactIdentityAndPacksDown(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        CompoundTag firstTag = new CompoundTag();
        firstTag.putInt("variant", 1);
        CompoundTag secondTag = new CompoundTag();
        secondTag.putInt("variant", 2);

        ItemStack first = new ItemStack(Items.STONE, 40);
        first.setTag(firstTag);
        ItemStack compatible = new ItemStack(Items.STONE, 30);
        compatible.setTag(firstTag.copy());
        ItemStack distinct = new ItemStack(Items.STONE, 5);
        distinct.setTag(secondTag);
        storage.getItems().insertItem(0, distinct.copy(), false);
        storage.getItems().insertItem(10, first, false);
        storage.getItems().insertItem(20, compatible, false);
        storage.getItems().insertItem(25, distinct, false);
        ItemStack undamaged = new ItemStack(Items.WOODEN_PICKAXE);
        ItemStack damaged = new ItemStack(Items.WOODEN_PICKAXE);
        damaged.setDamageValue(1);
        storage.getItems().insertItem(1, damaged, false);
        storage.getItems().insertItem(2, new ItemStack(Items.STONE), false);
        storage.getItems().insertItem(3, undamaged, false);
        storage.getItems().insertItem(4, new ItemStack(Items.DIRT), false);
        ItemStack emptyTag = new ItemStack(Items.STONE);
        emptyTag.setTag(new CompoundTag());
        storage.getItems().insertItem(5, emptyTag, false);

        StoragePile pile = storage.pile();
        check(pile != null, "Pile did not resolve");
        pile.settle();

        checkEquals(Items.DIRT, pile.getSlot(0).getItem(), "Registry item id ordering");
        checkEquals(Items.STONE, pile.getSlot(1).getItem(), "Untagged stone item");
        check(pile.getSlot(1).getTag() == null, "Untagged stone did not precede tagged stone");
        checkEquals(64, pile.getSlot(2).getCount(), "Consolidated full stack");
        checkEquals(6, pile.getSlot(3).getCount(), "Consolidated partial stack");
        checkEquals(10, pile.getSlot(4).getCount(), "Distinct tagged stack");
        checkEquals(firstTag, pile.getSlot(2).getTag(), "First tag identity");
        checkEquals(firstTag, pile.getSlot(3).getTag(), "Partial stack tag identity");
        checkEquals(secondTag, pile.getSlot(4).getTag(), "Distinct tag identity");
        checkEquals(Items.STONE, pile.getSlot(5).getItem(), "Empty-tag stone item");
        checkEquals(1, pile.getSlot(5).getCount(), "Empty-tag stone merged with untagged stone");
        checkEquals(new CompoundTag(), pile.getSlot(5).getTag(), "Empty tag treated as absent");
        checkEquals(1, pile.getSlot(1).getCount(), "Untagged stone absorbed empty-tag stone");
        checkEquals(Items.WOODEN_PICKAXE, pile.getSlot(6).getItem(), "First pickaxe item");
        checkEquals(Items.WOODEN_PICKAXE, pile.getSlot(7).getItem(), "Second pickaxe item");
        checkEquals(0, pile.getSlot(6).getDamageValue(), "Undamaged pickaxe ordering");
        checkEquals(1, pile.getSlot(7).getDamageValue(), "Damaged pickaxe ordering");
        check(pile.getSlot(8).isEmpty(), "Packed contents left a gap");
        checkEquals(40, first.getCount(), "Settlement mutated its first source stack");
        checkEquals(30, compatible.getCount(), "Settlement mutated its second source stack");
        check(
                pile.getSlot(2).getTag() != pile.getSlot(3).getTag(),
                "Consolidated pieces share mutable NBT");
        helper.succeed();
    }

    /**
     * To reproduce: rename stone "Alpha" and "Beta" in an anvil. Store a full stack of dirt in a
     * Storage Stack, then three Alpha, two Beta, and four Alpha in separate later cells. After
     * settlement the dirt stays in the base cell, the two Alpha stacks merge into one cell of
     * seven, and Beta keeps its own cell of two. The test seeds the separated cells directly; that
     * each written cell holds its own stack, and that the already-settled dirt cell is not
     * rewritten, are internal ownership checks.
     */
    public static void settleMergesOnlyIdenticalNames(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        ItemStack dirt = new ItemStack(Items.DIRT, 64);
        ItemStack alphaFirst =
                new ItemStack(Items.STONE, 3).setHoverName(Component.literal("Alpha"));
        ItemStack beta = new ItemStack(Items.STONE, 2).setHoverName(Component.literal("Beta"));
        ItemStack alphaSecond =
                new ItemStack(Items.STONE, 4).setHoverName(Component.literal("Alpha"));
        storage.getItems().setStackInSlot(0, dirt);
        storage.getItems().setStackInSlot(5, alphaFirst);
        storage.getItems().setStackInSlot(10, beta);
        storage.getItems().setStackInSlot(15, alphaSecond);

        StoragePile pile = storage.pile();
        check(pile != null, "Pile did not resolve");
        pile.settle();

        check(pile.getSlot(0) == dirt, "A settled cell was rewritten");
        checkEquals(7, pile.getSlot(1).getCount(), "Identically named stacks did not merge");
        checkEquals("Alpha", pile.getSlot(1).getHoverName().getString(), "Merged name");
        checkEquals(2, pile.getSlot(2).getCount(), "Differently named stack count");
        checkEquals("Beta", pile.getSlot(2).getHoverName().getString(), "Separate name");
        check(pile.getSlot(3).isEmpty(), "Packed contents left a gap");
        check(
                pile.getSlot(1) != alphaFirst && pile.getSlot(1) != alphaSecond,
                "A written cell kept a source stack");
        check(pile.getSlot(1).getTag() != alphaFirst.getTag(), "A written cell shares source NBT");
        check(pile.getSlot(1) != pile.getSlot(2), "Two cells share one stack");
        checkEquals(3, alphaFirst.getCount(), "Settlement mutated a source stack");
        checkEquals(4, alphaSecond.getCount(), "Settlement mutated a source stack");
        helper.succeed();
    }

    /**
     * To reproduce: store one full stone stack and one dirt item in Storage. Plain-right-click the
     * stone cell with an empty hand. The stone enters the hand, and after the scheduled block
     * update the remaining dirt packs into the base slot. The test directly seeds the initial order
     * and waits two ticks.
     */
    public static void extractionSettlesOnScheduledBlockTick(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            InteractionChecks.LoaderClick click) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        storage.getItems().insertItem(0, new ItemStack(Items.STONE, 64), false);
        storage.getItems().insertItem(1, new ItemStack(Items.DIRT, 1), false);

        try (InteractionChecks.PlayerScope scope =
                new InteractionChecks.PlayerScope(factory.apply(ItemStack.EMPTY))) {
            var player = scope.player;
            click.click(
                    player,
                    InteractionChecks.HAND,
                    InteractionChecks.aim(player, storage.getBlockPos(), 0.2, 0.2));
            checkEquals(Items.STONE, player.getMainHandItem().getItem(), "Extracted item");
            checkEquals(64, player.getMainHandItem().getCount(), "Extracted count");
        }

        helper.runAfterDelay(
                2,
                () -> {
                    StoragePile pile =
                            StoragePile.at(helper.getLevel(), helper.absolutePos(ORIGIN));
                    check(pile != null, "Pile disappeared unexpectedly");
                    checkEquals(
                            Items.DIRT,
                            pile.getSlot(0).getItem(),
                            "Remaining stack did not pack down");
                    check(pile.getSlot(1).isEmpty(), "Gap survived settle");
                    helper.succeed();
                });
    }

    /**
     * To reproduce: place two Storage piles with one item each. Select Toggle Permanent, hold V,
     * and empty-hand right-click one pile. Release V and extract the last item from each. After
     * settlement, the temporary pile disappears while the permanent pile remains empty. The
     * automated fixture starts with empty blocks, invokes settlement directly, and checks that the
     * removed pile reports zero fill and signal and accepts a cleanup request without a base.
     */
    public static void emptyTemporaryPileDisappearsButPermanentPileRemains(GameTestHelper helper) {
        StorageStackBE temporary = placeStorage(helper, ORIGIN);
        StoragePile temporaryPile = temporary.pile();
        check(temporaryPile != null, "Temporary pile did not resolve");
        temporaryPile.settle();
        check(
                helper.getLevel().isEmptyBlock(helper.absolutePos(ORIGIN)),
                "Empty temporary pile remained");
        checkEquals(0, temporaryPile.height(), "Trimmed temporary pile height");
        checkEquals(0.0, temporaryPile.fillLevel(), "Trimmed temporary pile fill");
        checkEquals(0, temporaryPile.comparatorSignal(), "Trimmed temporary pile comparator");
        temporaryPile.setPermanent(false, null);

        BlockPos permanentPos = ORIGIN.east(3);
        StorageStackBE permanent = placeStorage(helper, permanentPos);
        StoragePile permanentPile = permanent.pile();
        check(permanentPile != null, "Permanent pile did not resolve");
        permanentPile.setPermanent(true, null);
        permanentPile.settle();
        check(
                helper.getLevel().getBlockEntity(helper.absolutePos(permanentPos))
                        instanceof StorageStackBE,
                "Empty permanent pile disappeared");
        helper.succeed();
    }

    /**
     * The fixture fills every slot of the lower block of a two-block pile with full stone stacks
     * and leaves the upper block empty, so the pile is half full. Both blocks read 8: a lower-only
     * read would give 15 and an upper-only read 0.
     *
     * <p>To reproduce: create a permanent two-block Storage pile by filling until it grows, then
     * toggling permanence. Extract until exactly 27 full stacks remain, which settle into the lower
     * block. Put outward-facing comparators beside both blocks on solid supports; both output 8.
     */
    public static void comparatorReadsTheWholePileFromEveryBlock(GameTestHelper helper) {
        StorageStackBE lower = placeStorage(helper, ORIGIN);
        StorageStackBE upper = placeStorage(helper, ORIGIN.above());
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            lower.getItems().insertItem(slot, new ItemStack(Items.STONE, 64), false);
        }
        check(lower.pile() != null, "Pile did not resolve");

        checkEquals(8, signalAt(helper, ORIGIN), "Lower comparator signal");
        checkEquals(8, signalAt(helper, ORIGIN.above()), "Upper comparator signal");
        helper.succeed();
    }

    /**
     * Internal native-placement check: create a full, permanent Storage block above an empty
     * position with an outward-facing comparator. Its output is 15. Directly place an empty Storage
     * block below it with neighbor updates. After settlement, both blocks remain permanent,
     * contents move into the new base, and the comparator reads 8.
     *
     * <p>To reproduce this placement path, prepare the upper pile on a temporary support, fill it,
     * toggle permanence, remove the support, and use /setblock to place
     * somestacks:storage_stack_block below it. Ordinary item deposits do not create the test's
     * empty new base.
     */
    public static void ordinaryPlacementBelowPermanentPileRepairsDerivedState(
            GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos lowerPos = helper.absolutePos(ORIGIN);
        BlockPos upperPos = helper.absolutePos(ORIGIN.above());
        BlockPos comparatorPos = upperPos.east();

        StorageStackBE upper = placeStorage(helper, ORIGIN.above());
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            upper.getItems().insertItem(slot, new ItemStack(Items.STONE, 64), false);
        }
        StoragePile originalPile = upper.pile();
        check(originalPile != null, "Original pile did not resolve");
        originalPile.setPermanent(true, null);

        check(
                level.setBlock(
                        comparatorPos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL),
                "Could not place comparator support");
        check(
                level.setBlock(
                        comparatorPos,
                        Blocks.COMPARATOR
                                .defaultBlockState()
                                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST),
                        Block.UPDATE_ALL),
                "Could not place comparator");
        originalPile.settle();

        helper.runAfterDelay(
                3,
                () -> {
                    check(
                            level.getBlockEntity(comparatorPos) instanceof ComparatorBlockEntity,
                            "Comparator block entity was missing");
                    ComparatorBlockEntity comparator =
                            (ComparatorBlockEntity) level.getBlockEntity(comparatorPos);
                    checkEquals(15, comparator.getOutputSignal(), "Initial comparator output");

                    check(
                            level.setBlock(
                                    lowerPos,
                                    CommonRegistry.STORAGE_STACK_BLOCK.get().defaultBlockState(),
                                    Block.UPDATE_ALL),
                            "Could not place Storage Stack below pile");

                    helper.runAfterDelay(
                            4,
                            () -> {
                                check(
                                        level.getBlockEntity(lowerPos) instanceof StorageStackBE,
                                        "Placed Storage Stack disappeared");
                                StorageStackBE lower =
                                        (StorageStackBE) level.getBlockEntity(lowerPos);
                                StoragePile joinedPile = lower.pile();
                                check(joinedPile != null, "Joined pile did not resolve");
                                checkEquals(2, joinedPile.height(), "Joined pile height");
                                checkEquals(8, joinedPile.comparatorSignal(), "Joined pile signal");
                                checkEquals(
                                        8,
                                        comparator.getOutputSignal(),
                                        "Published comparator output");
                                check(
                                        lower.isPermanent(),
                                        "New pile base did not inherit permanence");
                                check(
                                        upper.isPermanent(),
                                        "Permanence was not propagated through pile");
                                checkEquals(
                                        Items.STONE,
                                        lower.getItems().getStackInSlot(0).getItem(),
                                        "Pile did not settle into its new base");
                                check(
                                        upper.getItems().getStackInSlot(0).isEmpty(),
                                        "Old base contents did not pack down");
                                helper.succeed();
                            });
                });
    }

    /**
     * Internal Storage slot check: insert eight stone into local slot 20 through the loader's
     * automation view, reject dirt offered to that now-incompatible slot, then wait for the
     * settlement the insertion scheduled. Before settlement stone occupies slot 20; afterward it
     * occupies slot 0. Simulation and caller-owned input are unchanged.
     *
     * <p>To check manually, use slot-addressing automation to insert into an empty Storage slot,
     * then observe the next settlement pack its contents toward the base. Precise pre-tick
     * placement and dry-run results require an automation probe.
     */
    public static void capabilityInsertionAnswersForTheSlotItIsGiven(
            GameTestHelper helper, Function<BlockEntity, Automation> automation) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        Automation capability = automation.apply(storage);
        ItemStack offered = new ItemStack(Items.STONE, 8);

        // A pile is a bag, so the slot named takes what a slot takes — here all eight, in slot 20
        // rather than at the base a deposit would have filled.
        checkEquals(
                0,
                capability.insert(20, offered, true).getCount(),
                "Simulated insertion into an empty slot");
        checkEquals(
                0,
                capability.insert(20, offered, false).getCount(),
                "Committed insertion into an empty slot");
        checkEquals(8, offered.getCount(), "Capability mutated caller input");
        checkEquals(
                8,
                storage.getItems().getStackInSlot(20).getCount(),
                "The slot named should hold the items");

        // An incompatible slot takes nothing rather than redirecting to available space elsewhere.
        checkEquals(
                8,
                capability.insert(20, new ItemStack(Items.DIRT, 8), true).getCount(),
                "Simulated insertion into an occupied incompatible slot");

        // Nothing here settles the pile: the settlement the insertion scheduled does.
        helper.runAfterDelay(
                2,
                () -> {
                    checkEquals(
                            8,
                            storage.getItems().getStackInSlot(0).getCount(),
                            "Scheduled settlement did not pack the insertion down to the base");
                    check(
                            storage.getItems().getStackInSlot(20).isEmpty(),
                            "Scheduled settlement left the inserted slot occupied");
                    checkEquals(
                            0,
                            count(storage.getItems(), Items.DIRT),
                            "Refused insertion stored something");
                    helper.succeed();
                });
    }

    /**
     * To reproduce: fill all 27 slots of a Storage block with full dirt stacks, allow height above
     * one, and put solid stone directly above. Offer four stone through automation to first
     * headroom slot 27. All four are refused and the obstruction remains. A simulated offer must
     * report the same refusal without modifying input.
     */
    public static void obstructionPreventsGrowthAndSimulationReportsNoRoom(
            GameTestHelper helper, Function<BlockEntity, Automation> automation) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
        }
        BlockPos above = helper.absolutePos(ORIGIN.above());
        helper.getLevel().setBlock(above, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        ItemStack offered = new ItemStack(Items.STONE, 4);

        withPolicy(
                Map.of(ServerConfig.Setting.MAX_PILE_HEIGHT, 2),
                () -> {
                    Automation capability = automation.apply(storage);
                    // The first advertised slot past what the pile holds: the one insertion grows
                    // into, and so the only one an obstruction can refuse.
                    int headroom = 27;
                    checkEquals(
                            4,
                            capability.insert(headroom, offered, true).getCount(),
                            "Simulated remainder");
                    checkEquals(
                            4,
                            capability.insert(headroom, offered, false).getCount(),
                            "Committed remainder");
                });

        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        check(helper.getLevel().getBlockState(above).is(Blocks.STONE), "Obstruction changed");
        helper.succeed();
    }

    /**
     * Internal shared-view check: seed twelve stone in the base of two contiguous Storage blocks.
     * Slot 0 and advertised slot count agree through automation views attached to either block.
     *
     * <p>To reproduce visibly, make a permanent two-block Storage pile, leave twelve stone stored,
     * and attach machines at different heights. Both access the same inventory and capacity.
     */
    public static void capabilityFromEveryBlockAddressesTheSamePile(
            GameTestHelper helper, Function<BlockEntity, Automation> automation) {
        StorageStackBE lower = placeStorage(helper, ORIGIN);
        StorageStackBE upper = placeStorage(helper, ORIGIN.above());
        lower.getItems().insertItem(0, new ItemStack(Items.STONE, 12), false);

        Automation lowerCapability = automation.apply(lower);
        Automation upperCapability = automation.apply(upper);

        checkEquals(12, lowerCapability.stackAt(0).getCount(), "Lower capability count");
        checkEquals(12, upperCapability.stackAt(0).getCount(), "Upper capability count");
        checkEquals(lowerCapability.slots(), upperCapability.slots(), "Capability shape");
        helper.succeed();
    }
}
