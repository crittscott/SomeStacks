package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.WorldEdits;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/**
 * The rules that keep a gesture from writing where it should not: build height, entity obstruction,
 * destination consultation, actor-preserving cleanup, and growth checks in simulation and commit.
 */
public final class ProtectionGameTests implements FabricGameTest {
    private static final Map<TestTarget, PlacementProbe> PLACEMENT_PROBES =
            new ConcurrentHashMap<>();
    private static final Map<TestTarget, AtomicReference<Player>> REMOVAL_PROBES =
            new ConcurrentHashMap<>();

    static {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.PASS;
            }
            PlacementProbe probe = PLACEMENT_PROBES.get(
                    new TestTarget(serverLevel, hit.getBlockPos()));
            if (probe == null) {
                return InteractionResult.PASS;
            }
            probe.invoked().set(true);
            probe.sawAir().set(level.getBlockState(hit.getBlockPos()).isAir());
            return probe.result();
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            AtomicReference<Player> actor = REMOVAL_PROBES.get(new TestTarget(level, pos));
            if (actor != null) {
                actor.set(player);
            }
            return true;
        });
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void automationUsesSharedIdentity(GameTestHelper helper) {
        ProtectionChecks.automationUsesSharedIdentity(helper);
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void automationUsesFabricFakePlayer(GameTestHelper helper) {
        check(WorldEdits.automationActor(helper.getLevel()) instanceof FakePlayer,
                "Fabric automation actor was not a Fabric API FakePlayer");
        helper.succeed();
    }

    /** In game, click outside a claim toward its interior; Fabric must deny before placing there. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void destinationConsultationRunsBeforePlacement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayer.get(level);
        BlockPos target = helper.absolutePos(ORIGIN);
        TestTarget key = new TestTarget(level, target);
        PlacementProbe probe = new PlacementProbe(
                InteractionResult.SUCCESS, new AtomicBoolean(), new AtomicBoolean());
        PLACEMENT_PROBES.put(key, probe);
        try {
            check(!WorldEdits.placeChecked(
                            player, level, target, Blocks.STONE.defaultBlockState(), Direction.DOWN),
                    "A handled destination consultation permitted placement");
        } finally {
            PLACEMENT_PROBES.remove(key);
        }

        check(probe.invoked().get(), "Fabric did not consult the placement destination");
        check(probe.sawAir().get(), "Fabric consulted protection only after placement");
        helper.assertBlockNotPresent(Blocks.STONE, ORIGIN);
        helper.succeed();
    }

    /** In game, allow a player but deny [SomeStacks] in a claim, then extract the last Single. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerExtractionUsesPlayerForCleanup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        singles.getItems().insertItem(0, new ItemStack(Items.STONE), false);
        ServerPlayer player = FakePlayer.get(level);
        BlockPos target = helper.absolutePos(ORIGIN);
        TestTarget key = new TestTarget(level, target);
        AtomicReference<Player> observedActor = new AtomicReference<>();
        REMOVAL_PROBES.put(key, observedActor);
        try {
            check(!singles.extractAt(0, player).isEmpty(), "Player extraction returned nothing");
        } finally {
            REMOVAL_PROBES.remove(key);
        }

        check(observedActor.get() == player, "Cleanup break event did not carry the player");
        helper.assertBlockNotPresent(CommonRegistry.SINGLES_STACK_BLOCK.get(), ORIGIN);
        helper.succeed();
    }

    /** In game, allow a player but deny [SomeStacks], then extract the last item from Storage. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerStorageSettlementUsesPlayerForCleanup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, ORIGIN);
        storage.getItems().insertItem(0, new ItemStack(Items.STONE), false);
        ServerPlayer player = FakePlayer.get(level);
        BlockPos target = helper.absolutePos(ORIGIN);
        TestTarget key = new TestTarget(level, target);
        AtomicReference<Player> observedActor = new AtomicReference<>();
        REMOVAL_PROBES.put(key, observedActor);
        try {
            check(!storage.extractAt(0, 64, ItemStack.EMPTY, player).isEmpty(),
                    "Player extraction returned nothing");
            StoragePile pile = storage.pile();
            check(pile != null, "Pile did not resolve before settlement");
            pile.settle();
        } finally {
            REMOVAL_PROBES.remove(key);
        }

        check(observedActor.get() == player, "Deferred cleanup did not retain the player");
        helper.assertBlockNotPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN);
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(GameTestHelper helper) {
        ProtectionChecks.checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(
                helper, playerFactory(helper));
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void checkedPlacementRejectsAnObstructingEntity(GameTestHelper helper) {
        ProtectionChecks.checkedPlacementRejectsAnObstructingEntity(helper, playerFactory(helper));
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void creativeDepositFillsTheStackWithoutSpendingTheHand(GameTestHelper helper) {
        ProtectionChecks.creativeDepositFillsTheStackWithoutSpendingTheHand(
                helper, playerFactory(helper));
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void placementIntoWaterKeepsTheWater(GameTestHelper helper) {
        ProtectionChecks.placementIntoWaterKeepsTheWater(helper, playerFactory(helper));
    }

    private static Function<ItemStack, ServerPlayer> playerFactory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return stack -> {
            ServerPlayer player = FakePlayer.get(level);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            return player;
        };
    }

    private record TestTarget(Level level, BlockPos pos) {}

    private record PlacementProbe(
            InteractionResult result, AtomicBoolean invoked, AtomicBoolean sawAir) {}

    // Growth under protection
    //
    // A storage insertion aimed at a slot past what the run holds is the one that grows it, and it
    // checks the position above the run before promising the caller anything, so a simulation and the
    // commit that follows agree about a position growth cannot have. The three tests below stage that
    // with the world border. Spawn protection, the other half of the same predicate, cannot be staged
    // here: it is implemented on DedicatedServer, and the server running these tests is not one.

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void storageGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        StorageStackBE blockEntity = GameTestScaffold.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            blockEntity.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
        }
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(blockEntity);
        ItemStack offered = new ItemStack(Items.STONE, 4);

        int headroom = StorageStackBE.SLOTS;
        check(FabricGameTestSupport.insertAt(storage, headroom, offered, true).isEmpty(),
                "A full pile with free headroom did not credit growth");

        outsideTheBorder(helper, () -> {
            checkEquals(4, FabricGameTestSupport.insertAt(storage, headroom, offered, true).getCount(),
                    "Simulated remainder");
            checkEquals(4, FabricGameTestSupport.insertAt(storage, headroom, offered, false).getCount(),
                    "Committed remainder");
        });

        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void singlesGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            singles.getItems().insertItem(slot, new ItemStack(Items.STONE, 1), false);
        }
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(singles);
        ItemStack offered = new ItemStack(Items.STONE, 4);

        // A cell takes one item, so a credited growth leaves three of the four behind.
        int headroom = SinglesStackBE.SLOTS;
        checkEquals(3, FabricGameTestSupport.insertAt(storage, headroom, offered, true).getCount(),
                "A full column with free headroom did not credit growth");

        outsideTheBorder(helper, () -> {
            checkEquals(4, FabricGameTestSupport.insertAt(storage, headroom, offered, true).getCount(),
                    "Simulated remainder");
            checkEquals(4, FabricGameTestSupport.insertAt(storage, headroom, offered, false).getCount(),
                    "Committed remainder");
        });

        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.SINGLES_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void barGrowthAnswersToProtectionInSimulationAndCommit(GameTestHelper helper) {
        BarStackBE bars = GameTestScaffold.placeBar(helper, ORIGIN);
        Item bar = GameTestScaffold.firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            bars.getItems().insertItem(slot, new ItemStack(bar, 1), false);
        }
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(bars);
        ItemStack offered = new ItemStack(bar, 4);

        // A position takes one bar, so a credited growth leaves three of the four behind.
        int headroom = BarStackBE.SLOTS;
        checkEquals(3, FabricGameTestSupport.insertAt(storage, headroom, offered, true).getCount(),
                "A full column with free headroom did not credit growth");

        outsideTheBorder(helper, () -> {
            checkEquals(4, FabricGameTestSupport.insertAt(storage, headroom, offered, true).getCount(),
                    "Simulated remainder");
            checkEquals(4, FabricGameTestSupport.insertAt(storage, headroom, offered, false).getCount(),
                    "Committed remainder");
        });

        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.BAR_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    // Growth through entities

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void storageGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        StorageStackBE blockEntity = GameTestScaffold.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            blockEntity.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
        }
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(blockEntity);
        ItemStack offered = new ItemStack(Items.STONE, 4);
        putCowIn(helper, ORIGIN.above());

        int headroom = StorageStackBE.SLOTS;
        checkEquals(4, FabricGameTestSupport.insertAt(storage, headroom, offered, true).getCount(),
                "Simulated remainder");
        checkEquals(4, FabricGameTestSupport.insertAt(storage, headroom, offered, false).getCount(),
                "Committed remainder");
        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void singlesGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            singles.getItems().insertItem(slot, new ItemStack(Items.STONE), false);
        }
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(singles);
        ItemStack offered = new ItemStack(Items.STONE, 4);
        putCowIn(helper, ORIGIN.above());

        int headroom = SinglesStackBE.SLOTS;
        checkEquals(4, FabricGameTestSupport.insertAt(storage, headroom, offered, true).getCount(),
                "Simulated remainder");
        checkEquals(4, FabricGameTestSupport.insertAt(storage, headroom, offered, false).getCount(),
                "Committed remainder");
        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.SINGLES_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void barGrowthRejectsAnObstructingEntityInSimulationAndCommit(GameTestHelper helper) {
        BarStackBE bars = GameTestScaffold.placeBar(helper, ORIGIN);
        Item bar = GameTestScaffold.firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            bars.getItems().insertItem(slot, new ItemStack(bar), false);
        }
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(bars);
        ItemStack offered = new ItemStack(bar, 4);
        putCowIn(helper, ORIGIN.above());

        int headroom = BarStackBE.SLOTS;
        checkEquals(4, FabricGameTestSupport.insertAt(storage, headroom, offered, true).getCount(),
                "Simulated remainder");
        checkEquals(4, FabricGameTestSupport.insertAt(storage, headroom, offered, false).getCount(),
                "Committed remainder");
        checkEquals(4, offered.getCount(), "Input stack must not be mutated");
        helper.assertBlockNotPresent(
                CommonRegistry.BAR_STACK_BLOCK.get(), ORIGIN.above());
        helper.succeed();
    }

    /**
     * Puts a living placement-blocking entity across the bottom north-west cell and bar position
     * of {@code relative}.
     */
    private static void putCowIn(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(relative);
        Cow cow = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
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
