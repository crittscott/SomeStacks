package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.server.PlayerEdits;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.BlockType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

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

    public static void handleServer(TogglePermanentPkt msg, ServerPlayer sp) {
        if (PacketBoundary.validate(sp, msg.pos) == null) return;

        if (WorldEdits.isProtected(sp, msg.pos)) return;

        // Do not claim this empty-hand click. The stack's use handler safely consumes the
        // vanilla interaction that follows, and there is no held item for it to spend.
        if (!PlayerEdits.mayInteract(sp, msg.pos)) return;

        if (!PacketBoundary.mainHandEmpty(sp)) return;

        Level level = sp.level();
        if (BlockType.of(level.getBlockState(msg.pos).getBlock()) != BlockType.STORAGE_STACK) {
            return;
        }

        StoragePile pile = StoragePile.at(level, msg.pos);
        if (pile == null) return;

        boolean newState = !pile.isPermanent();
        pile.setPermanent(newState);

        sp.displayClientMessage(
                Component.translatable("somestacks.message.pile_state", Component.translatable(
                        newState ? "somestacks.state.permanent" : "somestacks.state.temporary")),
                true
        );
    }
}
