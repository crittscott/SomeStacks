package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.client.ClientEvents;

/**
 * Modifier plus a right-click at open air cycles the placement mode. Disabled stack types are
 * skipped as the cycle passes them. The click is taken only when the hand holds an item, since an
 * empty-hand click at air has nothing to spend.
 */
public final class ModeCycleRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        return ctx.isClientSide()
                && ctx.isMainHand()
                && ctx.isVDown()
                && !ctx.isHittingBlock();
    }

    @Override
    public void execute(InteractionContext ctx) {
        SomeStacks.LOGGER.debug("Executing ModeCycleRule");
        ClientEvents.cycleModeAllFour(ctx.getPlayer());
        ClientEvents.displayModeMessage(ctx.getPlayer());
        if (ctx.hasItemInHand()) {
            ctx.cancelEvent();
        }
    }
}
