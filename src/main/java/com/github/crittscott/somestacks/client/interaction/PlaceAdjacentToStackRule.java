package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.client.ClientEvents;

public final class PlaceAdjacentToStackRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        return ctx.isShift()
                && ctx.isVDown()
                && ctx.hasItemInHand()
                && ctx.isAnyStackBlock()
                && ctx.canPlaceOrDeposit();
    }

    @Override
    public void execute(InteractionContext ctx) {
        SomeStacks.LOGGER.debug("Executing PlaceAdjacentToStackRule");
        if (ctx.isClientSide() && ctx.isMainHand()) {
            ClientEvents.sendPlaceAndDeposit(
                    ctx.getClickedPos().relative(ctx.getFace()),
                    ctx.getFace()
            );
        }
        ctx.cancelEvent();
    }
}
