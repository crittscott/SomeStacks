package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.client.ClientEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

public final class DepositIntoAdjacentStackRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        if (!ctx.isVDown() || ctx.isShift() || !ctx.hasItemInHand() || !ctx.canPlaceOrDeposit()) {
            return false;
        }

        // Check if the block ADJACENT to where we clicked (in the direction of the face) is a Singles/Bar stack
        BlockPos adjacentPos = ctx.getClickedPos().relative(ctx.getFace());
        Block adjacentBlock = ctx.getLevel().getBlockState(adjacentPos).getBlock();

        return ctx.isSinglesOrBarStack(adjacentBlock);
    }

    @Override
    public void execute(InteractionContext ctx) {
        SomeStacks.LOGGER.debug("Executing DepositIntoAdjacentStackRule");
        if (ctx.isClientSide() && ctx.isMainHand()) {
            BlockPos adjacentPos = ctx.getClickedPos().relative(ctx.getFace());
            ClientEvents.sendDeposit(adjacentPos);
        }
        ctx.cancelEvent();
    }
}
