package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.occupied;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

/** Fabric-native transaction behavior exercised through the complete Transfer API storage. */
public final class FabricTransferGameTests implements FabricGameTest {
    /**
     * No in-game reproduction applies: Fabric automation stages an insertion without publishing
     * it to the block entity, then publishes the complete edit when the transaction commits.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
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
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
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
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
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
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void transactionAllowsOnlyOneStructuralExtractionPosition(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        int firstSlot = SinglesCubeIdx.indexFromColumn(0, 0);
        int secondSlot = SinglesCubeIdx.indexFromColumn(1, 0);
        singles.getItems().insertItem(firstSlot, new ItemStack(Items.STICK), false);
        singles.getItems().insertItem(secondSlot, new ItemStack(Items.PAPER), false);
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
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void repeatedLookupsShareOneRunLedger(GameTestHelper helper) {
        StorageStackBE base = placeStorage(helper, ORIGIN);
        StorageStackBE top = placeStorage(helper, ORIGIN.above());
        base.getItems().insertItem(0, new ItemStack(Items.STONE, 4), false);
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
        base.getItems().insertItem(0, new ItemStack(Items.STONE, 60), false);
        try (Transaction outer = Transaction.openOuter()) {
            checkEquals(3L, second.getSlot(0).insert(stone, 3, outer), "First insertion");
            checkEquals(1L, above.getSlot(0).insert(stone, 3, outer), "Shared remaining capacity");
            outer.commit();
        }
        checkEquals(64L, first.getSlot(0).getAmount(), "Committed insertion count");
        helper.succeed();
    }

}
