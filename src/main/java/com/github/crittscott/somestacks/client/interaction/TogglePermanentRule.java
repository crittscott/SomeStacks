package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.client.ClientEvents;
import com.github.crittscott.somestacks.util.StackMode;

public final class TogglePermanentRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        return  ctx.isVDown()
                && !ctx.hasItemInHand()
                && ctx.getCurrentMode() == StackMode.TOGGLE_PERMANENT
                && ctx.isStorageStack();
    }

    @Override
    public void execute(InteractionContext ctx) {
        SomeStacks.LOGGER.debug("Executing TogglePermanentRule");
        if (ctx.isClientSide() && ctx.isMainHand()) {
            ClientEvents.sendTogglePermanent(ctx.getClickedPos());
        }
        ctx.cancelEvent();
    }
}
