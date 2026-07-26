package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.server.RightClickBlockSuppressor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RotateBlockPkt {
    private final BlockPos pos;

    public RotateBlockPkt(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(RotateBlockPkt msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static RotateBlockPkt decode(FriendlyByteBuf buf) {
        return new RotateBlockPkt(buf.readBlockPos());
    }

    public static void handle(RotateBlockPkt msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = PacketBoundary.validate(ctx, msg.pos);
            if (sp == null) return;

            if (Protection.isProtected(sp, msg.pos)) return;

            if (!Protection.mayInteract(sp, msg.pos)) return;

            if (!PacketBoundary.holdsInMainHand(sp, Items.REDSTONE_TORCH)) return;

            Level level = sp.level();
            Block block = level.getBlockState(msg.pos).getBlock();
            var be = level.getBlockEntity(msg.pos);

            int newRotation;
            if (block == ModRegistry.SINGLES_STACK_BLOCK.get() && be instanceof SinglesStackBE ssbe) {
                newRotation = (ssbe.getRotation() + 1) % 4;
                ssbe.setRotation(newRotation);
            } else if (block == ModRegistry.STORAGE_STACK_BLOCK.get() && be instanceof StorageStackBE sbe) {
                newRotation = (sbe.getRotation() + 1) % 4;
                sbe.setRotation(newRotation);
            } else {
                return;
            }

            // The gesture is a sneaking click holding an item, which vanilla resolves as a use of
            // that item on the block. Deny the use-item-on packet that follows so the rotation does
            // not also place the torch against the stack.
            RightClickBlockSuppressor.suppress(sp, msg.pos, level);

            sp.displayClientMessage(Component.literal("Rotation: " + (newRotation * 90) + "°"), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
