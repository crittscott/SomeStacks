package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.server.StackSounds;
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

/**
 * A deposit into an existing stack. Carries the stack's position and the position actually clicked,
 * which differ when the gesture reached the stack through a neighboring block; the server checks
 * protection at both and writes only the stack.
 *
 * <p>The server recomputes the target cell from the player's current view rather than trusting a
 * cell index from the client, so the packet names none.
 */
public class DepositPkt {
    private final BlockPos pos;
    private final BlockPos clickedPos;

    public DepositPkt(BlockPos pos, BlockPos clickedPos) {
        this.pos = pos;
        this.clickedPos = clickedPos;
    }

    public static void encode(DepositPkt msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeBlockPos(msg.clickedPos);
    }

    public static DepositPkt decode(FriendlyByteBuf buf) {
        return new DepositPkt(buf.readBlockPos(), buf.readBlockPos());
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
        Level level = sp.level();

        // A deposit reaches the stack either from a click on it or from a click on the block beside
        // it; nothing further apart is a gesture.
        boolean adjacent = !msg.clickedPos.equals(msg.pos);
        if (adjacent && msg.clickedPos.distManhattan(msg.pos) != 1) {
            return;
        }

        // Both the stack and the block the player actually clicked, because the two can fall on
        // opposite sides of a protection boundary and the click lands on the latter. The mark goes
        // on the clicked position: a deposit takes at most one item from a Singles or Bar hand and
        // leaves the rest for the vanilla interaction to use there.
        //
        // Gated as an item use rather than a block access: the deposit spends the held stack off
        // the click itself, so permission to reach the block is not on its own enough.
        if (!Protection.claimItemUse(
                sp, msg.clickedPos, adjacent ? new BlockPos[] {msg.pos, msg.clickedPos}
                                             : new BlockPos[] {msg.pos})) {
            return;
        }

        Block block = level.getBlockState(msg.pos).getBlock();

        // A creative player keeps what they deposit, the way vanilla placement leaves their stack
        // untouched, so the deposit works from a copy and the hand is never written back.
        boolean creative = sp.getAbilities().instabuild;
        ItemStack held = sp.getMainHandItem();
        ItemStack handStack = creative ? held.copy() : held;

        if (handStack.isEmpty()) {
            return;
        }

        if (ItemOps.checkDisabledModAndNotify(handStack, sp)) {
            return;
        }

        if (ItemOps.checkDisabledItemAndNotify(handStack, sp)) {
            return;
        }

        var be = level.getBlockEntity(msg.pos);

        if (block == ModRegistry.STORAGE_STACK_BLOCK.get() && be instanceof StorageStackBE sbe) {
            int deposited = sbe.deposit(handStack, sp);
            returnToHand(sp, creative, handStack);

            if (deposited > 0) {
                level.playSound(null, msg.pos, StackSounds.STORAGE_DEPOSIT, SoundSource.BLOCKS, StackSounds.VOLUME, 1.0f);
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
            returnToHand(sp, creative, handStack);

            if (deposited) {
                level.playSound(null, msg.pos, StackSounds.SINGLES_DEPOSIT, SoundSource.BLOCKS, StackSounds.VOLUME, 1.0f);
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
            returnToHand(sp, creative, handStack);

            if (deposited) {
                level.playSound(null, msg.pos, StackSounds.BAR_DEPOSIT, SoundSource.BLOCKS, StackSounds.VOLUME, 1.0f);
            }
        }
    }

    /**
     * Writes back what the deposit left of the hand stack. A creative deposit worked from a copy,
     * so there is nothing to write back and the original hand stack remains unchanged.
     */
    private static void returnToHand(ServerPlayer sp, boolean creative, ItemStack handStack) {
        if (!creative) {
            sp.setItemInHand(InteractionHand.MAIN_HAND, handStack);
        }
    }
}
