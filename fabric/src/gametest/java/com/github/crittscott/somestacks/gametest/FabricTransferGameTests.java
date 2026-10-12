package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.occupied;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
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
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.capability(blockEntity);
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
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.capability(blockEntity);
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
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.capability(singles);

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
        SlottedStorage<ItemVariant> first = FabricGameTestSupport.capability(base);
        SlottedStorage<ItemVariant> second = (SlottedStorage<ItemVariant>) ItemStorage.SIDED.find(
                helper.getLevel(), base.getBlockPos(), base.getBlockState(), base, Direction.DOWN);
        SlottedStorage<ItemVariant> above = FabricGameTestSupport.capability(top);
        check(first == second, "Sided lookup did not retain the adapter");
        check(first != above, "Different blocks did not retain their own adapters");
        for (Direction side : Direction.values()) {
            check(first == ItemStorage.SIDED.find(helper.getLevel(), base.getBlockPos(),
                    base.getBlockState(), base, side), "Side did not retain Storage adapter");
        }
        ItemVariant stone = ItemVariant.of(Items.STONE);
        try (Transaction outer = Transaction.openOuter()) {
            checkEquals(3L, first.getSlot(0).extract(stone, 3, outer), "First staged extraction");
            checkEquals(1L, above.getSlot(0).getAmount(), "Upper adapter staged amount");
            checkEquals(stone, above.getSlot(0).getResource(), "Upper adapter staged resource");
            checkEquals(4, base.getItems().getStackInSlot(0).getCount(), "Staging edited live contents");
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

    /**
     * No in-game reproduction applies: every side retains its block entity's adapter, and
     * Singles/Bar extraction restrictions and nested rollback are shared across run blocks.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void structuralRestrictionsAreSharedAcrossBlocksAndRollBack(GameTestHelper helper) {
        checkStructuralLedger(helper, placeSingles(helper, ORIGIN),
                placeSingles(helper, ORIGIN.above()), Items.STICK);
        checkStructuralLedger(helper, placeBar(helper, ORIGIN.east(3)),
                placeBar(helper, ORIGIN.east(3).above()), Items.IRON_INGOT);
        helper.succeed();
    }

    private static void checkStructuralLedger(GameTestHelper helper, StackBlockEntity base,
                                              StackBlockEntity top, Item item) {
        base.getItems().insertItem(0, new ItemStack(item), false);
        base.getItems().insertItem(1, new ItemStack(item), false);
        SlottedStorage<ItemVariant> first = FabricGameTestSupport.capability(base);
        SlottedStorage<ItemVariant> above = FabricGameTestSupport.capability(top);
        for (Direction side : Direction.values()) {
            check(first == ItemStorage.SIDED.find(helper.getLevel(), base.getBlockPos(),
                    base.getBlockState(), base, side), "Side did not retain structural adapter");
        }
        ItemVariant resource = ItemVariant.of(item);
        try (Transaction outer = Transaction.openOuter()) {
            try (Transaction nested = Transaction.openNested(outer)) {
                checkEquals(1L, above.getSlot(0).extract(resource, 1, nested),
                        "Nested structural extraction");
                checkEquals(0L, first.getSlot(1).extract(resource, 1, nested),
                        "Second structural position accepted through base");
                checkEquals(0L, first.getSlot(0).getAmount(), "Base did not see staged extraction");
            }
            checkEquals(1L, first.getSlot(0).getAmount(), "Nested abort did not restore item");
            checkEquals(1L, first.getSlot(1).extract(resource, 1, outer),
                    "Nested abort did not release structural restriction");
            checkEquals(0L, above.getSlot(0).extract(resource, 1, outer),
                    "Second structural position accepted through top");
        }
        checkEquals(2, occupied(base.getItems()), "Outer abort changed live structural contents");
        try (Transaction outer = Transaction.openOuter()) {
            try (Transaction nested = Transaction.openNested(outer)) {
                checkEquals(1L, above.getSlot(0).extract(resource, 1, nested),
                        "Nested committed structural extraction");
                nested.commit();
            }
            checkEquals(0L, first.getSlot(1).extract(resource, 1, outer),
                    "Nested commit lost structural restriction");
        }
        checkEquals(2, occupied(base.getItems()), "Outer abort retained nested committed extraction");
        try (Transaction outer = Transaction.openOuter()) {
            checkEquals(1L, first.getSlot(1).extract(resource, 1, outer),
                    "Outer abort did not release structural restriction");
            outer.commit();
        }
        checkEquals(1, occupied(base.getItems()), "Structural extraction did not commit once");
        checkEquals(item, base.getItems().getStackInSlot(0).getItem(), "Other position changed");
    }

}
