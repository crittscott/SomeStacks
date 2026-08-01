package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.client.ClientEvents;

/**
 * Modifier plus an item, on a click no deposit rule claimed, places the selected stack type in the
 * position against the clicked face and makes the first deposit into it. Last of the modified-click
 * rules, so it names no target of its own and takes whatever the narrower rules left.
 */
public final class PlaceAdjacentGenericRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        return ctx.isVDown()
                && !ctx.isShift()
                && ctx.hasItemInHand()
                && ctx.canPlaceOrDeposit();
    }

    @Override
    public void execute(InteractionContext ctx) {
        if (ctx.isClientSide() && ctx.isMainHand()) {
            ClientEvents.sendPlaceAndDeposit(
                    ctx.getClickedPos().relative(ctx.getFace()),
                    ctx.getFace()
            );
        }
        ctx.cancelEvent();
    }
}
