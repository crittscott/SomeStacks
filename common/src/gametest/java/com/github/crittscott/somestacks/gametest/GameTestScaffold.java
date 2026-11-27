package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StackBlock;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.SlotAccess;
import com.github.crittscott.somestacks.util.StackItemStorage;
import com.github.crittscott.somestacks.util.StorageCubeIdx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Scaffolding shared by each loader's GameTests: placing each stack type on the empty template
 * through {@link CommonRegistry}, seeding handlers directly, and the assertion helpers every test
 * reads through. Loader-native automation access stays in Forge's and NeoForge's {@code
 * GameTestSupport} and Fabric's {@code FabricGameTestSupport}.
 *
 * <p>Seeding writes to a handler rather than going through a deposit, so a test can build a state
 * the ordinary rules would not produce and check what happens next.
 */
public final class GameTestScaffold {
    public static final BlockPos ORIGIN = new BlockPos(2, 1, 2);

    private GameTestScaffold() {}

    /**
     * A stack block's inventory as a hopper or pipe reaches it through the loader: Forge's and
     * NeoForge's {@code IItemHandler} capability or Fabric's Transfer API storage. Each loader's
     * support class supplies one, so a shared scenario drives the same automation surface on every
     * loader. Insertion returns what was left over, and neither call changes the stack it is given.
     */
    public interface Automation {
        ItemStack extract(int slot, int amount, boolean simulate);

        ItemStack insert(int slot, ItemStack stack, boolean simulate);

        /**
         * Commits {@code stack} slot by slot from slot 0 upward, as a hopper filling the first slot
         * with room does, and returns what no slot took.
         */
        default ItemStack insertWalking(ItemStack stack) {
            ItemStack remaining = stack.copy();
            for (int slot = 0; slot < slots() && !remaining.isEmpty(); slot++) {
                remaining = insert(slot, remaining, false);
            }
            return remaining;
        }

        int slots();

        ItemStack stackAt(int slot);
    }

    public static StorageStackBE placeStorage(GameTestHelper helper, BlockPos relative) {
        return place(helper, relative, CommonRegistry.storageStackBlock(), StorageStackBE.class);
    }

    public static SinglesStackBE placeSingles(GameTestHelper helper, BlockPos relative) {
        return place(helper, relative, CommonRegistry.singlesStackBlock(), SinglesStackBE.class);
    }

    public static BarStackBE placeBar(GameTestHelper helper, BlockPos relative) {
        return place(helper, relative, CommonRegistry.barStackBlock(), BarStackBE.class);
    }

    /**
     * State-only changes preserve the entity and contents without drops. To reproduce in-game:
     * rotate a filled Storage/Singles block, waterlog it, or change its stored light source; no
     * contents should fall out and supported Bars above it must remain in place.
     */
    public static void checkStateUpdatesPreserveContents(
            GameTestHelper helper, StackBlockEntity entity) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = entity.getBlockPos();
        BlockState original = level.getBlockState(pos);
        CompoundTag contents = entity.saveWithoutMetadata(level.registryAccess());
        int dropsBefore =
                droppedInColumn(helper, pos, pos).stream()
                        .mapToInt(drop -> drop.getItem().getCount())
                        .sum();
        BlockState changed =
                original.setValue(
                                StackBlock.WATERLOGGED, !original.getValue(StackBlock.WATERLOGGED))
                        .setValue(
                                StackBlock.LIGHT_LEVEL,
                                (original.getValue(StackBlock.LIGHT_LEVEL) + 1) % 16);
        if (changed.hasProperty(StackBlock.HORIZONTAL_FACING)) {
            changed = changed.cycle(StackBlock.HORIZONTAL_FACING);
        }
        level.setBlock(pos, changed, Block.UPDATE_ALL);
        check(level.getBlockEntity(pos) == entity, "State update replaced the block entity");
        checkEquals(
                contents,
                entity.saveWithoutMetadata(level.registryAccess()),
                "State update changed saved contents");
        level.setBlock(pos, original, Block.UPDATE_ALL);
        check(level.getBlockEntity(pos) == entity, "Restoring state replaced the block entity");
        checkEquals(
                contents,
                entity.saveWithoutMetadata(level.registryAccess()),
                "Restoring state changed saved contents");
        checkEquals(
                dropsBefore,
                droppedInColumn(helper, pos, pos).stream()
                        .mapToInt(drop -> drop.getItem().getCount())
                        .sum(),
                "State update dropped items");
    }

    public static <T extends BlockEntity> T place(
            GameTestHelper helper, BlockPos relative, Block block, Class<T> type) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(relative);
        check(
                level.setBlock(absolute, block.defaultBlockState(), Block.UPDATE_ALL),
                "Could not place " + block + " at " + relative);
        BlockEntity blockEntity = level.getBlockEntity(absolute);
        check(
                type.isInstance(blockEntity),
                "Expected " + type.getSimpleName() + " at " + relative + ", found " + blockEntity);
        return type.cast(blockEntity);
    }

    /**
     * The unrotated block-local box of cell {@code slot} of {@code type}, where a fresh block lays
     * it out.
     */
    public static AABB cellBox(BlockType type, int slot) {
        return switch (type) {
            case STORAGE_STACK -> StorageCubeIdx.GRID.localBox(slot, 0);
            case SINGLES_STACK -> SinglesCubeIdx.GRID.localBox(slot, 0);
            case BAR_STACK -> BarCubeIdx.localBox(slot);
        };
    }

    /** Puts a full stack of {@code item} in every slot of {@code block}, bypassing admission. */
    public static void fill(StackBlockEntity block, Item item) {
        for (int slot = 0; slot < block.getItems().getSlots(); slot++) {
            seedSlot(block.getItems(), slot, new ItemStack(item, block.slotLimit()));
        }
    }

    public static Item firstBarItem() {
        for (Item item : BuiltInRegistries.ITEM) {
            if (BarStackBE.isValidBarItem(new ItemStack(item))) {
                return item;
            }
        }
        throw new GameTestAssertException(
                Component.literal("No item is currently valid for Bar Stack tests"), 0);
    }

    public static int count(SlotAccess handler, Item item) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static int occupied(SlotAccess handler) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                total++;
            }
        }
        return total;
    }

    /**
     * {@code base} with only the named settings replaced. Each value has its setting's {@link
     * ServerConfig.Setting.Kind} type ({@code Integer}, {@code Boolean}, or {@code List<String>});
     * naming settings rather than listing every record component keeps a swapped pair of same-typed
     * values from compiling into the wrong policy.
     */
    public static ServerConfig.Settings policy(
            ServerConfig.Settings base, Map<ServerConfig.Setting, ?> changes) {
        return ServerConfig.Settings.from(
                setting -> changes.containsKey(setting) ? changes.get(setting) : setting.get(base));
    }

    /**
     * Runs {@code body} with the applied policy changed only by {@code changes}, then applies the
     * previous policy again, including when {@code body} fails. The body runs synchronously on the
     * server thread, so tests sharing the batch never observe the pinned policy.
     */
    public static void withPolicy(Map<ServerConfig.Setting, ?> changes, Runnable body) {
        ServerConfig.Settings before = ServerConfig.settings();
        ServerConfig.apply(policy(before, changes));
        try {
            body.run();
        } finally {
            ServerConfig.apply(before);
        }
    }

    /**
     * Puts {@code stack} straight into a slot, bypassing the validity test an insertion runs.
     *
     * <p>This is how a test reaches the state an {@code ss deny mod} edit or a data pack reload
     * leaves behind: contents a block holds and must keep handing back, which that same block would
     * refuse from a deposit today. Validity gates insertion only, so the stored side of that rule
     * has to be set up without going through insertion. {@link
     * com.github.crittscott.somestacks.block.StorageStackBE#setSlotIfChanged} takes the same route
     * for the same reason.
     */
    public static void seedSlot(StackItemStorage handler, int slot, ItemStack stack) {
        handler.setStackInSlot(slot, stack);
    }

    /** An item {@code type} admits, for a scenario that needs only some valid content. */
    public static Item sampleItem(BlockType type) {
        return switch (type) {
            case STORAGE_STACK -> Items.STONE;
            case SINGLES_STACK -> Items.STICK;
            case BAR_STACK -> firstBarItem();
        };
    }

    /**
     * Items of {@code item} still held by the stack block at {@code absolutePos}. A position the
     * block has removed itself from holds none, which is what a caller counting whether an
     * operation conserved its contents wants to see.
     */
    public static int heldAt(GameTestHelper helper, BlockPos absolutePos, Item item) {
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(absolutePos);
        if (blockEntity instanceof StorageStackBE storage) {
            return count(storage.getItems(), item);
        }
        if (blockEntity instanceof SinglesStackBE singles) {
            return count(singles.getItems(), item);
        }
        if (blockEntity instanceof BarStackBE bars) {
            return count(bars.getItems(), item);
        }
        return 0;
    }

    /** Items of {@code item} lying on the ground around {@code center}. */
    public static int droppedNear(GameTestHelper helper, BlockPos center, Item item) {
        return helper
                .getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(2.0))
                .stream()
                .filter(entity -> entity.getItem().is(item))
                .mapToInt(entity -> entity.getItem().getCount())
                .sum();
    }

    /** Item entities around the vertical span from {@code bottom} through {@code top}. */
    public static List<ItemEntity> droppedInColumn(
            GameTestHelper helper, BlockPos bottom, BlockPos top) {
        AABB bounds =
                new AABB(
                                Math.min(bottom.getX(), top.getX()),
                                Math.min(bottom.getY(), top.getY()),
                                Math.min(bottom.getZ(), top.getZ()),
                                Math.max(bottom.getX(), top.getX()) + 1.0,
                                Math.max(bottom.getY(), top.getY()) + 1.0,
                                Math.max(bottom.getZ(), top.getZ()) + 1.0)
                        .inflate(2.0);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds);
    }

    /**
     * The comparator output at {@code relative}, read the way a comparator against it reads. Stack
     * signals do not depend on the side a comparator reads from, so one side stands for all.
     */
    public static int signalAt(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        BlockState state = helper.getLevel().getBlockState(absolute);
        return state.getAnalogOutputSignal(helper.getLevel(), absolute, Direction.NORTH);
    }

    /** Puts a living placement-blocking entity across the center of {@code relative}. */
    public static void putCowIn(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(relative);
        Cow cow = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
        check(cow != null, "Could not create obstruction cow");
        check(cow.blocksBuilding, "Cow does not block building");
        cow.snapTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.0F, 0.0F);
        check(level.addFreshEntity(cow), "Could not add obstruction cow");
    }

    /**
     * Runs {@code action} while {@code relative} lies outside the world border, then restores it.
     */
    public static void outsideWorldBorder(
            GameTestHelper helper, BlockPos relative, Runnable action) {
        WorldBorder border = helper.getLevel().getWorldBorder();
        BlockPos target = helper.absolutePos(relative);
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();
        double size = border.getSize();
        try {
            border.setCenter(target.getX() + 1000.0, target.getZ());
            border.setSize(16.0);
            check(
                    !border.isWithinBounds(target),
                    "Test setup left the position inside the world border");
            action.run();
        } finally {
            border.setCenter(centerX, centerZ);
            border.setSize(size);
        }
    }

    public static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(Component.literal(message), 0);
        }
    }

    public static void checkEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new GameTestAssertException(
                    Component.literal(message + ": expected " + expected + ", found " + actual), 0);
        }
    }
}
