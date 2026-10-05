package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Fabric's {@link EditAuthority}: the automation actor is a synthetic {@link ServerPlayer}, and
 * removal answers to Fabric API's block-break event, the counterpart to Forge's block-break event,
 * so claim and protection mods can veto automation-driven removal the same way they can on Forge.
 *
 * <p>Player placement consults the destination through {@link FabricAdjacentEditAuthority} before
 * changing the world. Automated growth has no corresponding click, and Fabric API has no generic
 * block-place event, so claim mods that implement Common Protection API are consulted directly
 * through {@link CommonProtectionCheck}. Both checks run before placement.
 */
public final class FabricEditAuthority implements EditAuthority {
    public FabricEditAuthority() {
        if (CommonProtectionCheck.isLoaded()) {
            SomeStacksCommon.LOGGER.info("Enabled Common Protection API checks for placement on Fabric");
        }
    }

    @Override
    public ServerPlayer automationActor(ServerLevel level) {
        return FakePlayer.get(level, AutomationActor.PROFILE);
    }

    @Override
    public PlacementVeto preparePlacement(ServerLevel level, BlockPos pos) {
        return new PlacementVeto() {
            @Override
            public boolean isVetoedBefore(Player placer, Direction placedAgainst) {
                ServerPlayer actor = (ServerPlayer) placer;
                if (!AutomationActor.is(actor) && !AdjacentEdits.mayUseItemAt(actor, pos)) {
                    return true;
                }
                return CommonProtectionCheck.isLoaded()
                        && CommonProtectionCheck.prevents(level, actor, pos);
            }
        };
    }

    @Override
    public boolean vetoesRemoval(
            ServerPlayer actor, ServerLevel level, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity) {
        boolean allowed = PlayerBlockBreakEvents.BEFORE.invoker()
                .beforeBlockBreak(level, actor, pos, state, blockEntity);
        if (!allowed) {
            PlayerBlockBreakEvents.CANCELED.invoker()
                    .onBlockBreakCanceled(level, actor, pos, state, blockEntity);
        }
        return !allowed;
    }

    @Override
    public void afterRemoval(
            ServerPlayer actor, ServerLevel level, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity) {
        PlayerBlockBreakEvents.AFTER.invoker()
                .afterBlockBreak(level, actor, pos, state, blockEntity);
    }
}
