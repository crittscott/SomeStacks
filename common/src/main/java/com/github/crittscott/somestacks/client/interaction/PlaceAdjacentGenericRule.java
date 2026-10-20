package com.github.crittscott.somestacks.client.interaction;


/**
 * Consumes modifier-plus-item placement or deposit. The server selects the clicked stack,
 * an adjacent stack, or a new block from the vanilla hit.
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
