package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.util.StackGestures;

/**
 * Consumes a sneaking redstone-torch click on Storage or Singles so local item use does not run.
 * The server handles the layout rotation.
 */
public final class RotateBlockWithRedstoneTorchRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        return StackGestures.rotatesBlock(ctx.isShift(), ctx.getHeld(), ctx.getClickedType());
    }

    @Override
    public void execute(InteractionContext ctx) {
        ctx.cancelEvent();
    }
}
