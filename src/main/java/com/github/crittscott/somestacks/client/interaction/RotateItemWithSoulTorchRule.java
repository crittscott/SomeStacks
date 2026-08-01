package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.client.ClientEvents;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.ViewRay;
import net.minecraft.world.item.Items;

/**
 * Shift plus a soul torch rotates one rendered item within a Singles Stack, leaving the block's own
 * layout alone. Only Singles carries per-item rotation.
 */
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
        if (!ctx.isClientSide() || !ctx.isMainHand()) {
            ctx.cancelEvent();
            return;
        }

        ViewRay ray = ViewRay.of(ctx.getPlayer());

        if (ctx.getLevel().getBlockEntity(ctx.getClickedPos()) instanceof SinglesStackBE ssbe) {
            // Sent even when the ray hit no item. The gesture takes the click either way, and the
            // packet is what tells the server to suppress the vanilla torch placement; the server
            // ignores an index that names no cell.
            int index = SinglesCubeIdx.traceCubes(ray, ctx.getClickedPos(), ssbe);
            ClientEvents.sendRotateItem(ctx.getClickedPos(), index);
        }

        ctx.cancelEvent();
    }
}
