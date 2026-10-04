package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Fabric's {@link EditAuthority}: the automation actor is a synthetic {@link ServerPlayer}, and
 * removal answers to Fabric API's block-break event, the counterpart to Forge's block-break event,
 * so claim and protection mods can veto automation-driven removal the same way they can on Forge.
 *
 * <p>Placement has no comparable vanilla click to consult: automated growth has no player gesture
 * for the mod's adjacent-target protection hook ({@link FabricAdjacentEditAuthority}) to fire, and Fabric
 * API has no generic "a block was placed" event the way Forge's block-place event is. Claim mods
 * that implement Common Protection API are consulted directly instead, through
 * {@link CommonProtectionCheck}, when the API is installed; other claim mods are not covered.
 */
public final class FabricEditAuthority implements EditAuthority {
    public FabricEditAuthority() {
        if (CommonProtectionCheck.isLoaded()) {
            SomeStacksCommon.LOGGER.info("Enabled Common Protection API checks for automated growth on Fabric");
        }
    }

    @Override
    public ServerPlayer automationActor(ServerLevel level) {
        return FakePlayer.get(level, AutomationActor.PROFILE);
    }

    @Override
    public PlacementVeto preparePlacement(ServerLevel level, BlockPos pos) {
        return (placer, placedAgainst) -> {
            ServerPlayer actor = (ServerPlayer) placer;
            return CommonProtectionCheck.isLoaded() && CommonProtectionCheck.prevents(level, actor, pos);
        };
    }

    @Override
    public boolean vetoesRemoval(
            ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        ServerPlayer breaker = automationActor(level);
        boolean allowed = PlayerBlockBreakEvents.BEFORE.invoker()
                .beforeBlockBreak(level, breaker, pos, state, blockEntity);
        if (!allowed) {
            PlayerBlockBreakEvents.CANCELED.invoker()
                    .onBlockBreakCanceled(level, breaker, pos, state, blockEntity);
        }
        return !allowed;
    }

    @Override
    public void afterRemoval(
            ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        PlayerBlockBreakEvents.AFTER.invoker()
                .afterBlockBreak(level, automationActor(level), pos, state, blockEntity);
    }
}
