package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.client.ClientEvents;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;

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

        Vec3 eyePos = ctx.getPlayer().getEyePosition(1.0f);
        Vec3 lookDir = ctx.getPlayer().getLookAngle();
        var be = ctx.getLevel().getBlockEntity(ctx.getClickedPos());

        if (be instanceof SinglesStackBE ssbe) {
            IItemHandler handler = ssbe.getItems();
            int index = SinglesCubeIdx.traceAllPositions(eyePos, lookDir, ctx.getClickedPos(), handler, ssbe.getRotation());
            return index >= 0 && handler.getStackInSlot(index).isEmpty() && SinglesCubeIdx.isGrounded(index, handler);
        }

        if (be instanceof BarStackBE barbe) {
            IItemHandler handler = barbe.getItems();
            int index = BarCubeIdx.traceAllPositions(eyePos, lookDir, ctx.getClickedPos(), handler);
            return index >= 0 && handler.getStackInSlot(index).isEmpty() && BarCubeIdx.isGrounded(index, handler);
        }

        return true;
    }

    @Override
    public void execute(InteractionContext ctx) {
        SomeStacks.LOGGER.debug("Executing DepositIntoClickedStackRule");
        if (ctx.isClientSide() && ctx.isMainHand()) {
            ClientEvents.sendDeposit(ctx.getClickedPos());
        }
        ctx.cancelEvent();
    }
}
