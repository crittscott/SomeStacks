package com.github.crittscott.somestacks.client.interaction;


/**
 * Consumes modified item clicks for server-side deposit or placement. Target and cell selection
 * belong to the server; the client only suppresses competing local item use.
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
        ctx.cancelEvent();
    }
}
