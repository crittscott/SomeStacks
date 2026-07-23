package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ModSounds;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.server.RightClickBlockSuppressor;
import com.github.crittscott.somestacks.util.ItemOps;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ExtractPkt(InteractionHand hand, BlockPos pos, int index) {

    public static void encode(ExtractPkt msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.hand);
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.index);
    }

    public static ExtractPkt decode(FriendlyByteBuf buf) {
        return new ExtractPkt(
                buf.readEnum(InteractionHand.class),
                buf.readBlockPos(),
                buf.readInt()
        );
    }

    public static void handle(ExtractPkt msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = PacketBoundary.validate(ctx, msg.pos);
            if (player == null) return;

            if (msg.index < 0 || msg.index >= 64) return;

            if (Protection.isProtected(player, msg.pos)) return;

            if (!Protection.mayInteract(player, msg.pos)) return;

            Level level = player.level();
            BlockEntity be = level.getBlockEntity(msg.pos);
            if (be == null) return;

            if (be instanceof StorageStackBE sbe) {
                handleStorageExtract(level, msg.pos, player, msg.hand, sbe, msg.index);
            } else if (be instanceof SinglesStackBE ssbe) {
                handleSinglesExtract(level, msg.pos, player, msg.hand, ssbe, msg.index);
            } else if (be instanceof BarStackBE barbe) {
                handleBarExtract(level, msg.pos, player, msg.hand, barbe, msg.index);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleStorageExtract(Level level, BlockPos pos, Player player, InteractionHand hand, StorageStackBE sbe, int index) {
        ItemStack handStack = player.getItemInHand(hand);

        int maxCanTake = handStack.isEmpty()
                ? Integer.MAX_VALUE
                : handStack.getMaxStackSize() - handStack.getCount();

        ItemStack taken = sbe.extractAt(index, maxCanTake, handStack.isEmpty() ? ItemStack.EMPTY : handStack);

        if (!taken.isEmpty()) {
            // Prevent the vanilla use-item-on packet (processed after this pkt) from placing into the now-air position.
            RightClickBlockSuppressor.suppress(player, pos, level);

            level.playSound(null, pos, ModSounds.STORAGE_EXTRACT, SoundSource.BLOCKS, 0.5f, 1.0f);

            boolean shouldRemove = sbe.isEmpty() && !sbe.isPermanent() && !sbe.hasStorageBlockAbove();
            if (shouldRemove) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }

            if (handStack.isEmpty()) {
                player.setItemInHand(hand, taken);
                taken = ItemStack.EMPTY;
            } else {
                int moved = ItemOps.mergeIntoStack(handStack, taken);
                taken.shrink(moved);
                player.setItemInHand(hand, handStack);
            }

            if (!taken.isEmpty()) {
                player.drop(taken, false);
            }
        }
    }

    private static void handleSinglesExtract(Level level, BlockPos pos, Player player, InteractionHand hand, SinglesStackBE ssbe, int index) {
        ItemStack cubeStack = ssbe.getItems().getStackInSlot(index);

        if (cubeStack.isEmpty()) return;

        ItemStack handStack = player.getItemInHand(hand);

        if (!ItemOps.canTakeIntoHand(handStack, cubeStack)) return;

        ItemStack taken = ssbe.extractAt(index);

        if (!taken.isEmpty()) {
            RightClickBlockSuppressor.suppress(player, pos, level);

            level.playSound(null, pos, ModSounds.SINGLES_EXTRACT, SoundSource.BLOCKS, 0.5f, 1.0f);

            ItemOps.giveToPlayerOrDrop(player, hand, taken);
        }
    }

    private static void handleBarExtract(Level level, BlockPos pos, Player player, InteractionHand hand, BarStackBE barbe, int index) {
        ItemStack barStack = barbe.getItems().getStackInSlot(index);

        if (barStack.isEmpty()) return;

        ItemStack handStack = player.getItemInHand(hand);

        if (!ItemOps.canTakeIntoHand(handStack, barStack)) return;

        ItemStack taken = barbe.extractAt(index);

        if (!taken.isEmpty()) {
            RightClickBlockSuppressor.suppress(player, pos, level);

            level.playSound(null, pos, ModSounds.BAR_EXTRACT, SoundSource.BLOCKS, 0.5f, 1.0f);

            ItemOps.giveToPlayerOrDrop(player, hand, taken);
        }
    }
}
