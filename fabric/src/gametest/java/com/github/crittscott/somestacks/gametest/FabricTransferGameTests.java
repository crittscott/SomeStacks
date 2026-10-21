package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.firstBarItem;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.occupied;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.seedSlot;

/** Fabric-native transaction behavior exercised through the complete Transfer API storage. */
public final class FabricTransferGameTests {
    /**
     * No in-game reproduction applies: Fabric automation stages an insertion without publishing
     * it to the block entity, then publishes the complete edit when the transaction commits.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void stagedInsertionIsInvisibleUntilCommit(GameTestHelper helper) {
        StorageStackBE blockEntity = placeStorage(helper, ORIGIN);
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(blockEntity);
        try (Transaction transaction = Transaction.openOuter()) {
            checkEquals(4L, storage.insert(
                    ItemVariant.of(Items.STONE), 4, transaction), "Staged insertion");
            checkEquals(0, GameTestScaffold.count(blockEntity.getItems(), Items.STONE),
                    "Uncommitted insertion reached the world");
            transaction.commit();
        }
        checkEquals(4, GameTestScaffold.count(blockEntity.getItems(), Items.STONE),
                "Committed insertion did not reach the world");
        helper.succeed();
    }

    /**
     * No in-game reproduction applies: this verifies whole-storage Fabric transactions, including
     * nested rollback, commit all mutations together or leave the run unchanged.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void wholeStorageInsertExtractAndNestedRollbackAreAtomic(GameTestHelper helper) {
        StorageStackBE blockEntity = placeStorage(helper, ORIGIN);
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(blockEntity);
        ItemVariant stone = ItemVariant.of(Items.STONE);
        ItemVariant dirt = ItemVariant.of(Items.DIRT);

        try (Transaction outer = Transaction.openOuter()) {
            checkEquals(12L, storage.insert(stone, 12, outer), "Staged stone insertion");
            try (Transaction nested = Transaction.openNested(outer)) {
                checkEquals(3L, storage.insert(dirt, 3, nested), "Nested dirt insertion");
                nested.commit();
            }
        }
        checkEquals(0, FabricGameTestSupport.count(storage, Items.STONE),
                "Aborted outer transaction kept stone");
        checkEquals(0, FabricGameTestSupport.count(storage, Items.DIRT),
                "Aborted outer transaction kept nested dirt");

        try (Transaction outer = Transaction.openOuter()) {
            checkEquals(12L, storage.insert(stone, 12, outer), "Committed stone insertion");
            try (Transaction nested = Transaction.openNested(outer)) {
                checkEquals(3L, storage.insert(dirt, 3, nested), "Aborted nested insertion");
            }
            outer.commit();
        }
        checkEquals(12, FabricGameTestSupport.count(storage, Items.STONE),
                "Committed stone count");
        checkEquals(0, FabricGameTestSupport.count(storage, Items.DIRT),
                "Aborted nested dirt count");

        try (Transaction transaction = Transaction.openOuter()) {
            checkEquals(5L, storage.extract(stone, 5, transaction),
                    "Whole-storage extraction");
            transaction.commit();
        }
        checkEquals(7, FabricGameTestSupport.count(storage, Items.STONE),
                "Count after whole-storage extraction");
        helper.succeed();
    }

    /**
     * To reproduce in-game: connect Fabric Transfer API automation between a chest and a SomeStacks
     * run; moving an item removes it from the chest and inserts it into the run.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void storageUtilMovesItemsFromChestIntoRun(GameTestHelper helper) {
        StorageStackBE blockEntity = placeStorage(helper, ORIGIN);
        SlottedStorage<ItemVariant> destination = FabricGameTestSupport.storage(blockEntity);
        BlockPos chestPos = helper.absolutePos(ORIGIN.east(3));
        ServerLevel level = helper.getLevel();
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
        check(chest != null, "Chest block entity was not created");
        chest.setItem(0, new ItemStack(Items.APPLE, 6));
        Storage<ItemVariant> source = ItemStorage.SIDED.find(
                level, chestPos, chest.getBlockState(), chest, Direction.UP);
        check(source != null, "Chest storage was not exposed");

        try (Transaction transaction = Transaction.openOuter()) {
            long moved = StorageUtil.move(
                    source, destination, variant -> variant.isOf(Items.APPLE), 4, transaction);
            checkEquals(4L, moved, "Moved apple count");
            transaction.commit();
        }

        checkEquals(2, chest.getItem(0).getCount(), "Apples left in chest");
        checkEquals(4, FabricGameTestSupport.count(destination, Items.APPLE),
                "Apples moved into run");
        helper.succeed();
    }

    /**
     * No in-game reproduction applies: this verifies one Fabric transaction cannot stage structural
     * extraction from two run positions whose simultaneous gravity updates would conflict.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void transactionAllowsOnlyOneStructuralExtractionPosition(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        int firstSlot = SinglesCubeIdx.indexFromColumn(0, 0);
        int secondSlot = SinglesCubeIdx.indexFromColumn(1, 0);
        singles.getItems().insertItem(firstSlot, new ItemStack(Items.STICK));
        singles.getItems().insertItem(secondSlot, new ItemStack(Items.PAPER));
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(singles);

        try (Transaction transaction = Transaction.openOuter()) {
            checkEquals(1L, storage.getSlot(firstSlot).extract(
                    ItemVariant.of(Items.STICK), 1, transaction), "First extraction");
            checkEquals(0L, storage.getSlot(secondSlot).extract(
                    ItemVariant.of(Items.PAPER), 1, transaction), "Second extraction");
            transaction.commit();
        }

        checkEquals(1, occupied(singles.getItems()), "Occupancy after structural extraction");
        checkEquals(Items.PAPER, singles.getItems().getStackInSlot(secondSlot).getItem(),
                "Refused second position changed");
        helper.succeed();
    }
    /**
     * No in-game reproduction applies: repeated sided lookups and different blocks in a run must
     * observe the same staged contents, including nested rollback and outer abort.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void repeatedLookupsShareOneRunLedger(GameTestHelper helper) {
        StorageStackBE base = placeStorage(helper, ORIGIN);
        StorageStackBE top = placeStorage(helper, ORIGIN.above());
        base.getItems().insertItem(0, new ItemStack(Items.STONE, 4));
        SlottedStorage<ItemVariant> first = FabricGameTestSupport.storage(base);
        SlottedStorage<ItemVariant> second = (SlottedStorage<ItemVariant>) ItemStorage.SIDED.find(
                helper.getLevel(), base.getBlockPos(), base.getBlockState(), base, Direction.DOWN);
        SlottedStorage<ItemVariant> above = FabricGameTestSupport.storage(top);
        check(first == second, "Sided lookup did not retain the adapter");
        ItemVariant stone = ItemVariant.of(Items.STONE);
        try (Transaction outer = Transaction.openOuter()) {
            checkEquals(3L, first.getSlot(0).extract(stone, 3, outer), "First staged extraction");
            try (Transaction nested = Transaction.openNested(outer)) {
                checkEquals(1L, above.getSlot(0).extract(stone, 3, nested), "Shared remainder");
            }
            checkEquals(1L, second.getSlot(0).getAmount(), "Nested rollback remainder");
        }
        checkEquals(4L, first.getSlot(0).getAmount(), "Outer abort remainder");
        try (Transaction outer = Transaction.openOuter()) {
            checkEquals(3L, first.getSlot(0).extract(stone, 3, outer), "Committed first extraction");
            checkEquals(1L, above.getSlot(0).extract(stone, 3, outer), "Committed shared remainder");
            outer.commit();
        }
        checkEquals(0L, first.getSlot(0).getAmount(), "Extraction committed twice");
        base.getItems().insertItem(0, new ItemStack(Items.STONE, 60));
        try (Transaction outer = Transaction.openOuter()) {
            checkEquals(3L, second.getSlot(0).insert(stone, 3, outer), "First insertion");
            checkEquals(1L, above.getSlot(0).insert(stone, 3, outer), "Shared remaining capacity");
            outer.commit();
        }
        checkEquals(64L, first.getSlot(0).getAmount(), "Committed insertion count");
        helper.succeed();
    }

    /**
     * No in-game reproduction applies: staging an insertion into a run's headroom leaves the water
     * there untouched when the transaction aborts, and committing it grows a waterlogged block of
     * the run's own type for Storage, Singles, and Bar alike.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void headroomRollbackRestoresWaterAndCommitGrowsEachType(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        SinglesStackBE singles = placeSingles(helper, ORIGIN.east());
        BarStackBE bar = placeBar(helper, ORIGIN.east(2));
        Item barItem = firstBarItem();
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            seedSlot(singles.getItems(), slot, new ItemStack(Items.STICK));
            seedSlot(bar.getItems(), slot, new ItemStack(barItem));
        }

        checkHeadroomGrowth(helper, storage, StorageStackBE.SLOTS, Items.STONE);
        checkHeadroomGrowth(helper, singles, SinglesStackBE.SLOTS, Items.STICK);
        checkHeadroomGrowth(helper, bar, BarStackBE.SLOTS, barItem);
        helper.succeed();
    }

    private static void checkHeadroomGrowth(
            GameTestHelper helper, StackBlockEntity base, int headroomSlot, Item item) {
        ServerLevel level = helper.getLevel();
        BlockPos above = base.getBlockPos().above();
        level.setBlock(above, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(base);
        ItemVariant variant = ItemVariant.of(item);
        String type = base.getClass().getSimpleName();

        try (Transaction transaction = Transaction.openOuter()) {
            checkEquals(1L, storage.getSlot(headroomSlot).insert(variant, 1, transaction),
                    type + " staged headroom insertion");
            check(level.getBlockState(above).is(Blocks.WATER), type + " staged growth reached the world");
        }
        check(level.getBlockState(above).is(Blocks.WATER), type + " rollback did not leave the water");

        try (Transaction transaction = Transaction.openOuter()) {
            checkEquals(1L, storage.getSlot(headroomSlot).insert(variant, 1, transaction),
                    type + " committed headroom insertion");
            transaction.commit();
        }
        BlockState grown = level.getBlockState(above);
        check(grown.is(base.getBlockState().getBlock()), type + " commit did not grow the run");
        check(grown.getValue(BlockStateProperties.WATERLOGGED), type + " grown block lost the water");
        check(level.getBlockEntity(above) instanceof StackBlockEntity grownEntity
                        && grownEntity.getItems().getStackInSlot(0).is(item),
                type + " grown block did not receive the item");
    }

    /**
     * No in-game reproduction applies: one transaction fills every Singles cell and Bar position of
     * a block bottom-up and then the bottom layer of its headroom, each placement standing on ones
     * staged earlier in the same transaction.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void stagedSupportFillsCellsAndHeadroomBottomUp(GameTestHelper helper) {
        checkStagedFill(helper, placeSingles(helper, ORIGIN),
                SinglesCubeIdx.LAYER_SIZE, SinglesStackBE.SLOTS, Items.STICK);
        checkStagedFill(helper, placeBar(helper, ORIGIN.east(2)),
                BarCubeIdx.LAYER_SIZE, BarStackBE.SLOTS, firstBarItem());
        helper.succeed();
    }

    private static void checkStagedFill(
            GameTestHelper helper, StackBlockEntity base, int layerSize, int slotsPerBlock, Item item) {
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(base);
        ItemVariant variant = ItemVariant.of(item);
        String type = base.getClass().getSimpleName();
        int fillTo = slotsPerBlock + layerSize;

        try (Transaction transaction = Transaction.openOuter()) {
            checkEquals(0L, storage.getSlot(layerSize).insert(variant, 1, transaction),
                    type + " unsupported second layer accepted");
            for (int slot = 0; slot < fillTo; slot++) {
                checkEquals(1L, storage.getSlot(slot).insert(variant, 1, transaction),
                        type + " staged insertion at " + slot);
            }
            checkEquals(0, occupied(base.getItems()), type + " staged fill reached the world");
            transaction.commit();
        }

        checkEquals(slotsPerBlock, occupied(base.getItems()), type + " committed base occupancy");
        check(helper.getLevel().getBlockEntity(base.getBlockPos().above()) instanceof StackBlockEntity grown
                        && occupied(grown.getItems()) == layerSize,
                type + " committed headroom layer");
    }

    /**
     * No in-game reproduction applies: a staged Singles or Bar extraction refuses insertion while it
     * is pending, leaves the run unchanged on abort, and on commit performs the run's own movement:
     * Singles shifts the column down, Bar backfills from its topmost bar.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void positionalExtractionRollsBackAndCommitsMovement(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        int bottom = SinglesCubeIdx.indexFromColumn(0, 0);
        int second = SinglesCubeIdx.indexFromColumn(0, 1);
        int besideBottom = SinglesCubeIdx.indexFromColumn(1, 0);
        seedSlot(singles.getItems(), bottom, new ItemStack(Items.STICK));
        seedSlot(singles.getItems(), second, new ItemStack(Items.PAPER));
        SlottedStorage<ItemVariant> singlesStorage = FabricGameTestSupport.storage(singles);

        try (Transaction transaction = Transaction.openOuter()) {
            checkEquals(1L, singlesStorage.getSlot(bottom).extract(
                    ItemVariant.of(Items.STICK), 1, transaction), "Staged Singles extraction");
            checkEquals(0L, singlesStorage.getSlot(besideBottom).insert(
                    ItemVariant.of(Items.STICK), 1, transaction), "Insertion during structural extraction");
        }
        check(singles.getItems().getStackInSlot(bottom).is(Items.STICK), "Singles abort moved the bottom item");
        check(singles.getItems().getStackInSlot(second).is(Items.PAPER), "Singles abort moved the upper item");

        try (Transaction transaction = Transaction.openOuter()) {
            checkEquals(1L, singlesStorage.getSlot(bottom).extract(
                    ItemVariant.of(Items.STICK), 1, transaction), "Committed Singles extraction");
            transaction.commit();
        }
        check(singles.getItems().getStackInSlot(bottom).is(Items.PAPER), "Singles column did not shift down");
        check(singles.getItems().getStackInSlot(second).isEmpty(), "Singles column kept the upper item");

        BarStackBE bar = placeBar(helper, ORIGIN.east(2));
        Item barItem = firstBarItem();
        int lastInLayer = BarCubeIdx.LAYER_SIZE - 1;
        for (int slot = 0; slot <= lastInLayer; slot++) {
            seedSlot(bar.getItems(), slot, new ItemStack(barItem));
        }
        SlottedStorage<ItemVariant> barStorage = FabricGameTestSupport.storage(bar);

        try (Transaction transaction = Transaction.openOuter()) {
            checkEquals(1L, barStorage.getSlot(0).extract(ItemVariant.of(barItem), 1, transaction),
                    "Staged Bar extraction");
            checkEquals(0L, barStorage.getSlot(0).insert(ItemVariant.of(barItem), 1, transaction),
                    "Insertion during structural extraction");
        }
        checkEquals(BarCubeIdx.LAYER_SIZE, occupied(bar.getItems()), "Bar abort changed occupancy");

        try (Transaction transaction = Transaction.openOuter()) {
            checkEquals(1L, barStorage.getSlot(0).extract(ItemVariant.of(barItem), 1, transaction),
                    "Committed Bar extraction");
            transaction.commit();
        }
        check(!bar.getItems().getStackInSlot(0).isEmpty(), "Bar extraction was not backfilled");
        check(bar.getItems().getStackInSlot(lastInLayer).isEmpty(), "Bar backfill did not take the topmost bar");
        checkEquals(BarCubeIdx.LAYER_SIZE - 1, occupied(bar.getItems()), "Bar occupancy after extraction");
        helper.succeed();
    }

    /**
     * No in-game reproduction applies: one transaction empties a Storage slot and refills it with a
     * different item, judging the second step against the slot's staged contents.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void storageSlotReplacementUsesStagedCapacity(GameTestHelper helper) {
        StorageStackBE blockEntity = placeStorage(helper, ORIGIN);
        blockEntity.getItems().insertItem(0, new ItemStack(Items.STONE, 10));
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(blockEntity);

        try (Transaction transaction = Transaction.openOuter()) {
            checkEquals(10L, storage.getSlot(0).extract(ItemVariant.of(Items.STONE), 10, transaction),
                    "Staged extraction");
            checkEquals(5L, storage.getSlot(0).insert(ItemVariant.of(Items.DIRT), 5, transaction),
                    "Staged replacement");
            check(storage.getSlot(0).getResource().isOf(Items.DIRT), "Staged slot did not show the replacement");
            checkEquals(10, blockEntity.getItems().getStackInSlot(0).getCount(),
                    "Uncommitted replacement reached the world");
            transaction.commit();
        }

        check(blockEntity.getItems().getStackInSlot(0).is(Items.DIRT), "Committed slot item");
        checkEquals(5, blockEntity.getItems().getStackInSlot(0).getCount(), "Committed slot count");
        checkEquals(0, GameTestScaffold.count(blockEntity.getItems(), Items.STONE), "Stone left after replacement");
        helper.succeed();
    }
}
