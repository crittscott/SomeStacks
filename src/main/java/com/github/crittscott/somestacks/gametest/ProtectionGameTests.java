package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.StorageStackBlock;
import com.github.crittscott.somestacks.network.DepositPkt;
import com.github.crittscott.somestacks.network.PlaceAndDepositPkt;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.util.BlockType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.function.Consumer;

import static com.github.crittscott.somestacks.gametest.GameTestSupport.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.check;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.checkEquals;

@GameTestHolder(SomeStacks.MODID)
@PrefixGameTestTemplate(false)
public final class ProtectionGameTests {
    private ProtectionGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        BlockPos valid = helper.absolutePos(ORIGIN);
        BlockPos invalid = new BlockPos(
                valid.getX(), level.getMinBuildHeight() - 1, valid.getZ());

        check(Protection.placeChecked(
                        player, level, valid, Blocks.STONE.defaultBlockState(), Direction.DOWN),
                "Valid checked placement was rejected");
        check(level.getBlockState(valid).is(Blocks.STONE),
                "Valid checked placement did not change the world");
        check(!Protection.placeChecked(
                        player, level, invalid, Blocks.STONE.defaultBlockState(), Direction.DOWN),
                "Out-of-height checked placement was accepted");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void checkedPlacementRejectsAnObstructingEntity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        BlockPos target = helper.absolutePos(ORIGIN);
        putCowIn(helper, ORIGIN);

        check(!Protection.placeChecked(
                        player, level, target, Blocks.STONE.defaultBlockState(), Direction.DOWN),
                "Entity-obstructed checked placement was accepted");
        helper.assertBlockNotPresent(Blocks.STONE, ORIGIN);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void sameTickSuppressionVetoesOnlyTheMarkedPosition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        BlockPos marked = helper.absolutePos(ORIGIN);
        BlockPos other = marked.east();

        check(Protection.claimInteraction(player, marked, marked),
                "The claim itself was refused");

        check(!Protection.mayInteract(player, marked),
                "Marked same-tick interaction was not suppressed");
        check(Protection.mayInteract(player, other),
                "Different position was suppressed");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = 20)
    public static void suppressionExpiresOnTheNextTick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        BlockPos marked = helper.absolutePos(ORIGIN);

        check(Protection.claimInteraction(player, marked, marked),
                "The claim itself was refused");
        helper.runAfterDelay(1, () -> {
            check(Protection.mayInteract(player, marked),
                    "Expired suppression still vetoed interaction");
            helper.succeed();
        });
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void depositSuppressesTheClickedBlockAndStillDeposits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        BlockPos target = helper.absolutePos(ORIGIN);
        BlockPos clicked = target.north();
        GameTestSupport.placeStorage(helper, ORIGIN);

        withMainHand(player, new ItemStack(Items.DIRT, 64), () ->
                DepositPkt.apply(player, new DepositPkt(target, clicked)));

        checkEquals(64, GameTestSupport.heldAt(helper, target, Items.DIRT),
                "Deposit did not reach the stack");
        check(!Protection.mayInteract(player, clicked),
                "The clicked block was left open to the vanilla interaction");
        check(Protection.mayInteract(player, target),
                "The deposit target was suppressed instead of the clicked block");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void placementSuppressesTheClickedBlockAndStillPlaces(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        BlockPos target = helper.absolutePos(ORIGIN);
        BlockPos clicked = target.below();

        withMainHand(player, new ItemStack(Items.DIRT, 64), () ->
                PlaceAndDepositPkt.apply(player, new PlaceAndDepositPkt(
                        BlockType.STORAGE_STACK, Direction.UP, target)));

        helper.assertBlockPresent(ModRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN);
        check(!Protection.mayInteract(player, clicked),
                "The clicked block was left open to the vanilla interaction");
        check(Protection.mayInteract(player, target),
                "The placed position was suppressed instead of the clicked block");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void depositConsultsTheAdjacentBlockThatWasActuallyClicked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        BlockPos target = helper.absolutePos(ORIGIN);
        BlockPos clicked = target.north();
        GameTestSupport.placeStorage(helper, ORIGIN);

        // The stack itself is open; the block the player put their cursor on is not. The deposit
        // reaches the stack through that click, so refusing the click refuses the deposit.
        Consumer<PlayerInteractEvent.RightClickBlock> denyClicked = event -> {
            if (event.getEntity() == player && event.getPos().equals(clicked)) {
                event.setUseBlock(Event.Result.DENY);
            }
        };

        MinecraftForge.EVENT_BUS.addListener(denyClicked);
        try {
            withMainHand(player, new ItemStack(Items.DIRT, 64), () ->
                    DepositPkt.apply(player, new DepositPkt(target, clicked)));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(denyClicked);
        }

        checkEquals(0, GameTestSupport.heldAt(helper, target, Items.DIRT),
                "Deposit ran despite the clicked block being denied");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void creativeDepositFillsTheStackWithoutSpendingTheHand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        BlockPos target = helper.absolutePos(ORIGIN);
        GameTestSupport.placeStorage(helper, ORIGIN);

        player.getAbilities().instabuild = true;
        try {
            withMainHand(player, new ItemStack(Items.DIRT, 64), () -> {
                DepositPkt.apply(player, new DepositPkt(target, target));
                checkEquals(64, player.getMainHandItem().getCount(),
                        "A creative deposit spent the held stack");
            });
        } finally {
            player.getAbilities().instabuild = false;
        }

        checkEquals(64, GameTestSupport.heldAt(helper, target, Items.DIRT),
                "Creative deposit did not reach the stack");
        helper.succeed();
    }

    // Mod-driven removal under protection
    //
    // A settle or a collapse takes down the blocks it empties, and that is a world edit with no
    // actor left to ask, so it answers to the level's fake player exactly as growth does. A refusal
    // has to leave the block standing without leaving the run's own model out of step with it.

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void settleKeepsAnEmptyTopBlockWhoseRemovalIsRefused(GameTestHelper helper) {
        BlockPos topRelative = ORIGIN.above();
        StorageStackBE base = GameTestSupport.placeStorage(helper, ORIGIN);
        GameTestSupport.placeStorage(helper, topRelative);
        base.getItems().insertItem(0, new ItemStack(Items.DIRT, 1), false);

        BlockPos top = helper.absolutePos(topRelative);
        Consumer<BlockEvent.BreakEvent> denyTop = event -> {
            if (event.getPos().equals(top)) {
                event.setCanceled(true);
            }
        };

        MinecraftForge.EVENT_BUS.addListener(denyTop);
        try {
            StoragePile pile = base.pile();
            check(pile != null, "Pile did not resolve");
            pile.settle();
            helper.assertBlockPresent(ModRegistry.STORAGE_STACK_BLOCK.get(), topRelative);
            checkEquals(2, pile.height(), "The pile dropped a block it never removed");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(denyTop);
        }

        // With nothing refusing it, the same settle takes the block down.
        StoragePile pile = base.pile();
        check(pile != null, "Pile did not resolve after the refusal");
        pile.settle();
        helper.assertBlockNotPresent(ModRegistry.STORAGE_STACK_BLOCK.get(), topRelative);
        helper.succeed();
    }

    // Waterlogging

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void placementIntoWaterKeepsTheWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        BlockPos target = helper.absolutePos(ORIGIN);
        level.setBlock(target, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);

        withMainHand(player, new ItemStack(Items.DIRT, 64), () ->
                PlaceAndDepositPkt.apply(player, new PlaceAndDepositPkt(
                        BlockType.STORAGE_STACK, Direction.UP, target)));

        helper.assertBlockPresent(ModRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN);
        check(level.getBlockState(target).getValue(StorageStackBlock.WATERLOGGED),
                "A stack placed into water was not waterlogged");
        check(level.getFluidState(target).getType() == Fluids.WATER,
                "A stack placed into water swallowed the water");
        helper.succeed();
    }

    /**
     * Runs {@code action} with {@code stack} held, and empties the hand again before returning.
     * The fake player is shared across tests, so what it carries has to be put back.
     */
    private static void withMainHand(ServerPlayer player, ItemStack stack, Runnable action) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            action.run();
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void placementHonorsUseItemDenyWithoutChangingBlockAccess(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        BlockPos clicked = helper.absolutePos(ORIGIN);
        Consumer<PlayerInteractEvent.RightClickBlock> denyItem = event -> {
            if (event.getEntity() == player && event.getPos().equals(clicked)) {
                event.setUseItem(Event.Result.DENY);
            }
        };

        MinecraftForge.EVENT_BUS.addListener(denyItem);
        try {
            check(Protection.mayInteract(player, clicked),
                    "Item-use denial incorrectly vetoed block access");
            check(!Protection.mayPlaceAgainst(player, clicked),
                    "Item-use denial did not veto placement");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(denyItem);
        }
        helper.succeed();
    }

    // Growth under protection
    //
    // A capability insertion aimed at a slot past what the run holds is the one that grows it, and it
    // weighs the position above the run before promising the caller anything, so a simulation and the
    // commit that follows agree about a position growth cannot have. The three tests below stage that
    // with the world border. Spawn protection, the other half of the same predicate, cannot be staged
    // here: it is implemented on DedicatedServer, and the server running these tests is not one.

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void storageGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        StorageStackBE storage = GameTestSupport.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
        }
        IItemHandler capability = GameTestSupport.capability(storage);
        ItemStack offered = new ItemStack(Items.STONE, 4);

        int headroom = StorageStackBE.SLOTS;
        check(capability.insertItem(headroom, offered, true).isEmpty(),
                "A full pile with free headroom did not credit growth");

        outsideTheBorder(helper, () -> {
            checkEquals(4, capability.insertItem(headroom, offered, true).getCount(),
                    "Simulated remainder");
            checkEquals(4, capability.insertItem(headroom, offered, false).getCount(),
                    "Committed remainder");
        });

        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                ModRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void singlesGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        SinglesStackBE singles = GameTestSupport.placeSingles(helper, ORIGIN);
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            singles.getItems().insertItem(slot, new ItemStack(Items.STONE, 1), false);
        }
        IItemHandler capability = GameTestSupport.capability(singles);
        ItemStack offered = new ItemStack(Items.STONE, 4);

        // A cell takes one item, so a credited growth leaves three of the four behind.
        int headroom = SinglesStackBE.SLOTS;
        checkEquals(3, capability.insertItem(headroom, offered, true).getCount(),
                "A full column with free headroom did not credit growth");

        outsideTheBorder(helper, () -> {
            checkEquals(4, capability.insertItem(headroom, offered, true).getCount(),
                    "Simulated remainder");
            checkEquals(4, capability.insertItem(headroom, offered, false).getCount(),
                    "Committed remainder");
        });

        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                ModRegistry.SINGLES_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void barGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        BarStackBE bars = GameTestSupport.placeBar(helper, ORIGIN);
        Item bar = GameTestSupport.firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            bars.getItems().insertItem(slot, new ItemStack(bar, 1), false);
        }
        IItemHandler capability = GameTestSupport.capability(bars);
        ItemStack offered = new ItemStack(bar, 4);

        // A position takes one bar, so a credited growth leaves three of the four behind.
        int headroom = BarStackBE.SLOTS;
        checkEquals(3, capability.insertItem(headroom, offered, true).getCount(),
                "A full column with free headroom did not credit growth");

        outsideTheBorder(helper, () -> {
            checkEquals(4, capability.insertItem(headroom, offered, true).getCount(),
                    "Simulated remainder");
            checkEquals(4, capability.insertItem(headroom, offered, false).getCount(),
                    "Committed remainder");
        });

        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                ModRegistry.BAR_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    // Growth through entities

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void storageGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        StorageStackBE storage = GameTestSupport.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            storage.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
        }
        IItemHandler capability = GameTestSupport.capability(storage);
        ItemStack offered = new ItemStack(Items.STONE, 4);
        putCowIn(helper, ORIGIN.above());

        int headroom = StorageStackBE.SLOTS;
        checkEquals(4, capability.insertItem(headroom, offered, true).getCount(),
                "Simulated remainder");
        checkEquals(4, capability.insertItem(headroom, offered, false).getCount(),
                "Committed remainder");
        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                ModRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void singlesGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        SinglesStackBE singles = GameTestSupport.placeSingles(helper, ORIGIN);
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            singles.getItems().insertItem(slot, new ItemStack(Items.STONE), false);
        }
        IItemHandler capability = GameTestSupport.capability(singles);
        ItemStack offered = new ItemStack(Items.STONE, 4);
        putCowIn(helper, ORIGIN.above());

        int headroom = SinglesStackBE.SLOTS;
        checkEquals(4, capability.insertItem(headroom, offered, true).getCount(),
                "Simulated remainder");
        checkEquals(4, capability.insertItem(headroom, offered, false).getCount(),
                "Committed remainder");
        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                ModRegistry.SINGLES_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void barGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        BarStackBE bars = GameTestSupport.placeBar(helper, ORIGIN);
        Item bar = GameTestSupport.firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            bars.getItems().insertItem(slot, new ItemStack(bar), false);
        }
        IItemHandler capability = GameTestSupport.capability(bars);
        ItemStack offered = new ItemStack(bar, 4);
        putCowIn(helper, ORIGIN.above());

        int headroom = BarStackBE.SLOTS;
        checkEquals(4, capability.insertItem(headroom, offered, true).getCount(),
                "Simulated remainder");
        checkEquals(4, capability.insertItem(headroom, offered, false).getCount(),
                "Committed remainder");
        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                ModRegistry.BAR_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    /**
     * Puts a living placement-blocking entity across the bottom north-west cell and bar position
     * of {@code relative}.
     */
    private static void putCowIn(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(relative);
        Cow cow = EntityType.COW.create(level);
        check(cow != null, "Could not create obstruction cow");
        check(cow.blocksBuilding, "Cow does not block building");
        cow.moveTo(
                target.getX() + 0.5,
                target.getY(),
                target.getZ() + 0.5,
                0.0F,
                0.0F);
        check(level.addFreshEntity(cow), "Could not add obstruction cow");
    }

    /**
     * Runs {@code action} with the world border moved off the test structure, and restores it
     * before returning. The border is level-wide state, but the move and the restore both happen
     * inside this one synchronous call, so no other test observes it moved.
     */
    private static void outsideTheBorder(GameTestHelper helper, Runnable action) {
        WorldBorder border = helper.getLevel().getWorldBorder();
        BlockPos above = helper.absolutePos(ORIGIN.above());
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();
        double size = border.getSize();
        try {
            border.setCenter(above.getX() + 1000.0, above.getZ());
            border.setSize(16.0);
            check(!border.isWithinBounds(above),
                    "Test setup left the position inside the world border");
            action.run();
        } finally {
            border.setCenter(centerX, centerZ);
            border.setSize(size);
        }
    }
}
