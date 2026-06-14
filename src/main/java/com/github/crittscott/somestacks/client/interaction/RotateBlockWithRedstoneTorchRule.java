package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.client.ClientEvents;
import net.minecraft.world.item.Items;

public final class RotateBlockWithRedstoneTorchRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        return ctx.isShift()
                && ctx.hasItemInHand()
                && ctx.getPlayer().getMainHandItem().is(Items.REDSTONE_TORCH)
                && ctx.isAnyStackBlock();
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
