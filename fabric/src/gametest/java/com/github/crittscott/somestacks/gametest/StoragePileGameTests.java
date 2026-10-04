package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.count;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

/**
 * Storage pile behavior in a live level: deposits filling partial stacks before empty ones, growth
 * when a pile fills, storage insertion addressing the specified slot, obstruction refusing growth,
 * and settling consolidating, packing, and sorting.
 */
public final class StoragePileGameTests implements FabricGameTest {
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void depositFillsCompatiblePartialBeforeEmptySlot(GameTestHelper helper) {
        StoragePileChecks.depositFillsCompatiblePartialBeforeEmptySlot(helper);
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void fullPileGrowsByOneBlock(GameTestHelper helper) {
        StoragePileChecks.fullPileGrowsByOneBlock(helper);
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void storageInsertionAnswersForTheSlotItIsGiven(GameTestHelper helper) {
        StorageStackBE blockEntity = placeStorage(helper, ORIGIN);
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(blockEntity);
        ItemStack offered = new ItemStack(Items.STONE, 8);

        // A pile is a bag, so the slot named takes what a slot takes — here all eight, in slot 20
        // rather than at the base a deposit would have filled.
        checkEquals(0, FabricGameTestSupport.insertAt(storage, 20, offered, true).getCount(),
                "Simulated insertion into an empty slot");
        checkEquals(0, FabricGameTestSupport.insertAt(storage, 20, offered, false).getCount(),
                "Committed insertion into an empty slot");
        checkEquals(8, offered.getCount(), "Storage mutated caller input");
        checkEquals(8, blockEntity.getItems().getStackInSlot(20).getCount(),
                "The slot named should hold the items");

        // An incompatible slot takes nothing rather than redirecting to available space elsewhere.
        ItemStack other = new ItemStack(Items.DIRT, 8);
        checkEquals(8, FabricGameTestSupport.insertAt(storage, 20, other, true).getCount(),
                "Simulated insertion into an occupied incompatible slot");

        // The settle the insertion scheduled is what puts the items at the base.
        StoragePile pile = blockEntity.pile();
        check(pile != null, "Pile did not resolve");
        pile.settle();
        checkEquals(8, blockEntity.getItems().getStackInSlot(0).getCount(),
                "Settle should pack the insertion down to the base");
        checkEquals(0, count(blockEntity.getItems(), Items.DIRT),
                "Refused insertion stored something");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void obstructionPreventsGrowthAndSimulationReportsNoRoom(GameTestHelper helper) {
        StorageStackBE blockEntity = placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            blockEntity.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
        }
        BlockPos above = helper.absolutePos(ORIGIN.above());
        helper.getLevel().setBlock(above, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(blockEntity);
        ItemStack offered = new ItemStack(Items.STONE, 4);

        // The first advertised slot past what the pile holds: the one insertion grows into, and so
        // the only one an obstruction can refuse.
        int headroom = StorageStackBE.SLOTS;
        ItemStack simulatedRemainder = FabricGameTestSupport.insertAt(storage, headroom, offered, true);
        ItemStack committedRemainder = FabricGameTestSupport.insertAt(storage, headroom, offered, false);

        checkEquals(4, simulatedRemainder.getCount(), "Simulated remainder");
        checkEquals(4, committedRemainder.getCount(), "Committed remainder");
        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        check(helper.getLevel().getBlockState(above).is(Blocks.STONE),
                "Obstruction changed");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void settleConsolidatesExactIdentityAndPacksDown(GameTestHelper helper) {
        StoragePileChecks.settleConsolidatesExactIdentityAndPacksDown(helper);
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE, timeoutTicks = 20)
    public void extractionSettlesOnScheduledBlockTick(GameTestHelper helper) {
        StoragePileChecks.extractionSettlesOnScheduledBlockTick(helper);
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void emptyTemporaryPileDisappearsButPermanentPileRemains(GameTestHelper helper) {
        StoragePileChecks.emptyTemporaryPileDisappearsButPermanentPileRemains(helper);
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void storageFromEveryBlockAddressesTheSamePile(GameTestHelper helper) {
        StorageStackBE lower = placeStorage(helper, ORIGIN);
        StorageStackBE upper = placeStorage(helper, ORIGIN.above());
        lower.getItems().insertItem(0, new ItemStack(Items.STONE, 12), false);

        SlottedStorage<ItemVariant> lowerStorage = FabricGameTestSupport.storage(lower);
        SlottedStorage<ItemVariant> upperStorage = FabricGameTestSupport.storage(upper);

        checkEquals(12, FabricGameTestSupport.stackAt(lowerStorage, 0).getCount(),
                "Lower storage count");
        checkEquals(12, FabricGameTestSupport.stackAt(upperStorage, 0).getCount(),
                "Upper storage count");
        checkEquals(lowerStorage.getSlotCount(), upperStorage.getSlotCount(),
                "Storage shape");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorReadsTheWholePileFromEveryBlock(GameTestHelper helper) {
        StoragePileChecks.comparatorReadsTheWholePileFromEveryBlock(helper);
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE, timeoutTicks = 20)
    public void ordinaryPlacementBelowPermanentPileRepairsDerivedState(GameTestHelper helper) {
        StoragePileChecks.ordinaryPlacementBelowPermanentPileRepairsDerivedState(helper);
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorReservesZeroForAnEmptyPile(GameTestHelper helper) {
        StoragePileChecks.comparatorReservesZeroForAnEmptyPile(helper);
    }
}
