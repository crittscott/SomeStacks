package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.network.DepositPkt;
import com.github.crittscott.somestacks.network.ExtractPkt;
import com.github.crittscott.somestacks.network.PacketBoundary;
import com.github.crittscott.somestacks.network.PlaceAndDepositPkt;
import com.github.crittscott.somestacks.network.RotateBlockPkt;
import com.github.crittscott.somestacks.network.RotateItemPkt;
import com.github.crittscott.somestacks.network.TogglePermanentPkt;
import com.github.crittscott.somestacks.server.GestureThrottle;
import com.github.crittscott.somestacks.util.BlockType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.border.WorldBorder;

import java.util.function.Function;
import java.util.UUID;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.droppedNear;

/**
 * The shared packet boundary and the mutation handlers behind it: accepted entry-point dispatch,
 * gesture pacing, held-item requirements, cell bounds, and refused extraction states.
 *
 * <p>Each test is handed a {@code playerFactory} that builds a fake player holding the given main
 * hand item; the loader shell supplies it, since obtaining a fake player is loader-native.
 */
public final class PacketBoundaryChecks {
    public static final BlockPos TARGET = new BlockPos(2, 1, 2);
    public static final InteractionHand HAND = InteractionHand.MAIN_HAND;

    private PacketBoundaryChecks() {}

    public static void reachCheckAcceptsNearTargetAndRejectsFarTarget(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        BlockPos targetAbsolute = helper.absolutePos(TARGET);
        player.setPos(
                targetAbsolute.getX() + 0.5, targetAbsolute.getY(), targetAbsolute.getZ() + 0.5);

        check(PacketBoundary.withinReach(player, helper.absolutePos(TARGET)),
                "Near target was rejected");
        check(!PacketBoundary.withinReach(player, helper.absolutePos(TARGET.east(20))),
                "Far target was accepted");
        helper.succeed();
    }

    public static void gestureChecksReadOnlyTheMainHand(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
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

    public static void rotateBlockHandlerValidatesTheHeldItemAndBlockType(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, TARGET);
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, TARGET.east(3));
        BarStackBE bars = GameTestScaffold.placeBar(helper, TARGET.east(6));
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.REDSTONE_TORCH));

        try {
            placeBy(player, storage.getBlockPos());
            GestureThrottle.clear(player.getUUID());
            RotateBlockPkt.handleServer(new RotateBlockPkt(storage.getBlockPos()), player);
            checkEquals(1, storage.getRotation(), "Storage rotation");

            placeBy(player, singles.getBlockPos());
            player.setItemInHand(HAND, new ItemStack(Items.SOUL_TORCH));
            GestureThrottle.clear(player.getUUID());
            RotateBlockPkt.handleServer(new RotateBlockPkt(singles.getBlockPos()), player);
            checkEquals(0, singles.getRotation(), "Wrong torch rotated Singles");

            player.setItemInHand(HAND, new ItemStack(Items.REDSTONE_TORCH));
            RotateBlockPkt.handleServer(new RotateBlockPkt(singles.getBlockPos()), player);
            checkEquals(0, singles.getRotation(),
                    "Rejected gesture did not spend the same-tick packet allowance");

            GestureThrottle.clear(player.getUUID());
            RotateBlockPkt.handleServer(new RotateBlockPkt(singles.getBlockPos()), player);
            checkEquals(1, singles.getRotation(), "Singles rotation");

            placeBy(player, bars.getBlockPos());
            player.setItemInHand(HAND, new ItemStack(Items.REDSTONE_TORCH));
            GestureThrottle.clear(player.getUUID());
            RotateBlockPkt.handleServer(new RotateBlockPkt(bars.getBlockPos()), player);
            check(helper.getLevel().getBlockEntity(bars.getBlockPos()) == bars,
                    "Rotate-block handler changed a Bar Stack");
        } finally {
            GestureThrottle.clear(player.getUUID());
            player.setItemInHand(HAND, ItemStack.EMPTY);
        }
        helper.succeed();
    }

    public static void mutationPacketEntryPointsReachTheirValidatedOperations(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        StorageStackBE depositTarget = GameTestScaffold.placeStorage(helper, TARGET);
        StorageStackBE extractTarget = GameTestScaffold.placeStorage(helper, TARGET.east(3));
        extractTarget.getItems().insertItem(0, new ItemStack(Items.STICK, 3), false);
        BlockPos placementTarget = helper.absolutePos(TARGET.east(6));
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.STONE, 4));

        try {
            placeBy(player, depositTarget.getBlockPos());
            GestureThrottle.clear(player.getUUID());
            DepositPkt.handleServer(
                    new DepositPkt(depositTarget.getBlockPos(), depositTarget.getBlockPos()), player);
            checkEquals(4, GameTestScaffold.count(depositTarget.getItems(), Items.STONE),
                    "Deposit handler contents");

            placeBy(player, extractTarget.getBlockPos());
            player.setItemInHand(HAND, ItemStack.EMPTY);
            GestureThrottle.clear(player.getUUID());
            ExtractPkt.handleServer(new ExtractPkt(extractTarget.getBlockPos(), 0), player);
            checkEquals(Items.STICK, player.getMainHandItem().getItem(),
                    "Extract handler item");
            checkEquals(3, player.getMainHandItem().getCount(), "Extract handler count");

            placeBy(player, placementTarget);
            player.setItemInHand(HAND, new ItemStack(Items.DIRT, 2));
            GestureThrottle.clear(player.getUUID());
            PlaceAndDepositPkt.handleServer(new PlaceAndDepositPkt(
                    BlockType.STORAGE_STACK, Direction.UP, placementTarget), player);
            check(helper.getLevel().getBlockState(placementTarget)
                            .is(CommonRegistry.STORAGE_STACK_BLOCK.get()),
                    "Place-and-deposit handler did not place Storage");
            checkEquals(2, GameTestScaffold.heldAt(helper, placementTarget, Items.DIRT),
                    "Place-and-deposit handler contents");
            check(player.getMainHandItem().isEmpty(),
                    "Place-and-deposit handler left a hand remainder");
        } finally {
            GestureThrottle.clear(player.getUUID());
            player.setItemInHand(HAND, ItemStack.EMPTY);
        }
        helper.succeed();
    }

    public static void rotateItemHandlerLeavesEmptyCellsUnoriented(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        SinglesStackBE emptySingles = GameTestScaffold.placeSingles(helper, TARGET);
        SinglesStackBE occupiedSingles = GameTestScaffold.placeSingles(helper, TARGET.east(3));
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.SOUL_TORCH));
        int emptyCell = 0;

        try {
            placeBy(player, emptySingles.getBlockPos());
            GestureThrottle.clear(player.getUUID());
            RotateItemPkt.handleServer(
                    new RotateItemPkt(emptySingles.getBlockPos(), emptyCell), player);
            checkEquals(0, emptySingles.getCubeRotation(emptyCell), "Empty cell retained rotation");

            check(emptySingles.depositAt(emptyCell, new ItemStack(Items.STICK)),
                    "Singles fixture deposit failed");
            checkEquals(0, emptySingles.getCubeRotation(emptyCell),
                    "Deposit inherited an empty-cell rotation");

            check(occupiedSingles.depositAt(emptyCell, new ItemStack(Items.STICK)),
                    "Occupied Singles fixture deposit failed");
            placeBy(player, occupiedSingles.getBlockPos());
            player.setItemInHand(HAND, new ItemStack(Items.REDSTONE_TORCH));
            GestureThrottle.clear(player.getUUID());
            RotateItemPkt.handleServer(
                    new RotateItemPkt(occupiedSingles.getBlockPos(), emptyCell), player);
            checkEquals(0, occupiedSingles.getCubeRotation(emptyCell),
                    "Wrong torch rotated a Singles item");

            player.setItemInHand(HAND, new ItemStack(Items.SOUL_TORCH));
            GestureThrottle.clear(player.getUUID());
            RotateItemPkt.handleServer(
                    new RotateItemPkt(occupiedSingles.getBlockPos(), emptyCell), player);
            checkEquals(1, occupiedSingles.getCubeRotation(emptyCell), "Occupied item rotation");

            GestureThrottle.clear(player.getUUID());
            RotateItemPkt.handleServer(
                    new RotateItemPkt(occupiedSingles.getBlockPos(), SinglesStackBE.SLOTS), player);
            checkEquals(1, occupiedSingles.getCubeRotation(emptyCell),
                    "Out-of-range rotation changed an occupied item");
        } finally {
            GestureThrottle.clear(player.getUUID());
            player.setItemInHand(HAND, ItemStack.EMPTY);
        }
        helper.succeed();
    }

    public static void togglePermanentHandlerRequiresAnEmptyHandAndStoragePile(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, TARGET);
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, TARGET.east(3));
        StorageStackBE protectedStorage = GameTestScaffold.placeStorage(helper, TARGET.east(6));
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.STONE));

        try {
            placeBy(player, storage.getBlockPos());
            GestureThrottle.clear(player.getUUID());
            TogglePermanentPkt.handleServer(new TogglePermanentPkt(storage.getBlockPos()), player);
            check(!storage.pile().isPermanent(), "Occupied hand toggled permanence");

            player.setItemInHand(HAND, ItemStack.EMPTY);
            GestureThrottle.clear(player.getUUID());
            TogglePermanentPkt.handleServer(new TogglePermanentPkt(storage.getBlockPos()), player);
            check(storage.pile().isPermanent(), "Empty hand did not toggle permanence");

            placeBy(player, singles.getBlockPos());
            GestureThrottle.clear(player.getUUID());
            TogglePermanentPkt.handleServer(new TogglePermanentPkt(singles.getBlockPos()), player);
            check(storage.pile().isPermanent(), "Non-Storage target changed permanence");

            placeBy(player, protectedStorage.getBlockPos());
            GestureThrottle.clear(player.getUUID());
            outsideWorldBorder(helper, protectedStorage.getBlockPos(), () ->
                    TogglePermanentPkt.handleServer(
                            new TogglePermanentPkt(protectedStorage.getBlockPos()), player));
            check(!protectedStorage.pile().isPermanent(),
                    "Protected Storage pile changed permanence");
        } finally {
            GestureThrottle.clear(player.getUUID());
            player.setItemInHand(HAND, ItemStack.EMPTY);
        }
        helper.succeed();
    }

    public static void gestureThrottleEnforcesTickAndRotationSoundIntervals(
            GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        long tick = 100L;
        try {
            check(GestureThrottle.claimTick(id, tick), "First gesture claim was refused");
            check(!GestureThrottle.claimTick(id, tick),
                    "Second same-tick gesture claim succeeded");
            check(GestureThrottle.claimTick(id, tick + 1),
                    "Next-tick gesture claim was refused");

            check(GestureThrottle.claimRotationSound(id, tick),
                    "First rotation sound was refused");
            check(!GestureThrottle.claimRotationSound(id, tick + 1),
                    "Rotation sound succeeded after one tick");
            check(!GestureThrottle.claimRotationSound(id, tick + 3),
                    "Rotation sound succeeded after three ticks");
            check(GestureThrottle.claimRotationSound(id, tick + 4),
                    "Rotation sound was refused after four ticks");

            GestureThrottle.clear(id);
            check(GestureThrottle.claimTick(id, tick), "Clear did not reset gesture claims");
            check(GestureThrottle.claimRotationSound(id, tick),
                    "Clear did not reset rotation-sound claims");
        } finally {
            GestureThrottle.clear(id);
        }
        helper.succeed();
    }

    /**
     * Extraction is the one mutation packet that acts on a cell the client chose rather than one
     * the server recomputes, so the per-type handler checks are what stand between a client
     * integer and the stored contents. The extraction tests below drive those handlers directly
     * with an index or a hand the gesture could never have produced.
     */
    public static void extractIgnoresAnEmptyCell(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerLevel level = helper.getLevel();
        Fixture fixture = new Fixture(helper);
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
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

    public static void extractRefusesAHandHoldingSomethingElse(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerLevel level = helper.getLevel();
        Fixture fixture = new Fixture(helper);
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.DIRT, 1));

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

    public static void extractRefusesAFullHandOfTheSameItem(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerLevel level = helper.getLevel();
        Fixture fixture = new Fixture(helper);

        ItemStack stone = fullStack(Items.STONE);
        ServerPlayer player = playerFactory.apply(stone);
        ExtractPkt.handleStorageExtract(
                level, fixture.storage.getBlockPos(), player, HAND, fixture.storage, 0);
        checkEquals(stone.getMaxStackSize(), player.getMainHandItem().getCount(),
                "Full hand grew past its stack size");

        ItemStack sticks = fullStack(Items.STICK);
        player = playerFactory.apply(sticks);
        ExtractPkt.handleSinglesExtract(
                level, fixture.singles.getBlockPos(), player, HAND, fixture.singles, 0);
        checkEquals(sticks.getMaxStackSize(), player.getMainHandItem().getCount(),
                "Full hand grew past its stack size");

        ItemStack bars = fullStack(fixture.barItem);
        player = playerFactory.apply(bars);
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
    public static void refusedBarExtractionLeavesTheBlockStanding(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerLevel level = helper.getLevel();
        BarStackBE bar = GameTestScaffold.placeBar(helper, TARGET);
        Item barItem = GameTestScaffold.firstBarItem();
        check(bar.depositAt(0, new ItemStack(barItem)), "Bar deposit failed");
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.DIRT, 1));

        ExtractPkt.handleBarExtract(level, bar.getBlockPos(), player, HAND, bar, 0);

        checkEquals(barItem, bar.getItems().getStackInSlot(0).getItem(),
                "Refused extraction removed the bar");
        check(level.getBlockEntity(bar.getBlockPos()) instanceof BarStackBE,
                "Refused extraction emptied and removed the block");
        checkEquals(0, droppedNear(helper, bar.getBlockPos(), barItem),
                "Refused extraction dropped a bar");
        helper.succeed();
    }

    /**
     * Each handler bounds the index against its own type's slot count, so Storage refuses indices
     * the two 64-slot types accept, and no int reaches a slot lookup that would reject it.
     */
    public static void extractIgnoresAnIndexOutsideTheBlocksOwnSlots(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerLevel level = helper.getLevel();
        Fixture fixture = new Fixture(helper);
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
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
            storage = GameTestScaffold.placeStorage(helper, TARGET);
            singles = GameTestScaffold.placeSingles(helper, TARGET.east(3));
            bar = GameTestScaffold.placeBar(helper, TARGET.east(6));
            barItem = GameTestScaffold.firstBarItem();
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
            checkEquals(0, droppedNear(helper, singles.getBlockPos(), Items.STICK),
                    "Singles dropped its item");
            checkEquals(0, droppedNear(helper, bar.getBlockPos(), barItem),
                    "Bar dropped its item");
        }
    }

    private static ItemStack fullStack(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.setCount(stack.getMaxStackSize());
        return stack;
    }

    private static void placeBy(ServerPlayer player, BlockPos pos) {
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    private static void outsideWorldBorder(
            GameTestHelper helper, BlockPos target, Runnable action) {
        WorldBorder border = helper.getLevel().getWorldBorder();
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();
        double size = border.getSize();
        try {
            border.setCenter(target.getX() + 1000.0, target.getZ());
            border.setSize(16.0);
            check(!border.isWithinBounds(target),
                    "Test setup left protected target inside the world border");
            action.run();
        } finally {
            border.setCenter(centerX, centerZ);
            border.setSize(size);
        }
    }
}
