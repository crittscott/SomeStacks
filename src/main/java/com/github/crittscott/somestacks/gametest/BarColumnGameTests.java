package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import static com.github.crittscott.somestacks.gametest.GameTestSupport.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.check;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.checkEquals;

@GameTestHolder(SomeStacks.MODID)
@PrefixGameTestTemplate(false)
public final class BarColumnGameTests {
    private BarColumnGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void validityAndBottomGroundingAreEnforced(GameTestHelper helper) {
        BarStackBE bars = GameTestSupport.placeBar(helper, ORIGIN);
        Item barItem = GameTestSupport.firstBarItem();
        ItemStack accepted = new ItemStack(barItem, 2);

        check(bars.depositAt(0, accepted), "Valid bottom bar was rejected");
        checkEquals(1, accepted.getCount(), "Accepted remainder");

        ItemStack invalid = new ItemStack(Items.STICK);
        check(!bars.depositAt(1, invalid), "Invalid Bar item was accepted");
        checkEquals(1, invalid.getCount(), "Rejected stack changed");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void upperBarRequiresOverlappingSupport(GameTestHelper helper) {
        BarStackBE bars = GameTestSupport.placeBar(helper, ORIGIN);
        Item barItem = GameTestSupport.firstBarItem();
        int upper = firstSupportedBy(0, 8, 16);

        check(!bars.depositAt(upper, new ItemStack(barItem)),
                "Floating upper bar was accepted");
        check(bars.depositAt(0, new ItemStack(barItem)), "Bottom bar deposit failed");
        check(bars.depositAt(upper, new ItemStack(barItem)),
                "Supported upper bar was rejected");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void playerExtractionDropsUnsupportedBars(GameTestHelper helper) {
        BarStackBE bars = GameTestSupport.placeBar(helper, ORIGIN);
        Item barItem = GameTestSupport.firstBarItem();
        int upper = firstSupportedBy(0, 8, 16);
        bars.depositAt(0, new ItemStack(barItem));
        bars.depositAt(upper, new ItemStack(barItem));

        ItemStack extracted = bars.extractAt(0);

        checkEquals(barItem, extracted.getItem(), "Extracted item");
        checkEquals(1, extracted.getCount(), "Extracted count");
        check(helper.getLevel().getBlockEntity(bars.getBlockPos()) == null,
                "Emptied Bar block remained");
        checkEquals(1, GameTestSupport.droppedNear(helper, bars.getBlockPos(), barItem),
                "Unsupported bar drop count");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void supportedNeighborSurvivesPlayerExtraction(GameTestHelper helper) {
        BarStackBE bars = GameTestSupport.placeBar(helper, ORIGIN);
        Item barItem = GameTestSupport.firstBarItem();
        int removedSupport = 0;
        int survivingSupport = 1;
        int upper = firstSupportedBy(survivingSupport, 8, 16);
        bars.depositAt(removedSupport, new ItemStack(barItem));
        bars.depositAt(survivingSupport, new ItemStack(barItem));
        bars.depositAt(upper, new ItemStack(barItem));

        bars.extractAt(removedSupport);

        checkEquals(barItem, bars.getItems().getStackInSlot(upper).getItem(),
                "Still-supported upper bar was removed");
        checkEquals(0, GameTestSupport.droppedNear(helper, bars.getBlockPos(), barItem),
                "A supported bar was dropped");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void differentlyOrientedSeamUsesFootprintOverlap(GameTestHelper helper) {
        BarStackBE lower = GameTestSupport.placeBar(helper, ORIGIN);
        BarStackBE upper = GameTestSupport.placeBar(helper, ORIGIN.above());
        Item barItem = GameTestSupport.firstBarItem();
        int lowerTop = 56;
        int upperBottom = firstSeamSupportedBy(0);
        lower.getItems().insertItem(lowerTop, new ItemStack(barItem), false);

        check(upper.depositAt(upperBottom, new ItemStack(barItem)),
                "Cross-block footprint support was rejected");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void automationBackfillsWithoutDropping(GameTestHelper helper) {
        BarStackBE bars = GameTestSupport.placeBar(helper, ORIGIN);
        Item barItem = GameTestSupport.firstBarItem();
        IItemHandler capability = GameTestSupport.capability(bars);
        ItemStack offered = new ItemStack(barItem, 10);

        // A walk of the advertised positions fills the block from the bottom, because each bar
        // placed supports the layer above it before the walk reaches it.
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(capability, offered, false);
        check(remainder.isEmpty(), "A walk of the positions left a remainder");
        checkEquals(10, GameTestSupport.count(bars.getItems(), barItem),
                "Inserted bar count");

        ItemStack extracted = capability.extractItem(0, 64, false);

        checkEquals(1, extracted.getCount(), "Automated extracted count");
        checkEquals(9, GameTestSupport.count(bars.getItems(), barItem),
                "Count after automated extraction");
        checkEquals(barItem, bars.getItems().getStackInSlot(0).getItem(),
                "Hole was not backfilled");
        checkEquals(0, GameTestSupport.droppedNear(helper, bars.getBlockPos(), barItem),
                "Automation dropped a bar");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void capabilityInsertionAnswersForThePositionItIsGiven(GameTestHelper helper) {
        BarStackBE bars = GameTestSupport.placeBar(helper, ORIGIN);
        Item barItem = GameTestSupport.firstBarItem();
        IItemHandler capability = GameTestSupport.capability(bars);
        ItemStack offered = new ItemStack(barItem, 4);

        // Position 8 is the first of layer 1, with an empty layer 0 beneath it, so it is refused
        // rather than redirected to a position that would take the bar.
        checkEquals(4, capability.insertItem(8, offered, true).getCount(),
                "Simulated insertion into an unsupported position");
        checkEquals(4, capability.insertItem(8, offered, false).getCount(),
                "Committed insertion into an unsupported position");
        checkEquals(0, GameTestSupport.occupied(bars.getItems()),
                "An unsupported position stored something");

        // Position 0 is in the bottom layer of a column standing on the world, so it is grounded
        // outright and takes exactly the one bar a position holds.
        checkEquals(3, capability.insertItem(0, offered, true).getCount(),
                "Simulated insertion into a grounded position");
        checkEquals(3, capability.insertItem(0, offered, false).getCount(),
                "Committed insertion into a grounded position");
        checkEquals(4, offered.getCount(), "Capability mutated caller input");
        checkEquals(1, GameTestSupport.occupied(bars.getItems()),
                "One call should store one bar");
        checkEquals(1, capability.getSlotLimit(0),
                "A position's slot limit should be what one call takes");
        helper.succeed();
    }

    /**
     * Validity gates insertion only, so a bar stored before an {@code ss ingot} edit or a data pack
     * reload narrowed the rule has to survive the backfill an automated extraction moves it through.
     * A stick stands in for such a bar: a Bar Stack refuses one from a deposit, but may be holding
     * contents the current ingot set no longer covers.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void backfillKeepsABarItWouldNoLongerAccept(GameTestHelper helper) {
        BarStackBE bars = GameTestSupport.placeBar(helper, ORIGIN);
        Item barItem = GameTestSupport.firstBarItem();
        BlockPos pos = bars.getBlockPos();
        check(!BarStackBE.isValidBarItem(new ItemStack(Items.STICK)),
                "Test needs an item a Bar Stack refuses");
        bars.getItems().insertItem(0, new ItemStack(barItem), false);
        GameTestSupport.seedSlot(bars.getItems(), 1, new ItemStack(Items.STICK));
        IItemHandler capability = GameTestSupport.capability(bars);

        ItemStack extracted = capability.extractItem(0, 64, false);

        checkEquals(barItem, extracted.getItem(), "Automated extracted item");
        checkEquals(1, extracted.getCount(), "Automated extraction took more than one bar");
        int accounted = GameTestSupport.heldAt(helper, pos, Items.STICK)
                + GameTestSupport.droppedNear(helper, pos, Items.STICK);
        checkEquals(1, accounted, "Refused bar after being backfilled into the hole");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void capabilitySimulationDoesNotMutateExtraction(GameTestHelper helper) {
        BarStackBE bars = GameTestSupport.placeBar(helper, ORIGIN);
        Item barItem = GameTestSupport.firstBarItem();
        bars.depositAt(0, new ItemStack(barItem));
        IItemHandler capability = GameTestSupport.capability(bars);

        ItemStack simulated = capability.extractItem(0, 64, true);

        checkEquals(barItem, simulated.getItem(), "Simulated item");
        checkEquals(1, simulated.getCount(), "Simulated count");
        checkEquals(barItem, bars.getItems().getStackInSlot(0).getItem(),
                "Simulation mutated contents");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void breakingLowerBlockCollapsesDependentUpperBlock(GameTestHelper helper) {
        BarStackBE lower = GameTestSupport.placeBar(helper, ORIGIN);
        BarStackBE upper = GameTestSupport.placeBar(helper, ORIGIN.above());
        Item barItem = GameTestSupport.firstBarItem();
        int upperBottom = firstSeamSupportedBy(0);
        lower.getItems().insertItem(56, new ItemStack(barItem), false);
        upper.depositAt(upperBottom, new ItemStack(barItem));

        helper.getLevel().setBlock(lower.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);

        check(helper.getLevel().getBlockEntity(upper.getBlockPos()) == null,
                "Dependent upper Bar block remained");
        checkEquals(2, GameTestSupport.droppedNear(helper, lower.getBlockPos(), barItem),
                "Break/collapse drop count");
        helper.succeed();
    }

    /**
     * The comparator range reserves 0 for an empty column, as a vanilla container's does, and a
     * position holds one bar, so full means every position of every block occupied.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorReservesZeroForAnEmptyColumn(GameTestHelper helper) {
        BarStackBE bars = GameTestSupport.placeBar(helper, ORIGIN);
        Item barItem = GameTestSupport.firstBarItem();

        checkEquals(0, GameTestSupport.signalAt(helper, ORIGIN), "Empty column signal");

        bars.getItems().insertItem(0, new ItemStack(barItem, 1), false);
        checkEquals(1, GameTestSupport.signalAt(helper, ORIGIN),
                "Signal for a single bar in a whole column");

        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            bars.getItems().insertItem(slot, new ItemStack(barItem, 1), false);
        }
        checkEquals(15, GameTestSupport.signalAt(helper, ORIGIN), "Full column signal");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        BarStackBE lower = GameTestSupport.placeBar(helper, ORIGIN);
        GameTestSupport.placeBar(helper, ORIGIN.above());
        Item barItem = GameTestSupport.firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            lower.getItems().insertItem(slot, new ItemStack(barItem, 1), false);
        }

        // Half the column's positions are occupied, and both blocks report that rather than their own.
        checkEquals(8, GameTestSupport.signalAt(helper, ORIGIN), "Lower block signal");
        checkEquals(8, GameTestSupport.signalAt(helper, ORIGIN.above()), "Upper block signal");
        helper.succeed();
    }

    /**
     * The fill is measured against the column's current height, so a run that loses a block reports
     * the same contents as a larger share of a smaller column.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorFollowsAColumnLosingABlock(GameTestHelper helper) {
        BarStackBE lower = GameTestSupport.placeBar(helper, ORIGIN);
        GameTestSupport.placeBar(helper, ORIGIN.above());
        Item barItem = GameTestSupport.firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            lower.getItems().insertItem(slot, new ItemStack(barItem, 1), false);
        }
        checkEquals(8, GameTestSupport.signalAt(helper, ORIGIN), "Signal before the block was lost");

        helper.setBlock(ORIGIN.above(), Blocks.AIR);

        checkEquals(15, GameTestSupport.signalAt(helper, ORIGIN), "Signal after the block was lost");
        helper.succeed();
    }

    private static int firstSupportedBy(int lowerIndex, int from, int to) {
        boolean[] occupancy = new boolean[64];
        occupancy[lowerIndex] = true;
        for (int index = from; index < to; index++) {
            if (BarCubeIdx.isGroundedIn(occupancy, index, null)) {
                return index;
            }
        }
        throw new AssertionError("No supported upper bar");
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
}
