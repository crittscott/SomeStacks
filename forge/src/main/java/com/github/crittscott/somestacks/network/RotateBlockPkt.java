package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
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
 * Rotates a Storage or Singles Stack's layout 90 degrees. Rotation is per block even in a run whose
 * contents settling moves between blocks.
 *
 * <p>The gesture is a sneaking click with a redstone torch in hand, which vanilla would resolve as
 * placing that torch, so the handler claims the click to deny the placement that would follow.
 */
public class RotateBlockPkt {
    private static final int DEGREES_PER_ROTATION = 90;

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

            // Establish that this packet describes the gesture before claiming it. A claim fires
            // the interaction event and suppresses the vanilla click that follows.
            if (!PacketBoundary.holdsInMainHand(sp, Items.REDSTONE_TORCH)) return;

            Level level = sp.level();
            Block block = level.getBlockState(msg.pos).getBlock();
            var be = level.getBlockEntity(msg.pos);

            boolean singles = block == ModRegistry.SINGLES_STACK_BLOCK.get() && be instanceof SinglesStackBE;
            boolean storage = block == ModRegistry.STORAGE_STACK_BLOCK.get() && be instanceof StorageStackBE;
            if (!singles && !storage) return;

            // Claiming suppresses the vanilla attempt to place the redstone torch.
            if (!Protection.claimInteraction(sp, msg.pos, msg.pos)) return;

            int newRotation;
            if (singles) {
                SinglesStackBE ssbe = (SinglesStackBE) be;
                newRotation = (ssbe.getRotation() + 1) % 4;
                ssbe.setRotation(newRotation);
            } else {
                StorageStackBE sbe = (StorageStackBE) be;
                newRotation = (sbe.getRotation() + 1) % 4;
                sbe.setRotation(newRotation);
            }

            StackSounds.playRotation(sp, msg.pos,
                    singles ? StackSounds.SINGLES_ROTATE : StackSounds.STORAGE_ROTATE);
            sp.displayClientMessage(Component.translatable(
                    "somestacks.message.rotation", newRotation * DEGREES_PER_ROTATION), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
