package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.StackItemStorage;
import com.github.crittscott.somestacks.util.ViewRay;
import com.github.crittscott.somestacks.util.ViewRays;
import net.minecraft.core.Direction;

/**
 * Consumes a modifier-plus-item click classified as a deposit into the clicked stack.
 * A full or unsupported top-face target declines to the generic placement rule, which also
 * consumes local use. The server independently chooses the destination and performs the edit.
 */
public final class DepositIntoClickedStackRule implements InteractionRule {
    @Override
    public boolean matches(InteractionContext ctx) {
        return ctx.isVDown()
                && !ctx.isShift()
                && ctx.hasItemInHand()
                && ctx.isAnyStackBlock()
                && ctx.canPlaceOrDeposit()
                && depositsIntoClickedBlock(ctx);
    }

    /** Classifies local top-face clicks; declining delegates consumption to generic placement. */
    private boolean depositsIntoClickedBlock(InteractionContext ctx) {
        if (ctx.getFace() != Direction.UP || ctx.isStorageStack()) {
            return true;
        }

        ViewRay ray = ViewRays.of(ctx.getPlayer());
        var be = ctx.getLevel().getBlockEntity(ctx.getClickedPos());

        if (be instanceof SinglesStackBE ssbe) {
            StackItemStorage handler = ssbe.getItems();
            int index = SinglesCubeIdx.traceAllPositions(ray, ctx.getClickedPos(), handler, ssbe.getRotation());
            boolean[] seam = ctx.getLevel().getBlockEntity(ctx.getClickedPos().below()) instanceof SinglesStackBE below
                    ? SinglesCubeIdx.topLayerOccupancy(below.getItems(), below.getRotation())
                    : null;
            return index >= 0 && handler.getStackInSlot(index).isEmpty()
                    && SinglesCubeIdx.isGrounded(index, handler, ssbe.getRotation(), seam);
        }

        if (be instanceof BarStackBE barbe) {
            StackItemStorage handler = barbe.getItems();
            int index = BarCubeIdx.traceAllPositions(ray, ctx.getClickedPos(), handler);
            boolean[] seam = ctx.getLevel().getBlockEntity(ctx.getClickedPos().below()) instanceof BarStackBE below
                    ? BarCubeIdx.topLayerOccupancy(below.getItems())
                    : null;
            return index >= 0 && handler.getStackInSlot(index).isEmpty()
                    && BarCubeIdx.isGrounded(index, handler, seam);
        }

        return true;
    }

    @Override
    public void execute(InteractionContext ctx) {
        ctx.cancelEvent();
    }
}
