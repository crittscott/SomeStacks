package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
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

            if (!PacketBoundary.holdsInMainHand(sp, Items.REDSTONE_TORCH)) return;

            Level level = sp.level();
            Block block = level.getBlockState(msg.pos).getBlock();
            var be = level.getBlockEntity(msg.pos);

            if (block == ModRegistry.SINGLES_STACK_BLOCK.get() && be instanceof SinglesStackBE ssbe) {
                int currentRotation = ssbe.getRotation();
                int newRotation = (currentRotation + 1) % 4;
                ssbe.setRotation(newRotation);
                sp.displayClientMessage(Component.literal("Rotation: " + (newRotation * 90) + "°"), true);
            } else if (block == ModRegistry.STORAGE_STACK_BLOCK.get() && be instanceof StorageStackBE sbe) {
                int currentRotation = sbe.getRotation();
                int newRotation = (currentRotation + 1) % 4;
                sbe.setRotation(newRotation);
                sp.displayClientMessage(Component.literal("Rotation: " + (newRotation * 90) + "°"), true);
                sbe.resortAndPackPile();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
