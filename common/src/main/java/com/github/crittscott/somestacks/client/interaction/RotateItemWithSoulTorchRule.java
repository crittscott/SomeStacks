package com.github.crittscott.somestacks.client.interaction;

import net.minecraft.world.item.Items;

/**
 * Consumes a sneaking soul-torch click on Singles so local item use does not run.
 * The server selects and rotates the targeted item.
 */
public final class RotateItemWithSoulTorchRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        if (!ctx.isShift() || !ctx.hasItemInHand()) {
            return false;
        }

        if (!ctx.getPlayer().getMainHandItem().is(Items.SOUL_TORCH)) {
            return false;
        }

        return ctx.isSinglesStack();
    }

    @Override
    public void execute(InteractionContext ctx) {
        ctx.cancelEvent();
    }
}
