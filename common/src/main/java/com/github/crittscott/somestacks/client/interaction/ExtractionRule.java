package com.github.crittscott.somestacks.client.interaction;


/**
 * An unmodified right-click on any stack takes from the nearest occupied cell along the player's
 * reach ray. The click is consumed even when the ray finds nothing, so a held item is never used
 * against the block.
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
