package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.server.StackSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Rotates one rendered item within a Singles Stack, leaving the block's layout alone.
 *
 * <p>The gesture is a sneaking click with a soul torch in hand, which vanilla would resolve as
 * placing that torch, so the handler claims the click to deny the placement that would follow. The
 * client sends this even when its ray hit no item, because claiming the click is what suppresses
 * the torch; a slot index naming no occupied cell simply rotates nothing.
 */
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

            // Establish that this packet describes the gesture before claiming it. A claim fires
            // the interaction event and suppresses the vanilla click that follows.
            if (!PacketBoundary.holdsInMainHand(sp, Items.SOUL_TORCH)) return;

            Level level = sp.level();
            Block block = level.getBlockState(msg.pos).getBlock();
            var be = level.getBlockEntity(msg.pos);

            if (block != ModRegistry.SINGLES_STACK_BLOCK.get() || !(be instanceof SinglesStackBE ssbe)) return;

            if (msg.slotIndex < 0 || msg.slotIndex >= SinglesStackBE.SLOTS) return;

            // Claim even when the target cell is empty, because the gesture must still suppress
            // the vanilla attempt to place the soul torch.
            if (!Protection.claimInteraction(sp, msg.pos, msg.pos)) return;

            // Empty cells carry no orientation; a later deposit must not inherit a prior gesture.
            if (ssbe.getItems().getStackInSlot(msg.slotIndex).isEmpty()) return;

            int newCubeRot = (ssbe.getCubeRotation(msg.slotIndex) + 1) % 4;
            ssbe.setCubeRotation(msg.slotIndex, newCubeRot);
            StackSounds.playRotation(sp, msg.pos, StackSounds.SINGLES_ROTATE_ITEM);
            sp.displayClientMessage(Component.literal("Item Rotation: " + (newCubeRot * 90) + "°"), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
