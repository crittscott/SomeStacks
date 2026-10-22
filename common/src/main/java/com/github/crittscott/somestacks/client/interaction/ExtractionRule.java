package com.github.crittscott.somestacks.client.interaction;


/**
 * Consumes an unmodified, nonsneaking click on a stack, suppressing local item use.
 * The server selects and extracts the targeted contents.
 */
public final class ExtractionRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        return !ctx.isShift()
                && !ctx.isVDown()
                && ctx.isAnyStackBlock();
    }

    @Override
    public void execute(InteractionContext ctx) {
        ctx.cancelEvent();
    }
}
