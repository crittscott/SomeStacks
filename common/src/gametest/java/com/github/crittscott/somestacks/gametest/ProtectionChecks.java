package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.StackBlock;
import com.github.crittscott.somestacks.network.DepositPkt;
import com.github.crittscott.somestacks.network.PlaceAndDepositPkt;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.BlockType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import java.util.function.Function;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/** Loader-neutral placement and player-deposit protection scenarios. */
public final class ProtectionChecks {
    private ProtectionChecks() {}

    public static void checkedPlacementPlacesInBoundsAndRejectsOutsideBuildHeight(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        BlockPos valid = helper.absolutePos(ORIGIN);
        BlockPos invalid = new BlockPos(valid.getX(), level.getMinY() - 1, valid.getZ());

        check(WorldEdits.placeChecked(
                        player, level, valid, Blocks.STONE.defaultBlockState(), Direction.DOWN),
                "Valid checked placement was rejected");
        check(level.getBlockState(valid).is(Blocks.STONE),
                "Valid checked placement did not change the world");
        check(!WorldEdits.placeChecked(
                        player, level, invalid, Blocks.STONE.defaultBlockState(), Direction.DOWN),
                "Out-of-height checked placement was accepted");
        helper.succeed();
    }

    public static void checkedPlacementRejectsAnObstructingEntity(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
        BlockPos target = helper.absolutePos(ORIGIN);
        putCowIn(helper, ORIGIN);

        check(!WorldEdits.placeChecked(
                        player, level, target, Blocks.STONE.defaultBlockState(), Direction.DOWN),
                "Entity-obstructed checked placement was accepted");
        helper.assertBlockNotPresent(Blocks.STONE, ORIGIN);
        helper.succeed();
    }

    public static void creativeDepositFillsTheStackWithoutSpendingTheHand(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.DIRT, 64));
        BlockPos target = helper.absolutePos(ORIGIN);
        GameTestScaffold.placeStorage(helper, ORIGIN);

        player.getAbilities().instabuild = true;
        try {
            DepositPkt.apply(player, new DepositPkt(target, target));
            checkEquals(64, player.getMainHandItem().getCount(),
                    "A creative deposit spent the held stack");
        } finally {
            player.getAbilities().instabuild = false;
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }

        checkEquals(64, GameTestScaffold.heldAt(helper, target, Items.DIRT),
                "Creative deposit did not reach the stack");
        helper.succeed();
    }

    public static void placementIntoWaterKeepsTheWater(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.DIRT, 64));
        BlockPos target = helper.absolutePos(ORIGIN);
        level.setBlock(target, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);

        try {
            PlaceAndDepositPkt.apply(player, new PlaceAndDepositPkt(
                    BlockType.STORAGE_STACK, Direction.UP, target));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }

        helper.assertBlockPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN);
        check(level.getBlockState(target).getValue(StackBlock.WATERLOGGED),
                "A stack placed into water was not waterlogged");
        check(level.getFluidState(target).getType() == Fluids.WATER,
                "A stack placed into water swallowed the water");
        helper.succeed();
    }

    private static void putCowIn(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(relative);
        Cow cow = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
        check(cow != null, "Could not create obstruction cow");
        check(cow.blocksBuilding, "Cow does not block building");
        cow.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.0F, 0.0F);
        check(level.addFreshEntity(cow), "Could not add obstruction cow");
    }
}
