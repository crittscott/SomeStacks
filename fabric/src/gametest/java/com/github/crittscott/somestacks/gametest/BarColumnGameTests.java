package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.List;

import static com.github.crittscott.somestacks.gametest.FabricGameTestSupport.ORIGIN;
import static com.github.crittscott.somestacks.gametest.FabricGameTestSupport.check;
import static com.github.crittscott.somestacks.gametest.FabricGameTestSupport.checkEquals;

/**
 * Bar support and collapse in a live level: item validity, grounding at the bottom of a column,
 * footprint overlap deciding support across the alternating layer orientations, and the difference
 * between player extraction, which drops what it unsupports, and automated extraction, which
 * backfills from the top instead.
 */
public final class BarColumnGameTests implements FabricGameTest {
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void validityAndBottomGroundingAreEnforced(GameTestHelper helper) {
        BarStackBE bars = FabricGameTestSupport.placeBar(helper, ORIGIN);
        Item barItem = FabricGameTestSupport.firstBarItem();
        ItemStack accepted = new ItemStack(barItem, 2);

        check(bars.depositAt(0, accepted), "Valid bottom bar was rejected");
        checkEquals(1, accepted.getCount(), "Accepted remainder");

        ItemStack invalid = new ItemStack(Items.STICK);
        check(!bars.depositAt(1, invalid), "Invalid Bar item was accepted");
        checkEquals(1, invalid.getCount(), "Rejected stack changed");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void upperBarRequiresOverlappingSupport(GameTestHelper helper) {
        BarStackBE bars = FabricGameTestSupport.placeBar(helper, ORIGIN);
        Item barItem = FabricGameTestSupport.firstBarItem();
        int upper = firstSupportedBy(0, 8, 16);

        check(!bars.depositAt(upper, new ItemStack(barItem)),
                "Floating upper bar was accepted");
        check(bars.depositAt(0, new ItemStack(barItem)), "Bottom bar deposit failed");
        check(bars.depositAt(upper, new ItemStack(barItem)),
                "Supported upper bar was rejected");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerExtractionDropsUnsupportedBars(GameTestHelper helper) {
        BarStackBE bars = FabricGameTestSupport.placeBar(helper, ORIGIN);
        Item barItem = FabricGameTestSupport.firstBarItem();
        int upper = firstSupportedBy(0, 8, 16);
        bars.depositAt(0, new ItemStack(barItem));
        bars.depositAt(upper, new ItemStack(barItem));

        ItemStack extracted = bars.extractAt(0);

        checkEquals(barItem, extracted.getItem(), "Extracted item");
        checkEquals(1, extracted.getCount(), "Extracted count");
        check(helper.getLevel().getBlockEntity(bars.getBlockPos()) == null,
                "Emptied Bar block remained");
        checkEquals(1, FabricGameTestSupport.droppedNear(helper, bars.getBlockPos(), barItem),
                "Unsupported bar drop count");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void supportedNeighborSurvivesPlayerExtraction(GameTestHelper helper) {
        BarStackBE bars = FabricGameTestSupport.placeBar(helper, ORIGIN);
        Item barItem = FabricGameTestSupport.firstBarItem();
        int removedSupport = 0;
        int survivingSupport = 1;
        int upper = firstSupportedBy(survivingSupport, 8, 16);
        bars.depositAt(removedSupport, new ItemStack(barItem));
        bars.depositAt(survivingSupport, new ItemStack(barItem));
        bars.depositAt(upper, new ItemStack(barItem));

        bars.extractAt(removedSupport);

        checkEquals(barItem, bars.getItems().getStackInSlot(upper).getItem(),
                "Still-supported upper bar was removed");
        checkEquals(0, FabricGameTestSupport.droppedNear(helper, bars.getBlockPos(), barItem),
                "A supported bar was dropped");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void differentlyOrientedSeamUsesFootprintOverlap(GameTestHelper helper) {
        BarStackBE lower = FabricGameTestSupport.placeBar(helper, ORIGIN);
        BarStackBE upper = FabricGameTestSupport.placeBar(helper, ORIGIN.above());
        Item barItem = FabricGameTestSupport.firstBarItem();
        int lowerTop = 56;
        int upperBottom = firstSeamSupportedBy(0);
        lower.getItems().insertItem(lowerTop, new ItemStack(barItem), false);

        check(upper.depositAt(upperBottom, new ItemStack(barItem)),
                "Cross-block footprint support was rejected");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void automationBackfillsWithoutDropping(GameTestHelper helper) {
        BarStackBE bars = FabricGameTestSupport.placeBar(helper, ORIGIN);
        Item barItem = FabricGameTestSupport.firstBarItem();
        SlottedStorage<ItemVariant> capability = FabricGameTestSupport.capability(bars);
        ItemStack offered = new ItemStack(barItem, 10);

        // A walk of the advertised positions fills the block from the bottom, because each bar
        // placed supports the layer above it before the walk reaches it.
        ItemStack remainder = FabricGameTestSupport.insertWalkingSlots(capability, offered, false);
        check(remainder.isEmpty(), "A walk of the positions left a remainder");
        checkEquals(10, FabricGameTestSupport.count(bars.getItems(), barItem),
                "Inserted bar count");

        ItemStack extracted = FabricGameTestSupport.extractAt(capability, 0, 64, false);

        checkEquals(1, extracted.getCount(), "Automated extracted count");
        checkEquals(9, FabricGameTestSupport.count(bars.getItems(), barItem),
                "Count after automated extraction");
        checkEquals(barItem, bars.getItems().getStackInSlot(0).getItem(),
                "Hole was not backfilled");
        checkEquals(0, FabricGameTestSupport.droppedNear(helper, bars.getBlockPos(), barItem),
                "Automation dropped a bar");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void capabilityInsertionAnswersForThePositionItIsGiven(GameTestHelper helper) {
        BarStackBE bars = FabricGameTestSupport.placeBar(helper, ORIGIN);
        Item barItem = FabricGameTestSupport.firstBarItem();
        SlottedStorage<ItemVariant> capability = FabricGameTestSupport.capability(bars);
        ItemStack offered = new ItemStack(barItem, 4);

        // Position 8 is the first of layer 1, with an empty layer 0 beneath it, so it is refused
        // rather than redirected to a position that would take the bar.
        checkEquals(4, FabricGameTestSupport.insertAt(capability, 8, offered, true).getCount(),
                "Simulated insertion into an unsupported position");
        checkEquals(4, FabricGameTestSupport.insertAt(capability, 8, offered, false).getCount(),
                "Committed insertion into an unsupported position");
        checkEquals(0, FabricGameTestSupport.occupied(bars.getItems()),
                "An unsupported position stored something");

        // Position 0 is in the bottom layer of a column standing on the world, so it is grounded
        // outright and takes exactly the one bar a position holds.
        checkEquals(3, FabricGameTestSupport.insertAt(capability, 0, offered, true).getCount(),
                "Simulated insertion into a grounded position");
        checkEquals(3, FabricGameTestSupport.insertAt(capability, 0, offered, false).getCount(),
                "Committed insertion into a grounded position");
        checkEquals(4, offered.getCount(), "Capability mutated caller input");
        checkEquals(1, FabricGameTestSupport.occupied(bars.getItems()),
                "One call should store one bar");
        checkEquals(1, FabricGameTestSupport.slotLimit(capability, 0),
                "A position's slot limit should be what one call takes");
        helper.succeed();
    }

    /**
     * Validity gates insertion only, so a bar stored before an {@code ss ingot} edit or a data pack
     * reload narrowed the rule has to survive the backfill an automated extraction moves it through.
     * A stick stands in for such a bar: a Bar Stack refuses one from a deposit, but may be holding
     * contents the current ingot set no longer covers.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void backfillKeepsABarItWouldNoLongerAccept(GameTestHelper helper) {
        BarStackBE bars = FabricGameTestSupport.placeBar(helper, ORIGIN);
        Item barItem = FabricGameTestSupport.firstBarItem();
        BlockPos pos = bars.getBlockPos();
        check(!BarStackBE.isValidBarItem(new ItemStack(Items.STICK)),
                "Test needs an item a Bar Stack refuses");
        bars.getItems().insertItem(0, new ItemStack(barItem), false);
        FabricGameTestSupport.seedSlot(bars.getItems(), 1, new ItemStack(Items.STICK));
        SlottedStorage<ItemVariant> capability = FabricGameTestSupport.capability(bars);

        ItemStack extracted = FabricGameTestSupport.extractAt(capability, 0, 64, false);

        checkEquals(barItem, extracted.getItem(), "Automated extracted item");
        checkEquals(1, extracted.getCount(), "Automated extraction took more than one bar");
        int accounted = FabricGameTestSupport.heldAt(helper, pos, Items.STICK)
                + FabricGameTestSupport.droppedNear(helper, pos, Items.STICK);
        checkEquals(1, accounted, "Refused bar after being backfilled into the hole");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void capabilitySimulationDoesNotMutateExtraction(GameTestHelper helper) {
        BarStackBE bars = FabricGameTestSupport.placeBar(helper, ORIGIN);
        Item barItem = FabricGameTestSupport.firstBarItem();
        bars.depositAt(0, new ItemStack(barItem));
        SlottedStorage<ItemVariant> capability = FabricGameTestSupport.capability(bars);

        ItemStack simulated = FabricGameTestSupport.extractAt(capability, 0, 64, true);

        checkEquals(barItem, simulated.getItem(), "Simulated item");
        checkEquals(1, simulated.getCount(), "Simulated count");
        checkEquals(barItem, bars.getItems().getStackInSlot(0).getItem(),
                "Simulation mutated contents");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void breakingLowerBlockCollapsesDependentUpperBlock(GameTestHelper helper) {
        BarStackBE lower = FabricGameTestSupport.placeBar(helper, ORIGIN);
        BarStackBE upper = FabricGameTestSupport.placeBar(helper, ORIGIN.above());
        Item barItem = FabricGameTestSupport.firstBarItem();
        int upperBottom = firstSeamSupportedBy(0);
        lower.getItems().insertItem(56, new ItemStack(barItem), false);
        upper.depositAt(upperBottom, new ItemStack(barItem));

        helper.getLevel().setBlock(lower.getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        check(helper.getLevel().getBlockEntity(upper.getBlockPos()) == null,
                "Dependent upper Bar block remained");
        checkEquals(2, FabricGameTestSupport.droppedNear(helper, lower.getBlockPos(), barItem),
                "Break/collapse drop count");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void breakingFullColumnConsolidatesDrops(GameTestHelper helper) {
        BarStackBE lower = FabricGameTestSupport.placeBar(helper, ORIGIN);
        BarStackBE upper = FabricGameTestSupport.placeBar(helper, ORIGIN.above());
        Item barItem = FabricGameTestSupport.firstBarItem();
        BlockPos lowerPos = lower.getBlockPos();
        BlockPos upperPos = upper.getBlockPos();

        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            FabricGameTestSupport.seedSlot(lower.getItems(), slot, new ItemStack(barItem));
            FabricGameTestSupport.seedSlot(upper.getItems(), slot, new ItemStack(barItem));
        }

        helper.getLevel().setBlock(lowerPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        check(helper.getLevel().getBlockEntity(lowerPos) == null,
                "Broken lower Bar block remained");
        check(helper.getLevel().getBlockEntity(upperPos) == null,
                "Collapsed upper Bar block remained");

        List<ItemEntity> drops = FabricGameTestSupport.droppedInColumn(helper, lowerPos, upperPos).stream()
                .filter(entity -> entity.getItem().is(barItem))
                .toList();
        checkEquals(2, drops.size(), "Consolidated item-entity count");
        checkEquals(128, drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum(),
                "Consolidated item count");
        check(drops.stream().allMatch(entity -> entity.getItem().getCount() == 64),
                "A full block's bars were not packed into one full stack");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerCascadeConsolidatesAcrossBlocks(GameTestHelper helper) {
        BarStackBE lower = FabricGameTestSupport.placeBar(helper, ORIGIN);
        BarStackBE upper = FabricGameTestSupport.placeBar(helper, ORIGIN.above());
        Item barItem = FabricGameTestSupport.firstBarItem();
        BlockPos lowerPos = lower.getBlockPos();
        BlockPos upperPos = upper.getBlockPos();

        int slot = 0;
        FabricGameTestSupport.seedSlot(lower.getItems(), slot, new ItemStack(barItem));
        for (int layer = 1; layer < 8; layer++) {
            slot = firstSupportedBy(slot, layer * 8, (layer + 1) * 8);
            FabricGameTestSupport.seedSlot(lower.getItems(), slot, new ItemStack(barItem));
        }

        slot = firstSeamSupportedBy(slot - 56);
        FabricGameTestSupport.seedSlot(upper.getItems(), slot, new ItemStack(barItem));
        for (int layer = 1; layer < 8; layer++) {
            slot = firstSupportedBy(slot, layer * 8, (layer + 1) * 8);
            FabricGameTestSupport.seedSlot(upper.getItems(), slot, new ItemStack(barItem));
        }

        ItemStack extracted = lower.extractAt(0);

        checkEquals(barItem, extracted.getItem(), "Extracted support item");
        checkEquals(1, extracted.getCount(), "Extracted support count");
        check(helper.getLevel().getBlockEntity(lowerPos) == null,
                "Emptied lower Bar block remained");
        check(helper.getLevel().getBlockEntity(upperPos) == null,
                "Emptied upper Bar block remained");

        List<ItemEntity> drops = FabricGameTestSupport.droppedInColumn(helper, lowerPos, upperPos).stream()
                .filter(entity -> entity.getItem().is(barItem))
                .toList();
        checkEquals(2, drops.size(), "Cross-block cascade item-entity count");
        checkEquals(15, drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum(),
                "Cross-block cascade item count");
        int[] counts = drops.stream().mapToInt(entity -> entity.getItem().getCount()).sorted().toArray();
        check(Arrays.equals(new int[]{7, 8}, counts),
                "Drops were not packed at their originating blocks: " + Arrays.toString(counts));
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void collapseDoesNotMergeDifferentTags(GameTestHelper helper) {
        BarStackBE bars = FabricGameTestSupport.placeBar(helper, ORIGIN);
        Item barItem = FabricGameTestSupport.firstBarItem();
        BlockPos pos = bars.getBlockPos();
        int[] upper = supportedBy(0, 8, 16);
        checkEquals(2, upper.length, "Test support fan-out");

        ItemStack first = new ItemStack(barItem);
        first.getOrCreateTag().putInt("somestacks_test_variant", 1);
        ItemStack second = new ItemStack(barItem);
        second.getOrCreateTag().putInt("somestacks_test_variant", 2);
        FabricGameTestSupport.seedSlot(bars.getItems(), 0, new ItemStack(barItem));
        FabricGameTestSupport.seedSlot(bars.getItems(), upper[0], first);
        FabricGameTestSupport.seedSlot(bars.getItems(), upper[1], second);

        bars.extractAt(0);

        List<ItemEntity> drops = FabricGameTestSupport.droppedInColumn(helper, pos, pos).stream()
                .filter(entity -> entity.getItem().is(barItem))
                .toList();
        checkEquals(2, drops.size(), "Differently tagged item-entity count");
        checkEquals(2, drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum(),
                "Differently tagged item count");
        int[] variants = drops.stream().mapToInt(BarColumnGameTests::testVariant).sorted().toArray();
        check(Arrays.equals(new int[]{1, 2}, variants),
                "Different tags were merged or changed: " + Arrays.toString(variants));
        helper.succeed();
    }

    /**
     * The comparator range reserves 0 for an empty column, as a vanilla container's does, and a
     * position holds one bar, so full means every position of every block occupied.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorReservesZeroForAnEmptyColumn(GameTestHelper helper) {
        BarStackBE bars = FabricGameTestSupport.placeBar(helper, ORIGIN);
        Item barItem = FabricGameTestSupport.firstBarItem();

        checkEquals(0, FabricGameTestSupport.signalAt(helper, ORIGIN), "Empty column signal");

        bars.getItems().insertItem(0, new ItemStack(barItem, 1), false);
        checkEquals(1, FabricGameTestSupport.signalAt(helper, ORIGIN),
                "Signal for a single bar in a whole column");

        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            bars.getItems().insertItem(slot, new ItemStack(barItem, 1), false);
        }
        checkEquals(15, FabricGameTestSupport.signalAt(helper, ORIGIN), "Full column signal");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        BarStackBE lower = FabricGameTestSupport.placeBar(helper, ORIGIN);
        FabricGameTestSupport.placeBar(helper, ORIGIN.above());
        Item barItem = FabricGameTestSupport.firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            lower.getItems().insertItem(slot, new ItemStack(barItem, 1), false);
        }

        // Half the column's positions are occupied, and both blocks report that rather than their own.
        checkEquals(8, FabricGameTestSupport.signalAt(helper, ORIGIN), "Lower block signal");
        checkEquals(8, FabricGameTestSupport.signalAt(helper, ORIGIN.above()), "Upper block signal");
        helper.succeed();
    }

    /**
     * The fill is measured against the column's current height, so a run that loses a block reports
     * the same contents as a larger share of a smaller column.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorFollowsAColumnLosingABlock(GameTestHelper helper) {
        BarStackBE lower = FabricGameTestSupport.placeBar(helper, ORIGIN);
        FabricGameTestSupport.placeBar(helper, ORIGIN.above());
        Item barItem = FabricGameTestSupport.firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            lower.getItems().insertItem(slot, new ItemStack(barItem, 1), false);
        }
        checkEquals(8, FabricGameTestSupport.signalAt(helper, ORIGIN), "Signal before the block was lost");

        helper.setBlock(ORIGIN.above(), Blocks.AIR);

        checkEquals(15, FabricGameTestSupport.signalAt(helper, ORIGIN), "Signal after the block was lost");
        helper.succeed();
    }

    private static int firstSupportedBy(int lowerIndex, int from, int to) {
        int[] supported = supportedBy(lowerIndex, from, to);
        if (supported.length > 0) {
            return supported[0];
        }
        throw new AssertionError("No supported upper bar");
    }

    private static int[] supportedBy(int lowerIndex, int from, int to) {
        boolean[] occupancy = new boolean[64];
        occupancy[lowerIndex] = true;
        int[] supported = new int[to - from];
        int count = 0;
        for (int index = from; index < to; index++) {
            if (BarCubeIdx.isGroundedIn(occupancy, index, null)) {
                supported[count++] = index;
            }
        }
        return Arrays.copyOf(supported, count);
    }

    private static int firstSeamSupportedBy(int seamIndex) {
        boolean[] seam = new boolean[8];
        seam[seamIndex] = true;
        for (int index = 0; index < 8; index++) {
            if (BarCubeIdx.seamSupports(index, seam)) {
                return index;
            }
        }
        throw new AssertionError("No seam-supported bottom bar");
    }

    private static int testVariant(ItemEntity entity) {
        CompoundTag tag = entity.getItem().getTag();
        return tag == null ? -1 : tag.getInt("somestacks_test_variant");
    }
}
