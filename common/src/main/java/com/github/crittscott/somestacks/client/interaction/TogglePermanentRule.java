package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.util.StackMode;

/**
 * Consumes an empty-main-hand modifier click on Storage while Toggle Permanent mode is selected.
 * The server applies the permanence change.
 */
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
        ctx.cancelEvent();
    }
}
