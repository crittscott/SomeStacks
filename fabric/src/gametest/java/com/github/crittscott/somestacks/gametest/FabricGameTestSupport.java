package com.github.crittscott.somestacks.gametest;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Function;

/**
 * Fabric-native scaffolding for the GameTests: the namespaced template name, the shared fake
 * player, the real block-use click, and Transfer API storage access, including the insert/extract
 * shims that mirror Forge's {@code IItemHandler} call surface. Everything loader-neutral lives in
 * {@link GameTestScaffold}.
 */
public final class FabricGameTestSupport {
    public static final String TEMPLATE = "somestacks:somestacks_empty";

    private FabricGameTestSupport() {}

    /**
     * The {@link GameTestScaffold.Automation} view of {@code blockEntity}'s registered Transfer API
     * storage, as a hopper or pipe reaches it; each call runs in its own outer transaction.
     */
    public static GameTestScaffold.Automation automation(BlockEntity blockEntity) {
        SlottedStorage<ItemVariant> storage = capability(blockEntity);
        return new GameTestScaffold.Automation() {
            @Override
            public ItemStack extract(int slot, int amount, boolean simulate) {
                return extractAt(storage, slot, amount, simulate);
            }

            @Override
            public ItemStack insert(int slot, ItemStack stack, boolean simulate) {
                return insertAt(storage, slot, stack, simulate);
            }

            @Override
            public ItemStack insertStacked(ItemStack stack, boolean simulate) {
                return insertWalkingSlots(storage, stack, simulate);
            }

            @Override
            public int slotLimit(int slot) {
                return FabricGameTestSupport.slotLimit(storage, slot);
            }

            @Override
            public int slots() {
                return storage.getSlotCount();
            }

            @Override
            public ItemStack stackAt(int slot) {
                return FabricGameTestSupport.stackAt(storage, slot);
            }
        };
    }

    /**
     * Runs vanilla's server block use, which invokes Fabric's callback and then calls the block.
     */
    public static boolean click(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        return player.gameMode
                .useItemOn(player, player.serverLevel(), player.getItemInHand(hand), hand, hit)
                .consumesAction();
    }

    /** Hands out Fabric's shared fake player for the level, holding the given main-hand stack. */
    public static Function<ItemStack, ServerPlayer> players(GameTestHelper helper) {
        return held -> {
            ServerPlayer player = FakePlayer.get(helper.getLevel());
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            return player;
        };
    }

    /**
     * The registered whole-run storage for {@code blockEntity}, queried the way an external
     * automation caller would: through {@link ItemStorage#SIDED}, not the block entity directly.
     */
    @SuppressWarnings("unchecked")
    public static SlottedStorage<ItemVariant> capability(BlockEntity blockEntity) {
        Storage<ItemVariant> storage =
                ItemStorage.SIDED.find(
                        (ServerLevel) blockEntity.getLevel(),
                        blockEntity.getBlockPos(),
                        blockEntity.getBlockState(),
                        blockEntity,
                        null);
        GameTestScaffold.check(
                storage instanceof SlottedStorage,
                "Missing item storage at " + blockEntity.getBlockPos());
        return (SlottedStorage<ItemVariant>) storage;
    }

    /**
     * Inserts {@code stack} into {@code slot}, mirroring Forge's {@code
     * IItemHandler.insertItem(slot, stack, simulate)}: returns the remainder left over, and a
     * {@code simulate} transaction is opened and never committed.
     */
    public static ItemStack insertAt(
            SlottedStorage<ItemVariant> storage, int slot, ItemStack stack, boolean simulate) {
        long inserted;
        try (Transaction transaction = Transaction.openOuter()) {
            inserted =
                    storage.getSlot(slot)
                            .insert(ItemVariant.of(stack), stack.getCount(), transaction);
            if (!simulate) {
                transaction.commit();
            }
        }
        ItemStack remainder = stack.copy();
        remainder.shrink((int) inserted);
        return remainder;
    }

    /**
     * Walks every advertised slot from 0 upward inserting as much of {@code stack} as each slot
     * will take, mirroring Forge's {@code ItemHandlerHelper.insertItemStacked}. Returns the
     * remainder left over once the stack is exhausted or every slot has been offered it.
     */
    public static ItemStack insertWalkingSlots(
            SlottedStorage<ItemVariant> storage, ItemStack stack, boolean simulate) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < storage.getSlotCount() && !remaining.isEmpty(); slot++) {
            remaining = insertAt(storage, slot, remaining, simulate);
        }
        return remaining;
    }

    /**
     * Extracts up to {@code amount} from {@code slot}, mirroring Forge's {@code
     * IItemHandler.extractItem(slot, amount, simulate)}.
     */
    public static ItemStack extractAt(
            SlottedStorage<ItemVariant> storage, int slot, int amount, boolean simulate) {
        SingleSlotStorage<ItemVariant> slotStorage = storage.getSlot(slot);
        ItemVariant resource = slotStorage.getResource();
        long extracted;
        try (Transaction transaction = Transaction.openOuter()) {
            extracted = slotStorage.extract(resource, amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
        }
        return resource.toStack((int) extracted);
    }

    public static ItemStack stackAt(SlottedStorage<ItemVariant> storage, int slot) {
        SingleSlotStorage<ItemVariant> slotStorage = storage.getSlot(slot);
        return slotStorage.getResource().toStack((int) slotStorage.getAmount());
    }

    public static int slotLimit(SlottedStorage<ItemVariant> storage, int slot) {
        return (int) storage.getSlot(slot).getCapacity();
    }

    public static int count(SlottedStorage<ItemVariant> storage, Item item) {
        int total = 0;
        for (int slot = 0; slot < storage.getSlotCount(); slot++) {
            ItemStack stack = stackAt(storage, slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static int occupied(SlottedStorage<ItemVariant> storage) {
        int total = 0;
        for (int slot = 0; slot < storage.getSlotCount(); slot++) {
            if (!storage.getSlot(slot).isResourceBlank()) {
                total++;
            }
        }
        return total;
    }
}
