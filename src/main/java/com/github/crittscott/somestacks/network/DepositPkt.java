package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.ModSounds;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DepositPkt {
    private final InteractionHand hand;
    private final BlockPos pos;

    public DepositPkt(InteractionHand hand, BlockPos pos) {
        this.hand = hand;
        this.pos = pos;
    }

    public static void encode(DepositPkt msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.hand);
        buf.writeBlockPos(msg.pos);
    }

    public static DepositPkt decode(FriendlyByteBuf buf) {
        return new DepositPkt(
                buf.readEnum(InteractionHand.class),
                buf.readBlockPos());
    }

    public static void handle(DepositPkt msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = PacketBoundary.validate(ctx, msg.pos);
            if (sp == null) {
                return;
            }

            if (PacketBoundary.isProtected(sp, msg.pos)) {
                return;
            }

            Level level = sp.level();
            Block block = level.getBlockState(msg.pos).getBlock();
            ItemStack handStack = sp.getItemInHand(msg.hand);

            if (handStack.isEmpty()) {
                return;
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
                int deposited = sbe.deposit(handStack);
                sp.setItemInHand(msg.hand, handStack);

                if (deposited > 0) {
                    level.playSound(null, msg.pos, ModSounds.STORAGE_DEPOSIT, SoundSource.BLOCKS, 0.5f, 1.0f);
                }
            } else if (block == ModRegistry.SINGLES_STACK_BLOCK.get() && be instanceof SinglesStackBE ssbe) {
                IItemHandler handler = ssbe.getItems();
                Vec3 eyePos = sp.getEyePosition(1.0f);
                Vec3 lookDir = sp.getLookAngle();
                int index = SinglesCubeIdx.traceAllPositions(eyePos, lookDir, msg.pos, handler, ssbe.getRotation());

                if (index < 0) {
                    return;
                }

                if (!handler.getStackInSlot(index).isEmpty()) {
                    return;
                }

                if (!SinglesCubeIdx.isGrounded(index, handler)) {
                    return;
                }

                boolean deposited = ssbe.depositAt(index, handStack);
                sp.setItemInHand(msg.hand, handStack);

                if (deposited) {
                    level.playSound(null, msg.pos, ModSounds.SINGLES_DEPOSIT, SoundSource.BLOCKS, 0.5f, 1.0f);
                }
            } else if (block == ModRegistry.BAR_STACK_BLOCK.get() && be instanceof BarStackBE barbe) {
                IItemHandler handler = barbe.getItems();
                Vec3 eyePos = sp.getEyePosition(1.0f);
                Vec3 lookDir = sp.getLookAngle();
                int index = BarCubeIdx.traceAllPositions(eyePos, lookDir, msg.pos, handler);

                if (index < 0) {
                    return;
                }

                if (!handler.getStackInSlot(index).isEmpty()) {
                    return;
                }

                if (!BarCubeIdx.isGrounded(index, handler)) {
                    return;
                }

                boolean deposited = barbe.depositAt(index, handStack);
                sp.setItemInHand(msg.hand, handStack);

                if (deposited) {
                    level.playSound(null, msg.pos, ModSounds.BAR_DEPOSIT, SoundSource.BLOCKS, 0.5f, 1.0f);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
