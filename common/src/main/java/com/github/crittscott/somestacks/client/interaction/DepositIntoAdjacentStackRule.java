package com.github.crittscott.somestacks.client.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

/**
 * Modifier plus an item, clicking a face of some other block that has a Singles or Bar Stack
 * against it, deposits into that stack. The vanilla click names the neighbor, and the server also
 * checks protection at the adjacent stack before depositing.
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
