package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.gametest.GameTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.github.crittscott.somestacks.gametest.GameTestSupport.check;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.checkEquals;

@GameTestHolder(SomeStacks.MODID)
@PrefixGameTestTemplate(false)
public final class PacketBoundaryGameTests {
    private static final String TEMPLATE = "somestacks_empty";
    private static final BlockPos TARGET = new BlockPos(2, 1, 2);
    private static final InteractionHand HAND = InteractionHand.MAIN_HAND;

    private PacketBoundaryGameTests() {}

    @GameTest(template = TEMPLATE)
    public static void reachCheckAcceptsNearTargetAndRejectsFarTarget(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        Vec3 targetCenter = Vec3.atCenterOf(helper.absolutePos(TARGET));
        player.setPos(targetCenter.x, targetCenter.y, targetCenter.z);

        check(PacketBoundary.withinReach(player, helper.absolutePos(TARGET)),
                "Near target was rejected");
        check(!PacketBoundary.withinReach(player, helper.absolutePos(TARGET.east(20))),
                "Far target was accepted");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void gestureChecksReadOnlyTheMainHand(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE));

        check(PacketBoundary.mainHandEmpty(player),
                "Off-hand item made main hand nonempty");
        check(!PacketBoundary.holdsInMainHand(player, Items.STONE),
                "Off-hand item satisfied main-hand check");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        check(!PacketBoundary.mainHandEmpty(player), "Occupied main hand reported empty");
        check(PacketBoundary.holdsInMainHand(player, Items.STONE),
                "Held main-hand item was rejected");
        check(!PacketBoundary.holdsInMainHand(player, Items.DIRT),
                "Wrong main-hand item was accepted");
        helper.succeed();
    }

    /**
     * Extraction is the one mutation packet that acts on a cell the client chose rather than one
     * the server recomputes, so the per-type handler checks are what stand between a client
     * integer and the stored contents. The extraction tests below drive those handlers directly
     * with an index or a hand the gesture could never have produced.
     */
    @GameTest(template = TEMPLATE)
    public static void extractIgnoresAnEmptyCell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Fixture fixture = new Fixture(helper);
        ServerPlayer player = player(level, ItemStack.EMPTY);
        int emptyCell = 5;

        ExtractPkt.handleStorageExtract(
                level, fixture.storage.getBlockPos(), player, HAND, fixture.storage, emptyCell);
        ExtractPkt.handleSinglesExtract(
                level, fixture.singles.getBlockPos(), player, HAND, fixture.singles, emptyCell);
        ExtractPkt.handleBarExtract(
                level, fixture.bar.getBlockPos(), player, HAND, fixture.bar, emptyCell);

        check(player.getMainHandItem().isEmpty(), "An empty cell yielded an item");
        fixture.checkUntouched(helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void extractRefusesAHandHoldingSomethingElse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Fixture fixture = new Fixture(helper);
        ServerPlayer player = player(level, new ItemStack(Items.DIRT, 1));

        ExtractPkt.handleStorageExtract(
                level, fixture.storage.getBlockPos(), player, HAND, fixture.storage, 0);
        ExtractPkt.handleSinglesExtract(
                level, fixture.singles.getBlockPos(), player, HAND, fixture.singles, 0);
        ExtractPkt.handleBarExtract(
                level, fixture.bar.getBlockPos(), player, HAND, fixture.bar, 0);

        checkEquals(Items.DIRT, player.getMainHandItem().getItem(), "Held item was replaced");
        checkEquals(1, player.getMainHandItem().getCount(), "Held count changed");
        fixture.checkUntouched(helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void extractRefusesAFullHandOfTheSameItem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Fixture fixture = new Fixture(helper);

        ItemStack stone = fullStack(Items.STONE);
        ServerPlayer player = player(level, stone);
        ExtractPkt.handleStorageExtract(
                level, fixture.storage.getBlockPos(), player, HAND, fixture.storage, 0);
        checkEquals(stone.getMaxStackSize(), player.getMainHandItem().getCount(),
                "Full hand grew past its stack size");

        ItemStack sticks = fullStack(Items.STICK);
        player = player(level, sticks);
        ExtractPkt.handleSinglesExtract(
                level, fixture.singles.getBlockPos(), player, HAND, fixture.singles, 0);
        checkEquals(sticks.getMaxStackSize(), player.getMainHandItem().getCount(),
                "Full hand grew past its stack size");

        ItemStack bars = fullStack(fixture.barItem);
        player = player(level, bars);
        ExtractPkt.handleBarExtract(
                level, fixture.bar.getBlockPos(), player, HAND, fixture.bar, 0);
        checkEquals(bars.getMaxStackSize(), player.getMainHandItem().getCount(),
                "Full hand grew past its stack size");

        fixture.checkUntouched(helper);
        helper.succeed();
    }

    /**
     * A refused Bar extraction must also leave the support cascade unrun. Taking the bar would
     * empty the block, which removes itself and drops what it held, so the block still standing
     * with its bar in place is the whole of the guarantee.
     */
    @GameTest(template = TEMPLATE)
    public static void refusedBarExtractionLeavesTheBlockStanding(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BarStackBE bar = GameTestSupport.placeBar(helper, TARGET);
        Item barItem = GameTestSupport.firstBarItem();
        check(bar.depositAt(0, new ItemStack(barItem)), "Bar deposit failed");
        ServerPlayer player = player(level, new ItemStack(Items.DIRT, 1));

        ExtractPkt.handleBarExtract(level, bar.getBlockPos(), player, HAND, bar, 0);

        checkEquals(barItem, bar.getItems().getStackInSlot(0).getItem(),
                "Refused extraction removed the bar");
        check(level.getBlockEntity(bar.getBlockPos()) instanceof BarStackBE,
                "Refused extraction emptied and removed the block");
        checkEquals(0, droppedCount(helper, bar.getBlockPos(), barItem),
                "Refused extraction dropped a bar");
        helper.succeed();
    }

    /**
     * Each handler bounds the index against its own type's slot count, so Storage refuses indices
     * the two 64-slot types accept, and no int reaches a slot lookup that would reject it.
     */
    @GameTest(template = TEMPLATE)
    public static void extractIgnoresAnIndexOutsideTheBlocksOwnSlots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Fixture fixture = new Fixture(helper);
        ServerPlayer player = player(level, ItemStack.EMPTY);
        int[] neverACell = {-1, Integer.MIN_VALUE, Integer.MAX_VALUE};

        for (int index : neverACell) {
            ExtractPkt.handleStorageExtract(
                    level, fixture.storage.getBlockPos(), player, HAND, fixture.storage, index);
            ExtractPkt.handleSinglesExtract(
                    level, fixture.singles.getBlockPos(), player, HAND, fixture.singles, index);
            ExtractPkt.handleBarExtract(
                    level, fixture.bar.getBlockPos(), player, HAND, fixture.bar, index);
        }

        ExtractPkt.handleStorageExtract(level, fixture.storage.getBlockPos(), player, HAND,
                fixture.storage, StorageStackBE.SLOTS);
        ExtractPkt.handleStorageExtract(level, fixture.storage.getBlockPos(), player, HAND,
                fixture.storage, SinglesStackBE.SLOTS - 1);
        ExtractPkt.handleSinglesExtract(level, fixture.singles.getBlockPos(), player, HAND,
                fixture.singles, SinglesStackBE.SLOTS);
        ExtractPkt.handleBarExtract(level, fixture.bar.getBlockPos(), player, HAND,
                fixture.bar, BarStackBE.SLOTS);

        check(player.getMainHandItem().isEmpty(), "An out-of-range index yielded an item");
        fixture.checkUntouched(helper);
        helper.succeed();
    }

    /** One block of each type, each holding a single known item in its first cell. */
    private static final class Fixture {
        private final StorageStackBE storage;
        private final SinglesStackBE singles;
        private final BarStackBE bar;
        private final Item barItem;

        private Fixture(GameTestHelper helper) {
            storage = GameTestSupport.placeStorage(helper, TARGET);
            singles = GameTestSupport.placeSingles(helper, TARGET.east(3));
            bar = GameTestSupport.placeBar(helper, TARGET.east(6));
            barItem = GameTestSupport.firstBarItem();
            storage.getItems().insertItem(0, new ItemStack(Items.STONE, 8), false);
            singles.getItems().insertItem(0, new ItemStack(Items.STICK), false);
            check(bar.depositAt(0, new ItemStack(barItem)), "Bar fixture deposit failed");
        }

        private void checkUntouched(GameTestHelper helper) {
            checkEquals(8, storage.getItems().getStackInSlot(0).getCount(),
                    "Storage contents changed");
            checkEquals(Items.STICK, singles.getItems().getStackInSlot(0).getItem(),
                    "Singles contents changed");
            checkEquals(barItem, bar.getItems().getStackInSlot(0).getItem(),
                    "Bar contents changed");
            checkEquals(0, droppedCount(helper, singles.getBlockPos(), Items.STICK),
                    "Singles dropped its item");
            checkEquals(0, droppedCount(helper, bar.getBlockPos(), barItem),
                    "Bar dropped its item");
        }
    }

    private static ItemStack fullStack(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.setCount(stack.getMaxStackSize());
        return stack;
    }

    /** The fake player is shared per level, so every test sets the hand it means to test with. */
    private static ServerPlayer player(ServerLevel level, ItemStack mainHand) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        player.setItemInHand(HAND, mainHand);
        return player;
    }

    private static int droppedCount(GameTestHelper helper, BlockPos center, Item item) {
        return helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(2.0))
                .stream()
                .filter(entity -> entity.getItem().is(item))
                .mapToInt(entity -> entity.getItem().getCount())
                .sum();
    }
}
