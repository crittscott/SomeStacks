package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.util.StackGestures;

/**
 * Consumes a modifier-plus-item click in a placement mode, on any block. This suppresses local
 * block and item use; the server chooses between depositing into the clicked or neighboring stack
 * and placing the selected type, and performs the edit.
 */
public final class ModifiedItemClickRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        return StackGestures.modifiedItemClick(
                ctx.isModifierDown(), ctx.getCurrentMode(), ctx.isShift(), ctx.getHeld());
    }

    @Override
    public void execute(InteractionContext ctx) {
        ctx.cancelEvent();
    }
}
