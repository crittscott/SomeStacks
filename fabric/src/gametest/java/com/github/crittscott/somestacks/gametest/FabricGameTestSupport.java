package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.server.SyntheticPlayer;
import com.mojang.authlib.GameProfile;

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

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Function;

/**
 * Fabric-native scaffolding for the GameTests: the namespaced template name, Transfer API storage
 * access and the shared scenarios' automation view of it, and the small transaction helpers used
 * by adapter-specific tests. Everything loader-neutral lives in {@link GameTestScaffold}.
 */
public final class FabricGameTestSupport {
    public static final String TEMPLATE = "somestacks:somestacks_empty";

    private FabricGameTestSupport() {}

    /**
     * The {@link GameTestScaffold.Automation} view of {@code blockEntity}'s registered Transfer API
     * storage, as a hopper or pipe reaches it; each call runs in its own outer transaction.
     */
    public static GameTestScaffold.Automation automation(BlockEntity blockEntity) {
        SlottedStorage<ItemVariant> storage = storage(blockEntity);
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
     * The registered whole-run storage for {@code blockEntity}, queried the way an external
     * automation caller would: through {@link ItemStorage#SIDED}, not the block entity directly.
     */
    @SuppressWarnings("unchecked")
    public static SlottedStorage<ItemVariant> storage(BlockEntity blockEntity) {
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

    /** A fake-player factory that resets the main hand for each shared check. */
    public static Function<ItemStack, ServerPlayer> playerFactory(GameTestHelper helper) {
        ServerPlayer player = player(helper.getLevel());
        return mainHand -> {
            player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
            return player;
        };
    }

    /** A connection-safe test player distinct from the automation identity. */
    public static ServerPlayer player(ServerLevel level) {
        GameProfile profile =
                new GameProfile(
                        UUID.nameUUIDFromBytes(
                                "somestacks:gametest".getBytes(StandardCharsets.UTF_8)),
                        "[SomeStacksGameTest]");
        return new SyntheticPlayer(level, profile);
    }
}
