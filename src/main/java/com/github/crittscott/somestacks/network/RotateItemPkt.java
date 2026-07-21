package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RotateItemPkt {
    private final BlockPos pos;
    private final int slotIndex;

    public RotateItemPkt(BlockPos pos, int slotIndex) {
        this.pos = pos;
        this.slotIndex = slotIndex;
    }

    public static void encode(RotateItemPkt msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.slotIndex);
    }

    public static RotateItemPkt decode(FriendlyByteBuf buf) {
        return new RotateItemPkt(buf.readBlockPos(), buf.readInt());
    }

    public static void handle(RotateItemPkt msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = PacketBoundary.validate(ctx, msg.pos);
            if (sp == null) return;

            if (!PacketBoundary.holdsInMainHand(sp, Items.SOUL_TORCH)) return;

            Level level = sp.level();
            Block block = level.getBlockState(msg.pos).getBlock();
            var be = level.getBlockEntity(msg.pos);

            if (block == ModRegistry.SINGLES_STACK_BLOCK.get() && be instanceof SinglesStackBE ssbe) {
                if (msg.slotIndex < 0 || msg.slotIndex >= 64) return;

                int currentCubeRot = ssbe.getCubeRotation(msg.slotIndex);
                int newCubeRot = (currentCubeRot + 1) % 4;
                ssbe.setCubeRotation(msg.slotIndex, newCubeRot);
                sp.displayClientMessage(Component.literal("Item Rotation: " + (newCubeRot * 90) + "°"), true);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
