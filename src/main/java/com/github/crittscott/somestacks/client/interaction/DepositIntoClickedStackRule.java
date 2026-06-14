package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.client.ClientEvents;

public final class DepositIntoClickedStackRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        return ctx.isVDown()
                && !ctx.isShift()
                && ctx.hasItemInHand()
                && ctx.isAnyStackBlock()
                && ctx.canPlaceOrDeposit();
    }

    @Override
    public void execute(InteractionContext ctx) {
        SomeStacks.LOGGER.debug("Executing DepositIntoClickedStackRule");
        if (ctx.isClientSide() && ctx.isMainHand()) {
            ClientEvents.sendDeposit(ctx.getClickedPos());
        }
        ctx.cancelEvent();
    }
}
