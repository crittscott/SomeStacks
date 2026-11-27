package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.util.StackGestures;

/**
 * Consumes a sneaking soul-torch click on Singles so local item use does not run. The server
 * selects and rotates the targeted item.
 */
public final class RotateItemWithSoulTorchRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        return StackGestures.rotatesItem(ctx.isShift(), ctx.getHeld(), ctx.getClickedType());
    }

    @Override
    public void execute(InteractionContext ctx) {
        ctx.cancelEvent();
    }
}
