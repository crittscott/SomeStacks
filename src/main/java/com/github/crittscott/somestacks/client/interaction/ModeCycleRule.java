package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.client.ClientEvents;

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
