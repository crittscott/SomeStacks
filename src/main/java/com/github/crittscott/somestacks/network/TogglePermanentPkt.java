package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.server.Protection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Switches automatic removal for a Storage pile. The flag belongs to the pile, so the clicked block
 * only identifies which one; a permanent pile keeps its emptied blocks standing.
 *
 * <p>Alone among the gesture packets this consults protection without claiming the click: the
 * gesture is empty-handed, and the vanilla interaction that follows reaches the stack's own use
 * handler, which absorbs it harmlessly.
 */
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
            ServerPlayer sp = PacketBoundary.validate(ctx, msg.pos);
            if (sp == null) return;

            if (Protection.isProtected(sp, msg.pos)) return;

            // Do not claim this empty-hand click. The stack's use handler safely consumes the
            // vanilla interaction that follows, and there is no held item for it to spend.
            if (!Protection.mayInteract(sp, msg.pos)) return;

            if (!PacketBoundary.mainHandEmpty(sp)) return;

            Level level = sp.level();
            if (level.getBlockState(msg.pos).getBlock() != ModRegistry.STORAGE_STACK_BLOCK.get()) {
                return;
            }

            StoragePile pile = StoragePile.at(level, msg.pos);
            if (pile == null) return;

            boolean newState = !pile.isPermanent();
            pile.setPermanent(newState);

            sp.displayClientMessage(
                    Component.literal("Pile: " + (newState ? "Permanent" : "Temporary")),
                    true
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
