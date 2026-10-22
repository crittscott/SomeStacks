package com.github.crittscott.somestacks.client.interaction;


/**
 * Consumes a modifier-plus-item click not claimed by an earlier rule when placement/deposit
 * mode is selected. This suppresses local block/item use; the server performs the edit.
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
