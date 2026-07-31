package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.ModSounds;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.server.RightClickBlockSuppressor;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.ViewRay;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DepositPkt {
    private final InteractionHand hand;
    private final BlockPos pos;
    private final BlockPos clickedPos;

    public DepositPkt(InteractionHand hand, BlockPos pos, BlockPos clickedPos) {
        this.hand = hand;
        this.pos = pos;
        this.clickedPos = clickedPos;
    }

    public static void encode(DepositPkt msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.hand);
        buf.writeBlockPos(msg.pos);
        buf.writeBlockPos(msg.clickedPos);
    }

    public static DepositPkt decode(FriendlyByteBuf buf) {
        return new DepositPkt(
                buf.readEnum(InteractionHand.class),
                buf.readBlockPos(),
                buf.readBlockPos());
    }

    public static void handle(DepositPkt msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = PacketBoundary.validate(ctx, msg.pos);
            if (sp == null) {
                return;
            }
            apply(sp, msg);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void apply(ServerPlayer sp, DepositPkt msg) {
        if (Protection.isProtected(sp, msg.pos)) {
            return;
        }

        if (!Protection.mayInteract(sp, msg.pos, msg.hand)) {
            return;
        }

        Level level = sp.level();
        Block block = level.getBlockState(msg.pos).getBlock();
        ItemStack handStack = sp.getItemInHand(msg.hand);

        if (handStack.isEmpty()) {
            return;
        }

        // The gesture claimed the click, so the vanilla interaction the client still sends for it
        // must not also run: a deposit takes at most one item from a Singles or Bar hand and leaves
        // the rest for that interaction to use against the clicked block. The mark goes on the
        // clicked position, which is the deposit target when the player aimed at the stack and its
        // neighbour when they aimed at the block beside it; nothing further apart is a gesture.
        //
        // It goes here rather than earlier because the consults above fire the very event the mark
        // vetoes, and a deposit into the clicked block would otherwise refuse itself.
        if (msg.clickedPos.equals(msg.pos) || msg.clickedPos.distManhattan(msg.pos) == 1) {
            RightClickBlockSuppressor.suppress(sp, msg.clickedPos, level);
        }

        // Check if item is from a disabled mod and notify player
        if (ItemOps.checkDisabledModAndNotify(handStack, sp)) {
            return;
        }

        // Check if specific item is disabled and notify player
        if (ItemOps.checkDisabledItemAndNotify(handStack, sp)) {
            return;
        }

        var be = level.getBlockEntity(msg.pos);

        if (block == ModRegistry.STORAGE_STACK_BLOCK.get() && be instanceof StorageStackBE sbe) {
            int deposited = sbe.deposit(handStack, sp);
            sp.setItemInHand(msg.hand, handStack);

            if (deposited > 0) {
                level.playSound(null, msg.pos, ModSounds.STORAGE_DEPOSIT, SoundSource.BLOCKS, 0.5f, 1.0f);
            }
        } else if (block == ModRegistry.SINGLES_STACK_BLOCK.get() && be instanceof SinglesStackBE ssbe) {
            IItemHandler handler = ssbe.getItems();
            int index = SinglesCubeIdx.traceAllPositions(ViewRay.of(sp), msg.pos, handler, ssbe.getRotation());

            if (index < 0) {
                return;
            }

            if (!handler.getStackInSlot(index).isEmpty()) {
                return;
            }

            // Grounding is left to depositAt, which is the only caller holding the seam beneath.
            boolean deposited = ssbe.depositAt(index, handStack);
            sp.setItemInHand(msg.hand, handStack);

            if (deposited) {
                level.playSound(null, msg.pos, ModSounds.SINGLES_DEPOSIT, SoundSource.BLOCKS, 0.5f, 1.0f);
            }
        } else if (block == ModRegistry.BAR_STACK_BLOCK.get() && be instanceof BarStackBE barbe) {
            IItemHandler handler = barbe.getItems();
            int index = BarCubeIdx.traceAllPositions(ViewRay.of(sp), msg.pos, handler);

            if (index < 0) {
                return;
            }

            if (!handler.getStackInSlot(index).isEmpty()) {
                return;
            }

            // Grounding is left to depositAt, which is the only caller holding the seam beneath.
            boolean deposited = barbe.depositAt(index, handStack);
            sp.setItemInHand(msg.hand, handStack);

            if (deposited) {
                level.playSound(null, msg.pos, ModSounds.BAR_DEPOSIT, SoundSource.BLOCKS, 0.5f, 1.0f);
            }
        }
    }
}
