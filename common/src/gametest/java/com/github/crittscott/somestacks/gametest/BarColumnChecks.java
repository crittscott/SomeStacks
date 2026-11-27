package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.count;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.droppedInColumn;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.droppedNear;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.firstBarItem;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.heldAt;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.occupied;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.seedSlot;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.signalAt;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.gametest.GameTestScaffold.Automation;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.BlockType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Bar support and collapse in a live level: item validity, grounding at the bottom of a column,
 * footprint overlap deciding support across the alternating layer orientations, player extraction
 * dropping what it unsupports, and automated extraction backfilling from the top instead. Player
 * scenarios take the loader's click; automation scenarios take the loader's {@link Automation}
 * view. Forge's event-bus-driven protection test stays in Forge's {@code BarColumnGameTests}.
 */
public final class BarColumnChecks {
    private BarColumnChecks() {}

    /**
     * To reproduce: select Bar with V + right-click air, then V + right-click a floor with two
     * accepted ingots. One bar is placed and one ingot remains in Survival. Offer a stick to a
     * bottom position; it is refused without spending the stick.
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
     * To reproduce: select Bar and V-deposit an iron, gold, copper, and netherite ingot, a brick,
     * and a nether brick; each becomes a bar. V-deposit a stick or an iron nugget; nothing happens.
     * Select Singles and V-deposit an iron ingot; it is refused there too.
     */
    public static void barAdmissionFollowsTheIngotTag(GameTestHelper helper) {
        for (Item item :
                List.of(
                        Items.IRON_INGOT,
                        Items.GOLD_INGOT,
                        Items.COPPER_INGOT,
                        Items.NETHERITE_INGOT,
                        Items.BRICK,
                        Items.NETHER_BRICK)) {
            check(new ItemStack(item).is(BarStackBE.INGOTS), item + " is not in the ingot tag");
            check(
                    BlockType.BAR_STACK.admits(new ItemStack(item), null),
                    item + " was refused by Bar");
            check(
                    !BlockType.SINGLES_STACK.admits(new ItemStack(item), null),
                    item + " was accepted by Singles");
        }
        for (Item item : List.of(Items.STICK, Items.IRON_NUGGET)) {
            check(
                    !BlockType.BAR_STACK.admits(new ItemStack(item), null),
                    item + " was accepted by Bar");
            check(
                    BlockType.SINGLES_STACK.admits(new ItemStack(item), null),
                    item + " was refused by Singles");
        }
        helper.succeed();
    }

    /**
     * To reproduce: create one bottom-layer Bar with an accepted ingot. Try depositing into a
     * second-layer position with no bar under its footprint, then into a position overlapping the
     * bottom bar. Only the supported position accepts the ingot.
     */
    public static void upperBarRequiresOverlappingSupport(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        int upper = firstSupportedBy(0, 8, 16);

        check(!bars.depositAt(upper, new ItemStack(barItem)), "Floating upper bar was accepted");
        check(bars.depositAt(0, new ItemStack(barItem)), "Bottom bar deposit failed");
        check(bars.depositAt(upper, new ItemStack(barItem)), "Supported upper bar was rejected");
        helper.succeed();
    }

    /**
     * To reproduce: deposit one accepted ingot on the bottom layer and one overlapping bar on the
     * next layer, with no other supports. Release V and plain-right-click the bottom bar with an
     * empty hand. One ingot enters the hand, the unsupported upper bar drops, and the empty block
     * disappears.
     */
    public static void playerExtractionDropsUnsupportedBars(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            InteractionChecks.LoaderClick click) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        int upper = firstSupportedBy(0, 8, 16);
        bars.depositAt(0, new ItemStack(barItem));
        bars.depositAt(upper, new ItemStack(barItem));

        ItemStack extracted = extractFirstBarByHand(factory, click, bars.getBlockPos());

        checkEquals(barItem, extracted.getItem(), "Extracted item");
        checkEquals(1, extracted.getCount(), "Extracted count");
        check(
                helper.getLevel().getBlockEntity(bars.getBlockPos()) == null,
                "Emptied Bar block remained");
        checkEquals(
                1, droppedNear(helper, bars.getBlockPos(), barItem), "Unsupported bar drop count");
        helper.succeed();
    }

    /**
     * To reproduce: place two neighboring bottom bars and an upper bar overlapping the second.
     * Plain-right-click the first bottom bar with an empty hand. The upper bar remains supported by
     * the second bottom bar and no bar drops.
     */
    public static void supportedNeighborSurvivesPlayerExtraction(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            InteractionChecks.LoaderClick click) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        int removedSupport = 0;
        int survivingSupport = 1;
        int upper = firstSupportedBy(survivingSupport, 8, 16);
        bars.depositAt(removedSupport, new ItemStack(barItem));
        bars.depositAt(survivingSupport, new ItemStack(barItem));
        bars.depositAt(upper, new ItemStack(barItem));

        ItemStack extracted = extractFirstBarByHand(factory, click, bars.getBlockPos());

        checkEquals(1, extracted.getCount(), "Extracted count");
        check(
                bars.getItems().getStackInSlot(removedSupport).isEmpty(),
                "The aimed support was not the one extracted");

        checkEquals(
                barItem,
                bars.getItems().getStackInSlot(upper).getItem(),
                "Still-supported upper bar was removed");
        checkEquals(
                0, droppedNear(helper, bars.getBlockPos(), barItem), "A supported bar was dropped");
        helper.succeed();
    }

    /**
     * The fixture directly fills a top-layer position of the lower block and deposits into an
     * overlapping bottom position of the upper block.
     *
     * <p>To check the visible seam: build a supported Bar column to its top layer, then V-deposit
     * an accepted ingot into an overlapping position in a Bar block above it. The perpendicular
     * layers support the new bar through footprint overlap.
     */
    public static void differentlyOrientedSeamUsesFootprintOverlap(GameTestHelper helper) {
        BarStackBE lower = placeBar(helper, ORIGIN);
        BarStackBE upper = placeBar(helper, ORIGIN.above());
        Item barItem = firstBarItem();
        int lowerTop = 56;
        int upperBottom = firstSeamSupportedBy(0);
        lower.getItems().insertItem(lowerTop, new ItemStack(barItem), false);

        check(
                upper.depositAt(upperBottom, new ItemStack(barItem)),
                "Cross-block footprint support was rejected");
        helper.succeed();
    }

    /**
     * The fixture seeds one top-layer bar below one dependent bar in the next block.
     *
     * <p>To check manually: build a supported two-block Bar column and break the lower block. Its
     * contents drop and dependent bars above collapse; an emptied upper block disappears. The
     * sparse fixture asserts two total dropped ingots.
     */
    public static void breakingLowerBlockCollapsesDependentUpperBlock(GameTestHelper helper) {
        BarStackBE lower = placeBar(helper, ORIGIN);
        BarStackBE upper = placeBar(helper, ORIGIN.above());
        Item barItem = firstBarItem();
        int upperBottom = firstSeamSupportedBy(0);
        lower.getItems().insertItem(56, new ItemStack(barItem), false);
        upper.depositAt(upperBottom, new ItemStack(barItem));

        helper.getLevel()
                .setBlock(lower.getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        check(
                helper.getLevel().getBlockEntity(upper.getBlockPos()) == null,
                "Dependent upper Bar block remained");
        checkEquals(
                2, droppedNear(helper, lower.getBlockPos(), barItem), "Break/collapse drop count");
        helper.succeed();
    }

    /**
     * To reproduce: fill two contiguous Bar blocks with 128 identical accepted ingots, then break
     * the lower block. Both blocks disappear and all 128 ingots drop. The automated check asserts
     * 64 ingots at each originating block, allowing the container drop helper to split stacks;
     * later vanilla item-entity merging is outside this check.
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

        check(
                helper.getLevel().getBlockEntity(lowerPos) == null,
                "Broken lower Bar block remained");
        check(
                helper.getLevel().getBlockEntity(upperPos) == null,
                "Collapsed upper Bar block remained");

        List<ItemEntity> drops =
                droppedInColumn(helper, lowerPos, upperPos).stream()
                        .filter(entity -> entity.getItem().is(barItem))
                        .toList();
        checkEquals(
                128,
                drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum(),
                "Consolidated item count");
        checkEquals(
                64,
                drops.stream()
                        .filter(entity -> entity.blockPosition().equals(lowerPos))
                        .mapToInt(entity -> entity.getItem().getCount())
                        .sum(),
                "Broken block's drop count at its position");
        checkEquals(
                64,
                drops.stream()
                        .filter(entity -> entity.blockPosition().equals(upperPos))
                        .mapToInt(entity -> entity.getItem().getCount())
                        .sum(),
                "Collapsed block's drop count at its position");
        helper.succeed();
    }

    /**
     * To reproduce: set /gamerule doTileDrops false, fill two contiguous Bar blocks with accepted
     * ingots, then break the lower block. All 128 ingots drop from their originating blocks and
     * both blocks disappear. Restore the gamerule afterward.
     */
    public static void breakingFullColumnDropsWithBlockDropsDisabled(GameTestHelper helper) {
        withBlockDropsDisabled(helper, () -> breakingFullColumnConsolidatesDrops(helper));
    }

    /**
     * To reproduce: set /gamerule doTileDrops false and build a chain of one supported bar in each
     * layer of two Bar blocks. Extract the lowest bar with an empty hand. One ingot enters the
     * hand, fifteen drop from their originating blocks, and both blocks disappear. Restore the
     * gamerule afterward.
     */
    public static void playerCascadeDropsWithBlockDropsDisabled(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            InteractionChecks.LoaderClick click) {
        withBlockDropsDisabled(
                helper, () -> playerCascadeConsolidatesAcrossBlocks(helper, factory, click));
    }

    private static void withBlockDropsDisabled(GameTestHelper helper, Runnable scenario) {
        GameRules.BooleanValue blockDrops =
                helper.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS);
        boolean previous = blockDrops.get();
        try {
            blockDrops.set(false, helper.getLevel().getServer());
            scenario.run();
        } finally {
            blockDrops.set(previous, helper.getLevel().getServer());
        }
    }

    /**
     * To reproduce: build a chain of one bar in each of the sixteen layers of two Bar blocks, each
     * overlapping its sole support below. Plain-right-click the lowest bar with an empty hand. One
     * ingot enters the hand and fifteen drop; both empty blocks disappear. The check inspects drops
     * before vanilla merging: seven from below and eight from above.
     */
    public static void playerCascadeConsolidatesAcrossBlocks(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            InteractionChecks.LoaderClick click) {
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

        ItemStack extracted = extractFirstBarByHand(factory, click, lowerPos);

        checkEquals(barItem, extracted.getItem(), "Extracted support item");
        checkEquals(1, extracted.getCount(), "Extracted support count");
        check(
                helper.getLevel().getBlockEntity(lowerPos) == null,
                "Emptied lower Bar block remained");
        check(
                helper.getLevel().getBlockEntity(upperPos) == null,
                "Emptied upper Bar block remained");

        List<ItemEntity> drops =
                droppedInColumn(helper, lowerPos, upperPos).stream()
                        .filter(entity -> entity.getItem().is(barItem))
                        .toList();
        checkEquals(2, drops.size(), "Cross-block cascade item-entity count");
        checkEquals(
                15,
                drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum(),
                "Cross-block cascade item count");
        int[] counts =
                drops.stream().mapToInt(entity -> entity.getItem().getCount()).sorted().toArray();
        check(
                Arrays.equals(new int[] {7, 8}, counts),
                "Drops were not packed at their originating blocks: " + Arrays.toString(counts));
        checkEquals(
                7,
                drops.stream()
                        .filter(entity -> entity.blockPosition().equals(lowerPos))
                        .mapToInt(entity -> entity.getItem().getCount())
                        .sum(),
                "Lower cascade drop count at its position");
        checkEquals(
                8,
                drops.stream()
                        .filter(entity -> entity.blockPosition().equals(upperPos))
                        .mapToInt(entity -> entity.getItem().getCount())
                        .sum(),
                "Upper cascade drop count at its position");
        helper.succeed();
    }

    /**
     * To reproduce: use /give to obtain two accepted ingots with distinct custom NBT, such as
     * {somestacks_test_variant:1} and {somestacks_test_variant:2}. Place them in the two
     * second-layer positions supported by a single bottom bar. Extract that support with an empty
     * hand. Both upper bars drop with their original tags and remain distinct stacks; the support
     * goes to the hand.
     */
    public static void collapseDoesNotMergeDifferentTags(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            InteractionChecks.LoaderClick click) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        BlockPos pos = bars.getBlockPos();
        int[] upper = supportedBy(0, 8, 16);
        checkEquals(2, upper.length, "Test support fan-out");

        ItemStack first = new ItemStack(barItem);
        first.getOrCreateTag().putInt("somestacks_test_variant", 1);
        ItemStack second = new ItemStack(barItem);
        second.getOrCreateTag().putInt("somestacks_test_variant", 2);
        seedSlot(bars.getItems(), 0, new ItemStack(barItem));
        seedSlot(bars.getItems(), upper[0], first);
        seedSlot(bars.getItems(), upper[1], second);

        checkEquals(
                barItem, extractFirstBarByHand(factory, click, pos).getItem(), "Extracted support");

        List<ItemEntity> drops =
                droppedInColumn(helper, pos, pos).stream()
                        .filter(entity -> entity.getItem().is(barItem))
                        .toList();
        checkEquals(2, drops.size(), "Differently tagged item-entity count");
        checkEquals(
                2,
                drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum(),
                "Differently tagged item count");
        int[] variants = drops.stream().mapToInt(BarColumnChecks::testVariant).sorted().toArray();
        check(
                Arrays.equals(new int[] {1, 2}, variants),
                "Different tags were merged or changed: " + Arrays.toString(variants));
        helper.succeed();
    }

    /**
     * Internal layout check: the fixture fills all 64 positions of a lower Bar block and leaves its
     * contiguous upper block empty. Both block signal queries return 8 for the half-full run. Empty
     * upper blocks are temporary, so there is no stable manual reproduction of that exact layout.
     *
     * <p>For the visible behavior, build two contiguous partly filled Bar blocks and put an
     * outward-facing comparator beside each on a solid support. Both report the same whole-run fill
     * after updates.
     */
    public static void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        BarStackBE lower = placeBar(helper, ORIGIN);
        placeBar(helper, ORIGIN.above());
        Item barItem = firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            lower.getItems().insertItem(slot, new ItemStack(barItem, 1), false);
        }

        // Half the column's positions are occupied, and both blocks report that rather than their
        // own.
        checkEquals(8, signalAt(helper, ORIGIN), "Lower block signal");
        checkEquals(8, signalAt(helper, ORIGIN.above()), "Upper block signal");
        helper.succeed();
    }

    /**
     * Internal layout check: a full lower Bar block and empty upper block report 8; removing the
     * upper block changes the lower signal to 15.
     *
     * <p>To check the visible capacity change, fill a lower Bar block and add one bar in a
     * supported block above. The comparator reads 8. Extract the upper bar; its empty block
     * disappears and the full remaining block reads 15.
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

    /**
     * To reproduce: allow the chosen ingot for Bar, then use a machine that walks positions
     * bottom-up to insert ten identical ingots. Extract one from bottom position 0 through
     * automation. Nine remain, position 0 is backfilled from the topmost bar, and no ingot item
     * entity is dropped.
     */
    public static void automationBackfillsWithoutDropping(
            GameTestHelper helper, Function<BlockEntity, Automation> automation) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        Automation capability = automation.apply(bars);
        ItemStack offered = new ItemStack(barItem, 10);

        // A walk of the advertised positions fills the block from the bottom, because each bar
        // placed supports the layer above it before the walk reaches it.
        ItemStack remainder = capability.insertStacked(offered, false);
        check(remainder.isEmpty(), "A walk of the positions left a remainder");
        checkEquals(10, count(bars.getItems(), barItem), "Inserted bar count");

        ItemStack extracted = capability.extract(0, 64, false);

        checkEquals(1, extracted.getCount(), "Automated extracted count");
        checkEquals(9, count(bars.getItems(), barItem), "Count after automated extraction");
        checkEquals(
                barItem, bars.getItems().getStackInSlot(0).getItem(), "Hole was not backfilled");
        checkEquals(
                0, droppedNear(helper, bars.getBlockPos(), barItem), "Automation dropped a bar");
        helper.succeed();
    }

    /**
     * Internal positional insertion check: offer four accepted ingots to unsupported slot 8, then
     * grounded slot 0, through the loader's automation view. Slot 8 refuses all four; slot 0
     * accepts exactly one, leaving the caller's offer unchanged. Simulation reports the same
     * capacity without storing.
     *
     * <p>To check manually, use slot-addressing automation on an empty Bar fixture. An unsupported
     * target must not redirect to another position; a bottom target stores one bar. Simulation
     * requires a machine or probe exposing that operation.
     */
    public static void capabilityInsertionAnswersForThePositionItIsGiven(
            GameTestHelper helper, Function<BlockEntity, Automation> automation) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        Automation capability = automation.apply(bars);
        ItemStack offered = new ItemStack(barItem, 4);

        // Position 8 is the first of layer 1, with an empty layer 0 beneath it, so it is refused
        // rather than redirected to a position that would take the bar.
        checkEquals(
                4,
                capability.insert(8, offered, true).getCount(),
                "Simulated insertion into an unsupported position");
        checkEquals(
                4,
                capability.insert(8, offered, false).getCount(),
                "Committed insertion into an unsupported position");
        checkEquals(0, occupied(bars.getItems()), "An unsupported position stored something");

        // Position 0 is in the bottom layer of a column standing on the world, so it is grounded
        // outright and takes exactly the one bar a position holds.
        checkEquals(
                3,
                capability.insert(0, offered, true).getCount(),
                "Simulated insertion into a grounded position");
        checkEquals(
                3,
                capability.insert(0, offered, false).getCount(),
                "Committed insertion into a grounded position");
        checkEquals(4, offered.getCount(), "Capability mutated caller input");
        checkEquals(1, occupied(bars.getItems()), "One call should store one bar");
        checkEquals(
                1,
                capability.slotLimit(0),
                "A position's slot limit should be what one call takes");
        helper.succeed();
    }

    /**
     * Synthetic admission fixture: directly seed a refused stick at Bar slot 1 beside an accepted
     * ingot at slot 0, then automate extraction from slot 0. The ingot is extracted and the stick
     * moves into slot 0 without dropping. A per-run tag isolates its drops from other fixtures. The
     * test does not change policy.
     *
     * <p>To check manually, enable a data pack adding minecraft:oak_planks to the somestacks:ingots
     * item tag and run /reload. Store an ordinary ingot at bottom position 0 and oak planks at
     * position 1, then disable the data pack and /reload. Automate extraction from position 0; the
     * stored plank remains conserved despite its new refusal.
     */
    public static void backfillKeepsABarItWouldNoLongerAccept(
            GameTestHelper helper, Function<BlockEntity, Automation> automation) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        BlockPos pos = bars.getBlockPos();
        check(
                !BlockType.BAR_STACK.admits(new ItemStack(Items.STICK), null),
                "Test needs an item a Bar Stack refuses");
        bars.getItems().insertItem(0, new ItemStack(barItem), false);
        String marker = UUID.randomUUID().toString();
        ItemStack refused = GameTestScaffold.markedStack(Items.STICK, 1, marker);
        seedSlot(bars.getItems(), 1, refused.copy());

        ItemStack extracted = automation.apply(bars).extract(0, 64, false);

        checkEquals(barItem, extracted.getItem(), "Automated extracted item");
        checkEquals(1, extracted.getCount(), "Automated extraction took more than one bar");
        check(
                ItemStack.isSameItemSameTags(refused, bars.getItems().getStackInSlot(0)),
                "Backfill did not preserve the refused bar and its tags in the hole");
        checkEquals(1, bars.getItems().getStackInSlot(0).getCount(), "Backfilled bar count");
        check(bars.getItems().getStackInSlot(1).isEmpty(), "Backfill left its source occupied");
        checkEquals(1, heldAt(helper, pos, Items.STICK), "Stored refused bar count");
        checkEquals(
                0L,
                droppedInColumn(helper, pos, pos).stream()
                        .filter(entity -> GameTestScaffold.hasMarker(entity.getItem(), marker))
                        .count(),
                "Automation dropped the backfilled bar");
        helper.succeed();
    }

    /**
     * Internal extraction simulation: put one accepted ingot at Bar slot 0 and simulate extracting
     * up to 64 through the loader's automation view. The result is one ingot and the stored bar
     * remains unchanged.
     *
     * <p>No player gesture performs this dry run. Use slot-addressing automation or a probe that
     * simulates extraction without committing; the bar should remain visible.
     */
    public static void capabilitySimulationDoesNotMutateExtraction(
            GameTestHelper helper, Function<BlockEntity, Automation> automation) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        bars.depositAt(0, new ItemStack(barItem));

        ItemStack simulated = automation.apply(bars).extract(0, 64, true);

        checkEquals(barItem, simulated.getItem(), "Simulated item");
        checkEquals(1, simulated.getCount(), "Simulated count");
        checkEquals(
                barItem,
                bars.getItems().getStackInSlot(0).getItem(),
                "Simulation mutated contents");
        helper.succeed();
    }

    /**
     * Plain-clicks the Bar block at {@code pos} with an empty hand, aimed from the west through the
     * bottom layer's first row, which meets bottom position 0 first, and returns what the hand
     * received.
     */
    private static ItemStack extractFirstBarByHand(
            Function<ItemStack, ServerPlayer> factory,
            InteractionChecks.LoaderClick click,
            BlockPos pos) {
        try (InteractionChecks.PlayerScope scope =
                new InteractionChecks.PlayerScope(factory.apply(ItemStack.EMPTY))) {
            var player = scope.player;
            click.click(
                    player,
                    InteractionChecks.HAND,
                    InteractionChecks.aim(player, pos, 0.0625, 0.125));
            return player.getMainHandItem().copy();
        }
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
        CompoundTag tag = entity.getItem().getTag();
        return tag == null ? -1 : tag.getInt("somestacks_test_variant");
    }
}
