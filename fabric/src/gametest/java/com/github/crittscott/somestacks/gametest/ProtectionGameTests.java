package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.FabricAdjacentEditAuthority;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final Map<TestTarget, RemovalProbe> REMOVAL_PROBES =
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
            RemovalProbe probe = REMOVAL_PROBES.get(new TestTarget(level, pos));
            if (probe != null) {
                probe.actor().accept(player);
                return probe.allowed().test(player);
            }
            return true;
        });
    }

    /** See {@link ProtectionChecks#automationUsesSharedIdentity}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void automationUsesSharedIdentity(GameTestHelper helper) {
        ProtectionChecks.automationUsesSharedIdentity(helper);
    }

    /**
     * No in-game reproduction applies: this verifies that Fabric backs the shared SomeStacks
     * automation identity with its FakePlayer implementation.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void automationUsesFabricFakePlayer(GameTestHelper helper) {
        check(WorldEdits.automationActor(helper.getLevel()) instanceof FakePlayer,
                "Fabric automation actor was not a Fabric API FakePlayer");
        helper.succeed();
    }

    /**
     * To reproduce in-game: stand outside a claim and click toward a denied destination inside it.
     * Fabric consults that destination while it is still air and places nothing there.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void destinationConsultationRunsBeforePlacement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayer.get(level);
        BlockPos target = helper.absolutePos(ORIGIN);
        TestTarget key = new TestTarget(level, target);
        PlacementProbe probe = new PlacementProbe(
                InteractionResult.FAIL, new AtomicBoolean(), new AtomicBoolean());
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

    /**
     * To reproduce in-game: allow a player but deny [SomeStacks] in a claim, then have the player
     * extract the last Single. Player-attributed cleanup removes the empty block.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerExtractionUsesPlayerForCleanup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        singles.getItems().insertItem(0, new ItemStack(Items.STONE), false);
        ServerPlayer player = FakePlayer.get(level);
        BlockPos target = helper.absolutePos(ORIGIN);
        TestTarget key = new TestTarget(level, target);
        AtomicReference<Player> observedActor = new AtomicReference<>();
        REMOVAL_PROBES.put(key, new RemovalProbe(true, observedActor));
        try {
            check(!singles.extractAt(0, player).isEmpty(), "Player extraction returned nothing");
        } finally {
            REMOVAL_PROBES.remove(key);
        }

        check(observedActor.get() == player, "Cleanup break event did not carry the player");
        helper.assertBlockNotPresent(CommonRegistry.SINGLES_STACK_BLOCK.get(), ORIGIN);
        helper.succeed();
    }

    /**
     * To reproduce in-game: allow a player but deny [SomeStacks] in a claim, then have the player
     * extract the last Storage item. Deferred cleanup retains the player and removes the block.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerStorageSettlementUsesPlayerForCleanup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StorageStackBE storage = GameTestScaffold.placeStorage(helper, ORIGIN);
        storage.getItems().insertItem(0, new ItemStack(Items.STONE), false);
        ServerPlayer player = FakePlayer.get(level);
        BlockPos target = helper.absolutePos(ORIGIN);
        TestTarget key = new TestTarget(level, target);
        AtomicReference<Player> observedActor = new AtomicReference<>();
        REMOVAL_PROBES.put(key, new RemovalProbe(true, observedActor));
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

    /**
     * A refused cleanup leaves the empty top Storage block in both the world and the resolved run.
     * To reproduce in-game: protect the top block of a two-block temporary Storage pile from
     * [SomeStacks], leave one item in the bottom block, and trigger settlement. The protected empty
     * top block remains; after allowing [SomeStacks], the next settlement removes it.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void settleKeepsAnEmptyTopBlockWhoseRemovalIsRefused(GameTestHelper helper) {
        BlockPos topRelative = ORIGIN.above();
        StorageStackBE base = GameTestScaffold.placeStorage(helper, ORIGIN);
        GameTestScaffold.placeStorage(helper, topRelative);
        base.getItems().insertItem(0, new ItemStack(Items.DIRT), false);

        ServerLevel level = helper.getLevel();
        TestTarget key = new TestTarget(level, helper.absolutePos(topRelative));
        REMOVAL_PROBES.put(key, new RemovalProbe(false, new AtomicReference<>()));
        try {
            StoragePile pile = base.pile();
            check(pile != null, "Pile did not resolve");
            pile.settle();
            helper.assertBlockPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), topRelative);
            checkEquals(2, pile.height(), "The pile dropped a block it never removed");
        } finally {
            REMOVAL_PROBES.remove(key);
        }

        StoragePile pile = base.pile();
        check(pile != null, "Pile did not resolve after the refusal");
        pile.settle();
        helper.assertBlockNotPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), topRelative);
        helper.succeed();
    }



    /**
     * A Fabric item-use denial vetoes an adjacent-stack consultation. To reproduce in-game: deny
     * item use at a claimed destination, then hold the modifier and right-click the neighboring
     * block toward it. No item is deposited and no stack is placed in the denied position.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void adjacentConsultationHonorsUseItemDeny(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayer.get(level);
        BlockPos target = helper.absolutePos(ORIGIN);
        TestTarget key = new TestTarget(level, target);
        PlacementProbe probe = new PlacementProbe(
                InteractionResult.FAIL, new AtomicBoolean(), new AtomicBoolean());
        PLACEMENT_PROBES.put(key, probe);
        try {
            check(!new FabricAdjacentEditAuthority().mayUseItemAt(player, target),
                    "Item-use denial did not veto the adjacent stack consultation");
        } finally {
            PLACEMENT_PROBES.remove(key);
        }
        check(probe.invoked().get(), "Fabric did not consult the adjacent destination");
        helper.succeed();
    }

    /**
     * A refused removal keeps an emptied Bar block without preventing the unsupported block above
     * from collapsing. To reproduce in-game: build a supported two-block Bar column, deny
     * [SomeStacks] permission to remove the lower block, and extract its bottom support. The empty
     * lower block remains, while the unsupported upper block comes down.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void refusedRemovalStillLetsTheBarsAboveComeDown(GameTestHelper helper) {
        BarStackBE lower = GameTestScaffold.placeBar(helper, ORIGIN);
        BarStackBE upper = GameTestScaffold.placeBar(helper, ORIGIN.above());
        Item barItem = GameTestScaffold.firstBarItem();
        BlockPos upperPos = upper.getBlockPos();

        int slot = 0;
        GameTestScaffold.seedSlot(lower.getItems(), slot, new ItemStack(barItem));
        for (int layer = 1; layer < 8; layer++) {
            slot = BarColumnChecks.firstSupportedBy(slot, layer * 8, (layer + 1) * 8);
            GameTestScaffold.seedSlot(lower.getItems(), slot, new ItemStack(barItem));
        }
        GameTestScaffold.seedSlot(
                upper.getItems(), BarColumnChecks.firstSeamSupportedBy(slot - 56),
                new ItemStack(barItem));

        TestTarget key = new TestTarget(helper.getLevel(), lower.getBlockPos());
        REMOVAL_PROBES.put(key, new RemovalProbe(false, new AtomicReference<>()));
        try {
            lower.extractAt(0);
        } finally {
            REMOVAL_PROBES.remove(key);
        }

        helper.assertBlockPresent(CommonRegistry.BAR_STACK_BLOCK.get(), ORIGIN);
        check(lower.isEmpty(), "The kept block held on to its bars");
        check(helper.getLevel().getBlockEntity(upperPos) == null,
                "The block above a kept block was not brought down");
        helper.succeed();
    }

    /** See {@link ProtectionChecks#checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(GameTestHelper helper) {
        ProtectionChecks.checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ProtectionChecks#checkedPlacementRejectsAnObstructingEntity}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void checkedPlacementRejectsAnObstructingEntity(GameTestHelper helper) {
        ProtectionChecks.checkedPlacementRejectsAnObstructingEntity(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ProtectionChecks#creativeDepositFillsTheStackWithoutSpendingTheHand}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void creativeDepositFillsTheStackWithoutSpendingTheHand(GameTestHelper helper) {
        ProtectionChecks.creativeDepositFillsTheStackWithoutSpendingTheHand(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link ProtectionChecks#placementIntoWaterKeepsTheWater}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void placementIntoWaterKeepsTheWater(GameTestHelper helper) {
        ProtectionChecks.placementIntoWaterKeepsTheWater(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    private record TestTarget(Level level, BlockPos pos) {}

    private record PlacementProbe(
            InteractionResult result, AtomicBoolean invoked, AtomicBoolean sawAir) {}

    private record RemovalProbe(java.util.function.Predicate<Player> allowed, java.util.function.Consumer<Player> actor) {
        RemovalProbe(boolean allowed, AtomicReference<Player> actor) {
            this(player -> allowed, actor::set);
        }
    }

    /** See {@link ProtectionChecks#playerBarExtractionUsesPlayerForCleanup}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerBarExtractionUsesPlayerForCleanup(GameTestHelper helper) {
        ProtectionChecks.playerBarExtractionUsesPlayerForCleanup(
                helper, FabricGameTestSupport.playerFactory(helper), ProtectionGameTests::observeRemoval);
    }

    /** See {@link ProtectionChecks#mixedStorageSettlementUsesAutomationForCleanup}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void mixedStorageSettlementUsesAutomationForCleanup(GameTestHelper helper) {
        ProtectionChecks.mixedStorageSettlementUsesAutomationForCleanup(
                helper, FabricGameTestSupport.playerFactory(helper), ProtectionGameTests::observeRemoval);
    }

    private static Runnable observeRemoval(ServerLevel level, BlockPos pos,
            java.util.function.Consumer<Player> observed, java.util.function.Predicate<Player> allowed) {
        TestTarget key = new TestTarget(level, pos);
        REMOVAL_PROBES.put(key, new RemovalProbe(allowed, observed));
        return () -> REMOVAL_PROBES.remove(key);
    }

    // Growth under protection
    //
    // A storage insertion aimed at a slot past what the run holds is the one that grows it, and it
    // checks the position above the run before promising the caller anything, so a simulation and the
    // commit that follows agree about a position growth cannot have. The three tests below stage that
    // with the world border. Spawn protection, the other half of the same predicate, cannot be staged
    // here: it is implemented on DedicatedServer, and the server running these tests is not one.

    /**
     * To reproduce in-game: protect the space above a full Storage pile from SomeStacks and insert
     * through item automation; it accepts nothing and creates no block.
     */
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

        GameTestScaffold.outsideWorldBorder(helper, ORIGIN.above(), () -> {
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

    /**
     * To reproduce in-game: protect the space above a full Singles column from SomeStacks and insert
     * through item automation; it accepts nothing and creates no block.
     */
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

        GameTestScaffold.outsideWorldBorder(helper, ORIGIN.above(), () -> {
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

    /**
     * To reproduce in-game: protect the space above a full Bar column from SomeStacks and insert
     * through item automation; it accepts nothing and creates no block.
     */
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

        GameTestScaffold.outsideWorldBorder(helper, ORIGIN.above(), () -> {
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

    /**
     * To reproduce in-game: stand in the growth space above a full Storage pile and insert through
     * item automation; it accepts nothing and does not grow into the entity.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void storageGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        StorageStackBE blockEntity = GameTestScaffold.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            blockEntity.getItems().insertItem(slot, new ItemStack(Items.DIRT, 64), false);
        }
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(blockEntity);
        ItemStack offered = new ItemStack(Items.STONE, 4);
        GameTestScaffold.putCowIn(helper, ORIGIN.above());

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

    /**
     * To reproduce in-game: stand in the growth space above a full Singles column and insert through
     * item automation; it accepts nothing and does not grow into the entity.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void singlesGrowthRejectsAnObstructingEntityInSimulationAndCommit(
            GameTestHelper helper) {
        SinglesStackBE singles = GameTestScaffold.placeSingles(helper, ORIGIN);
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            singles.getItems().insertItem(slot, new ItemStack(Items.STONE), false);
        }
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(singles);
        ItemStack offered = new ItemStack(Items.STONE, 4);
        GameTestScaffold.putCowIn(helper, ORIGIN.above());

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

    /**
     * To reproduce in-game: stand in the growth space above a full Bar column and insert through
     * item automation; it accepts nothing and does not grow into the entity.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void barGrowthRejectsAnObstructingEntityInSimulationAndCommit(GameTestHelper helper) {
        BarStackBE bars = GameTestScaffold.placeBar(helper, ORIGIN);
        Item bar = GameTestScaffold.firstBarItem();
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
            bars.getItems().insertItem(slot, new ItemStack(bar), false);
        }
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(bars);
        ItemStack offered = new ItemStack(bar, 4);
        GameTestScaffold.putCowIn(helper, ORIGIN.above());

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

}
