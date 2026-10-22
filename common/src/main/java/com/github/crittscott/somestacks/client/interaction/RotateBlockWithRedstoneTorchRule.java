package com.github.crittscott.somestacks.client.interaction;

import net.minecraft.world.item.Items;

/**
 * Consumes a sneaking redstone-torch click on Storage or Singles so local item use does not run.
 * The server handles the layout rotation; Bar has no layout-rotation gesture.
 */
public final class RotateBlockWithRedstoneTorchRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        return ctx.isShift()
                && ctx.hasItemInHand()
                && ctx.getPlayer().getMainHandItem().is(Items.REDSTONE_TORCH)
                && ctx.isRotatableStack();
    }

    @Override
    public void execute(InteractionContext ctx) {
        ctx.cancelEvent();
    }
}
