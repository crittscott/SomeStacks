package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.client.ClientEvents;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

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
        SomeStacks.LOGGER.debug("Executing RotateItemWithSoulTorchRule");
        if (!ctx.isClientSide() || !ctx.isMainHand()) {
            ctx.cancelEvent();
            return;
        }

        Vec3 eyePos = ctx.getPlayer().getEyePosition(1.0f);
        Vec3 lookDir = ctx.getPlayer().getLookAngle();

        if (ctx.getLevel().getBlockEntity(ctx.getClickedPos()) instanceof SinglesStackBE ssbe) {
            int index = SinglesCubeIdx.traceCubes(eyePos, lookDir, ctx.getClickedPos(), ssbe);
            if (index >= 0) {
                ClientEvents.sendRotateItem(ctx.getClickedPos(), index);
            }
        }

        ctx.cancelEvent();
    }
}
