package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.util.StackGestures;

/**
 * Consumes an unmodified, nonsneaking click on a stack, suppressing local item use. The server
 * selects and extracts the targeted contents.
 */
public final class ExtractionRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        return ctx.getClickedType() != null
                && StackGestures.extracts(ctx.isModifierDown(), ctx.isShift());
    }

    @Override
    public void execute(InteractionContext ctx) {
        ctx.cancelEvent();
    }
}
