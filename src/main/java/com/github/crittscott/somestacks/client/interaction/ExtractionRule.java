package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.client.ClientEvents;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import com.github.crittscott.somestacks.util.ViewRay;
import net.minecraft.world.level.block.Block;

/**
 * An unmodified right-click on any stack takes from the nearest occupied cell along the player's
 * reach ray. The click is consumed even when the ray finds nothing, so a held item is never used
 * against the block.
 */
public final class ExtractionRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        return !ctx.isShift()
                && !ctx.isVDown()
                && ctx.isAnyStackBlock();
    }

    @Override
    public void execute(InteractionContext ctx) {
        if (!ctx.isClientSide() || !ctx.isMainHand()) {
            ctx.cancelEvent();
            return;
        }

        ViewRay ray = ViewRay.of(ctx.getPlayer());
        var be = ctx.getLevel().getBlockEntity(ctx.getClickedPos());
        int index = -1;

        Block block = ctx.getLevel().getBlockState(ctx.getClickedPos()).getBlock();

        if (be instanceof StorageStackBE sbe) {
            index = StorageCubeIdx.traceCubes(ray, ctx.getClickedPos(), sbe, sbe.getRotation());
        } else if (be instanceof SinglesStackBE ssbe) {
            index = SinglesCubeIdx.traceCubes(ray, ctx.getClickedPos(), ssbe);
        } else if (be instanceof BarStackBE barbe) {
            index = BarCubeIdx.traceCubes(ray, ctx.getClickedPos(), barbe);
        }

        if (index >= 0) {
            ClientEvents.sendExtract(ctx.getClickedPos(), index);
        }

        ctx.cancelEvent();
    }
}
