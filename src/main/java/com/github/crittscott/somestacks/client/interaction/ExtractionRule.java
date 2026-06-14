package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.client.ClientEvents;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public final class ExtractionRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        return !ctx.isShift()
                && !ctx.isVDown()
                && ctx.isAnyStackBlock();
    }

    @Override
    public void execute(InteractionContext ctx) {
        SomeStacks.LOGGER.debug("Executing ExtractionRule");
        if (!ctx.isClientSide() || !ctx.isMainHand()) {
            ctx.cancelEvent();
            return;
        }

        Vec3 eyePos = ctx.getPlayer().getEyePosition(1.0f);
        Vec3 lookDir = ctx.getPlayer().getLookAngle();
        var be = ctx.getLevel().getBlockEntity(ctx.getClickedPos());
        int index = -1;

        Block block = ctx.getLevel().getBlockState(ctx.getClickedPos()).getBlock();

        if (be instanceof StorageStackBE sbe) {
            index = StorageCubeIdx.traceCubes(eyePos, lookDir, ctx.getClickedPos(), sbe, sbe.getRotation());
        } else if (be instanceof SinglesStackBE ssbe) {
            index = SinglesCubeIdx.traceCubes(eyePos, lookDir, ctx.getClickedPos(), ssbe);
        } else if (be instanceof BarStackBE barbe) {
            index = BarCubeIdx.traceCubes(eyePos, lookDir, ctx.getClickedPos(), barbe);
        }

        if (index >= 0) {
            ClientEvents.sendExtract(ctx.getClickedPos(), index);
        }

        ctx.cancelEvent();
    }
}
