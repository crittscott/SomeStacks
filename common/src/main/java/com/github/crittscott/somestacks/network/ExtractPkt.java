package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.server.PlayerEdits;
import com.github.crittscott.somestacks.server.StackSounds;
import com.github.crittscott.somestacks.util.ItemOps;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * An extraction from one rendered cell. The client traced the cell, so the index arrives from it
 * and is range-checked against the block entity it names. What the cell yields follows the type:
 * Storage gives as much as the hand accepts, Singles and Bar give one item.
 */
public record ExtractPkt(BlockPos pos, int index) implements CustomPacketPayload {

    public static final Type<ExtractPkt> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "extract"));
    public static final StreamCodec<FriendlyByteBuf, ExtractPkt> STREAM_CODEC =
            StreamCodec.ofMember(ExtractPkt::encode, ExtractPkt::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(ExtractPkt msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.index);
    }

    public static ExtractPkt decode(FriendlyByteBuf buf) {
        return new ExtractPkt(buf.readBlockPos(), buf.readInt());
    }

    public static void handleServer(ExtractPkt msg, ServerPlayer player) {
        if (PacketBoundary.validate(player, msg.pos) == null) return;

            // The mark goes on the stack itself: extraction can put an item in a hand that was
            // empty, at a position a Storage settle or a Bar collapse may be about to clear.
        if (!PlayerEdits.claimInteraction(player, msg.pos, msg.pos)) return;

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
    }

    /** Applies a validated player extraction to one local Storage slot. */
    public static void handleStorageExtract(Level level, BlockPos pos, Player player, InteractionHand hand, StorageStackBE sbe, int index) {
        ItemStack handStack = player.getItemInHand(hand);

        int maxCanTake = handStack.isEmpty()
                ? Integer.MAX_VALUE
                : handStack.getMaxStackSize() - handStack.getCount();

        ItemStack taken = sbe.extractAt(index, maxCanTake, handStack.isEmpty() ? ItemStack.EMPTY : handStack);

        if (!taken.isEmpty()) {
            level.playSound(null, pos, StackSounds.STORAGE_EXTRACT, SoundSource.BLOCKS, StackSounds.VOLUME, 1.0f);

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

    /** Applies a validated player extraction and draw-down to one local Singles cell. */
    public static void handleSinglesExtract(Level level, BlockPos pos, Player player, InteractionHand hand, SinglesStackBE ssbe, int index) {
        if (index < 0 || index >= SinglesStackBE.SLOTS) return;

        ItemStack cubeStack = ssbe.getItems().getStackInSlot(index);

        if (cubeStack.isEmpty()) return;

        ItemStack handStack = player.getItemInHand(hand);

        if (!ItemOps.canTakeIntoHand(handStack, cubeStack)) return;

        ItemStack taken = ssbe.extractAt(index);

        if (!taken.isEmpty()) {
            level.playSound(null, pos, StackSounds.SINGLES_EXTRACT, SoundSource.BLOCKS, StackSounds.VOLUME, 1.0f);

            ItemOps.giveToPlayerOrDrop(player, hand, taken);
        }
    }

    /** Applies a validated player extraction and support cascade to one local Bar position. */
    public static void handleBarExtract(Level level, BlockPos pos, Player player, InteractionHand hand, BarStackBE barbe, int index) {
        if (index < 0 || index >= BarStackBE.SLOTS) return;

        ItemStack barStack = barbe.getItems().getStackInSlot(index);

        if (barStack.isEmpty()) return;

        ItemStack handStack = player.getItemInHand(hand);

        if (!ItemOps.canTakeIntoHand(handStack, barStack)) return;

        ItemStack taken = barbe.extractAt(index);

        if (!taken.isEmpty()) {
            level.playSound(null, pos, StackSounds.BAR_EXTRACT, SoundSource.BLOCKS, StackSounds.VOLUME, 1.0f);

            ItemOps.giveToPlayerOrDrop(player, hand, taken);
        }
    }
}
