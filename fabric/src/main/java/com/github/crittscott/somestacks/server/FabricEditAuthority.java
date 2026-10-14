package com.github.crittscott.somestacks.server;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import javax.annotation.Nullable;

/**
 * Fabric's {@link EditAuthority}: the automation actor is Fabric API's native {@link FakePlayer}, and
 * removal answers to Fabric API's block-break event, the counterpart to Forge's block-break event,
 * so claim and protection mods can veto automation-driven removal the same way they can on Forge.
 * Vetoed removals report cancellation; successful removals report completion with the original
 * actor, block state, and block entity.
 *
 * <p>Placement has no comparable vanilla click to consult: automated growth has no player gesture
 * for the mod's placement-protection hook (the native block-use callback) to fire, and Fabric
 * API has no generic "a block was placed" event the way Forge's block-place event is. FTB Chunks
 * and Open Parties and Claims are consulted directly instead, through {@link FtbChunksProtection}
 * and {@link OpacProtection}, when either is installed; other claim mods are not covered.
 */
public final class FabricEditAuthority implements EditAuthority {
    @Override
    public ServerPlayer automationActor(ServerLevel level) {
        return FakePlayer.get(level);
    }

    @Override
    public boolean place(Player placer, ServerLevel level, BlockPos pos,
                         BlockState state, Direction placedAgainst) {
        ServerPlayer actor = (ServerPlayer) placer;
        if ((FtbChunksProtection.isLoaded() && FtbChunksProtection.prevents(actor, pos))
                || (OpacProtection.isLoaded() && OpacProtection.prevents(level, actor, pos))) return false;
        return level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    @Override
    public boolean mayUseAdjacent(ServerPlayer player, BlockPos pos) {
        return UseBlockCallback.EVENT.invoker().interact(player, player.level(),
                InteractionHand.MAIN_HAND, WorldEdits.adjacentHit(player, pos)) == InteractionResult.PASS;
    }

    @Override
    public boolean vetoesRemoval(ServerLevel level, ServerPlayer actor, BlockPos pos,
                                 BlockState state, @Nullable BlockEntity blockEntity) {
        if (PlayerBlockBreakEvents.BEFORE.invoker()
                .beforeBlockBreak(level, actor, pos, state, blockEntity)) return false;
        PlayerBlockBreakEvents.CANCELED.invoker()
                .onBlockBreakCanceled(level, actor, pos, state, blockEntity);
        return true;
    }

    @Override
    public void afterRemoval(ServerLevel level, ServerPlayer actor, BlockPos pos,
                             BlockState state, @Nullable BlockEntity blockEntity) {
        PlayerBlockBreakEvents.AFTER.invoker()
                .afterBlockBreak(level, actor, pos, state, blockEntity);
    }
}
