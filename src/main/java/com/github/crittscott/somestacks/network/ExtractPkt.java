package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ModSounds;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.util.ItemOps;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ExtractPkt(BlockPos pos, int index) {

    public static void encode(ExtractPkt msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.index);
    }

    public static ExtractPkt decode(FriendlyByteBuf buf) {
        return new ExtractPkt(buf.readBlockPos(), buf.readInt());
    }

    public static void handle(ExtractPkt msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = PacketBoundary.validate(ctx, msg.pos);
            if (player == null) return;

            // The mark goes on the stack itself: extraction can put an item in a hand that was
            // empty, at a position a Storage settle or a Bar collapse may be about to clear.
            if (!Protection.claimInteraction(player, msg.pos, msg.pos)) return;

            Level level = player.level();
            BlockEntity be = level.getBlockEntity(msg.pos);
            if (be == null) return;

            InteractionHand hand = InteractionHand.MAIN_HAND;
            if (be instanceof StorageStackBE sbe) {
                handleStorageExtract(level, msg.pos, player, hand, sbe, msg.index);
            } else if (be instanceof SinglesStackBE ssbe) {
                handleSinglesExtract(level, msg.pos, player, hand, ssbe, msg.index);
            } else if (be instanceof BarStackBE barbe) {
                handleBarExtract(level, msg.pos, player, hand, barbe, msg.index);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    static void handleStorageExtract(Level level, BlockPos pos, Player player, InteractionHand hand, StorageStackBE sbe, int index) {
        ItemStack handStack = player.getItemInHand(hand);

        int maxCanTake = handStack.isEmpty()
                ? Integer.MAX_VALUE
                : handStack.getMaxStackSize() - handStack.getCount();

        ItemStack taken = sbe.extractAt(index, maxCanTake, handStack.isEmpty() ? ItemStack.EMPTY : handStack);

        if (!taken.isEmpty()) {
            level.playSound(null, pos, ModSounds.STORAGE_EXTRACT, SoundSource.BLOCKS, 0.5f, 1.0f);

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

    static void handleSinglesExtract(Level level, BlockPos pos, Player player, InteractionHand hand, SinglesStackBE ssbe, int index) {
        if (index < 0 || index >= SinglesStackBE.SLOTS) return;

        ItemStack cubeStack = ssbe.getItems().getStackInSlot(index);

        if (cubeStack.isEmpty()) return;

        ItemStack handStack = player.getItemInHand(hand);

        if (!ItemOps.canTakeIntoHand(handStack, cubeStack)) return;

        ItemStack taken = ssbe.extractAt(index);

        if (!taken.isEmpty()) {
            level.playSound(null, pos, ModSounds.SINGLES_EXTRACT, SoundSource.BLOCKS, 0.5f, 1.0f);

            ItemOps.giveToPlayerOrDrop(player, hand, taken);
        }
    }

    static void handleBarExtract(Level level, BlockPos pos, Player player, InteractionHand hand, BarStackBE barbe, int index) {
        if (index < 0 || index >= BarStackBE.SLOTS) return;

        ItemStack barStack = barbe.getItems().getStackInSlot(index);

        if (barStack.isEmpty()) return;

        ItemStack handStack = player.getItemInHand(hand);

        if (!ItemOps.canTakeIntoHand(handStack, barStack)) return;

        ItemStack taken = barbe.extractAt(index);

        if (!taken.isEmpty()) {
            level.playSound(null, pos, ModSounds.BAR_EXTRACT, SoundSource.BLOCKS, 0.5f, 1.0f);

            ItemOps.giveToPlayerOrDrop(player, hand, taken);
        }
    }
}
