package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

/**
 * Storage pile behavior in a live level that does not touch loader-native automation directly:
 * deposits filling partial stacks before empty ones, growth when a pile fills, and settling
 * consolidating, packing, and sorting. Capability/Transfer-API-facing tests stay in each loader's
 * own {@code StoragePileGameTests}.
 */
public final class StoragePileChecks {
    private StoragePileChecks() {}

    public static void depositFillsCompatiblePartialBeforeEmptySlot(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
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

    public static void fullPileGrowsByOneBlock(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
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
        checkEquals(CustomData.of(firstTag), pile.getSlot(0).get(DataComponents.CUSTOM_DATA),
                "First component identity");
        checkEquals(CustomData.of(secondTag), pile.getSlot(2).get(DataComponents.CUSTOM_DATA),
                "Distinct component identity");
        helper.succeed();
    }

    public static void extractionSettlesOnScheduledBlockTick(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
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

    public static void emptyTemporaryPileDisappearsButPermanentPileRemains(GameTestHelper helper) {
        StorageStackBE temporary = placeStorage(helper, ORIGIN);
        StoragePile temporaryPile = temporary.pile();
        check(temporaryPile != null, "Temporary pile did not resolve");
        temporaryPile.settle();
        check(helper.getLevel().isEmptyBlock(helper.absolutePos(ORIGIN)),
                "Empty temporary pile remained");

        BlockPos permanentPos = ORIGIN.east(3);
        StorageStackBE permanent = placeStorage(helper, permanentPos);
        StoragePile permanentPile = permanent.pile();
        check(permanentPile != null, "Permanent pile did not resolve");
        permanentPile.setPermanent(true);
        permanentPile.settle();
        check(helper.getLevel().getBlockEntity(helper.absolutePos(permanentPos))
                        instanceof StorageStackBE,
                "Empty permanent pile disappeared");
        helper.succeed();
    }

    public static void comparatorReadsTheWholePileFromEveryBlock(GameTestHelper helper) {
        StorageStackBE lower = placeStorage(helper, ORIGIN);
        StorageStackBE upper = placeStorage(helper, ORIGIN.above());
        lower.getItems().insertItem(0, new ItemStack(Items.STONE, 64), false);
        StoragePile pile = lower.pile();
        check(pile != null, "Pile did not resolve");

        int expected = pile.comparatorSignal();
        int lowerSignal = lower.getBlockState().getAnalogOutputSignal(
                helper.getLevel(), lower.getBlockPos());
        int upperSignal = upper.getBlockState().getAnalogOutputSignal(
                helper.getLevel(), upper.getBlockPos());

        checkEquals(expected, lowerSignal, "Lower comparator signal");
        checkEquals(expected, upperSignal, "Upper comparator signal");
        helper.succeed();
    }

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
                            CommonRegistry.STORAGE_STACK_BLOCK.get().defaultBlockState(),
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
    public static void comparatorReservesZeroForAnEmptyPile(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
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
