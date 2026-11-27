package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.signalAt;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.block.StackBlock;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.StackItemStorage;
import com.github.crittscott.somestacks.util.StackMode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Storage pile behavior in a live level: deposits filling partial stacks before empty ones,
 * growth when a pile fills, and settling consolidating, packing, and sorting. Player scenarios
 * click through the loader; automation scenarios are in {@link AutomationChecks}.
 */
public final class StoragePileChecks {
    private StoragePileChecks() {}

    /**
     * A deposit fills a compatible partial stack before using an empty slot. Internal: this calls
     * the pile's deposit directly to pin which slot fills first, which a click cannot observe
     * slot by slot; {@link #fullPileGrowsByOneBlock} is the nearest real-click check. To reproduce
     * in-game: leave a partial stack of one item in Storage, then deposit more of that item. The
     * partial reaches its limit before any remainder appears in another cube.
     */
    public static void depositFillsCompatiblePartialBeforeEmptySlot(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        storage.getItems().insertItem(5, new ItemStack(Items.STONE, 60));
        ItemStack offered = new ItemStack(Items.STONE, 8);

        int moved = storage.deposit(offered, null);

        checkEquals(8, moved, "Moved count");
        checkEquals(0, offered.getCount(), "Offered remainder");
        checkEquals(64, storage.getItems().getStackInSlot(5).getCount(), "Compatible partial");
        checkEquals(4, storage.getItems().getStackInSlot(0).getCount(), "First empty slot");
        helper.succeed();
    }

    /**
     * Depositing into a full Storage pile grows it upward by one block. To reproduce in-game: with
     * {@code max_pile_height} at 2 or more, fill every slot in one Storage block, then
     * modifier-click it in Storage mode holding one more allowed item. A second Storage block
     * appears above and holds the item.
     */
    public static void fullPileGrowsByOneBlock(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64));
        }
        storage.setRotation(2);
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.STONE, 1));
        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        try {
            GameTestScaffold.withPolicy(
                    Map.of(
                            ServerConfig.Setting.MAX_PILE_HEIGHT,
                            2,
                            ServerConfig.Setting.ENABLE_STORAGE_STACK_BLOCK,
                            true),
                    () ->
                            InteractionChecks.use(
                                    player,
                                    InteractionChecks.aim(
                                            player, storage.getBlockPos(), 0.5, 0.5, 0.5)));
            check(player.getMainHandItem().isEmpty(), "Deposit did not take the held item");
        } finally {
            ServerGestureState.clear(player.getUUID());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        StoragePile pile = storage.pile();

        check(pile != null, "Pile did not resolve");
        checkEquals(2, pile.height(), "Pile height");
        StorageStackBE upper =
                (StorageStackBE) helper.getLevel().getBlockEntity(storage.getBlockPos().above());
        checkEquals(2, upper.getRotation(), "Growth did not inherit native facing");
        check(pile == upper.pile(), "Members did not share the resolved pile");
        checkEquals(
                Items.STONE,
                pile.getSlot(StorageStackBE.SLOTS).getItem(),
                "First slot in grown block");
        helper.succeed();
    }

    /**
     * Settlement merges only exact component identities, restores legal stack sizes, sorts by item
     * id, then damage, then plain before component-bearing, and packs downward. To reproduce
     * in-game: put scattered dirt, plain stone, compatible and component-distinct stone, and
     * damaged pickaxes in a Storage pile through automation, then let it settle. Dirt comes first
     * and plain stone before the other stone; compatible items merge while distinct component or
     * damage variants remain separate in stable order.
     */
    public static void settleConsolidatesExactIdentityAndPacksDown(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        CompoundTag firstTag = new CompoundTag();
        firstTag.putInt("variant", 1);
        CompoundTag secondTag = new CompoundTag();
        secondTag.putInt("variant", 2);

        ItemStack first = new ItemStack(Items.STONE, 40);
        first.set(DataComponents.CUSTOM_DATA, CustomData.of(firstTag));
        ItemStack compatible = new ItemStack(Items.STONE, 30);
        compatible.set(DataComponents.CUSTOM_DATA, CustomData.of(firstTag.copy()));
        ItemStack distinct = new ItemStack(Items.STONE, 5);
        distinct.set(DataComponents.CUSTOM_DATA, CustomData.of(secondTag));
        storage.getItems().insertItem(10, first);
        storage.getItems().insertItem(20, compatible);
        storage.getItems().insertItem(25, distinct);
        ItemStack firstPickaxe = new ItemStack(Items.WOODEN_PICKAXE);
        firstPickaxe.setDamageValue(1);
        ItemStack secondPickaxe = new ItemStack(Items.WOODEN_PICKAXE);
        secondPickaxe.setDamageValue(2);
        storage.getItems().insertItem(3, firstPickaxe);
        storage.getItems().insertItem(4, secondPickaxe);
        storage.getItems().insertItem(8, new ItemStack(Items.STONE, 3));
        storage.getItems().insertItem(15, new ItemStack(Items.DIRT, 2));

        StoragePile pile = storage.pile();
        check(pile != null, "Pile did not resolve");
        pile.settle();

        checkEquals(Items.DIRT, pile.getSlot(0).getItem(), "Item id did not lead the order");
        checkEquals(2, pile.getSlot(0).getCount(), "Dirt stack");
        checkEquals(3, pile.getSlot(1).getCount(), "Plain stone stack");
        check(
                pile.getSlot(1).getComponentsPatch().isEmpty(),
                "Plain stone did not sort before component-bearing stone");
        checkEquals(64, pile.getSlot(2).getCount(), "Consolidated full stack");
        checkEquals(6, pile.getSlot(3).getCount(), "Consolidated partial stack");
        checkEquals(5, pile.getSlot(4).getCount(), "Distinct component stack");
        check(pile.getSlot(7).isEmpty(), "Packed contents left a gap");
        checkEquals(
                CustomData.of(firstTag),
                pile.getSlot(2).get(DataComponents.CUSTOM_DATA),
                "First component identity");
        checkEquals(
                CustomData.of(secondTag),
                pile.getSlot(4).get(DataComponents.CUSTOM_DATA),
                "Distinct component identity");
        checkEquals(1, pile.getSlot(5).getDamageValue(), "First damage component identity");
        checkEquals(2, pile.getSlot(6).getDamageValue(), "Second damage component identity");
        helper.succeed();
    }

    /**
     * Settlement rewrites only the cells whose contents change, and every cell it writes gets its
     * own stack, leaving the stacks it read from untouched. No in-game form: this guards against
     * two cells sharing one stack, which would make an edit to one cell change the other, and
     * against resyncing cells that already hold what they should.
     */
    public static void settleWritesOnlyChangedCellsWithFreshStacks(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        ItemStack settled = new ItemStack(Items.STONE, 64);
        ItemStack scatteredFull = new ItemStack(Items.STONE, 64);
        ItemStack scatteredPartial = new ItemStack(Items.STONE, 10);
        storage.getItems().insertItem(0, settled);
        storage.getItems().insertItem(3, scatteredFull);
        storage.getItems().insertItem(7, scatteredPartial);

        StoragePile pile = storage.pile();
        check(pile != null, "Pile did not resolve");
        pile.settle();

        check(pile.getSlot(0) == settled, "An already-settled cell was rewritten");
        checkEquals(64, pile.getSlot(1).getCount(), "Second full stack");
        checkEquals(10, pile.getSlot(2).getCount(), "Partial stack");
        check(pile.getSlot(3).isEmpty() && pile.getSlot(7).isEmpty(), "Packing left a gap");
        for (ItemStack written : List.of(pile.getSlot(1), pile.getSlot(2))) {
            check(
                    written != settled && written != scatteredFull && written != scatteredPartial,
                    "A written cell shares a stack with another cell or its source");
        }
        check(pile.getSlot(1) != pile.getSlot(2), "Two written cells share one stack");
        checkEquals(64, scatteredFull.getCount(), "Source full stack changed");
        checkEquals(10, scatteredPartial.getCount(), "Source partial stack changed");
        helper.succeed();
    }

    /**
     * A Storage Stack glows a quarter of each stored block's light per occupied slot, rounded down
     * and independent of the stack's count, and the total caps at full light. To reproduce
     * in-game: deposit a full stack of glowstone into a Storage Stack in a dark room and read its
     * light level with F3 (3); deposit five more full stacks of glowstone and read it again (15,
     * not 18).
     */
    public static void storedLightIsPerOccupiedSlotAndCapped(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        storage.getItems().insertItem(0, new ItemStack(Items.GLOWSTONE, 64));
        helper.runAfterDelay(
                2,
                () -> {
                    checkEquals(3, lightAt(helper, ORIGIN), "One full glowstone slot");
                    for (int slot = 1; slot < 6; slot++) {
                        storage.getItems()
                                .insertItem(slot, new ItemStack(Items.GLOWSTONE, 64));
                    }
                    helper.runAfterDelay(
                            2,
                            () -> {
                                checkEquals(
                                        15, lightAt(helper, ORIGIN), "Six full glowstone slots");
                                helper.succeed();
                            });
                });
    }

    /**
     * Extraction schedules settlement instead of leaving a lasting gap. To reproduce in-game: store
     * two different stacks, plain-click the first one out with an empty hand, and wait a moment.
     * The remaining stack packs into the first Storage position.
     */
    public static void extractionSettlesOnScheduledBlockTick(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        storage.getItems().insertItem(0, new ItemStack(Items.STONE, 64));
        storage.getItems().insertItem(1, new ItemStack(Items.DIRT, 1));

        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
        try {
            InteractionChecks.use(
                    player,
                    InteractionChecks.aimAt(
                            player,
                            storage.getBlockPos(),
                            GameTestScaffold.cellBox(BlockType.STORAGE_STACK, 0)));
            checkEquals(64, player.getMainHandItem().getCount(), "Extracted count");
        } finally {
            ServerGestureState.clear(player.getUUID());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
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
     * Empty temporary piles disappear while permanent piles retain their blocks. To reproduce
     * in-game: empty one ordinary Storage pile, then toggle another permanent with an empty hand in
     * permanence mode and empty it. Only the permanent block remains.
     */
    public static void emptyTemporaryPileDisappearsButPermanentPileRemains(GameTestHelper helper) {
        StorageStackBE temporary = placeStorage(helper, ORIGIN);
        StoragePile temporaryPile = temporary.pile();
        check(temporaryPile != null, "Temporary pile did not resolve");
        temporaryPile.settle();
        check(
                helper.getLevel().isEmptyBlock(helper.absolutePos(ORIGIN)),
                "Empty temporary pile remained");

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
     * Every block in a Storage pile reports the same whole-pile fill. To reproduce in-game: make a
     * two-block pile, fill the lower block with full stacks and leave the upper empty, then place
     * comparators against both blocks. Both report the half-full value.
     */
    public static void comparatorReadsTheWholePileFromEveryBlock(GameTestHelper helper) {
        StorageStackBE lower = placeStorage(helper, ORIGIN);
        placeStorage(helper, ORIGIN.above());
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            lower.getItems().insertItem(slot, new ItemStack(Items.STONE, 64));
        }

        // Half the pile's capacity is filled, and both blocks report that rather than their own.
        checkEquals(8, signalAt(helper, ORIGIN), "Lower block signal");
        checkEquals(8, signalAt(helper, ORIGIN.above()), "Upper block signal");
        helper.succeed();
    }

    /**
     * Placing Storage beneath a permanent pile preserves permanence, repacks contents into the new
     * base, and republishes comparator output. To reproduce in-game: make a full one-block
     * permanent pile with a comparator, place another Storage block beneath it, and wait. The
     * joined pile stays permanent, moves contents downward, and the comparator changes to the
     * two-block fill value.
     */
    public static void ordinaryPlacementBelowPermanentPileRepairsDerivedState(
            GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos lowerPos = helper.absolutePos(ORIGIN);
        BlockPos upperPos = helper.absolutePos(ORIGIN.above());
        BlockPos comparatorPos = upperPos.east();

        StorageStackBE upper = placeStorage(helper, ORIGIN.above());
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            upper.getItems().insertItem(slot, new ItemStack(Items.STONE, 64));
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
                                    CommonRegistry.storageStackBlock().defaultBlockState(),
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
     * Breaking the middle Storage block drops only that block's contents and leaves independent
     * runs above and below. To reproduce in-game: put different items in each block of a
     * three-block pile and break the middle block. Its items drop, while the other two blocks keep
     * their own contents and settle independently.
     */
    public static void breakingMiddleBlockDropsItsContentsAndSplitsThePile(GameTestHelper helper) {
        StorageStackBE lower = placeStorage(helper, ORIGIN);
        StorageStackBE middle = placeStorage(helper, ORIGIN.above());
        StorageStackBE upper = placeStorage(helper, ORIGIN.above(2));
        lower.getItems().insertItem(0, new ItemStack(Items.STONE, 2));
        middle.getItems().insertItem(0, new ItemStack(Items.DIRT, 7));
        upper.getItems().insertItem(0, new ItemStack(Items.APPLE, 3));

        GameTestScaffold.checkStateUpdatesPreserveContents(helper, middle);

        helper.setBlock(ORIGIN.above(), Blocks.AIR);

        helper.runAfterDelay(
                3,
                () -> {
                    checkEquals(
                            7,
                            GameTestScaffold.droppedNear(helper, middle.getBlockPos(), Items.DIRT),
                            "Dropped middle contents");
                    checkEquals(
                            2,
                            GameTestScaffold.heldAt(helper, lower.getBlockPos(), Items.STONE),
                            "Lower-run contents");
                    checkEquals(
                            3,
                            GameTestScaffold.heldAt(helper, upper.getBlockPos(), Items.APPLE),
                            "Upper-run contents");
                    check(
                            helper.getLevel().getBlockEntity(lower.getBlockPos())
                                    instanceof StorageStackBE,
                            "Lower run disappeared");
                    check(
                            helper.getLevel().getBlockEntity(upper.getBlockPos())
                                    instanceof StorageStackBE,
                            "Upper run disappeared");
                    checkEquals(
                            1,
                            ((StorageStackBE) helper.getLevel().getBlockEntity(lower.getBlockPos()))
                                    .pile()
                                    .height(),
                            "Lower-run height");
                    checkEquals(
                            1,
                            ((StorageStackBE) helper.getLevel().getBlockEntity(upper.getBlockPos()))
                                    .pile()
                                    .height(),
                            "Upper-run height");
                    helper.succeed();
                });
    }

    /**
     * The comparator range reserves 0 for an empty pile, as a vanilla container's does. A pile is
     * large enough that a proportional conversion would round hundreds of items down to 0, so the
     * reserved value is what makes an emptiness circuit read a pile correctly.
     *
     * <p>To reproduce in-game: compare an empty Storage pile, the same pile with one item, and a
     * full pile. Their comparator outputs are 0, at least 1, and 15 respectively.
     */
    public static void comparatorReservesZeroForAnEmptyPile(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        StoragePile pile = storage.pile();
        check(pile != null, "Pile did not resolve");

        checkEquals(0, pile.comparatorSignal(), "Empty pile signal");

        storage.getItems().insertItem(0, new ItemStack(Items.STONE, 1));
        checkEquals(1, pile.comparatorSignal(), "Signal for a single item in a whole pile");

        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.STONE, 64));
        }
        checkEquals(15, pile.comparatorSignal(), "Full pile signal");
        helper.succeed();
    }

    /**
     * To reproduce in-game: fill a Storage pile with mixed stack limits, save and reopen the world,
     * then extract an item. Comparators match the contents immediately after loading and again
     * after extraction, before the deferred settlement runs.
     */
    public static void comparatorContributionLoadsAndTracksMutations(GameTestHelper helper) {
        StorageStackBE source = placeStorage(helper, ORIGIN);
        source.getItems().setStackInSlot(0, new ItemStack(Items.STONE, 64));
        source.getItems().setStackInSlot(1, new ItemStack(Items.WOODEN_PICKAXE));
        StorageStackBE loaded = placeStorage(helper, ORIGIN.east(3));
        loaded.loadWithComponents(
                TagValueInput.create(
                        ProblemReporter.DISCARDING,
                        helper.getLevel().registryAccess(),
                        source.saveWithoutMetadata(helper.getLevel().registryAccess())));
        checkEquals(
                source.pile().comparatorSignal(),
                loaded.pile().comparatorSignal(),
                "Signal after loading saved contents");
        checkEquals(2, loaded.pile().comparatorSignal(), "Mixed stack-limit fill");
        loaded.getItems().extractItem(1, 1);
        checkEquals(1, loaded.pile().comparatorSignal(), "Signal after extraction");
        loaded.getItems().setStackInSlot(0, ItemStack.EMPTY);
        checkEquals(0, loaded.pile().comparatorSignal(), "Signal after clearing the last slot");
        helper.succeed();
    }

    /**
     * To reproduce in-game: use an item with max_stack_size=99, fill Storage by hand and through
     * automation, then let it settle. Every slot holds at most 64 and a fully filled pile gives
     * comparator strength 15. An item with a smaller maximum still uses that smaller capacity.
     */
    public static void storageCapAppliesToPlayersAutomationAndSettlement(
            GameTestHelper helper, Function<BlockEntity, GameTestScaffold.Automation> automation) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        GameTestScaffold.Automation access = automation.apply(storage);
        StoragePile pile = storage.pile();
        pile.setPermanent(true, null);
        ItemStack large = new ItemStack(Items.STONE, 99);
        large.set(DataComponents.MAX_STACK_SIZE, 99);
        checkEquals(64, pile.insertAt(0, large, true), "Run simulation capacity");
        check(storage.getItems().getStackInSlot(0).isEmpty(), "Run simulation mutated contents");
        checkEquals(64, pile.insertAt(0, large, false), "Run commit capacity");
        checkEquals(35, access.insert(1, large, true).getCount(), "Native simulation remainder");
        check(storage.getItems().getStackInSlot(1).isEmpty(), "Native simulation changed contents");
        checkEquals(35, access.insert(1, large, false).getCount(), "Native commit remainder");
        ItemStack hand = large.copy();
        checkEquals(99, storage.deposit(hand, null), "Player deposit count");
        pile.settle();
        checkEquals(
                227,
                GameTestScaffold.count(storage.getItems(), Items.STONE),
                "Settlement lost items");
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            check(
                    storage.getItems().getStackInSlot(slot).getCount()
                            <= StackItemStorage.STORAGE_SLOT_LIMIT,
                    "Settlement exceeded Storage capacity");
            storage.getItems().setStackInSlot(slot, large.copyWithCount(64));
        }
        checkEquals(15, pile.comparatorSignal(), "Capped pile did not report full");
        checkEquals(
                99,
                access.insert(0, large, true).getCount(),
                "Native offer overfilled a full slot");
        storage.getItems().setStackInSlot(0, new ItemStack(Items.ENDER_PEARL, 16));
        checkEquals(15, pile.comparatorSignal(), "Smaller item limit did not count as full");
        helper.succeed();
    }

    /**
     * To reproduce in-game: load a preceding-release Storage block containing stacks above 64.
     * Spare cubes take the excess. When every cube is occupied, excess remains saved in SetAside
     * and returns after freeing its original cube and loading the block again.
     */
    public static void storageDiskMigrationPreservesOverCapContents(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        StorageStackBE source = placeStorage(helper, ORIGIN);
        ItemStack large = new ItemStack(Items.STONE, 99);
        large.set(DataComponents.MAX_STACK_SIZE, 99);
        source.getItems().setStackInSlot(0, large.copy());
        var sparseTag = source.saveWithoutMetadata(registries);
        StorageStackBE loaded = placeStorage(helper, ORIGIN.east(3));
        loaded.loadWithComponents(
                TagValueInput.create(ProblemReporter.DISCARDING, registries, sparseTag));
        checkEquals(64, loaded.getItems().getStackInSlot(0).getCount(), "Loaded primary capacity");
        checkEquals(
                99,
                GameTestScaffold.count(loaded.getItems(), Items.STONE),
                "Sparse migration lost items");
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++)
            source.getItems().setStackInSlot(slot, large.copy());
        loaded.loadWithComponents(
                TagValueInput.create(
                        ProblemReporter.DISCARDING,
                        registries,
                        source.saveWithoutMetadata(registries)));
        checkEquals(
                StorageStackBE.SLOTS * 64,
                GameTestScaffold.count(loaded.getItems(), Items.STONE),
                "Dense migration did not cap occupied slots");
        CompoundTag saved = loaded.saveWithoutMetadata(registries);
        var inventory = saved.getCompound(StackItemStorage.TAG_ITEMS).orElseThrow();
        var retained = inventory.getListOrEmpty(StackItemStorage.TAG_SET_ASIDE);
        int retainedCount = 0;
        for (int i = 0; i < retained.size(); i++) {
            retainedCount +=
                    ItemStack.CODEC
                            .parse(RegistryOps.create(NbtOps.INSTANCE, registries), retained.get(i))
                            .getOrThrow()
                            .getCount();
        }
        checkEquals(StorageStackBE.SLOTS * 35, retainedCount, "Dense migration lost overflow");
        loaded.getItems().setStackInSlot(0, ItemStack.EMPTY);
        saved = loaded.saveWithoutMetadata(registries);
        loaded.loadWithComponents(
                TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
        checkEquals(
                35,
                loaded.getItems().getStackInSlot(0).getCount(),
                "Retained overflow did not retry");
        checkEquals(
                StorageStackBE.SLOTS - 1,
                loaded.getItems()
                        .save(registries)
                        .getListOrEmpty(StackItemStorage.TAG_SET_ASIDE)
                        .size(),
                "Retry removed wrong retained entries");
        helper.succeed();
    }

    /** The light level the stack block at {@code relative} currently emits. */
    private static int lightAt(GameTestHelper helper, BlockPos relative) {
        return helper.getBlockState(relative).getValue(StackBlock.LIGHT_LEVEL);
    }
}
