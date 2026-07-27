package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import static com.github.crittscott.somestacks.gametest.GameTestSupport.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.check;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.checkEquals;

@GameTestHolder(SomeStacks.MODID)
@PrefixGameTestTemplate(false)
public final class StoragePileGameTests {
    private StoragePileGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void depositFillsCompatiblePartialBeforeEmptySlot(GameTestHelper helper) {
        StorageStackBE storage = GameTestSupport.placeStorage(helper, ORIGIN);
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

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void fullPileGrowsByOneBlock(GameTestHelper helper) {
        StorageStackBE storage = GameTestSupport.placeStorage(helper, ORIGIN);
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

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void obstructionPreventsGrowthAndSimulationReportsNoRoom(GameTestHelper helper) {
        StorageStackBE storage = GameTestSupport.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
        }
        BlockPos above = helper.absolutePos(ORIGIN.above());
        helper.getLevel().setBlock(above, Blocks.STONE.defaultBlockState(), 3);
        IItemHandler capability = GameTestSupport.capability(storage);
        ItemStack offered = new ItemStack(Items.STONE, 4);

        ItemStack simulatedRemainder = capability.insertItem(0, offered, true);
        ItemStack committedRemainder = capability.insertItem(0, offered, false);

        checkEquals(4, simulatedRemainder.getCount(), "Simulated remainder");
        checkEquals(4, committedRemainder.getCount(), "Committed remainder");
        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        check(helper.getLevel().getBlockState(above).is(Blocks.STONE),
                "Obstruction changed");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void settleConsolidatesExactIdentityAndPacksDown(GameTestHelper helper) {
        StorageStackBE storage = GameTestSupport.placeStorage(helper, ORIGIN);
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = 20)
    public static void extractionSettlesOnScheduledBlockTick(GameTestHelper helper) {
        StorageStackBE storage = GameTestSupport.placeStorage(helper, ORIGIN);
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

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void emptyTemporaryPileDisappearsButPermanentPileRemains(GameTestHelper helper) {
        StorageStackBE temporary = GameTestSupport.placeStorage(helper, ORIGIN);
        StoragePile temporaryPile = temporary.pile();
        check(temporaryPile != null, "Temporary pile did not resolve");
        temporaryPile.settle();
        check(helper.getLevel().isEmptyBlock(helper.absolutePos(ORIGIN)),
                "Empty temporary pile remained");

        BlockPos permanentPos = ORIGIN.east(3);
        StorageStackBE permanent = GameTestSupport.placeStorage(helper, permanentPos);
        StoragePile permanentPile = permanent.pile();
        check(permanentPile != null, "Permanent pile did not resolve");
        permanentPile.setPermanent(true);
        permanentPile.settle();
        check(helper.getLevel().getBlockEntity(helper.absolutePos(permanentPos))
                        instanceof StorageStackBE,
                "Empty permanent pile disappeared");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void capabilityFromEveryBlockAddressesTheSamePile(GameTestHelper helper) {
        StorageStackBE lower = GameTestSupport.placeStorage(helper, ORIGIN);
        StorageStackBE upper = GameTestSupport.placeStorage(helper, ORIGIN.above());
        lower.getItems().insertItem(0, new ItemStack(Items.STONE, 12), false);

        IItemHandler lowerCapability = GameTestSupport.capability(lower);
        IItemHandler upperCapability = GameTestSupport.capability(upper);

        checkEquals(12, lowerCapability.getStackInSlot(0).getCount(),
                "Lower capability count");
        checkEquals(12, upperCapability.getStackInSlot(0).getCount(),
                "Upper capability count");
        checkEquals(lowerCapability.getSlots(), upperCapability.getSlots(),
                "Capability shape");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorReadsTheWholePileFromEveryBlock(GameTestHelper helper) {
        StorageStackBE lower = GameTestSupport.placeStorage(helper, ORIGIN);
        StorageStackBE upper = GameTestSupport.placeStorage(helper, ORIGIN.above());
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
}
