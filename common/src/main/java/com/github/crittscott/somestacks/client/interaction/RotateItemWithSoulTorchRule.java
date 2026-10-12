package com.github.crittscott.somestacks.client.interaction;

import net.minecraft.world.item.Items;

/**
 * Shift plus a soul torch rotates one rendered item within a Singles Stack, leaving the block's own
 * layout alone. Only Singles carries per-item rotation.
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
