package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.client.ClientEvents;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
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

        Block block = ctx.getLevel().getBlockState(ctx.getClickedPos()).getBlock();
        return block == ModRegistry.SINGLES_STACK_BLOCK.get()
                || block == ModRegistry.BAR_STACK_BLOCK.get();
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
        var be = ctx.getLevel().getBlockEntity(ctx.getClickedPos());
        int index = -1;

        if (be instanceof SinglesStackBE ssbe) {
            index = SinglesCubeIdx.traceCubes(eyePos, lookDir, ctx.getClickedPos(), ssbe);
        } else if (be instanceof BarStackBE barbe) {
            index = BarCubeIdx.traceCubes(eyePos, lookDir, ctx.getClickedPos(), barbe);
        }

        if (index >= 0) {
            ClientEvents.sendRotateItem(ctx.getClickedPos(), index);
        }

        ctx.cancelEvent();
    }
}
