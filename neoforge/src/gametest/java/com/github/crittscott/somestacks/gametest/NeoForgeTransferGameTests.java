package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.function.Consumer;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.firstBarItem;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.occupied;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

/** NeoForge-native whole-run transactions, rollback, and protected growth. */
public final class NeoForgeTransferGameTests {
    private NeoForgeTransferGameTests() {}

    /**
     * No in-game reproduction applies: NeoForge automation stages an insertion without publishing
     * it to the block entity, then publishes the complete edit when the transaction commits.
     */
    public static void stagedInsertionIsInvisibleUntilCommit(GameTestHelper helper) {
        StorageStackBE blockEntity = placeStorage(helper, ORIGIN);
        ResourceHandler<ItemResource> storage = GameTestSupport.capability(blockEntity);
        try (Transaction transaction = Transaction.open(null)) {
            checkEquals(4, storage.insert(
                    ItemResource.of(Items.STONE), 4, transaction), "Staged insertion");
            checkEquals(0, GameTestScaffold.count(blockEntity.getItems(), Items.STONE),
                    "Uncommitted insertion reached the world");
            transaction.commit();
        }
        checkEquals(4, GameTestScaffold.count(blockEntity.getItems(), Items.STONE),
                "Committed insertion did not reach the world");
        helper.succeed();
    }

    /**
     * No in-game reproduction applies: this verifies whole-storage NeoForge transactions, including
     * nested rollback, commit all mutations together or leave the run unchanged.
     */
    public static void wholeStorageInsertExtractAndNestedRollbackAreAtomic(GameTestHelper helper) {
        StorageStackBE blockEntity = placeStorage(helper, ORIGIN);
        ResourceHandler<ItemResource> storage = GameTestSupport.capability(blockEntity);
        ItemResource stone = ItemResource.of(Items.STONE);
        ItemResource dirt = ItemResource.of(Items.DIRT);

        try (Transaction outer = Transaction.open(null)) {
            checkEquals(12, storage.insert(stone, 12, outer), "Staged stone insertion");
            try (Transaction nested = Transaction.open(outer)) {
                checkEquals(3, storage.insert(dirt, 3, nested), "Nested dirt insertion");
                nested.commit();
            }
        }
        checkEquals(0, GameTestSupport.count(storage, Items.STONE),
                "Aborted outer transaction kept stone");
        checkEquals(0, GameTestSupport.count(storage, Items.DIRT),
                "Aborted outer transaction kept nested dirt");

        try (Transaction outer = Transaction.open(null)) {
            checkEquals(12, storage.insert(stone, 12, outer), "Committed stone insertion");
            try (Transaction nested = Transaction.open(outer)) {
                checkEquals(3, storage.insert(dirt, 3, nested), "Aborted nested insertion");
            }
            outer.commit();
        }
        checkEquals(12, GameTestSupport.count(storage, Items.STONE),
                "Committed stone count");
        checkEquals(0, GameTestSupport.count(storage, Items.DIRT),
                "Aborted nested dirt count");

        try (Transaction transaction = Transaction.open(null)) {
            checkEquals(5, storage.extract(stone, 5, transaction),
                    "Whole-storage extraction");
            transaction.commit();
        }
        checkEquals(7, GameTestSupport.count(storage, Items.STONE),
                "Count after whole-storage extraction");
        helper.succeed();
    }

    /**
     * No in-game reproduction applies: this verifies one NeoForge transaction cannot stage structural
     * extraction from two run positions whose simultaneous gravity updates would conflict.
     */
    public static void transactionAllowsOnlyOneStructuralExtractionPosition(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        int firstSlot = SinglesCubeIdx.indexFromColumn(0, 0);
        int secondSlot = SinglesCubeIdx.indexFromColumn(1, 0);
        singles.getItems().insertItem(firstSlot, new ItemStack(Items.STICK));
        singles.getItems().insertItem(secondSlot, new ItemStack(Items.PAPER));
        ResourceHandler<ItemResource> storage = GameTestSupport.capability(singles);

        try (Transaction outer = Transaction.open(null)) {
            try (Transaction nested = Transaction.open(outer)) {
                checkEquals(1, storage.extract(firstSlot, ItemResource.of(Items.STICK), 1, nested),
                        "Nested structural extraction");
            }
            checkEquals(1, storage.extract(secondSlot, ItemResource.of(Items.PAPER), 1, outer),
                    "Nested abort did not release structural position");
            checkEquals(0, storage.insert(2, ItemResource.of(Items.STONE), 1, outer),
                    "Insertion followed structural extraction");
        }
        try (Transaction transaction = Transaction.open(null)) {
            checkEquals(1, storage.insert(2, ItemResource.of(Items.STONE), 1, transaction),
                    "Pending insertion");
            checkEquals(0, storage.extract(firstSlot, ItemResource.of(Items.STICK), 1, transaction),
                    "Extraction remapped another pending position");
        }
        try (Transaction transaction = Transaction.open(null)) {
            checkEquals(1, storage.extract(firstSlot,
                    ItemResource.of(Items.STICK), 1, transaction), "First extraction");
            checkEquals(0, storage.extract(secondSlot,
                    ItemResource.of(Items.PAPER), 1, transaction), "Second extraction");
            transaction.commit();
        }

        checkEquals(1, occupied(singles.getItems()), "Occupancy after structural extraction");
        checkEquals(Items.PAPER, singles.getItems().getStackInSlot(secondSlot).getItem(),
                "Refused second position changed");
        helper.succeed();
    }

    /**
     * No in-game reproduction applies: repeated sided lookups and different blocks in a run must
     * observe the same staged contents, including nested rollback and outer abort.
     */
    public static void repeatedLookupsShareOneRunLedger(GameTestHelper helper) {
        StorageStackBE base = placeStorage(helper, ORIGIN);
        StorageStackBE top = placeStorage(helper, ORIGIN.above());
        base.getItems().insertItem(0, new ItemStack(Items.STONE, 4));
        ResourceHandler<ItemResource> first = GameTestSupport.capability(base);
        ResourceHandler<ItemResource> second = helper.getLevel().getCapability(
                Capabilities.Item.BLOCK, base.getBlockPos(), Direction.DOWN);
        ResourceHandler<ItemResource> above = GameTestSupport.capability(top);
        check(first == second, "Sided lookup did not retain the adapter");
        ItemResource stone = ItemResource.of(Items.STONE);
        try (Transaction outer = Transaction.open(null)) {
            checkEquals(3, first.extract(0, stone, 3, outer), "First staged extraction");
            try (Transaction nested = Transaction.open(outer)) {
                checkEquals(1, above.extract(0, stone, 3, nested), "Shared remainder");
            }
            checkEquals(1, second.getAmountAsInt(0), "Nested rollback remainder");
        }
        checkEquals(4, first.getAmountAsInt(0), "Outer abort remainder");
        try (Transaction outer = Transaction.open(null)) {
            checkEquals(3, first.extract(0, stone, 3, outer), "Committed first extraction");
            checkEquals(1, above.extract(0, stone, 3, outer), "Committed shared remainder");
            outer.commit();
        }
        checkEquals(0, first.getAmountAsInt(0), "Extraction committed twice");
        base.getItems().insertItem(0, new ItemStack(Items.STONE, 60));
        try (Transaction outer = Transaction.open(null)) {
            checkEquals(3, second.insert(0, stone, 3, outer), "First insertion");
            checkEquals(1, above.insert(0, stone, 3, outer), "Shared remaining capacity");
            outer.commit();
        }
        checkEquals(64, first.getAmountAsInt(0), "Committed insertion count");
        helper.succeed();
    }

    /**
     * To reproduce in-game: pump into the headroom of each stack type in water. A successful
     * transfer grows a waterlogged block; an aborted machine transfer leaves the water intact.
     */
    public static void headroomRollbackRestoresWaterAndCommitGrowsEachType(GameTestHelper helper) {
        StackBlockEntity[] bases = {placeStorage(helper, ORIGIN),
                placeSingles(helper, ORIGIN.east(3)), placeBar(helper, ORIGIN.east(6))};
        for (StackBlockEntity base : bases) {
            ItemResource resource = ItemResource.of(base instanceof BarStackBE ? firstBarItem() : Items.STONE);
            for (int slot = 0; slot < base.getItems().getSlots(); slot++) {
                base.getItems().insertItem(slot, resource.toStack(base.localSlotLimit()));
            }
            BlockPos above = base.getBlockPos().above();
            helper.getLevel().setBlock(above, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            ResourceHandler<ItemResource> handler = GameTestSupport.capability(base);
            int headroom = base.getItems().getSlots();
            var saved = base.saveWithoutMetadata(helper.getLevel().registryAccess());
            try (Transaction outer = Transaction.open(null)) {
                try (Transaction nested = Transaction.open(outer)) {
                    checkEquals(1, handler.insert(headroom, resource, 1, nested), "Nested headroom offer");
                }
                checkEquals(0, handler.getAmountAsInt(headroom), "Nested abort kept headroom items");
                checkEquals(1, handler.insert(headroom, resource, 1, outer), "Outer headroom offer");
                check(helper.getLevel().getBlockState(above).is(Blocks.WATER), "Offer placed a block");
                checkEquals(saved, base.saveWithoutMetadata(helper.getLevel().registryAccess()),
                        "Offer mutated saved inventory");
            }
            check(helper.getLevel().getBlockState(above).is(Blocks.WATER), "Abort replaced water");
            check(helper.getLevel().getBlockEntity(above) == null, "Abort left a block entity");
            try (Transaction outer = Transaction.open(null)) {
                checkEquals(1, handler.insert(headroom, resource, 1, outer), "Committed headroom offer");
                outer.commit();
            }
            StackBlockEntity grown = (StackBlockEntity) helper.getLevel().getBlockEntity(above);
            check(grown != null, "Commit did not grow the run");
            checkEquals(base.getBlockState().getBlock(), grown.getBlockState().getBlock(), "Grown type");
            check(grown.getBlockState().getValue(BlockStateProperties.WATERLOGGED),
                    "Growth did not retain water");
            checkEquals(1, grown.getItems().getStackInSlot(0).getCount(), "Grown inventory");
        }
        helper.succeed();
    }

    /**
     * To reproduce in-game: fill Singles and Bar columns through automation. One transfer can fill
     * supporting cells and then insert above them, including across the new block's seam.
     */
    public static void stagedSupportFillsCellsAndHeadroomBottomUp(GameTestHelper helper) {
        StackBlockEntity[] bases = {placeSingles(helper, ORIGIN), placeBar(helper, ORIGIN.east(3))};
        for (StackBlockEntity base : bases) {
            ItemResource resource = ItemResource.of(base instanceof BarStackBE ? firstBarItem() : Items.STONE);
            ResourceHandler<ItemResource> handler = GameTestSupport.capability(base);
            try (Transaction transaction = Transaction.open(null)) {
                checkEquals(66, handler.insert(resource, 66, transaction), "Bottom-up insertion");
                checkEquals(0, occupied(base.getItems()), "Staged support changed live contents");
                transaction.commit();
            }
            checkEquals(64, occupied(base.getItems()), "Committed base occupancy");
            StackBlockEntity grown = (StackBlockEntity) helper.getLevel().getBlockEntity(base.getBlockPos().above());
            check(grown != null, "Bottom-up insertion did not grow");
            checkEquals(2, occupied(grown.getItems()), "Committed headroom occupancy");
        }
        helper.succeed();
    }

    /**
     * No in-game reproduction applies: aborting extraction preserves Singles rotations and Bar
     * positions; committing invokes the common gravity or backfill path exactly once.
     */
    public static void positionalExtractionRollsBackAndCommitsMovement(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        int aboveSlot = SinglesCubeIdx.indexFromColumn(0, 1);
        singles.getItems().insertItem(0, new ItemStack(Items.STICK));
        singles.getItems().insertItem(aboveSlot, new ItemStack(Items.PAPER));
        singles.setCubeRotation(aboveSlot, 2);
        BarStackBE bars = placeBar(helper, ORIGIN.east(3));
        ItemResource bar = ItemResource.of(firstBarItem());
        for (int slot = 0; slot < BarStackBE.SLOTS; slot++) bars.getItems().insertItem(slot, bar.toStack());
        for (StackBlockEntity base : new StackBlockEntity[]{singles, bars}) {
            ResourceHandler<ItemResource> handler = GameTestSupport.capability(base);
            ItemResource resource = handler.getResource(0);
            var saved = base.saveWithoutMetadata(helper.getLevel().registryAccess());
            try (Transaction transaction = Transaction.open(null)) {
                checkEquals(1, handler.extract(0, resource, 1, transaction), "Staged positional extraction");
                checkEquals(0, handler.extract(0, resource, 1, transaction), "Position extracted twice");
                checkEquals(saved, base.saveWithoutMetadata(helper.getLevel().registryAccess()), "Extraction changed live data");
            }
            checkEquals(saved, base.saveWithoutMetadata(helper.getLevel().registryAccess()), "Abort changed saved data");
            try (Transaction transaction = Transaction.open(null)) {
                checkEquals(1, handler.extract(0, resource, 1, transaction), "Committed positional extraction");
                transaction.commit();
            }
        }
        checkEquals(Items.PAPER, singles.getItems().getStackInSlot(0).getItem(), "Singles gravity");
        checkEquals(2, singles.getCubeRotation(0), "Gravity lost item rotation");
        checkEquals(63, occupied(bars.getItems()), "Bar extraction collapsed contents");
        check(!bars.getItems().getStackInSlot(0).isEmpty(), "Bar extraction did not backfill");
        helper.succeed();
    }

    /**
     * To reproduce in-game: deny [SomeStacks] placement in a claim and pump items into headroom.
     * The transfer accepts nothing, and neither a committed nor an aborted offer grows the run.
     */
    public static void placementVetoRefusesOfferBeforeCommit(GameTestHelper helper) {
        StorageStackBE base = placeStorage(helper, ORIGIN);
        ResourceHandler<ItemResource> handler = GameTestSupport.capability(base);
        BlockPos above = base.getBlockPos().above();
        int[] events = {0};
        Consumer<BlockEvent.EntityPlaceEvent> deny = event -> {
            if (event.getPos().equals(above)) {
                events[0]++;
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(deny);
        try {
            for (boolean commit : new boolean[]{false, true}) {
                try (Transaction transaction = Transaction.open(null)) {
                    checkEquals(0, handler.insert(StorageStackBE.SLOTS, ItemResource.of(Items.STONE), 4, transaction),
                            "Vetoed placement accepted items");
                    if (commit) transaction.commit();
                }
            }
        } finally {
            NeoForge.EVENT_BUS.unregister(deny);
        }
        checkEquals(2, events[0], "Placement event count");
        check(helper.getLevel().getBlockEntity(above) == null, "Veto left a block entity");
        checkEquals(0, GameTestSupport.count(handler, Items.STONE), "Veto kept items");
        helper.succeed();
    }

    /** No in-game reproduction applies: one transaction can replace a Storage slot's identity. */
    public static void storageSlotReplacementUsesStagedCapacity(GameTestHelper helper) {
        StorageStackBE base = placeStorage(helper, ORIGIN);
        base.getItems().insertItem(0, new ItemStack(Items.STONE, 64));
        ResourceHandler<ItemResource> handler = GameTestSupport.capability(base);
        try (Transaction transaction = Transaction.open(null)) {
            checkEquals(64, handler.extract(0, ItemResource.of(Items.STONE), 64, transaction), "Extract original");
            checkEquals(8, handler.insert(0, ItemResource.of(Items.DIRT), 8, transaction), "Insert replacement");
            transaction.commit();
        }
        checkEquals(Items.DIRT, base.getItems().getStackInSlot(0).getItem(), "Replacement identity");
        checkEquals(8, base.getItems().getStackInSlot(0).getCount(), "Replacement amount");
        helper.succeed();
    }
}
