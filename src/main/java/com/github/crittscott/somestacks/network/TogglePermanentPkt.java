package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TogglePermanentPkt {
    private final BlockPos pos;

    public TogglePermanentPkt(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(TogglePermanentPkt msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static TogglePermanentPkt decode(FriendlyByteBuf buf) {
        return new TogglePermanentPkt(buf.readBlockPos());
    }

    public static void handle(TogglePermanentPkt msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;

            Level level = sp.level();
            if (!level.isLoaded(msg.pos)) return;

            if (level.getBlockState(msg.pos).getBlock() != ModRegistry.STORAGE_STACK_BLOCK.get()) {
                return;
            }

            var be = level.getBlockEntity(msg.pos);
            if (!(be instanceof StorageStackBE sbe)) return;

            boolean newState = !sbe.isPermanent();
            sbe.setPermanent(newState);

            sp.displayClientMessage(
                    Component.literal("Stack: " + (newState ? "Permanent" : "Temporary")),
                    true
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
