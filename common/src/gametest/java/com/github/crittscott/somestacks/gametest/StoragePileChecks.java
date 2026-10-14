package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.StorageStackBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

    /**
     * To reproduce: create a permanent three-block Storage pile and use /data merge block to seed
     * sparse glowstone contents in its end blocks and diamonds in its middle block. Place
     * comparators beside both ends and break the middle before another inventory mutation.
     * Seed two full glowstone stacks below, seven diamonds in the middle, and two single glowstone
     * items above. Only seven diamonds drop. The lower pile packs to two slots (signal 2, light 6);
     * the upper pile merges to one slot (signal 1, light 3).
     * The fixture first drains placement ticks, then seeds sparse local slots and makes a pending
     * upper edit in the removal tick, so it specifically checks rescheduling at the new upper base.
     * That same-tick pending-publication assertion is internal; ordinary deposits pack from the
     * base and cannot create this sparse starting layout.
     */
    public static void middleRemovalSettlesAndPublishesBothSurvivingPiles(GameTestHelper helper) {
        StorageStackBE lower = placeStorage(helper, ORIGIN);
        StorageStackBE middle = placeStorage(helper, ORIGIN.above());
        StorageStackBE upper = placeStorage(helper, ORIGIN.above(2));
        StoragePile original = lower.pile();
        check(original != null, "Initial pile missing");
        original.setPermanent(true);
        BlockPos lowerComparator = GameTestScaffold.placeComparator(helper, ORIGIN);
        BlockPos upperComparator = GameTestScaffold.placeComparator(helper, ORIGIN.above(2));
        helper.runAfterDelay(6, () -> {
            check(!helper.getLevel().getBlockTicks().hasScheduledTick(upper.getBlockPos(),
                    upper.getBlockState().getBlock()), "Fixture retained an upper placement tick");
            StoragePile cached = lower.pile();
            check(cached != null && cached == upper.pile(), "Ends did not share the initial cache");
            checkEquals(3, cached.height(), "Initial height");
            lower.getItems().setStackInSlot(17, new ItemStack(Items.GLOWSTONE, 64));
            lower.getItems().setStackInSlot(25, new ItemStack(Items.GLOWSTONE, 64));
            middle.getItems().setStackInSlot(7, new ItemStack(Items.DIAMOND, 7));
            upper.getItems().setStackInSlot(12, new ItemStack(Items.GLOWSTONE));
            upper.getItems().setStackInSlot(24, new ItemStack(Items.GLOWSTONE));
            checkEquals(0, upper.getBlockState().getValue(
                    StorageStackBlock.LIGHT_LEVEL),
                    "Upper edit published before the split");
            check(helper.getLevel().setBlock(middle.getBlockPos(), Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL), "Middle removal failed");
            checkEquals(7, GameTestScaffold.droppedNear(helper, middle.getBlockPos(), Items.DIAMOND),
                    "Removed block drops");
            checkEquals(0L, GameTestScaffold.droppedInColumn(helper, lower.getBlockPos(), upper.getBlockPos())
                    .stream().filter(entity -> !entity.getItem().is(Items.DIAMOND)).count(),
                    "A surviving block dropped contents");
            helper.runAfterDelay(6, () -> {
                StoragePile below = lower.pile();
                StoragePile above = upper.pile();
                check(below != null && above != null && below != above, "Split piles not independent");
                checkEquals(lower.getBlockPos(), below.basePos(), "Lower base");
                checkEquals(upper.getBlockPos(), above.basePos(), "Upper base");
                checkEquals(1, below.height(), "Lower height");
                checkEquals(1, above.height(), "Upper height");
                checkEquals(64, below.getSlot(0).getCount(), "Lower packed first slot");
                checkEquals(64, below.getSlot(1).getCount(), "Lower packed second slot");
                checkEquals(2, above.getSlot(0).getCount(), "Upper merged and packed contents");
                checkEquals(2, GameTestScaffold.occupied(lower.getItems()), "Lower occupied slots");
                checkEquals(1, GameTestScaffold.occupied(upper.getItems()), "Upper occupied slots");
                checkEquals(128, GameTestScaffold.count(lower.getItems(), Items.GLOWSTONE), "Lower conservation");
                checkEquals(2, GameTestScaffold.count(upper.getItems(), Items.GLOWSTONE), "Upper conservation");
                checkEquals(7, GameTestScaffold.droppedNear(helper, middle.getBlockPos(), Items.DIAMOND),
                        "Dropped contents after settlement");
                checkEquals(6, lower.getBlockState().getValue(
                        StorageStackBlock.LIGHT_LEVEL), "Lower light");
                checkEquals(3, upper.getBlockState().getValue(
                        StorageStackBlock.LIGHT_LEVEL), "Upper light");
                checkEquals(2, GameTestScaffold.comparatorOutput(helper, lowerComparator), "Lower comparator");
                checkEquals(1, GameTestScaffold.comparatorOutput(helper, upperComparator), "Upper comparator");
                helper.succeed();
            });
        });
    }

    /**
     * The fixture seeds 60 stone in local slot 5 and offers eight more; slot 5 reaches 64 and slot
     * 0 receives four.
     *
     * <p>To check manually, V-deposit 60 stone into Storage, then eight more in Survival. All eight
     * leave the hand; one full stack and a four-item remainder occupy the pile. The exact
     * pre-settlement slot placement is an internal assertion.
     */
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

    /**
     * To reproduce: allow pile height of at least two, fill all 27 slots of a Storage block with
     * full dirt stacks, and leave replaceable, unobstructed space above. V-deposit one stone. A
     * second Storage block appears and stores it; the hand is emptied in Survival.
     */
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

    /**
     * To reproduce: build a two-block Storage pile, give its base one redstone-torch turn and its
     * top block three, fill both blocks with full dirt stacks, then V-deposit one stone. The new
     * third block shows the base's layout, not the top block's.
     */
    public static void grownBlockTakesTheBaseFacing(GameTestHelper helper) {
        StorageStackBE lower = placeStorage(helper, ORIGIN);
        StorageStackBE upper = placeStorage(helper, ORIGIN.above());
        lower.setRotation(1);
        upper.setRotation(3);
        for (StorageStackBE block : new StorageStackBE[]{lower, upper}) {
            for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
                block.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
            }
        }

        checkEquals(1, lower.deposit(new ItemStack(Items.STONE)), "Moved count");

        StorageStackBE grown = (StorageStackBE) helper.getLevel().getBlockEntity(
                upper.getBlockPos().above());
        check(grown != null, "Pile did not grow");
        checkEquals(1, grown.getRotation(), "Grown block rotation");
        checkEquals(3, upper.getRotation(), "Old top rotation changed");
        helper.succeed();
    }

    /**
     * Internal settlement fixture: directly seed stone stacks of 40 and 30 with {variant:1} and two
     * stacks of five with {variant:2} in separated slots, then settle. Slots 0-2 contain 64, 6, and
     * 10, preserving the two tag identities.
     *
     * <p>For a visible check, /give stone with those tags and deposit the same totals into Storage.
     * After settlement, matching tags merge into legal stacks, distinct tags stay separate, and
     * occupied cells pack toward the base. The test's initial gaps require direct fixture writes.
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

        StoragePile pile = storage.pile();
        check(pile != null, "Pile did not resolve");
        pile.settle();

        checkEquals(64, pile.getSlot(0).getCount(), "Consolidated full stack");
        checkEquals(6, pile.getSlot(1).getCount(), "Consolidated partial stack");
        checkEquals(10, pile.getSlot(2).getCount(), "Distinct tagged stack");
        check(pile.getSlot(3).isEmpty(), "Packed contents left a gap");
        checkEquals(firstTag, pile.getSlot(0).getTag(), "First tag identity");
        checkEquals(secondTag, pile.getSlot(2).getTag(), "Distinct tag identity");
        helper.succeed();
    }

    /**
     * To reproduce: store one full stone stack and one dirt item in Storage. Plain-right-click the
     * stone cell with an empty hand. The stone enters the hand, and after the scheduled block
     * update the remaining dirt packs into the base slot. The test directly seeds the initial order
     * and waits two ticks.
     */
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

    /**
     * To reproduce: place two Storage piles with one item each. Select Toggle Permanent, hold V,
     * and empty-hand right-click one pile. Release V and extract the last item from each. After
     * settlement, the temporary pile disappears while the permanent pile remains empty. The
     * automated fixture starts with empty blocks and invokes settlement directly.
     */
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

    /**
     * The fixture puts a full stone stack in the lower block of a two-block pile and checks that
     * both blocks return the pile signal.
     *
     * <p>To reproduce: create a permanent two-block Storage pile by filling until it grows, then
     * toggling permanence. Extract until only one full stack remains. Put outward-facing
     * comparators beside both blocks on solid supports; both read the same whole-pile fill.
     */
    public static void comparatorReadsTheWholePileFromEveryBlock(GameTestHelper helper) {
        StorageStackBE lower = placeStorage(helper, ORIGIN);
        StorageStackBE upper = placeStorage(helper, ORIGIN.above());
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
     * The fixture reads an empty pile, adds one stone, then fills all 27 slots with full stone
     * stacks: signals are 0, 1, and 15.
     *
     * <p>To reproduce: make a permanent one-block Storage pile with an outward-facing comparator.
     * Empty it, deposit one stone, then fill it without allowing growth. Read 0, 1, and 15 after
     * updates.
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
