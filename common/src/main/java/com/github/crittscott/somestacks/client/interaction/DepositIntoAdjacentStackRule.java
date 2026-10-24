package com.github.crittscott.somestacks.client.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

/**
 * Consumes a modified neighbor click toward a Singles or Bar Stack. The server decides whether
 * the loader can authorize the destination without another click; otherwise it refuses the deposit.
 */
public final class DepositIntoAdjacentStackRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        if (!ctx.isVDown() || ctx.isShift() || !ctx.hasItemInHand() || !ctx.canPlaceOrDeposit()) {
            return false;
        }

        BlockPos adjacentPos = ctx.getClickedPos().relative(ctx.getFace());
        Block adjacentBlock = ctx.getLevel().getBlockState(adjacentPos).getBlock();

        return ctx.isSinglesOrBarStack(adjacentBlock);
    }

    @Override
    public void execute(InteractionContext ctx) {
        ctx.cancelEvent();
    }
}
