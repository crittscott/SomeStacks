package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.util.StackGestures;

/**
 * Consumes an empty-main-hand modifier click on Storage while Toggle Permanent mode is selected.
 * The server switches automatic removal for the whole pile.
 */
public final class TogglePermanentRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        return StackGestures.togglesPermanence(
                ctx.isModifierDown(), ctx.getCurrentMode(), ctx.getHeld(), ctx.getClickedType());
    }

    @Override
    public void execute(InteractionContext ctx) {
        ctx.cancelEvent();
    }
}
