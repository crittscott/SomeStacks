package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.StackBlock;
import com.github.crittscott.somestacks.server.AutomationActor;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.server.StackInteractions;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.StackMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.putCowIn;

/** Loader-neutral placement and player-deposit protection scenarios. */
public final class ProtectionChecks {
    private ProtectionChecks() {}

    /**
     * Every loader attributes automation to the same [SomeStacks] profile. To reproduce in-game:
     * configure a claim or logging mod to report the actor for machine-driven stack growth or
     * cleanup. It reports [SomeStacks] with the shared profile identity.
     */
    public static void automationUsesSharedIdentity(GameTestHelper helper) {
        var actual = WorldEdits.automationActor(helper.getLevel()).getGameProfile();
        checkEquals(AutomationActor.PROFILE.getId(), actual.getId(), "Automation UUID");
        checkEquals(AutomationActor.PROFILE.getName(), actual.getName(), "Automation name");
        helper.succeed();
    }

    /**
     * Checked world edits place inside build height and reject positions outside it. To reproduce
     * in-game: grow or place a stack in ordinary space, then attempt the same edit at the world's
     * vertical limit. Only the in-bounds placement succeeds.
     */
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

    /**
     * Stack placement refuses a living entity intersecting its final collision shape. To reproduce
     * in-game: stand or put a mob in the occupied part of a proposed stack and try to place or grow
     * it. No stack block appears.
     */
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

    /**
     * Creative deposits fill a stack without consuming the held items. To reproduce in-game:
     * switch to Creative, hold the modifier, and deposit a full held stack into Storage. Storage
     * receives the items while the hand retains its original count.
     */
    public static void creativeDepositFillsTheStackWithoutSpendingTheHand(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.DIRT, 64));
        BlockPos target = helper.absolutePos(ORIGIN);
        GameTestScaffold.placeStorage(helper, ORIGIN);

        player.getAbilities().instabuild = true;
        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        try {
            StackInteractions.handleExistingStack(
                    player, InteractionHand.MAIN_HAND, centerHit(target));
            checkEquals(64, player.getMainHandItem().getCount(),
                    "A creative deposit spent the held stack");
        } finally {
            player.getAbilities().instabuild = false;
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }

        checkEquals(64, GameTestScaffold.heldAt(helper, target, Items.DIRT),
                "Creative deposit did not reach the stack");
        helper.succeed();
    }

    /**
     * Placing a stack into water waterlogs it instead of removing the fluid. To reproduce in-game:
     * hold the modifier and place a new stack into a water source. The block appears waterlogged and
     * the water remains present.
     */
    public static void placementIntoWaterKeepsTheWater(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = playerFactory.apply(new ItemStack(Items.DIRT, 64));
        BlockPos target = helper.absolutePos(ORIGIN);
        level.setBlock(target, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos support = target.below();

        ServerGestureState.set(player, StackMode.STORAGE_STACK, true);
        try {
            StackInteractions.handleAdjacentClick(
                    player,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                            new Vec3(support.getX() + 0.5, support.getY() + 1.0,
                                    support.getZ() + 0.5),
                            Direction.UP,
                            support,
                            false),
                    true,
                    true);
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            ServerGestureState.clear(player.getUUID());
        }

        helper.assertBlockPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN);
        check(level.getBlockState(target).getValue(StackBlock.WATERLOGGED),
                "A stack placed into water was not waterlogged");
        check(level.getFluidState(target).getType() == Fluids.WATER,
                "A stack placed into water swallowed the water");
        helper.succeed();
    }

    private static BlockHitResult centerHit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

}
