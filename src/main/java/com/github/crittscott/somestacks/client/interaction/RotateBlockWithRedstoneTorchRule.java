package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.client.ClientEvents;
import net.minecraft.world.item.Items;

/**
 * Shift plus a redstone torch rotates a Storage or Singles Stack's layout 90 degrees. A Bar Stack
 * is excluded: its bars are fixed to the orientation of their layer.
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
        SomeStacks.LOGGER.debug("Executing RotateBlockWithRedstoneTorchRule");
        if (ctx.isClientSide() && ctx.isMainHand()) {
            ClientEvents.sendRotateBlock(ctx.getClickedPos());
        }
        ctx.cancelEvent();
    }
}
