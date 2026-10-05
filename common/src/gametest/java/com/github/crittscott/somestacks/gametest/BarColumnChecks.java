package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.ViewRay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.droppedInColumn;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.droppedNear;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.firstBarItem;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.seedSlot;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.signalAt;

/**
 * Bar support and collapse in a live level that does not touch loader-native automation directly:
 * item validity, grounding at the bottom of a column, footprint overlap deciding support across
 * the alternating layer orientations, and player extraction dropping what it unsupports.
 * Loader-native automation and protection-event tests stay in each loader's GameTest classes.
 */
public final class BarColumnChecks {
    private BarColumnChecks() {}

    /**
     * Bar accepts configured ingots on its grounded bottom layer and rejects other items or
     * unsupported positions. To reproduce in-game: deposit a configured ingot into a bottom Bar
     * position, then try a non-ingot and a floating position. Only the grounded ingot is stored.
     */
    public static void validityAndBottomGroundingAreEnforced(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        ItemStack accepted = new ItemStack(barItem, 2);

        check(bars.depositAt(0, accepted), "Valid bottom bar was rejected");
        checkEquals(1, accepted.getCount(), "Accepted remainder");

        ItemStack invalid = new ItemStack(Items.STICK);
        check(!bars.depositAt(1, invalid), "Invalid Bar item was accepted");
        checkEquals(1, invalid.getCount(), "Rejected stack changed");
        helper.succeed();
    }

    /**
     * A Bar above the bottom layer needs footprint overlap with a Bar beneath it. To reproduce
     * in-game: place one bottom Bar, then try depositing above both an overlapping and a disjoint
     * position. Only the overlapping Bar remains.
     */
    public static void upperBarRequiresOverlappingSupport(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        int upper = firstSupportedBy(0, 8, 16);

        check(!bars.depositAt(upper, new ItemStack(barItem)),
                "Floating upper bar was accepted");
        check(bars.depositAt(0, new ItemStack(barItem)), "Bottom bar deposit failed");
        check(bars.depositAt(upper, new ItemStack(barItem)),
                "Supported upper bar was rejected");
        helper.succeed();
    }

    /**
     * Player extraction cascades through Bars that lose support. To reproduce in-game: build a
     * vertical chain of supported Bars and extract its bottom Bar. The unsupported Bars above drop.
     */
    public static void playerExtractionDropsUnsupportedBars(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        int upper = firstSupportedBy(0, 8, 16);
        bars.depositAt(0, new ItemStack(barItem));
        bars.depositAt(upper, new ItemStack(barItem));

        ItemStack extracted = bars.extractAt(0);

        checkEquals(barItem, extracted.getItem(), "Extracted item");
        checkEquals(1, extracted.getCount(), "Extracted count");
        check(helper.getLevel().getBlockEntity(bars.getBlockPos()) == null,
                "Emptied Bar block remained");
        checkEquals(1, droppedNear(helper, bars.getBlockPos(), barItem),
                "Unsupported bar drop count");
        helper.succeed();
    }

    /**
     * A neighboring Bar with another overlapping support survives a player cascade. To reproduce
     * in-game: support an upper Bar with two lower Bars, then extract one lower support. The upper
     * Bar remains on the other.
     */
    public static void supportedNeighborSurvivesPlayerExtraction(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        int removedSupport = 0;
        int survivingSupport = 1;
        int upper = firstSupportedBy(survivingSupport, 8, 16);
        bars.depositAt(removedSupport, new ItemStack(barItem));
        bars.depositAt(survivingSupport, new ItemStack(barItem));
        bars.depositAt(upper, new ItemStack(barItem));

        bars.extractAt(removedSupport);

        checkEquals(barItem, bars.getItems().getStackInSlot(upper).getItem(),
                "Still-supported upper bar was removed");
        checkEquals(0, droppedNear(helper, bars.getBlockPos(), barItem),
                "A supported bar was dropped");
        helper.succeed();
    }

    /**
     * Bar support across a block seam follows cuboid footprint overlap between alternating layers.
     * To reproduce in-game: fill a top-layer Bar below, then deposit into an overlapping bottom
     * position of the Bar block above. The cross-oriented overlap supports it.
     */
    public static void differentlyOrientedSeamUsesFootprintOverlap(GameTestHelper helper) {
        BarStackBE lower = placeBar(helper, ORIGIN);
        BarStackBE upper = placeBar(helper, ORIGIN.above());
        Item barItem = firstBarItem();
        int lowerTop = 56;
        int upperBottom = firstSeamSupportedBy(0);
        lower.getItems().insertItem(lowerTop, new ItemStack(barItem), false);

        check(upper.depositAt(upperBottom, new ItemStack(barItem)),
                "Cross-block footprint support was rejected");
        helper.succeed();
    }

    /**
     * A Bar deposit ray selects the last empty bar before the first occupied bar. To reproduce
     * in-game: place a Bar in the farther position of a bottom-layer row and aim through the empty
     * nearer position. The new Bar occupies the nearer position rather than passing through it.
     */
    public static void depositTraceStopsAtLastEmptyBeforeOccupiedBar(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        bars.getItems().insertItem(1, new ItemStack(barItem), false);
        BlockPos pos = bars.getBlockPos();
        double y = pos.getY() + 1.0 / 16.0;
        double z = pos.getZ() + 2.0 / 16.0;
        ViewRay ray = new ViewRay(
                new Vec3(pos.getX() - 1.0, y, z),
                new Vec3(pos.getX() + 2.0, y, z));

        checkEquals(0, BarCubeIdx.traceAllPositions(ray, pos, bars.getItems()),
                "Bar trace deposit index");
        helper.succeed();
    }

    /**
     * Breaking a lower Bar block collapses Bars in the block above that depended on its seam. To
     * reproduce in-game: build supported Bars across a two-block seam and break the lower block.
     * The upper dependent Bars drop and its empty block disappears.
     */
    public static void breakingLowerBlockCollapsesDependentUpperBlock(GameTestHelper helper) {
        BarStackBE lower = placeBar(helper, ORIGIN);
        BarStackBE upper = placeBar(helper, ORIGIN.above());
        Item barItem = firstBarItem();
        int upperBottom = firstSeamSupportedBy(0);
        lower.getItems().insertItem(56, new ItemStack(barItem), false);
        upper.depositAt(upperBottom, new ItemStack(barItem));

        helper.getLevel().setBlock(lower.getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        check(helper.getLevel().getBlockEntity(upper.getBlockPos()) == null,
                "Dependent upper Bar block remained");
        checkEquals(2, droppedNear(helper, lower.getBlockPos(), barItem),
                "Break/collapse drop count");
        helper.succeed();
    }

    /**
     * Breaking a full Bar column drops all contents without spawning one entity per Bar. To
     * reproduce in-game: fill a Bar block with one ingot type and break it. Every ingot is returned
     * in consolidated dropped stacks.
     */
    public static void breakingFullColumnConsolidatesDrops(GameTestHelper helper) {
        BarStackBE lower = placeBar(helper, ORIGIN);
        BarStackBE upper = placeBar(helper, ORIGIN.above());
        Item barItem = firstBarItem();
        BlockPos lowerPos = lower.getBlockPos();
        BlockPos upperPos = upper.getBlockPos();

        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            seedSlot(lower.getItems(), slot, new ItemStack(barItem));
            seedSlot(upper.getItems(), slot, new ItemStack(barItem));
        }

        helper.getLevel().setBlock(lowerPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        check(helper.getLevel().getBlockEntity(lowerPos) == null,
                "Broken lower Bar block remained");
        check(helper.getLevel().getBlockEntity(upperPos) == null,
                "Collapsed upper Bar block remained");

        List<ItemEntity> drops = droppedInColumn(helper, lowerPos, upperPos).stream()
                .filter(entity -> entity.getItem().is(barItem))
                .toList();
        checkEquals(2, drops.size(), "Consolidated item-entity count");
        checkEquals(128, drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum(),
                "Consolidated item count");
        check(drops.stream().allMatch(entity -> entity.getItem().getCount() == 64),
                "A full block's bars were not packed into one full stack");
        helper.succeed();
    }

    /**
     * A player-triggered cascade consolidates matching drops across block boundaries. To reproduce
     * in-game: build a dense supported Bar column spanning two blocks and remove its lowest support.
     * All unsupported Bars are returned in consolidated item entities.
     */
    public static void playerCascadeConsolidatesAcrossBlocks(GameTestHelper helper) {
        BarStackBE lower = placeBar(helper, ORIGIN);
        BarStackBE upper = placeBar(helper, ORIGIN.above());
        Item barItem = firstBarItem();
        BlockPos lowerPos = lower.getBlockPos();
        BlockPos upperPos = upper.getBlockPos();

        int slot = 0;
        seedSlot(lower.getItems(), slot, new ItemStack(barItem));
        for (int layer = 1; layer < 8; layer++) {
            slot = firstSupportedBy(slot, layer * 8, (layer + 1) * 8);
            seedSlot(lower.getItems(), slot, new ItemStack(barItem));
        }

        slot = firstSeamSupportedBy(slot - 56);
        seedSlot(upper.getItems(), slot, new ItemStack(barItem));
        for (int layer = 1; layer < 8; layer++) {
            slot = firstSupportedBy(slot, layer * 8, (layer + 1) * 8);
            seedSlot(upper.getItems(), slot, new ItemStack(barItem));
        }

        ItemStack extracted = lower.extractAt(0);

        checkEquals(barItem, extracted.getItem(), "Extracted support item");
        checkEquals(1, extracted.getCount(), "Extracted support count");
        check(helper.getLevel().getBlockEntity(lowerPos) == null,
                "Emptied lower Bar block remained");
        check(helper.getLevel().getBlockEntity(upperPos) == null,
                "Emptied upper Bar block remained");

        List<ItemEntity> drops = droppedInColumn(helper, lowerPos, upperPos).stream()
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

    /**
     * A Bar collapse never merges stacks with different data components. To reproduce in-game:
     * build a supported Bar chain from otherwise identical items carrying distinct components, then
     * remove the support. Each component identity remains a separate drop.
     */
    public static void collapseDoesNotMergeDifferentComponents(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        BlockPos pos = bars.getBlockPos();
        int[] upper = supportedBy(0, 8, 16);
        checkEquals(2, upper.length, "Test support fan-out");

        ItemStack first = new ItemStack(barItem);
        CustomData.update(DataComponents.CUSTOM_DATA, first, t -> t.putInt("somestacks_test_variant", 1));
        ItemStack second = new ItemStack(barItem);
        CustomData.update(DataComponents.CUSTOM_DATA, second, t -> t.putInt("somestacks_test_variant", 2));
        seedSlot(bars.getItems(), 0, new ItemStack(barItem));
        seedSlot(bars.getItems(), upper[0], first);
        seedSlot(bars.getItems(), upper[1], second);

        bars.extractAt(0);

        List<ItemEntity> drops = droppedInColumn(helper, pos, pos).stream()
                .filter(entity -> entity.getItem().is(barItem))
                .toList();
        checkEquals(2, drops.size(), "Component-distinct item-entity count");
        checkEquals(2, drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum(),
                "Component-distinct item count");
        int[] variants = drops.stream().mapToInt(BarColumnChecks::testVariant).sorted().toArray();
        check(Arrays.equals(new int[]{1, 2}, variants),
                "Different components were merged or changed: " + Arrays.toString(variants));
        helper.succeed();
    }

    /**
     * The comparator range reserves 0 for an empty column, as a vanilla container's does, and a
     * position holds one bar, so full means every position of every block occupied.
     *
     * <p>To reproduce in-game: compare an empty Bar column, the same column with one Bar, and a full
     * column. Their comparator outputs are 0, at least 1, and 15 respectively.
     */
    public static void comparatorReservesZeroForAnEmptyColumn(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();

        checkEquals(0, signalAt(helper, ORIGIN), "Empty column signal");

        bars.getItems().insertItem(0, new ItemStack(barItem, 1), false);
        checkEquals(1, signalAt(helper, ORIGIN),
                "Signal for a single bar in a whole column");

        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            bars.getItems().insertItem(slot, new ItemStack(barItem, 1), false);
        }
        checkEquals(15, signalAt(helper, ORIGIN), "Full column signal");
        helper.succeed();
    }

    /**
     * Every block in a Bar column reports the same whole-column fill. To reproduce in-game: make a
     * two-block column with one block full and the other empty, then place comparators against both.
     * Their outputs match.
     */
    public static void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        BarStackBE lower = placeBar(helper, ORIGIN);
        placeBar(helper, ORIGIN.above());
        Item barItem = firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            lower.getItems().insertItem(slot, new ItemStack(barItem, 1), false);
        }

        // Half the column's positions are occupied, and both blocks report that rather than their own.
        checkEquals(8, signalAt(helper, ORIGIN), "Lower block signal");
        checkEquals(8, signalAt(helper, ORIGIN.above()), "Upper block signal");
        helper.succeed();
    }

    /**
     * The fill is measured against the column's current height, so a run that loses a block reports
     * the same contents as a larger share of a smaller column.
     *
     * <p>To reproduce in-game: fill the lower half of a two-block Bar column, read its comparator,
     * then remove the empty upper block. The lower block's output rises from half full to full.
     */
    public static void comparatorFollowsAColumnLosingABlock(GameTestHelper helper) {
        BarStackBE lower = placeBar(helper, ORIGIN);
        placeBar(helper, ORIGIN.above());
        Item barItem = firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            lower.getItems().insertItem(slot, new ItemStack(barItem, 1), false);
        }
        checkEquals(8, signalAt(helper, ORIGIN), "Signal before the block was lost");

        helper.setBlock(ORIGIN.above(), Blocks.AIR);

        checkEquals(15, signalAt(helper, ORIGIN), "Signal after the block was lost");
        helper.succeed();
    }

    public static int firstSupportedBy(int lowerIndex, int from, int to) {
        int[] supported = supportedBy(lowerIndex, from, to);
        if (supported.length > 0) {
            return supported[0];
        }
        throw new AssertionError("No supported upper bar");
    }

    public static int[] supportedBy(int lowerIndex, int from, int to) {
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

    public static int firstSeamSupportedBy(int seamIndex) {
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
        CustomData data = entity.getItem().get(DataComponents.CUSTOM_DATA);
        return data == null ? -1 : data.copyTag().getInt("somestacks_test_variant");
    }
}
