package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.*;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.ServerLifecycle;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.network.GestureStatePkt;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.server.StackInteractions;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.StackMode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Shared regression checks driven through vanilla's server block-use path on each loader. */
public final class InteractionChecks {
    public static final BlockPos TARGET = new BlockPos(2, 1, 2);
    public static final InteractionHand HAND = InteractionHand.MAIN_HAND;

    private InteractionChecks() {}

    /**
     * One stack type as the policy checks drive it: the mode that selects it, its enable setting,
     * an item it accepts, its slots per block, and a side aim (block-local height and depth from
     * the west) at its first bottom-layer cell. Slot counts are written out rather than read from
     * the block entities they describe.
     */
    private record TypeCase(
            BlockType type,
            StackMode mode,
            ServerConfig.Setting enable,
            Item item,
            int perBlock,
            double sideY,
            double sideZ) {
        static List<TypeCase> all() {
            return List.of(
                    new TypeCase(
                            BlockType.STORAGE_STACK,
                            StackMode.STORAGE_STACK,
                            ServerConfig.Setting.ENABLE_STORAGE_STACK_BLOCK,
                            Items.STONE,
                            27,
                            0.2,
                            0.2),
                    new TypeCase(
                            BlockType.SINGLES_STACK,
                            StackMode.SINGLES_STACK,
                            ServerConfig.Setting.ENABLE_SINGLES_STACK_BLOCK,
                            Items.STONE,
                            64,
                            0.125,
                            0.125),
                    new TypeCase(
                            BlockType.BAR_STACK,
                            StackMode.BAR_STACK,
                            ServerConfig.Setting.ENABLE_BAR_STACK_BLOCK,
                            firstBarItem(),
                            64,
                            0.0625,
                            0.125));
        }

        /** Places an empty block of this type at {@code relative}. */
        StackBlockEntity place(GameTestHelper helper, BlockPos relative) {
            return switch (type) {
                case STORAGE_STACK -> placeStorage(helper, relative);
                case SINGLES_STACK -> placeSingles(helper, relative);
                case BAR_STACK -> placeBar(helper, relative);
            };
        }

        /** Places a block of this type at {@code relative} with every slot full. */
        StackBlockEntity placeFull(GameTestHelper helper, BlockPos relative) {
            StackBlockEntity block = place(helper, relative);
            int count = type == BlockType.STORAGE_STACK ? item.getMaxStackSize() : 1;
            for (int slot = 0; slot < perBlock; slot++) {
                seedSlot(block.getItems(), slot, new ItemStack(item, count));
            }
            return block;
        }

        boolean isAt(GameTestHelper helper, BlockPos absolute) {
            return helper.getLevel().getBlockState(absolute).is(type.getBlock());
        }
    }

    @FunctionalInterface
    public interface LoaderClick {
        boolean click(ServerPlayer player, InteractionHand hand, BlockHitResult hit);
    }

    /**
     * To reproduce: modifier-deposit four dirt into Storage, release the modifier and plain-click
     * with an empty hand; the four dirt return to the hand. Store ten stone and plain-click it
     * holding 60 stone: the hand fills to 64, six stone stay stored, and nothing drops. Then
     * modifier-click the top of an ordinary block holding two dirt; a Storage Stack holding the two
     * dirt appears there and the hand is empty.
     */
    public static void depositExtractAndPlacement(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            LoaderClick callback) {
        var storage = placeStorage(helper, TARGET);
        try (PlayerScope scope = new PlayerScope(factory.apply(new ItemStack(Items.DIRT, 4)))) {
            var player = scope.player;
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            callback.click(player, HAND, aim(player, storage.getBlockPos(), 0.2, 0.2));
            checkEquals(4, count(storage.getItems(), Items.DIRT), "Deposit contents");
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            callback.click(player, HAND, aim(player, storage.getBlockPos(), 0.2, 0.2));
            checkEquals(4, player.getMainHandItem().getCount(), "Extraction count");
            // A partial matching hand takes only what fits; the rest stays stored, not dropped.
            storage.getItems().setStackInSlot(0, new ItemStack(Items.STONE, 10));
            player.setItemInHand(HAND, new ItemStack(Items.STONE, 60));
            callback.click(player, HAND, aim(player, storage.getBlockPos(), 0.2, 0.2));
            checkEquals(64, player.getMainHandItem().getCount(), "Partial hand fill");
            checkEquals(6, count(storage.getItems(), Items.STONE), "Stone left after hand filled");
            checkEquals(
                    0, droppedNear(helper, storage.getBlockPos(), Items.STONE), "Overflow dropped");
            BlockPos support = helper.absolutePos(TARGET.east(3));
            helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            player.setItemInHand(HAND, new ItemStack(Items.DIRT, 2));
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            aim(player, support, 0.5, 0.5);
            check(
                    callback.click(
                            player,
                            HAND,
                            new BlockHitResult(
                                    Vec3.atCenterOf(support).add(0, 0.5, 0),
                                    Direction.UP,
                                    support,
                                    false)),
                    "Placement consumed");
            check(
                    helper.getLevel()
                            .getBlockState(support.above())
                            .is(CommonRegistry.STORAGE_STACK_BLOCK.get()),
                    "Placed type");
            checkEquals(2, heldAt(helper, support.above(), Items.DIRT), "First deposit");
            check(player.getMainHandItem().isEmpty(), "Placement did not spend hand");
        }
        helper.succeed();
    }

    /**
     * To reproduce: on a laggy connection, plain-click one Storage cell while turning toward
     * another. The cell under the crosshair when the client clicked is extracted, not the one the
     * server last saw the player facing.
     */
    public static void extractionFollowsHitPoint(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            LoaderClick callback) {
        var storage = placeStorage(helper, TARGET);
        storage.getItems().setStackInSlot(0, new ItemStack(Items.STONE, 8));
        storage.getItems().setStackInSlot(3, new ItemStack(Items.DIRT, 3));
        try (PlayerScope scope = new PlayerScope(factory.apply(ItemStack.EMPTY))) {
            var player = scope.player;
            BlockPos pos = storage.getBlockPos();
            // The server view still faces slot 0; the client's hit point lies on slot 3.
            aim(player, pos, 0.2, 0.2);
            callback.click(
                    player,
                    HAND,
                    new BlockHitResult(
                            new Vec3(pos.getX(), pos.getY() + 0.2, pos.getZ() + 0.5),
                            Direction.WEST,
                            pos,
                            false));
            checkEquals(Items.DIRT, player.getMainHandItem().getItem(), "Hit-point item");
            checkEquals(3, player.getMainHandItem().getCount(), "Hit-point count");
            checkEquals(8, storage.getItems().getStackInSlot(0).getCount(), "Viewed cell changed");
        }
        helper.succeed();
    }

    /**
     * To reproduce: Shift-click one of two Singles items with a soul torch, then an empty row. Only
     * the aimed occupied item rotates.
     */
    public static void rotationTargetsAimedItemAndConsumesMiss(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            LoaderClick callback) {
        var singles = placeSingles(helper, TARGET);
        singles.getItems().setStackInSlot(0, new ItemStack(Items.STICK));
        singles.getItems().setStackInSlot(1, new ItemStack(Items.STONE));
        try (PlayerScope scope = new PlayerScope(factory.apply(new ItemStack(Items.SOUL_TORCH)))) {
            var player = scope.player;
            player.setShiftKeyDown(true);
            check(
                    callback.click(player, HAND, aim(player, singles.getBlockPos(), 0.125, 0.125)),
                    "Rotation consumed");
            checkEquals(1, singles.getCubeRotation(0), "Aimed rotation");
            checkEquals(0, singles.getCubeRotation(1), "Other item rotation");
            check(
                    callback.click(player, HAND, aim(player, singles.getBlockPos(), 0.625, 0.625)),
                    "Miss consumed");
            checkEquals(1, singles.getCubeRotation(0), "Miss rotated occupied cell");
            checkEquals(1, player.getMainHandItem().getCount(), "Torch spent");
        }
        helper.succeed();
    }

    /**
     * To reproduce: store one item in each type, then plain-click each holding dirt, then the same
     * item carrying different NBT (for example a renamed copy), then a full stack of the same item.
     * No stored items or unsupported bars move, and each hand stays as it was.
     */
    public static void incompatibleAndFullHandsLeaveAllTypesUntouched(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            LoaderClick callback) {
        StackBlockEntity[] blocks = {
            placeStorage(helper, TARGET),
            placeSingles(helper, TARGET.east(3)),
            placeBar(helper, TARGET.east(6))
        };
        var barItem = firstBarItem();
        for (int i = 0; i < blocks.length; i++) {
            var item = i == 2 ? barItem : Items.STONE;
            int count = i == 0 ? 8 : 1;
            blocks[i].getItems().setStackInSlot(0, new ItemStack(item, count));
            try (PlayerScope scope = new PlayerScope(factory.apply(new ItemStack(Items.DIRT)))) {
                var player = scope.player;
                callback.click(
                        player,
                        HAND,
                        aim(player, blocks[i].getBlockPos(), i == 2 ? 0.0625 : 0.2, 0.125));
                checkEquals(Items.DIRT, player.getMainHandItem().getItem(), "Wrong hand replaced");
                ItemStack variant = new ItemStack(item);
                variant.getOrCreateTag().putInt("somestacks_test_variant", 1);
                player.setItemInHand(HAND, variant.copy());
                callback.click(
                        player,
                        HAND,
                        aim(player, blocks[i].getBlockPos(), i == 2 ? 0.0625 : 0.2, 0.125));
                check(
                        ItemStack.matches(variant, player.getMainHandItem()),
                        "Different-NBT hand changed");
                checkEquals(
                        count,
                        blocks[i].getItems().getStackInSlot(0).getCount(),
                        "Different-NBT hand took stored items");
                player.setItemInHand(
                        HAND, new ItemStack(item, new ItemStack(item).getMaxStackSize()));
                callback.click(
                        player,
                        HAND,
                        aim(player, blocks[i].getBlockPos(), i == 2 ? 0.0625 : 0.2, 0.125));
                checkEquals(
                        new ItemStack(item).getMaxStackSize(),
                        player.getMainHandItem().getCount(),
                        "Full hand grew");
                checkEquals(
                        count,
                        blocks[i].getItems().getStackInSlot(0).getCount(),
                        "Stored contents changed");
                check(
                        helper.getLevel().getBlockEntity(blocks[i].getBlockPos()) == blocks[i],
                        "Block removed");
                checkEquals(
                        0,
                        droppedNear(helper, blocks[i].getBlockPos(), item),
                        "Refusal dropped items");
            }
        }
        helper.succeed();
    }

    /**
     * To reproduce: plain-click empty space in any stack. The gesture consumes use and yields
     * nothing.
     */
    public static void extractionMissConsumesWithoutMutation(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            LoaderClick callback) {
        StackBlockEntity[] blocks = {
            placeStorage(helper, TARGET),
            placeSingles(helper, TARGET.east(3)),
            placeBar(helper, TARGET.east(6))
        };
        for (var block : blocks) {
            block.getItems().setStackInSlot(0, new ItemStack(Items.STONE));
            try (PlayerScope scope = new PlayerScope(factory.apply(ItemStack.EMPTY))) {
                check(
                        callback.click(
                                scope.player,
                                HAND,
                                aim(scope.player, block.getBlockPos(), 0.9, 0.9)),
                        "Miss not consumed");
                check(scope.player.getMainHandItem().isEmpty(), "Miss extracted item");
                checkEquals(
                        1, block.getItems().getStackInSlot(0).getCount(), "Miss changed contents");
            }
        }
        helper.succeed();
    }

    /**
     * To reproduce: hold dirt only in the off hand and modifier-click Storage; nothing is
     * deposited. Shift-click it with a redstone torch: the block rotates and the torch is neither
     * deposited nor spent. Shift-click it with dirt: the dirt is placed as an ordinary block, not
     * deposited.
     */
    public static void handsSneakingAndRotationPrecedence(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            LoaderClick callback) {
        var storage = placeStorage(helper, TARGET);
        try (PlayerScope scope = new PlayerScope(factory.apply(ItemStack.EMPTY))) {
            var player = scope.player;
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT));
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            var hit = aim(player, storage.getBlockPos(), 0.5, 0.5);
            callback.click(player, HAND, hit);
            checkEquals(0, count(storage.getItems(), Items.DIRT), "Off-hand item deposited");
            checkEquals(1, player.getOffhandItem().getCount(), "Off-hand item spent");
            player.setShiftKeyDown(true);
            player.setItemInHand(HAND, new ItemStack(Items.REDSTONE_TORCH));
            check(callback.click(player, HAND, hit), "Torch gesture not handled");
            checkEquals(1, storage.getRotation(), "Block rotation");
            checkEquals(0, count(storage.getItems(), Items.REDSTONE_TORCH), "Torch deposited");
            checkEquals(1, player.getMainHandItem().getCount(), "Torch spent");
            player.setItemInHand(HAND, new ItemStack(Items.DIRT));
            callback.click(player, HAND, hit);
            checkEquals(0, count(storage.getItems(), Items.DIRT), "Sneaking deposited");
            checkEquals(1, storage.getRotation(), "Sneaking rotated");
        }
        helper.succeed();
    }

    /**
     * To reproduce: select permanence, sneak, and modifier-click Storage empty-handed twice. Each
     * click toggles it.
     */
    public static void permanenceTogglesWhileSneaking(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            LoaderClick callback) {
        var storage = placeStorage(helper, TARGET);
        try (PlayerScope scope = new PlayerScope(factory.apply(ItemStack.EMPTY))) {
            var player = scope.player;
            player.setShiftKeyDown(true);
            ServerGestureState.set(player, StackMode.TOGGLE_PERMANENT, true);
            var hit = aim(player, storage.getBlockPos(), 0.5, 0.5);
            check(callback.click(player, HAND, hit), "Toggle not consumed");
            check(storage.pile().isPermanent(), "First click did not toggle");
            callback.click(player, HAND, hit);
            check(!storage.pile().isPermanent(), "Second click did not toggle");
        }
        helper.succeed();
    }

    /**
     * To reproduce: fill Singles, select Storage mode, and modifier-click its top. The new block
     * uses the selected type.
     */
    public static void fullTopFaceUsesSelectedType(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            LoaderClick callback) {
        var singles = placeSingles(helper, TARGET);
        for (int slot = 0; slot < singles.getItems().getSlots(); slot++)
            singles.getItems().setStackInSlot(slot, new ItemStack(Items.STONE));
        try (PlayerScope scope = new PlayerScope(factory.apply(new ItemStack(Items.DIRT, 2)))) {
            var player = scope.player;
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            BlockPos pos = singles.getBlockPos();
            callback.click(player, HAND, aimDown(player, pos, 0.125, 0.125));
            check(
                    helper.getLevel()
                            .getBlockState(pos.above())
                            .is(CommonRegistry.STORAGE_STACK_BLOCK.get()),
                    "Selected placement type");
            checkEquals(2, heldAt(helper, pos.above(), Items.DIRT), "Top placement contents");
        }
        helper.succeed();
    }

    /**
     * To reproduce: try placing an ingot in Singles, or an unsupported upper cell. Rejected first
     * deposits leave no block.
     */
    public static void firstDepositFailureLeavesNoBlock(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            LoaderClick callback) {
        BlockPos support = helper.absolutePos(TARGET);
        helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        BlockPos destination = support.east();
        try (PlayerScope scope = new PlayerScope(factory.apply(new ItemStack(firstBarItem())))) {
            var player = scope.player;
            ServerGestureState.set(player, StackMode.SINGLES_STACK, true);
            var hit = aim(player, support, 0.625, 0.125);
            callback.click(
                    player,
                    HAND,
                    new BlockHitResult(hit.getLocation(), Direction.EAST, support, false));
            check(helper.getLevel().getBlockState(destination).isAir(), "Invalid item left block");
            player.setItemInHand(HAND, new ItemStack(Items.STONE));
            callback.click(
                    player,
                    HAND,
                    new BlockHitResult(hit.getLocation(), Direction.EAST, support, false));
            check(
                    helper.getLevel().getBlockState(destination).isAir(),
                    "Unsupported first cell left block");
            checkEquals(1, player.getMainHandItem().getCount(), "Rejected deposit spent hand");
        }
        helper.succeed();
    }

    /**
     * To reproduce: hold snowballs, hold the Stack Modifier, and right-click open air. The mode
     * cycles and no snowball is thrown; release the modifier and right-click again to throw one.
     */
    public static void modifierAirUseLeavesItemUnused(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> factory) {
        try (PlayerScope scope = new PlayerScope(factory.apply(new ItemStack(Items.SNOWBALL, 4)))) {
            var player = scope.player;
            Vec3 at = helper.absoluteVec(Vec3.atCenterOf(TARGET));
            player.setPos(at.x, at.y, at.z);
            var area = new AABB(player.blockPosition()).inflate(4);
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            check(!useItem(player).consumesAction(), "Modifier item use consumed");
            checkEquals(4, player.getMainHandItem().getCount(), "Modifier item use spent item");
            checkEquals(
                    0,
                    helper.getLevel().getEntitiesOfClass(Snowball.class, area).size(),
                    "Modifier item use threw");
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            check(useItem(player).consumesAction(), "Plain item use refused");
            checkEquals(
                    3, player.getMainHandItem().getCount(), "Plain item use did not spend item");
            helper.getLevel().getEntitiesOfClass(Snowball.class, area).forEach(Entity::discard);
        }
        helper.succeed();
    }

    /**
     * Runs vanilla's server item use, which posts the loader's item-use event before the item acts.
     */
    private static InteractionResult useItem(ServerPlayer player) {
        return player.gameMode.useItem(
                player, player.serverLevel(), player.getItemInHand(HAND), HAND);
    }

    /**
     * To reproduce, for each stack type in turn: have an admin disable it (for example
     * enable_singles_stack_block = false) with max_pile_height at least 2, and reload. Select that
     * type and modifier-click the top of a stone block holding an item it accepts: no block
     * appears, the item stays in hand, and the hotbar says that type's placement is disabled. An
     * existing block of that type keeps working: modifier-click its side to deposit one item, then
     * release the modifier and plain-click it empty-handed to take the item back. Fill another
     * existing block and modifier-click its top (for Storage, also its side): it does not grow and
     * the hand keeps its items. A hopper feeding a third full block does not grow it either.
     * Re-enable the type: the same placement, top click, and hopper each add a block.
     */
    public static void disabledTypesRefuseNewBlocksButKeepExistingOnes(
            GameTestHelper helper,
            ServerPlayer messagePlayer,
            List<Component> messages,
            LoaderClick callback,
            Function<BlockEntity, Automation> automation) {
        List<TypeCase> cases = TypeCase.all();
        try (PlayerScope scope = new PlayerScope(messagePlayer)) {
            var player = scope.player;
            for (int row = 0; row < cases.size(); row++) {
                TypeCase type = cases.get(row);
                String name = type.type() + ": ";
                int z = 1 + 4 * row;
                BlockPos support = helper.absolutePos(new BlockPos(1, 1, z));
                helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
                StackBlockEntity existing = type.place(helper, new BlockPos(4, 1, z));
                BlockPos existingPos = existing.getBlockPos();
                BlockPos full = type.placeFull(helper, new BlockPos(7, 1, z)).getBlockPos();
                StackBlockEntity fed = type.placeFull(helper, new BlockPos(10, 1, z));
                BlockPos fedPos = fed.getBlockPos();

                withPolicy(
                        Map.of(type.enable(), false, ServerConfig.Setting.MAX_PILE_HEIGHT, 2),
                        () -> {
                            player.setItemInHand(HAND, new ItemStack(type.item(), 2));
                            ServerGestureState.set(player, type.mode(), true);
                            messages.clear();
                            callback.click(player, HAND, aimDown(player, support, 0.125, 0.125));
                            check(
                                    helper.getLevel().getBlockState(support.above()).isAir(),
                                    name + "disabled type placed");
                            checkEquals(
                                    2,
                                    player.getMainHandItem().getCount(),
                                    name + "refused placement spent hand");
                            check(
                                    hasMessage(messages, "somestacks.message.type_disabled"),
                                    name + "disabled type refused silently: " + messages);

                            // An existing block still takes and gives back items. One item in
                            // hand, since a Storage deposit moves the whole hand stack.
                            player.setItemInHand(HAND, new ItemStack(type.item(), 1));
                            callback.click(
                                    player,
                                    HAND,
                                    aim(player, existingPos, type.sideY(), type.sideZ()));
                            checkEquals(
                                    1,
                                    heldAt(helper, existingPos, type.item()),
                                    name + "existing block refused a deposit");
                            check(
                                    player.getMainHandItem().isEmpty(),
                                    name + "deposit into existing block kept the item");
                            player.setItemInHand(HAND, ItemStack.EMPTY);
                            ServerGestureState.set(player, type.mode(), false);
                            callback.click(
                                    player,
                                    HAND,
                                    aim(player, existingPos, type.sideY(), type.sideZ()));
                            checkEquals(
                                    1,
                                    player.getMainHandItem().getCount(),
                                    name + "existing block refused an extraction");
                            checkEquals(
                                    0,
                                    heldAt(helper, existingPos, type.item()),
                                    name + "extraction left the item stored");

                            // A full block does not grow, by gesture or by automation.
                            player.setItemInHand(HAND, new ItemStack(type.item(), 2));
                            ServerGestureState.set(player, type.mode(), true);
                            callback.click(player, HAND, aimDown(player, full, 0.125, 0.125));
                            if (type.type() == BlockType.STORAGE_STACK) {
                                callback.click(
                                        player,
                                        HAND,
                                        aim(player, full, type.sideY(), type.sideZ()));
                            }
                            check(
                                    helper.getLevel().getBlockState(full.above()).isAir(),
                                    name + "full block grew by gesture");
                            checkEquals(
                                    2,
                                    player.getMainHandItem().getCount(),
                                    name + "refused growth spent hand");
                            Automation hopper = automation.apply(fed);
                            ItemStack offered = new ItemStack(type.item());
                            checkEquals(
                                    1,
                                    hopper.insert(type.perBlock(), offered, true).getCount(),
                                    name + "simulated automation growth accepted");
                            checkEquals(
                                    1,
                                    hopper.insert(type.perBlock(), offered, false).getCount(),
                                    name + "automation growth accepted");
                            check(
                                    helper.getLevel().getBlockState(fedPos.above()).isAir(),
                                    name + "full block grew by automation");
                        });

                withPolicy(
                        Map.of(type.enable(), true, ServerConfig.Setting.MAX_PILE_HEIGHT, 2),
                        () -> {
                            player.setItemInHand(HAND, new ItemStack(type.item(), 2));
                            ServerGestureState.set(player, type.mode(), true);
                            messages.clear();
                            callback.click(player, HAND, aimDown(player, support, 0.125, 0.125));
                            check(
                                    type.isAt(helper, support.above()),
                                    name + "enabled type not placed");
                            check(
                                    !hasMessage(messages, "somestacks.message.type_disabled"),
                                    name + "enabled type reported disabled");
                            player.setItemInHand(HAND, new ItemStack(type.item(), 2));
                            callback.click(player, HAND, aimDown(player, full, 0.125, 0.125));
                            check(
                                    type.isAt(helper, full.above()),
                                    name + "enabled full block did not grow by gesture");
                            check(
                                    automation
                                            .apply(fed)
                                            .insert(
                                                    type.perBlock(),
                                                    new ItemStack(type.item()),
                                                    false)
                                            .isEmpty(),
                                    name + "enabled automation growth refused");
                            check(
                                    type.isAt(helper, fedPos.above()),
                                    name + "enabled full block did not grow by automation");
                        });
            }
        }
        helper.succeed();
    }

    /**
     * To reproduce, for each stack type and for max_pile_height 1 and then 2: build a column of
     * that many full blocks of the type and point a hopper at it; the hopper adds nothing. Select
     * that type and modifier-click the column's top holding an item it accepts: nothing grows and
     * the item stays in hand. Raise max_pile_height by one and reload: the hopper grows the column
     * by one block. The advertised slot counts (only the column's own blocks at the cap, one more
     * block of headroom below it) need a slot-addressing automation probe to see.
     */
    public static void maximumHeightRefusesGrowthAndHidesHeadroom(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            LoaderClick callback,
            Function<BlockEntity, Automation> automation) {
        List<TypeCase> cases = TypeCase.all();
        try (PlayerScope scope = new PlayerScope(factory.apply(ItemStack.EMPTY))) {
            var player = scope.player;
            for (int row = 0; row < cases.size(); row++) {
                TypeCase type = cases.get(row);
                for (int cap = 1; cap <= 2; cap++) {
                    String name = type.type() + " at height " + cap + ": ";
                    BlockPos baseRelative = new BlockPos(2 + 5 * (cap - 1), 1, 1 + 4 * row);
                    StackBlockEntity base = type.placeFull(helper, baseRelative);
                    BlockPos top = base.getBlockPos();
                    for (int level = 1; level < cap; level++) {
                        top = type.placeFull(helper, baseRelative.above(level)).getBlockPos();
                    }
                    BlockPos columnTop = top;
                    int height = cap;
                    withPolicy(
                            Map.of(
                                    ServerConfig.Setting.MAX_PILE_HEIGHT,
                                    height,
                                    type.enable(),
                                    true),
                            () -> {
                                Automation hopper = automation.apply(base);
                                checkEquals(
                                        type.perBlock() * height,
                                        hopper.slots(),
                                        name + "advertised slots");
                                checkEquals(
                                        1,
                                        hopper.insertStacked(new ItemStack(type.item()), true)
                                                .getCount(),
                                        name + "simulated automation grew past the cap");
                                checkEquals(
                                        1,
                                        hopper.insertStacked(new ItemStack(type.item()), false)
                                                .getCount(),
                                        name + "automation grew past the cap");
                                player.setItemInHand(HAND, new ItemStack(type.item(), 2));
                                ServerGestureState.set(player, type.mode(), true);
                                callback.click(
                                        player, HAND, aimDown(player, columnTop, 0.125, 0.125));
                                check(
                                        helper.getLevel().getBlockState(columnTop.above()).isAir(),
                                        name + "column grew past the cap");
                                checkEquals(
                                        2,
                                        player.getMainHandItem().getCount(),
                                        name + "refused growth spent hand");
                            });
                    withPolicy(
                            Map.of(
                                    ServerConfig.Setting.MAX_PILE_HEIGHT,
                                    height + 1,
                                    type.enable(),
                                    true),
                            () -> {
                                Automation hopper = automation.apply(base);
                                checkEquals(
                                        type.perBlock() * (height + 1),
                                        hopper.slots(),
                                        name + "advertised slots below the cap");
                                check(
                                        hopper.insertStacked(new ItemStack(type.item()), false)
                                                .isEmpty(),
                                        name + "automation refused growth below the cap");
                                check(
                                        type.isAt(helper, columnTop.above()),
                                        name + "column did not grow below the cap");
                            });
                }
            }
        }
        helper.succeed();
    }

    /**
     * To reproduce: add minecraft:dirt to disable_items and reload. Select Storage and
     * modifier-click a Storage Stack holding dirt: nothing moves, the dirt stays in hand, and the
     * hotbar names the disabled item. Modifier-clicking the top of a stone block with that dirt
     * places nothing. The same click holding stone deposits it, and a hopper feeding dirt into the
     * stack still works. Now clear disable_items and add minecraft to disable_mods instead: the
     * dirt deposit is refused with the disabled-mod message and the hopper adds nothing more, but a
     * plain click still takes stored items out. Clear disable_mods and the dirt deposit works.
     */
    public static void deniedItemsRefuseDepositsAndOnlyModDenialStopsAutomation(
            GameTestHelper helper,
            ServerPlayer messagePlayer,
            List<Component> messages,
            LoaderClick callback,
            Function<BlockEntity, Automation> automation) {
        StorageStackBE storage = placeStorage(helper, TARGET);
        BlockPos pos = storage.getBlockPos();
        BlockPos support = helper.absolutePos(TARGET.east(3));
        helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        try (PlayerScope scope = new PlayerScope(messagePlayer)) {
            var player = scope.player;
            withPolicy(
                    Map.of(ServerConfig.Setting.DISABLE_ITEMS, List.of("minecraft:dirt")),
                    () -> {
                        player.setItemInHand(HAND, new ItemStack(Items.DIRT, 2));
                        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
                        messages.clear();
                        callback.click(player, HAND, aim(player, pos, 0.2, 0.2));
                        checkEquals(0, heldAt(helper, pos, Items.DIRT), "Denied item deposited");
                        checkEquals(
                                2,
                                player.getMainHandItem().getCount(),
                                "Denied deposit spent hand");
                        check(
                                hasMessage(messages, "somestacks.message.disabled_item"),
                                "Denied item refused silently: " + messages);
                        callback.click(player, HAND, aimDown(player, support, 0.5, 0.5));
                        check(
                                helper.getLevel().getBlockState(support.above()).isAir(),
                                "Denied item placed a stack");
                        checkEquals(
                                2,
                                player.getMainHandItem().getCount(),
                                "Denied placement spent hand");

                        player.setItemInHand(HAND, new ItemStack(Items.STONE, 2));
                        callback.click(player, HAND, aim(player, pos, 0.2, 0.2));
                        checkEquals(2, heldAt(helper, pos, Items.STONE), "Allowed item refused");
                        check(
                                automation
                                        .apply(storage)
                                        .insertStacked(new ItemStack(Items.DIRT, 3), false)
                                        .isEmpty(),
                                "Automation refused an exact-item-denied item");
                        checkEquals(3, heldAt(helper, pos, Items.DIRT), "Automated dirt count");
                    });
            withPolicy(
                    Map.of(ServerConfig.Setting.DISABLE_MODS, List.of("minecraft")),
                    () -> {
                        player.setItemInHand(HAND, new ItemStack(Items.DIRT, 2));
                        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
                        messages.clear();
                        callback.click(player, HAND, aim(player, pos, 0.2, 0.2));
                        checkEquals(3, heldAt(helper, pos, Items.DIRT), "Denied mod deposited");
                        checkEquals(
                                2,
                                player.getMainHandItem().getCount(),
                                "Denied mod deposit spent hand");
                        check(
                                hasMessage(messages, "somestacks.message.disabled_mod"),
                                "Denied mod refused silently: " + messages);
                        checkEquals(
                                1,
                                automation
                                        .apply(storage)
                                        .insertStacked(new ItemStack(Items.DIRT), false)
                                        .getCount(),
                                "Automation accepted a denied mod's item");

                        player.setItemInHand(HAND, ItemStack.EMPTY);
                        ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
                        callback.click(player, HAND, aim(player, pos, 0.2, 0.2));
                        check(
                                !player.getMainHandItem().isEmpty(),
                                "Denied mod's stored items could not be taken out");
                    });
            player.setItemInHand(HAND, new ItemStack(Items.DIRT, 2));
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            int before = heldAt(helper, pos, Items.DIRT);
            callback.click(player, HAND, aim(player, pos, 0.2, 0.2));
            checkEquals(before + 2, heldAt(helper, pos, Items.DIRT), "Undenied deposit refused");
        }
        helper.succeed();
    }

    /**
     * To reproduce: set max_pile_height to 1, fill a Storage Stack with full stacks of stone, and
     * modifier-click it holding dirt. Nothing moves, and the hotbar says the stack is at its
     * maximum height. Empty one slot and the same click deposits the dirt.
     */
    public static void fullMaxHeightDepositExplainsRefusal(
            GameTestHelper helper,
            ServerPlayer messagePlayer,
            List<Component> messages,
            LoaderClick callback) {
        ServerConfig.Settings before = ServerConfig.settings();
        var storage = placeStorage(helper, TARGET);
        try (PlayerScope scope = new PlayerScope(messagePlayer)) {
            var player = scope.player;
            ServerConfig.publish(
                    policy(
                            before,
                            Map.of(
                                    ServerConfig.Setting.MAX_PILE_HEIGHT,
                                    1,
                                    ServerConfig.Setting.ENABLE_STORAGE_STACK_BLOCK,
                                    true)));
            for (int slot = 0; slot < storage.getItems().getSlots(); slot++) {
                storage.getItems()
                        .setStackInSlot(
                                slot, new ItemStack(Items.STONE, Items.STONE.getMaxStackSize()));
            }
            player.setItemInHand(HAND, new ItemStack(Items.DIRT, 2));
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            messages.clear();
            callback.click(player, HAND, aim(player, storage.getBlockPos(), 0.2, 0.2));
            checkEquals(0, count(storage.getItems(), Items.DIRT), "Full pile accepted dirt");
            checkEquals(2, player.getMainHandItem().getCount(), "Refused deposit spent hand");
            check(
                    helper.getLevel().getBlockState(storage.getBlockPos().above()).isAir(),
                    "Pile grew past maximum height");
            check(
                    hasMessage(messages, "somestacks.message.maximum_height"),
                    "Full pile refused silently: " + messages);

            storage.getItems().setStackInSlot(0, ItemStack.EMPTY);
            messages.clear();
            callback.click(player, HAND, aim(player, storage.getBlockPos(), 0.2, 0.2));
            checkEquals(2, count(storage.getItems(), Items.DIRT), "Deposit with room refused");
            check(
                    !hasMessage(messages, "somestacks.message.maximum_height"),
                    "Deposit with room reported maximum height");
        } finally {
            ServerConfig.publish(before);
        }
        helper.succeed();
    }

    /**
     * To reproduce: in Adventure mode, select Storage and modifier-click the top of a stone block
     * holding plain dirt: nothing is placed. Hold dirt whose CanPlaceOn names stone and Storage
     * Stack, and the same click places a Storage Stack holding it. Fill that stack with full stacks
     * of stone and modifier-click it holding plain dirt: it does not grow. Holding the CanPlaceOn
     * dirt, the same click grows a block above holding that dirt.
     */
    public static void adventurePlacementAndGrowthShareTheBuildCheck(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            LoaderClick callback) {
        var level = helper.getLevel();
        BlockPos support = helper.absolutePos(TARGET);
        level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        BlockPos base = support.above();
        BlockPos grown = base.above();
        BlockHitResult top =
                new BlockHitResult(
                        Vec3.atCenterOf(support).add(0, 0.5, 0), Direction.UP, support, false);
        withPolicy(
                Map.of(
                        ServerConfig.Setting.MAX_PILE_HEIGHT,
                        2,
                        ServerConfig.Setting.ENABLE_STORAGE_STACK_BLOCK,
                        true),
                () -> {
                    try (PlayerScope scope =
                            new PlayerScope(factory.apply(new ItemStack(Items.DIRT, 2)))) {
                        var player = scope.player;
                        boolean mayBuild = player.getAbilities().mayBuild;
                        player.getAbilities().mayBuild = false;
                        try {
                            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
                            aim(player, support, 0.5, 0.5);
                            callback.click(player, HAND, top);
                            check(level.getBlockState(base).isAir(), "Placed without CanPlaceOn");
                            checkEquals(
                                    2, player.getMainHandItem().getCount(), "Refusal spent hand");

                            player.setItemInHand(HAND, placeableDirt(2));
                            callback.click(player, HAND, top);
                            check(
                                    level.getBlockState(base)
                                            .is(CommonRegistry.STORAGE_STACK_BLOCK.get()),
                                    "CanPlaceOn placement refused");

                            var items = ((StackBlockEntity) level.getBlockEntity(base)).getItems();
                            for (int slot = 0; slot < items.getSlots(); slot++) {
                                items.setStackInSlot(slot, new ItemStack(Items.STONE, 64));
                            }
                            player.setItemInHand(HAND, new ItemStack(Items.DIRT, 2));
                            callback.click(player, HAND, aim(player, base, 0.2, 0.2));
                            check(level.getBlockState(grown).isAir(), "Grew without CanPlaceOn");
                            checkEquals(
                                    2, player.getMainHandItem().getCount(), "Refusal spent hand");

                            player.setItemInHand(HAND, placeableDirt(2));
                            callback.click(player, HAND, aim(player, base, 0.2, 0.2));
                            check(
                                    level.getBlockState(grown)
                                            .is(CommonRegistry.STORAGE_STACK_BLOCK.get()),
                                    "CanPlaceOn growth refused");
                            checkEquals(2, heldAt(helper, grown, Items.DIRT), "Grown contents");
                        } finally {
                            player.getAbilities().mayBuild = mayBuild;
                        }
                    }
                });
        helper.succeed();
    }

    /** Dirt an Adventure mode player may place against stone and against a Storage Stack. */
    private static ItemStack placeableDirt(int count) {
        ListTag canPlaceOn = new ListTag();
        for (var block : List.of(Blocks.STONE, CommonRegistry.STORAGE_STACK_BLOCK.get())) {
            canPlaceOn.add(StringTag.valueOf(BuiltInRegistries.BLOCK.getKey(block).toString()));
        }
        ItemStack stack = new ItemStack(Items.DIRT, count);
        stack.getOrCreateTag().put("CanPlaceOn", canPlaceOn);
        return stack;
    }

    /**
     * To reproduce: select Bar and modifier-click the side of an empty Bar Stack holding dirt.
     * Nothing moves and the hand keeps the dirt; the same click holding an ingot deposits one.
     */
    public static void barGestureDepositRefusesNonIngots(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            LoaderClick callback) {
        var bars = placeBar(helper, TARGET);
        try (PlayerScope scope = new PlayerScope(factory.apply(new ItemStack(Items.DIRT, 2)))) {
            var player = scope.player;
            ServerGestureState.set(player, StackMode.BAR_STACK, true);
            callback.click(player, HAND, aim(player, bars.getBlockPos(), 0.0625, 0.125));
            checkEquals(0, occupied(bars.getItems()), "Bar took a non-ingot");
            checkEquals(2, player.getMainHandItem().getCount(), "Refused deposit spent hand");

            player.setItemInHand(HAND, new ItemStack(firstBarItem(), 2));
            callback.click(player, HAND, aim(player, bars.getBlockPos(), 0.0625, 0.125));
            checkEquals(1, occupied(bars.getItems()), "Bar refused an ingot");
            checkEquals(1, player.getMainHandItem().getCount(), "Deposit did not spend one ingot");
        }
        helper.succeed();
    }

    /**
     * To reproduce: stand in a claim you are not trusted in, or anywhere a protection mod denies
     * you block interaction, beside a Storage Stack holding three stone and a Singles Stack holding
     * one item. V-deposit a stick into the Storage Stack; plain-click it empty-handed; select
     * Toggle Permanent and sneak-modifier-click it empty-handed; sneak-click it with a redstone
     * torch; sneak-click the Singles item with a soul torch. Nothing about either stack changes,
     * and the stick and the empty hand stay as they were. Switch to Spectator mode outside the
     * claim and repeat: again nothing changes. Back in Survival outside the claim, all five apply.
     *
     * <p>The torch steps assert the stacks, not the hand: a loader may let a held item act as an
     * ordinary item when only block use is denied (Forge's {@code setUseBlock(DENY)} leaves the
     * torch free to be placed beside the stack).
     */
    public static void blockUseDenialFreezesStackGestures(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            LoaderClick callback,
            BiConsumer<ServerPlayer, BlockPos> denyBlockUse,
            Runnable allowBlockUse) {
        var storage = placeStorage(helper, TARGET);
        var singles = placeSingles(helper, TARGET.east(3));
        storage.getItems().setStackInSlot(0, new ItemStack(Items.STONE, 3));
        singles.getItems().setStackInSlot(0, new ItemStack(Items.STONE));
        try (PlayerScope scope = new PlayerScope(factory.apply(ItemStack.EMPTY))) {
            var player = scope.player;
            expectGesturesRefused(
                    "Denied",
                    player,
                    callback,
                    storage,
                    singles,
                    pos -> denyBlockUse.accept(player, pos),
                    allowBlockUse);
            GameType previous = player.gameMode.getGameModeForPlayer();
            player.setGameMode(GameType.SPECTATOR);
            try {
                expectGesturesRefused(
                        "Spectator", player, callback, storage, singles, pos -> {}, () -> {});
            } finally {
                player.setGameMode(previous);
            }

            BlockPos pile = storage.getBlockPos();
            player.setItemInHand(HAND, new ItemStack(Items.STICK));
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            callback.click(player, HAND, aim(player, pile, 0.2, 0.2));
            checkEquals(1, count(storage.getItems(), Items.STICK), "Allowed deposit");
            check(player.getMainHandItem().isEmpty(), "Allowed deposit kept the stick");
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            callback.click(player, HAND, aim(player, pile, 0.2, 0.2));
            checkEquals(Items.STONE, player.getMainHandItem().getItem(), "Allowed extraction");
            checkEquals(3, player.getMainHandItem().getCount(), "Allowed extraction count");
            player.setItemInHand(HAND, ItemStack.EMPTY);
            player.setShiftKeyDown(true);
            ServerGestureState.set(player, StackMode.TOGGLE_PERMANENT, true);
            callback.click(player, HAND, aim(player, pile, 0.5, 0.5));
            check(storage.pile().isPermanent(), "Allowed toggle");
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            player.setItemInHand(HAND, new ItemStack(Items.REDSTONE_TORCH));
            callback.click(player, HAND, aim(player, pile, 0.5, 0.5));
            checkEquals(1, storage.getRotation(), "Allowed block rotation");
            player.setItemInHand(HAND, new ItemStack(Items.SOUL_TORCH));
            callback.click(player, HAND, aim(player, singles.getBlockPos(), 0.125, 0.125));
            checkEquals(1, singles.getCubeRotation(0), "Allowed item rotation");
        }
        helper.succeed();
    }

    /**
     * Runs the five gestures of {@link #blockUseDenialFreezesStackGestures}, calling {@code deny}
     * with each gesture's block before its click and {@code allow} after it, and requires each to
     * leave its stack unchanged.
     */
    private static void expectGesturesRefused(
            String label,
            ServerPlayer player,
            LoaderClick callback,
            StorageStackBE storage,
            SinglesStackBE singles,
            Consumer<BlockPos> deny,
            Runnable allow) {
        BlockPos pile = storage.getBlockPos();
        player.setShiftKeyDown(false);
        player.setItemInHand(HAND, new ItemStack(Items.STICK));
        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        clickWhile(
                deny, allow, pile, () -> callback.click(player, HAND, aim(player, pile, 0.2, 0.2)));
        checkEquals(0, count(storage.getItems(), Items.STICK), label + " deposit stored the stick");
        checkEquals(1, player.getMainHandItem().getCount(), label + " deposit spent the stick");

        player.setItemInHand(HAND, ItemStack.EMPTY);
        ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
        clickWhile(
                deny, allow, pile, () -> callback.click(player, HAND, aim(player, pile, 0.2, 0.2)));
        check(player.getMainHandItem().isEmpty(), label + " extraction filled the hand");
        checkEquals(3, count(storage.getItems(), Items.STONE), label + " extraction took stone");

        player.setShiftKeyDown(true);
        ServerGestureState.set(player, StackMode.TOGGLE_PERMANENT, true);
        clickWhile(
                deny, allow, pile, () -> callback.click(player, HAND, aim(player, pile, 0.5, 0.5)));
        check(!storage.pile().isPermanent(), label + " toggle made the pile permanent");

        ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
        player.setItemInHand(HAND, new ItemStack(Items.REDSTONE_TORCH));
        clickWhile(
                deny, allow, pile, () -> callback.click(player, HAND, aim(player, pile, 0.5, 0.5)));
        checkEquals(0, storage.getRotation(), label + " redstone torch rotated the block");

        BlockPos cell = singles.getBlockPos();
        player.setItemInHand(HAND, new ItemStack(Items.SOUL_TORCH));
        clickWhile(
                deny,
                allow,
                cell,
                () -> callback.click(player, HAND, aim(player, cell, 0.125, 0.125)));
        checkEquals(0, singles.getCubeRotation(0), label + " soul torch rotated the item");
        checkEquals(
                1, count(singles.getItems(), Items.STONE), label + " soul torch moved the item");
        player.setShiftKeyDown(false);
        player.setItemInHand(HAND, ItemStack.EMPTY);
    }

    private static void clickWhile(
            Consumer<BlockPos> deny, Runnable allow, BlockPos pos, Runnable click) {
        deny.accept(pos);
        try {
            click.run();
        } finally {
            allow.run();
        }
    }

    private static boolean hasMessage(List<Component> messages, String key) {
        return messages.stream()
                .anyMatch(message ->
                        message.getContents() instanceof TranslatableContents contents
                                && contents.getKey().equals(key));
    }

    /**
     * No in-game form: the client keeps its selected mode across connections and re-sends its state
     * with the first click after reconnecting, so a player sees no difference. It guards that the
     * server records the mode and modifier a gesture-state packet reports, and that logout removes
     * that per-player record so the server falls back to Storage mode with the modifier released.
     */
    public static void stateAndLogout(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            LoaderClick callback) {
        try (PlayerScope scope = new PlayerScope(factory.apply(ItemStack.EMPTY))) {
            var player = scope.player;
            GestureStatePkt.handleServer(new GestureStatePkt(StackMode.BAR_STACK, true), player);
            checkEquals(
                    new ServerGestureState.State(StackMode.BAR_STACK, true),
                    ServerGestureState.get(player),
                    "Synced state");
            ServerLifecycle.logout(player.getUUID());
            checkEquals(
                    new ServerGestureState.State(StackMode.STORAGE_STACK, false),
                    ServerGestureState.get(player),
                    "Logout state");
        }
        helper.succeed();
    }

    /**
     * To reproduce: put a sculk sensor beside a Storage, a Singles, and a Bar Stack. Deposit into
     * and extract from Storage, toggle its permanence, and rotate it with a redstone torch; rotate
     * a Singles item with a soul torch. Each successful use vibrates. Extract one of two Singles
     * items or bars, then the last: the first vibrates as a change, the last only as the block's
     * destruction.
     */
    public static void successfulUseEmitsBlockChange(
            GameTestHelper helper,
            Function<ItemStack, ServerPlayer> factory,
            LoaderClick callback) {
        var storage = placeStorage(helper, TARGET);
        var singles = placeSingles(helper, TARGET.east(3));
        var bars = placeBar(helper, TARGET.east(6));
        var barItem = firstBarItem();
        singles.getItems().setStackInSlot(0, new ItemStack(Items.STONE));
        singles.getItems().setStackInSlot(1, new ItemStack(Items.STONE));
        bars.getItems().setStackInSlot(0, new ItemStack(barItem));
        bars.getItems().setStackInSlot(1, new ItemStack(barItem));
        try (PlayerScope scope = new PlayerScope(factory.apply(new ItemStack(Items.DIRT)))) {
            var player = scope.player;
            BlockPos pile = storage.getBlockPos();
            ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
            expectUseEvent(
                    helper,
                    player,
                    pile,
                    GameEvent.BLOCK_CHANGE,
                    () -> callback.click(player, HAND, aim(player, pile, 0.2, 0.2)));
            checkEquals(1, count(storage.getItems(), Items.DIRT), "Deposit contents");
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            expectUseEvent(
                    helper,
                    player,
                    pile,
                    GameEvent.BLOCK_CHANGE,
                    () -> callback.click(player, HAND, aim(player, pile, 0.2, 0.2)));
            checkEquals(0, count(storage.getItems(), Items.DIRT), "Extraction contents");
            player.setItemInHand(HAND, ItemStack.EMPTY);
            ServerGestureState.set(player, StackMode.TOGGLE_PERMANENT, true);
            expectUseEvent(
                    helper,
                    player,
                    pile,
                    GameEvent.BLOCK_CHANGE,
                    () -> callback.click(player, HAND, aim(player, pile, 0.5, 0.5)));
            check(storage.pile().isPermanent(), "Toggle did not apply");
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            player.setShiftKeyDown(true);
            player.setItemInHand(HAND, new ItemStack(Items.REDSTONE_TORCH));
            expectUseEvent(
                    helper,
                    player,
                    pile,
                    GameEvent.BLOCK_CHANGE,
                    () -> callback.click(player, HAND, aim(player, pile, 0.5, 0.5)));
            checkEquals(1, storage.getRotation(), "Block rotation");
            player.setItemInHand(HAND, new ItemStack(Items.SOUL_TORCH));
            expectUseEvent(
                    helper,
                    player,
                    singles.getBlockPos(),
                    GameEvent.BLOCK_CHANGE,
                    () ->
                            callback.click(
                                    player,
                                    HAND,
                                    aim(player, singles.getBlockPos(), 0.125, 0.125)));
            checkEquals(1, singles.getCubeRotation(0), "Item rotation");
            player.setShiftKeyDown(false);
            for (var be : new StackBlockEntity[] {singles, bars}) {
                BlockPos pos = be.getBlockPos();
                double y = be == bars ? 0.0625 : 0.125;
                player.setItemInHand(HAND, ItemStack.EMPTY);
                expectUseEvent(
                        helper,
                        player,
                        pos,
                        GameEvent.BLOCK_CHANGE,
                        () -> callback.click(player, HAND, aim(player, pos, y, 0.125)));
                checkEquals(1, player.getMainHandItem().getCount(), "First extraction");
                expectUseEvent(
                        helper,
                        player,
                        pos,
                        GameEvent.BLOCK_DESTROY,
                        () -> callback.click(player, HAND, aim(player, pos, y, 0.125)));
                checkEquals(2, player.getMainHandItem().getCount(), "Last extraction");
            }
        }
        helper.succeed();
    }

    /**
     * Runs one gesture and requires exactly {@code event} at {@code pos}, attributed to the player.
     */
    private static void expectUseEvent(
            GameTestHelper helper,
            ServerPlayer player,
            BlockPos pos,
            GameEvent event,
            Runnable gesture) {
        var level = helper.getLevel();
        var before = level.getBlockState(pos);
        StackInteractions.clearRotationSoundPacing(player.getUUID());
        try (var events = new WorldEditChecks.EventProbe(level, pos)) {
            gesture.run();
            events.expectOne(
                    event,
                    player,
                    event == GameEvent.BLOCK_DESTROY ? before : level.getBlockState(pos));
        }
    }

    /**
     * Positions the eye west of the named row and aims east, independently of client hit
     * coordinates.
     */
    public static BlockHitResult aim(ServerPlayer player, BlockPos pos, double y, double z) {
        player.setPos(pos.getX() - 2, pos.getY() + y - player.getEyeHeight(), pos.getZ() + z);
        player.setXRot(0);
        player.setYRot(-90);
        return new BlockHitResult(
                new Vec3(pos.getX(), pos.getY() + y, pos.getZ() + z), Direction.WEST, pos, false);
    }

    /**
     * Stands the player two blocks above {@code pos} looking straight down and returns a hit on its
     * top face at block-local {@code x}, {@code z}, so the ray continues into the block above.
     */
    public static BlockHitResult aimDown(ServerPlayer player, BlockPos pos, double x, double z) {
        player.setPos(pos.getX() + x, pos.getY() + 2, pos.getZ() + z);
        player.setXRot(90);
        player.setYRot(0);
        return new BlockHitResult(
                new Vec3(pos.getX() + x, pos.getY() + 1, pos.getZ() + z), Direction.UP, pos, false);
    }

    /**
     * Restores the loader's possibly shared fake player and clears session state after a scenario.
     */
    public static final class PlayerScope implements AutoCloseable {
        public final ServerPlayer player;
        private final Vec3 position;
        private final float xRot, yRot;
        private final boolean shift, creative;
        private final ItemStack offhand;

        public PlayerScope(ServerPlayer player) {
            this.player = player;
            position = player.position();
            xRot = player.getXRot();
            yRot = player.getYRot();
            shift = player.isShiftKeyDown();
            creative = player.getAbilities().instabuild;
            offhand = player.getOffhandItem().copy();
            player.setShiftKeyDown(false);
            player.getAbilities().instabuild = false;
            ServerGestureState.clear(player.getUUID());
            StackInteractions.clearRotationSoundPacing(player.getUUID());
        }

        @Override
        public void close() {
            player.setPos(position.x, position.y, position.z);
            player.setXRot(xRot);
            player.setYRot(yRot);
            player.setShiftKeyDown(shift);
            player.getAbilities().instabuild = creative;
            player.setItemInHand(HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, offhand);
            ServerGestureState.clear(player.getUUID());
            StackInteractions.clearRotationSoundPacing(player.getUUID());
        }
    }
}
