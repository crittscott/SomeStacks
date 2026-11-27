package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.util.StackGestures;

/**
 * Consumes an unmodified, nonsneaking click on any stack, so a held item is never used against the
 * block. The server takes from the nearest occupied cell along the player's reach ray, if any.
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
