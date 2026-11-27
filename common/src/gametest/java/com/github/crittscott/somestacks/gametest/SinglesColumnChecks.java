package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.droppedNear;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.firstBarItem;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.heldAt;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.occupied;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.seedSlot;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.signalAt;

import com.github.crittscott.somestacks.block.SinglesColumn;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBlock;
import com.github.crittscott.somestacks.gametest.GameTestScaffold.Automation;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.ViewRay;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;

/**
 * Singles support and gravity in a live level: grounding at the bottom of a column, a cell resting
 * on the one below it, visual columns lining up across differently rotated blocks, extraction
 * shifting a column down while carrying each item's rotation with it, and automation addressing the
 * cell it names. Player scenarios take the loader's click; automation scenarios take the loader's
 * {@link Automation} view.
 */
public final class SinglesColumnChecks {
    private SinglesColumnChecks() {}

    /**
     * To reproduce: build three occupied Singles blocks, rotate the upper block and some of its
     * items, and put comparators beside the ends. Break the middle block. Only its local contents
     * drop; upper items keep their positions and rotations, and both surviving runs update fill and
     * light. The fixture drains placement ticks, then inserts an upper glowstone in the same tick
     * as removal to test pending publication at the new upper base without invoking gravity.
     */
    public static void middleRemovalPreservesAndPublishesBothSurvivingColumns(
            GameTestHelper helper) {
        SinglesStackBE lower = placeSingles(helper, ORIGIN);
        SinglesStackBE middle = placeSingles(helper, ORIGIN.above());
        SinglesStackBE upper = placeSingles(helper, ORIGIN.above(2));
        lower.getItems().setStackInSlot(0, new ItemStack(Items.GLOWSTONE));
        middle.getItems().setStackInSlot(0, new ItemStack(Items.DIAMOND));
        upper.setRotation(2);
        for (int slot = 0; slot < 4; slot++) {
            upper.getItems().setStackInSlot(slot, new ItemStack(Items.PAPER));
            upper.setCubeRotation(slot, 3 - slot);
        }
        BlockPos lowerComparator = GameTestScaffold.placeComparator(helper, ORIGIN);
        BlockPos upperComparator = GameTestScaffold.placeComparator(helper, ORIGIN.above(2));
        helper.runAfterDelay(
                6,
                () -> {
                    check(
                            !helper.getLevel()
                                    .getBlockTicks()
                                    .hasScheduledTick(
                                            upper.getBlockPos(), upper.getBlockState().getBlock()),
                            "Fixture retained an upper placement tick");
                    SinglesColumn cached = lower.column();
                    check(
                            cached != null && cached == upper.column(),
                            "Ends did not share the initial cache");
                    checkEquals(3, cached.height(), "Initial height");
                    upper.getItems().setStackInSlot(4, new ItemStack(Items.GLOWSTONE));
                    upper.setCubeRotation(4, 1);
                    checkEquals(
                            0,
                            upper.getBlockState().getValue(SinglesStackBlock.LIGHT_LEVEL),
                            "Upper edit published before the split");
                    check(
                            helper.getLevel()
                                    .setBlock(
                                            middle.getBlockPos(),
                                            Blocks.AIR.defaultBlockState(),
                                            Block.UPDATE_ALL),
                            "Middle removal failed");
                    checkEquals(
                            1,
                            droppedNear(helper, middle.getBlockPos(), Items.DIAMOND),
                            "Middle drop count");
                    checkEquals(
                            0L,
                            GameTestScaffold.droppedInColumn(
                                            helper, lower.getBlockPos(), upper.getBlockPos())
                                    .stream()
                                    .filter(entity -> !entity.getItem().is(Items.DIAMOND))
                                    .count(),
                            "A surviving block dropped contents");
                    helper.runAfterDelay(
                            6,
                            () -> {
                                SinglesColumn below = lower.column();
                                SinglesColumn above = upper.column();
                                check(
                                        below != null && above != null && below != above,
                                        "Split columns not independent");
                                checkEquals(lower.getBlockPos(), below.basePos(), "Lower base");
                                checkEquals(upper.getBlockPos(), above.basePos(), "Upper base");
                                checkEquals(1, below.height(), "Lower height");
                                checkEquals(1, above.height(), "Upper height");
                                checkEquals(
                                        1,
                                        GameTestScaffold.occupied(lower.getItems()),
                                        "Lower contents");
                                checkEquals(
                                        5,
                                        GameTestScaffold.occupied(upper.getItems()),
                                        "Upper contents");
                                checkEquals(2, upper.getRotation(), "Upper block rotation");
                                for (int slot = 0; slot < 4; slot++) {
                                    checkEquals(
                                            Items.PAPER,
                                            upper.getItems().getStackInSlot(slot).getItem(),
                                            "Upper paper position");
                                    checkEquals(
                                            3 - slot,
                                            upper.getCubeRotation(slot),
                                            "Upper item rotation");
                                }
                                checkEquals(
                                        Items.GLOWSTONE,
                                        upper.getItems().getStackInSlot(4).getItem(),
                                        "Pending upper item");
                                checkEquals(1, upper.getCubeRotation(4), "Pending upper rotation");
                                checkEquals(
                                        1,
                                        GameTestScaffold.count(lower.getItems(), Items.GLOWSTONE),
                                        "Lower conservation");
                                checkEquals(
                                        1,
                                        GameTestScaffold.count(upper.getItems(), Items.GLOWSTONE),
                                        "Upper glowstone conservation");
                                checkEquals(
                                        4,
                                        GameTestScaffold.count(upper.getItems(), Items.PAPER),
                                        "Upper paper conservation");
                                checkEquals(
                                        1,
                                        droppedNear(helper, middle.getBlockPos(), Items.DIAMOND),
                                        "Final middle drops");
                                checkEquals(
                                        3,
                                        lower.getBlockState()
                                                .getValue(SinglesStackBlock.LIGHT_LEVEL),
                                        "Lower light");
                                checkEquals(
                                        3,
                                        upper.getBlockState()
                                                .getValue(SinglesStackBlock.LIGHT_LEVEL),
                                        "Upper light");
                                checkEquals(
                                        1,
                                        GameTestScaffold.comparatorOutput(helper, lowerComparator),
                                        "Lower comparator");
                                checkEquals(
                                        2,
                                        GameTestScaffold.comparatorOutput(helper, upperComparator),
                                        "Upper comparator");
                                helper.succeed();
                            });
                });
    }

    /**
     * To reproduce: select Singles, V-place a stick on a floor, then V-deposit into an unsupported
     * second-layer cell in another visual column. The bottom cell accepts one stick; the
     * unsupported cell refuses it and leaves the hand unchanged.
     */
    public static void standaloneBottomIsGroundedAndFloatingCellIsRejected(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        check(
                BlockType.SINGLES_STACK.admits(new ItemStack(Items.STICK), null),
                "Stick is not valid for Singles tests");

        ItemStack grounded = new ItemStack(Items.STICK, 2);
        check(singles.depositAt(0, grounded), "Standalone bottom deposit failed");
        checkEquals(1, grounded.getCount(), "Grounded deposit remainder");

        ItemStack floating = new ItemStack(Items.STICK, 1);
        check(!singles.depositAt(16 + 1, floating), "Floating deposit succeeded");
        checkEquals(1, floating.getCount(), "Rejected deposit changed input");
        helper.succeed();
    }

    /**
     * To reproduce: V-deposit a stick into a bottom-layer Singles cell, then paper directly above
     * it in the same visual column. Both deposits succeed; the paper rests on the stick.
     */
    public static void upperCellUsesTheCellImmediatelyBelow(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        int column = 5;
        int bottom = SinglesCubeIdx.indexFromColumn(column, 0);
        int secondLayer = SinglesCubeIdx.indexFromColumn(column, 1);

        check(singles.depositAt(bottom, new ItemStack(Items.STICK)), "Bottom deposit failed");
        check(
                singles.depositAt(secondLayer, new ItemStack(Items.PAPER)),
                "Supported upper deposit failed");
        helper.succeed();
    }

    /**
     * The fixture uses lower and upper block rotations of one and three quarter-turns and directly
     * fills the lower top cell in visual column 6.
     *
     * <p>To check manually: build a Singles column to the top layer, place a Singles block above
     * it, and Shift-right-click each block with a redstone torch to give them different rotations.
     * Deposit into an empty upper bottom cell directly above an occupied lower top cell. Support
     * follows the visible column.
     */
    public static void differentlyRotatedBlocksShareVisualSeamColumns(GameTestHelper helper) {
        SinglesStackBE lower = placeSingles(helper, ORIGIN);
        SinglesStackBE upper = placeSingles(helper, ORIGIN.above());
        lower.setRotation(1);
        upper.setRotation(3);
        int visualColumn = 6;
        int lowerStorage =
                SinglesCubeIdx.storageColumnFromVisual(visualColumn, lower.getRotation());
        int upperStorage =
                SinglesCubeIdx.storageColumnFromVisual(visualColumn, upper.getRotation());
        int lowerTop = SinglesCubeIdx.indexFromColumn(lowerStorage, 3);
        int upperBottom = SinglesCubeIdx.indexFromColumn(upperStorage, 0);

        lower.getItems().insertItem(lowerTop, new ItemStack(Items.STICK), false);
        ItemStack offered = new ItemStack(Items.PAPER);

        check(upper.depositAt(upperBottom, offered), "Visual seam did not support the upper block");
        checkEquals(0, offered.getCount(), "Upper deposit remainder");
        helper.succeed();
    }

    /**
     * To reproduce: stack a stick, paper, and apple in three consecutive cells of one Singles
     * column. Shift-right-click paper once and apple three times with a soul torch. Release Shift
     * and plain-right-click the bottom stick with an empty hand. Paper and apple each fall one
     * layer with their item rotations unchanged; the vacated top cell is empty.
     */
    public static void extractionShiftsOneColumnAndCarriesItemRotation(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            InteractionChecks.LoaderClick click) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        int column = 3;
        int bottom = SinglesCubeIdx.indexFromColumn(column, 0);
        int middle = SinglesCubeIdx.indexFromColumn(column, 1);
        int top = SinglesCubeIdx.indexFromColumn(column, 2);
        singles.getItems().insertItem(bottom, new ItemStack(Items.STICK), false);
        singles.getItems().insertItem(middle, new ItemStack(Items.PAPER), false);
        singles.getItems().insertItem(top, new ItemStack(Items.APPLE), false);
        singles.setCubeRotation(middle, 1);
        singles.setCubeRotation(top, 3);

        ItemStack extracted = extractByHand(factory, click, singles.getBlockPos(), column, 0);

        checkEquals(Items.STICK, extracted.getItem(), "Extracted item");
        checkEquals(
                Items.PAPER,
                singles.getItems().getStackInSlot(bottom).getItem(),
                "Middle item did not shift to bottom");
        checkEquals(1, singles.getCubeRotation(bottom), "Middle item rotation did not move");
        checkEquals(
                Items.APPLE,
                singles.getItems().getStackInSlot(middle).getItem(),
                "Top item did not shift to middle");
        checkEquals(3, singles.getCubeRotation(middle), "Top item rotation did not move");
        check(
                singles.getItems().getStackInSlot(top).isEmpty(),
                "Top source cell remained occupied");
        checkEquals(0, singles.getCubeRotation(top), "Vacated cell retained a rotation");
        helper.succeed();
    }

    /**
     * Synthetic admission fixture: an apple supports a directly seeded item currently accepted by
     * Bar and refused by Singles. Plain-clicking the apple must conserve the refused item, counted
     * in storage plus nearby drops; the test does not change policy.
     *
     * <p>To check a tag change manually, deposit a stick above an apple in Singles, then enable a
     * data pack adding minecraft:stick to the somestacks:ingots item tag and run /reload.
     * Plain-click the apple with an empty hand; the stored stick is conserved despite being refused
     * for new Singles deposits. Disable the data pack and /reload afterward.
     */
    public static void shiftKeepsAnItemItWouldNoLongerAccept(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            InteractionChecks.LoaderClick click) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        BlockPos pos = singles.getBlockPos();
        Item legacy = firstBarItem();
        check(
                !BlockType.SINGLES_STACK.admits(new ItemStack(legacy), null),
                "Test needs an item a Singles Stack refuses");
        int bottom = SinglesCubeIdx.indexFromColumn(0, 0);
        int second = SinglesCubeIdx.indexFromColumn(0, 1);
        singles.getItems().insertItem(bottom, new ItemStack(Items.APPLE), false);
        seedSlot(singles.getItems(), second, new ItemStack(legacy));

        ItemStack extracted = extractByHand(factory, click, pos, 0, 0);

        checkEquals(Items.APPLE, extracted.getItem(), "Extracted item");
        int accounted = heldAt(helper, pos, legacy) + droppedNear(helper, pos, legacy);
        checkEquals(1, accounted, "Refused item after the shift that moved it");
        helper.succeed();
    }

    /**
     * Synthetic admission fixture: the lower block holds an apple and the upper block has a
     * directly seeded bottom item currently refused by Singles. Plain-clicking the apple exercises
     * cross-block draw-down; it asserts conservation across both inventories and nearby drops,
     * without changing policy.
     *
     * <p>For a visible tag-change check, build a supported column spanning two Singles blocks with
     * a stick above the seam, then enable a data pack adding minecraft:stick to the
     * somestacks:ingots item tag and run /reload. Plain-click below the seam with an empty hand and
     * confirm the stick is conserved. Disable the data pack and /reload afterward.
     */
    public static void drawDownKeepsAnItemItWouldNoLongerAccept(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            InteractionChecks.LoaderClick click) {
        SinglesStackBE lower = placeSingles(helper, ORIGIN);
        SinglesStackBE upper = placeSingles(helper, ORIGIN.above());
        BlockPos lowerPos = lower.getBlockPos();
        BlockPos upperPos = upper.getBlockPos();
        Item legacy = firstBarItem();
        check(
                !BlockType.SINGLES_STACK.admits(new ItemStack(legacy), null),
                "Test needs an item a Singles Stack refuses");
        int bottom = SinglesCubeIdx.indexFromColumn(0, 0);
        lower.getItems().insertItem(bottom, new ItemStack(Items.APPLE), false);
        seedSlot(upper.getItems(), bottom, new ItemStack(legacy));

        ItemStack extracted = extractByHand(factory, click, lowerPos, 0, 0);

        checkEquals(Items.APPLE, extracted.getItem(), "Extracted item");
        int accounted =
                heldAt(helper, lowerPos, legacy)
                        + heldAt(helper, upperPos, legacy)
                        + droppedNear(helper, lowerPos, legacy);
        checkEquals(1, accounted, "Refused item after being drawn down across the seam");
        helper.succeed();
    }

    /**
     * The fixture directly stores a stick at the bottom and paper two layers above in visual column
     * 2, leaving the middle cell empty. Plain-clicking the stick shifts paper down one layer and
     * leaves the bottom empty.
     *
     * <p>This unsupported starting layout requires a fixture or /data merge block rather than
     * ordinary deposits. Create a Singles block with /setblock, then merge
     * {Items:{Items:[{Slot:2,id:"minecraft:stick",Count:1b},{Slot:34,id:"minecraft:paper",Count:1b}]}}
     * into its block entity. Plain-right-click the stick; paper moves to the middle cell,
     * preserving the gap.
     */
    public static void extractionPreservesAnExistingGap(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            InteractionChecks.LoaderClick click) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        int column = 2;
        int bottom = SinglesCubeIdx.indexFromColumn(column, 0);
        int second = SinglesCubeIdx.indexFromColumn(column, 1);
        int third = SinglesCubeIdx.indexFromColumn(column, 2);
        singles.getItems().insertItem(bottom, new ItemStack(Items.STICK), false);
        singles.getItems().insertItem(third, new ItemStack(Items.PAPER), false);

        ItemStack extracted = extractByHand(factory, click, singles.getBlockPos(), column, 0);

        checkEquals(Items.STICK, extracted.getItem(), "Extracted item");
        check(
                singles.getItems().getStackInSlot(bottom).isEmpty(),
                "Existing gap was compacted away");
        checkEquals(
                Items.PAPER,
                singles.getItems().getStackInSlot(second).getItem(),
                "Upper item did not shift exactly one layer");
        helper.succeed();
    }

    /**
     * The fixture directly seeds a lower top stick and an upper bottom paper in visual column 9,
     * with block rotations of one and two quarter-turns and paper rotation of three quarter-turns.
     *
     * <p>To check manually: build a supported Singles column across two differently rotated blocks,
     * rotate the upper bottom item with a soul torch, and extract the lower top item with an empty
     * hand. The upper item crosses the visible seam with its facing unchanged; an emptied upper
     * block disappears.
     */
    public static void extractionDrawsAcrossDifferentlyRotatedBlockBoundary(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            InteractionChecks.LoaderClick click) {
        SinglesStackBE lower = placeSingles(helper, ORIGIN);
        SinglesStackBE upper = placeSingles(helper, ORIGIN.above());
        lower.setRotation(1);
        upper.setRotation(2);
        int visualColumn = 9;
        int lowerColumn = SinglesCubeIdx.storageColumnFromVisual(visualColumn, lower.getRotation());
        int upperColumn = SinglesCubeIdx.storageColumnFromVisual(visualColumn, upper.getRotation());
        int lowerTop = SinglesCubeIdx.indexFromColumn(lowerColumn, 3);
        int upperBottom = SinglesCubeIdx.indexFromColumn(upperColumn, 0);
        lower.getItems().insertItem(lowerTop, new ItemStack(Items.STICK), false);
        upper.getItems().insertItem(upperBottom, new ItemStack(Items.PAPER), false);
        upper.setCubeRotation(upperBottom, 3);

        ItemStack extracted = extractByHand(factory, click, lower.getBlockPos(), visualColumn, 3);

        checkEquals(Items.STICK, extracted.getItem(), "Extracted item");
        checkEquals(
                Items.PAPER,
                lower.getItems().getStackInSlot(lowerTop).getItem(),
                "Item did not cross the visual block seam");
        checkEquals(3, lower.getCubeRotation(lowerTop), "Cross-seam item rotation changed");
        check(
                helper.getLevel().getBlockEntity(upper.getBlockPos()) == null,
                "Emptied top block remained");
        helper.succeed();
    }

    /**
     * Plain-clicks the Singles block at {@code pos} with an empty hand, aimed from the west along
     * the row holding visual column {@code visualColumn} at {@code layer}, and returns what the
     * hand received. Visual columns count four to a row from the north-west corner, so the click
     * meets the target cell after the cells west of it in that row, which must be empty.
     */
    private static ItemStack extractByHand(
            Function<ItemStack, ServerPlayer> factory,
            InteractionChecks.LoaderClick click,
            BlockPos pos,
            int visualColumn,
            int layer) {
        try (InteractionChecks.PlayerScope scope =
                new InteractionChecks.PlayerScope(factory.apply(ItemStack.EMPTY))) {
            var player = scope.player;
            click.click(
                    player,
                    InteractionChecks.HAND,
                    InteractionChecks.aim(
                            player, pos, (layer + 0.5) / 4, (visualColumn / 4 + 0.5) / 4));
            return player.getMainHandItem().copy();
        }
    }

    /**
     * Internal ray-selection check: a north-to-south ray through the lowest west-side row meets an
     * occupied cell at slot 8. The trace selects the preceding empty cell, slot 4.
     *
     * <p>To check the visible targeting, deposit a stick at the far side of that bottom row, then
     * aim from the north through its empty cells while V-depositing. The nearest empty cell before
     * the occupied stick receives the item.
     */
    public static void depositTraceStopsAtLastEmptyBeforeOccupiedCell(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        singles.getItems().insertItem(8, new ItemStack(Items.STICK), false);
        BlockPos blockPos = singles.getBlockPos();

        int result = singles.depositTarget(traceThroughBlock(blockPos));

        checkEquals(4, result, "Trace deposit index");
        helper.succeed();
    }

    /**
     * Internal ray-selection check: a north-to-south ray through the lowest west-side row of an
     * empty Singles block selects its farthest cell, slot 12.
     *
     * <p>To check manually, V-place Singles against a floor while aiming through an empty bottom
     * row from the north. The first item appears in the farthest cell along the server view ray.
     */
    public static void depositTraceUsesFarthestCellWhenNothingIsOccupied(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        BlockPos blockPos = singles.getBlockPos();

        int result = singles.depositTarget(traceThroughBlock(blockPos));

        checkEquals(12, result, "Trace deposit index");
        helper.succeed();
    }

    /**
     * A ray entering the block's lowest north-west cell head on and running clear through it, so
     * the cells it meets are the column the trace is being asked about.
     */
    private static ViewRay traceThroughBlock(BlockPos blockPos) {
        double x = blockPos.getX() + 0.125;
        double y = blockPos.getY() + 0.125;
        return new ViewRay(
                new Vec3(x, y, blockPos.getZ() - 1.0), new Vec3(x, y, blockPos.getZ() + 2.0));
    }

    /**
     * Internal layout check: a full lower Singles block and empty contiguous upper block both
     * report 8. The empty upper block is temporary, so the exact layout is not a stable manual
     * setup.
     *
     * <p>To check whole-run readings, build two contiguous partly filled Singles blocks and put an
     * outward-facing comparator on a solid support beside each. Both report the same fill after
     * updates.
     */
    public static void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        SinglesStackBE lower = placeSingles(helper, ORIGIN);
        placeSingles(helper, ORIGIN.above());
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            lower.getItems().insertItem(slot, new ItemStack(Items.STONE, 1), false);
        }

        // Half the column's cells are occupied, and both blocks report that rather than their own.
        checkEquals(8, signalAt(helper, ORIGIN), "Lower block signal");
        checkEquals(8, signalAt(helper, ORIGIN.above()), "Upper block signal");
        helper.succeed();
    }

    /**
     * The fixture fills a lower Singles block beneath an empty upper block, checks signal 8,
     * removes the upper block, and checks 15.
     *
     * <p>To reproduce the visible capacity change, fill a lower Singles block and place one item in
     * the supported block above. The comparator reads 8. Extract the upper item; its empty block
     * disappears and the remaining full block reads 15.
     */
    public static void comparatorFollowsAColumnLosingABlock(GameTestHelper helper) {
        SinglesStackBE lower = placeSingles(helper, ORIGIN);
        placeSingles(helper, ORIGIN.above());
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            lower.getItems().insertItem(slot, new ItemStack(Items.STONE, 1), false);
        }
        checkEquals(8, signalAt(helper, ORIGIN), "Signal before the block was lost");

        helper.setBlock(ORIGIN.above(), Blocks.AIR);

        checkEquals(15, signalAt(helper, ORIGIN), "Signal after the block was lost");
        helper.succeed();
    }

    /**
     * Internal positional insertion check: offer three sticks to unsupported Singles slot 63, then
     * grounded slot 0, through the loader's automation view. Slot 63 refuses all three; slot 0
     * accepts exactly one without mutating the caller's offer. Simulation stores nothing.
     *
     * <p>To check manually, use a slot-addressing machine on an empty Singles fixture: an
     * unsupported target must not redirect, and a grounded target stores one stick. A dry-run
     * assertion requires automation that exposes simulation or abort.
     */
    public static void capabilityInsertionAnswersForTheCellItIsGiven(
            GameTestHelper helper, Function<BlockEntity, Automation> automation) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        Automation capability = automation.apply(singles);
        ItemStack offered = new ItemStack(Items.STICK, 3);

        // Cell 63 is a top-layer cell with nothing beneath it, so it is refused rather than
        // redirected to a cell that would take the item.
        checkEquals(
                3,
                capability.insert(63, offered, true).getCount(),
                "Simulated insertion into an unsupported cell");
        checkEquals(
                3,
                capability.insert(63, offered, false).getCount(),
                "Committed insertion into an unsupported cell");
        checkEquals(0, occupied(singles.getItems()), "An unsupported cell stored something");

        // Cell 0 is grounded outright, and takes exactly the one item a cell holds.
        checkEquals(
                2,
                capability.insert(0, offered, true).getCount(),
                "Simulated insertion into a grounded cell");
        checkEquals(3, offered.getCount(), "Simulation changed input");
        checkEquals(0, occupied(singles.getItems()), "Simulation changed occupancy");

        checkEquals(
                2,
                capability.insert(0, offered, false).getCount(),
                "Committed insertion into a grounded cell");
        checkEquals(3, offered.getCount(), "Capability mutated caller input");
        checkEquals(1, occupied(singles.getItems()), "One call should store one item");
        checkEquals(
                Items.STICK,
                singles.getItems().getStackInSlot(0).getItem(),
                "The cell named should hold the item");
        helper.succeed();
    }

    /**
     * To reproduce: offer three sticks to an empty Singles fixture through a machine that visits
     * slots in ascending order. The first three bottom-layer cells receive one stick each and
     * nothing remains uninserted. The test also asserts that the automation view does not mutate
     * its caller-owned offer.
     */
    public static void capabilityWalkFillsTheColumnFromTheBottom(
            GameTestHelper helper, Function<BlockEntity, Automation> automation) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        ItemStack offered = new ItemStack(Items.STICK, 3);

        // A caller walking the advertised cells in order still fills the column, because each
        // placement stands before the next cell is offered.
        ItemStack remainder = automation.apply(singles).insertStacked(offered, false);

        check(remainder.isEmpty(), "A walk of the cells left a remainder");
        checkEquals(3, offered.getCount(), "Capability mutated caller input");
        for (int cell = 0; cell < 3; cell++) {
            checkEquals(
                    Items.STICK,
                    singles.getItems().getStackInSlot(cell).getItem(),
                    "Lowest cell " + cell);
        }
        helper.succeed();
    }

    /**
     * To reproduce: put a stick at the bottom of one Singles column and paper directly above.
     * Automate extraction from the stick's slot. Exactly one stick is extracted and paper shifts
     * into its cell. The test also performs a dry run first, which returns a stick without changing
     * contents; this requires a machine or probe with simulation/abort support.
     */
    public static void capabilityExtractionUsesThePlayerGravityPath(
            GameTestHelper helper, Function<BlockEntity, Automation> automation) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        int column = 0;
        int bottom = SinglesCubeIdx.indexFromColumn(column, 0);
        int above = SinglesCubeIdx.indexFromColumn(column, 1);
        singles.getItems().insertItem(bottom, new ItemStack(Items.STICK), false);
        singles.getItems().insertItem(above, new ItemStack(Items.PAPER), false);
        Automation capability = automation.apply(singles);

        ItemStack simulated = capability.extract(bottom, 64, true);
        checkEquals(Items.STICK, simulated.getItem(), "Simulated extracted item");
        checkEquals(
                Items.STICK,
                singles.getItems().getStackInSlot(bottom).getItem(),
                "Simulation mutated contents");

        ItemStack extracted = capability.extract(bottom, 64, false);

        checkEquals(1, extracted.getCount(), "Capability extracted more than one item");
        checkEquals(
                Items.PAPER,
                singles.getItems().getStackInSlot(bottom).getItem(),
                "Capability extraction did not shift column");
        helper.succeed();
    }
}
