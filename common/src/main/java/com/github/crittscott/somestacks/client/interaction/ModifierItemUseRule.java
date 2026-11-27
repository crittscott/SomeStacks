package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.util.StackGestures;

/**
 * Consumes a main-hand item use while the Stack Modifier is held, the same uses the server refuses.
 * It catches the use that follows a modified block click no block gesture claimed, such as one made
 * while sneaking or in Toggle Permanent mode, so the item does not act locally.
 */
public final class ModifierItemUseRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        return StackGestures.modifierClaimsItemUse(ctx.getHand(), ctx.isModifierDown());
    }

    @Override
    public void execute(InteractionContext ctx) {
        ctx.cancelEvent();
    }
}
