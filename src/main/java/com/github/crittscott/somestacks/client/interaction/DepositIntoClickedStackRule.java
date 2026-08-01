package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.client.ClientEvents;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.ViewRay;
import net.minecraft.core.Direction;
import net.minecraftforge.items.IItemHandler;

/**
 * Modifier plus an item deposits into the stack that was clicked, whatever placement mode is
 * selected. Declining here is how a top-face click on a full Singles or Bar column reaches
 * {@link PlaceAdjacentGenericRule} and grows the column instead.
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

    /**
     * Whether the deposit should land in the clicked block rather than defer to placement. A Singles
     * or Bar Stack whose targeted column is full, clicked on its top face, has no cell to accept the
     * item, so it declines and lets the rule chain reach placement, growing the column with a new
     * block above. Storage climbs its own pile server-side and always deposits into the clicked block.
     */
    private boolean depositsIntoClickedBlock(InteractionContext ctx) {
        if (ctx.getFace() != Direction.UP || ctx.isStorageStack()) {
            return true;
        }

        ViewRay ray = ViewRay.of(ctx.getPlayer());
        var be = ctx.getLevel().getBlockEntity(ctx.getClickedPos());

        if (be instanceof SinglesStackBE ssbe) {
            IItemHandler handler = ssbe.getItems();
            int index = SinglesCubeIdx.traceAllPositions(ray, ctx.getClickedPos(), handler, ssbe.getRotation());
            boolean[] seam = ctx.getLevel().getBlockEntity(ctx.getClickedPos().below()) instanceof SinglesStackBE below
                    ? SinglesCubeIdx.topLayerOccupancy(below.getItems(), below.getRotation())
                    : null;
            return index >= 0 && handler.getStackInSlot(index).isEmpty()
                    && SinglesCubeIdx.isGrounded(index, handler, ssbe.getRotation(), seam);
        }

        if (be instanceof BarStackBE barbe) {
            IItemHandler handler = barbe.getItems();
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
        if (ctx.isClientSide() && ctx.isMainHand()) {
            ClientEvents.sendDeposit(ctx.getClickedPos(), ctx.getClickedPos());
        }
        ctx.cancelEvent();
    }
}
