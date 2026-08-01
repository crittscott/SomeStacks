package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.client.ClientEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

/**
 * Modifier plus an item, clicking a face of some other block that has a Singles or Bar Stack
 * against it, deposits into that stack. The click reaches the stack through its neighbour, so the
 * packet names both positions and the server checks protection at each.
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
        SomeStacks.LOGGER.debug("Executing DepositIntoAdjacentStackRule");
        if (ctx.isClientSide() && ctx.isMainHand()) {
            BlockPos adjacentPos = ctx.getClickedPos().relative(ctx.getFace());
            ClientEvents.sendDeposit(adjacentPos, ctx.getClickedPos());
        }
        ctx.cancelEvent();
    }
}
