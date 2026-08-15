package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.FabricRegistry;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;

import static com.github.crittscott.somestacks.gametest.FabricGameTestSupport.ORIGIN;
import static com.github.crittscott.somestacks.gametest.FabricGameTestSupport.check;
import static com.github.crittscott.somestacks.gametest.FabricGameTestSupport.checkEquals;

/**
 * Storage pile behavior in a live level: deposits filling partial stacks before empty ones, growth
 * when a pile fills, storage insertion addressing the specified slot, obstruction refusing growth,
 * and settling consolidating, packing, and sorting.
 */
public final class StoragePileGameTests implements FabricGameTest {
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void depositFillsCompatiblePartialBeforeEmptySlot(GameTestHelper helper) {
        StorageStackBE storage = FabricGameTestSupport.placeStorage(helper, ORIGIN);
        storage.getItems().insertItem(5, new ItemStack(Items.STONE, 60), false);
        ItemStack offered = new ItemStack(Items.STONE, 8);

        int moved = storage.deposit(offered);

        checkEquals(8, moved, "Moved count");
        checkEquals(0, offered.getCount(), "Offered remainder");
        checkEquals(64, storage.getItems().getStackInSlot(5).getCount(),
                "Compatible partial");
        checkEquals(4, storage.getItems().getStackInSlot(0).getCount(),
                "First empty slot");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void fullPileGrowsByOneBlock(GameTestHelper helper) {
        StorageStackBE storage = FabricGameTestSupport.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
        }
        ItemStack offered = new ItemStack(Items.STONE, 1);

        int moved = storage.deposit(offered);
        StoragePile pile = storage.pile();

        checkEquals(1, moved, "Moved count");
        checkEquals(0, offered.getCount(), "Offered remainder");
        check(pile != null, "Pile did not resolve");
        checkEquals(2, pile.height(), "Pile height");
        checkEquals(Items.STONE, pile.getSlot(StorageStackBE.SLOTS).getItem(),
                "First slot in grown block");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void capabilityInsertionAnswersForTheSlotItIsGiven(GameTestHelper helper) {
        StorageStackBE storage = FabricGameTestSupport.placeStorage(helper, ORIGIN);
        SlottedStorage<ItemVariant> capability = FabricGameTestSupport.capability(storage);
        ItemStack offered = new ItemStack(Items.STONE, 8);

        // A pile is a bag, so the slot named takes what a slot takes — here all eight, in slot 20
        // rather than at the base a deposit would have filled.
        checkEquals(0, FabricGameTestSupport.insertAt(capability, 20, offered, true).getCount(),
                "Simulated insertion into an empty slot");
        checkEquals(0, FabricGameTestSupport.insertAt(capability, 20, offered, false).getCount(),
                "Committed insertion into an empty slot");
        checkEquals(8, offered.getCount(), "Capability mutated caller input");
        checkEquals(8, storage.getItems().getStackInSlot(20).getCount(),
                "The slot named should hold the items");

        // An incompatible slot takes nothing rather than redirecting to available space elsewhere.
        ItemStack other = new ItemStack(Items.DIRT, 8);
        checkEquals(8, FabricGameTestSupport.insertAt(capability, 20, other, true).getCount(),
                "Simulated insertion into an occupied incompatible slot");

        // The settle the insertion scheduled is what puts the items at the base.
        StoragePile pile = storage.pile();
        check(pile != null, "Pile did not resolve");
        pile.settle();
        checkEquals(8, storage.getItems().getStackInSlot(0).getCount(),
                "Settle should pack the insertion down to the base");
        checkEquals(0, FabricGameTestSupport.count(storage.getItems(), Items.DIRT),
                "Refused insertion stored something");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void obstructionPreventsGrowthAndSimulationReportsNoRoom(GameTestHelper helper) {
        StorageStackBE storage = FabricGameTestSupport.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
        }
        BlockPos above = helper.absolutePos(ORIGIN.above());
        helper.getLevel().setBlock(above, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        SlottedStorage<ItemVariant> capability = FabricGameTestSupport.capability(storage);
        ItemStack offered = new ItemStack(Items.STONE, 4);

        // The first advertised slot past what the pile holds: the one insertion grows into, and so
        // the only one an obstruction can refuse.
        int headroom = StorageStackBE.SLOTS;
        ItemStack simulatedRemainder = FabricGameTestSupport.insertAt(capability, headroom, offered, true);
        ItemStack committedRemainder = FabricGameTestSupport.insertAt(capability, headroom, offered, false);

        checkEquals(4, simulatedRemainder.getCount(), "Simulated remainder");
        checkEquals(4, committedRemainder.getCount(), "Committed remainder");
        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        check(helper.getLevel().getBlockState(above).is(Blocks.STONE),
                "Obstruction changed");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void settleConsolidatesExactIdentityAndPacksDown(GameTestHelper helper) {
        StorageStackBE storage = FabricGameTestSupport.placeStorage(helper, ORIGIN);
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
        storage.getItems().insertItem(10, first, false);
        storage.getItems().insertItem(20, compatible, false);
        storage.getItems().insertItem(25, distinct, false);

        StoragePile pile = storage.pile();
        check(pile != null, "Pile did not resolve");
        pile.settle();

        checkEquals(64, pile.getSlot(0).getCount(), "Consolidated full stack");
        checkEquals(6, pile.getSlot(1).getCount(), "Consolidated partial stack");
        checkEquals(5, pile.getSlot(2).getCount(), "Distinct tagged stack");
        check(pile.getSlot(3).isEmpty(), "Packed contents left a gap");
        checkEquals(firstTag, pile.getSlot(0).getTag(), "First tag identity");
        checkEquals(secondTag, pile.getSlot(2).getTag(), "Distinct tag identity");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE, timeoutTicks = 20)
    public void extractionSettlesOnScheduledBlockTick(GameTestHelper helper) {
        StorageStackBE storage = FabricGameTestSupport.placeStorage(helper, ORIGIN);
        storage.getItems().insertItem(0, new ItemStack(Items.STONE, 64), false);
        storage.getItems().insertItem(1, new ItemStack(Items.DIRT, 1), false);

        ItemStack extracted = storage.extractAt(0, 64, ItemStack.EMPTY);
        checkEquals(64, extracted.getCount(), "Extracted count");

        helper.runAfterDelay(2, () -> {
            StoragePile pile = StoragePile.at(
                    helper.getLevel(), helper.absolutePos(ORIGIN));
            check(pile != null, "Pile disappeared unexpectedly");
            checkEquals(Items.DIRT, pile.getSlot(0).getItem(),
                    "Remaining stack did not pack down");
            check(pile.getSlot(1).isEmpty(), "Gap survived settle");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void emptyTemporaryPileDisappearsButPermanentPileRemains(GameTestHelper helper) {
        StorageStackBE temporary = FabricGameTestSupport.placeStorage(helper, ORIGIN);
        StoragePile temporaryPile = temporary.pile();
        check(temporaryPile != null, "Temporary pile did not resolve");
        temporaryPile.settle();
        check(helper.getLevel().isEmptyBlock(helper.absolutePos(ORIGIN)),
                "Empty temporary pile remained");

        BlockPos permanentPos = ORIGIN.east(3);
        StorageStackBE permanent = FabricGameTestSupport.placeStorage(helper, permanentPos);
        StoragePile permanentPile = permanent.pile();
        check(permanentPile != null, "Permanent pile did not resolve");
        permanentPile.setPermanent(true);
        permanentPile.settle();
        check(helper.getLevel().getBlockEntity(helper.absolutePos(permanentPos))
                        instanceof StorageStackBE,
                "Empty permanent pile disappeared");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void capabilityFromEveryBlockAddressesTheSamePile(GameTestHelper helper) {
        StorageStackBE lower = FabricGameTestSupport.placeStorage(helper, ORIGIN);
        StorageStackBE upper = FabricGameTestSupport.placeStorage(helper, ORIGIN.above());
        lower.getItems().insertItem(0, new ItemStack(Items.STONE, 12), false);

        SlottedStorage<ItemVariant> lowerCapability = FabricGameTestSupport.capability(lower);
        SlottedStorage<ItemVariant> upperCapability = FabricGameTestSupport.capability(upper);

        checkEquals(12, FabricGameTestSupport.stackAt(lowerCapability, 0).getCount(),
                "Lower capability count");
        checkEquals(12, FabricGameTestSupport.stackAt(upperCapability, 0).getCount(),
                "Upper capability count");
        checkEquals(lowerCapability.getSlotCount(), upperCapability.getSlotCount(),
                "Capability shape");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorReadsTheWholePileFromEveryBlock(GameTestHelper helper) {
        StorageStackBE lower = FabricGameTestSupport.placeStorage(helper, ORIGIN);
        StorageStackBE upper = FabricGameTestSupport.placeStorage(helper, ORIGIN.above());
        lower.getItems().insertItem(0, new ItemStack(Items.STONE, 64), false);
        StoragePile pile = lower.pile();
        check(pile != null, "Pile did not resolve");

        int expected = pile.comparatorSignal();
        int lowerSignal = lower.getBlockState().getBlock().getAnalogOutputSignal(
                lower.getBlockState(), helper.getLevel(), lower.getBlockPos());
        int upperSignal = upper.getBlockState().getBlock().getAnalogOutputSignal(
                upper.getBlockState(), helper.getLevel(), upper.getBlockPos());

        checkEquals(expected, lowerSignal, "Lower comparator signal");
        checkEquals(expected, upperSignal, "Upper comparator signal");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE, timeoutTicks = 20)
    public void ordinaryPlacementBelowPermanentPileRepairsDerivedState(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos lowerPos = helper.absolutePos(ORIGIN);
        BlockPos upperPos = helper.absolutePos(ORIGIN.above());
        BlockPos comparatorPos = upperPos.east();

        StorageStackBE upper = FabricGameTestSupport.placeStorage(helper, ORIGIN.above());
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            upper.getItems().insertItem(slot, new ItemStack(Items.STONE, 64), false);
        }
        StoragePile originalPile = upper.pile();
        check(originalPile != null, "Original pile did not resolve");
        originalPile.setPermanent(true);

        check(level.setBlock(
                        comparatorPos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL),
                "Could not place comparator support");
        check(level.setBlock(
                        comparatorPos,
                        Blocks.COMPARATOR.defaultBlockState()
                                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST),
                        Block.UPDATE_ALL),
                "Could not place comparator");
        originalPile.settle();

        helper.runAfterDelay(3, () -> {
            check(level.getBlockEntity(comparatorPos) instanceof ComparatorBlockEntity,
                    "Comparator block entity was missing");
            ComparatorBlockEntity comparator =
                    (ComparatorBlockEntity) level.getBlockEntity(comparatorPos);
            checkEquals(15, comparator.getOutputSignal(), "Initial comparator output");

            check(level.setBlock(
                            lowerPos,
                            FabricRegistry.STORAGE_STACK_BLOCK.defaultBlockState(),
                            Block.UPDATE_ALL),
                    "Could not place Storage Stack below pile");

            helper.runAfterDelay(4, () -> {
                check(level.getBlockEntity(lowerPos) instanceof StorageStackBE,
                        "Placed Storage Stack disappeared");
                StorageStackBE lower = (StorageStackBE) level.getBlockEntity(lowerPos);
                StoragePile joinedPile = lower.pile();
                check(joinedPile != null, "Joined pile did not resolve");
                checkEquals(2, joinedPile.height(), "Joined pile height");
                checkEquals(8, joinedPile.comparatorSignal(), "Joined pile signal");
                checkEquals(8, comparator.getOutputSignal(), "Published comparator output");
                check(lower.isPermanent(), "New pile base did not inherit permanence");
                check(upper.isPermanent(), "Permanence was not propagated through pile");
                checkEquals(Items.STONE, lower.getItems().getStackInSlot(0).getItem(),
                        "Pile did not settle into its new base");
                check(upper.getItems().getStackInSlot(0).isEmpty(),
                        "Old base contents did not pack down");
                helper.succeed();
            });
        });
    }

    /**
     * The comparator range reserves 0 for an empty pile, as a vanilla container's does. A pile is
     * large enough that a proportional conversion would round hundreds of items down to 0, so the
     * reserved value is what makes an emptiness circuit read a pile correctly.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorReservesZeroForAnEmptyPile(GameTestHelper helper) {
        StorageStackBE storage = FabricGameTestSupport.placeStorage(helper, ORIGIN);
        StoragePile pile = storage.pile();
        check(pile != null, "Pile did not resolve");

        checkEquals(0, pile.comparatorSignal(), "Empty pile signal");

        storage.getItems().insertItem(0, new ItemStack(Items.STONE, 1), false);
        checkEquals(1, pile.comparatorSignal(), "Signal for a single item in a whole pile");

        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.STONE, 64), false);
        }
        checkEquals(15, pile.comparatorSignal(), "Full pile signal");
        helper.succeed();
    }
}
