package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ModSounds;
import com.github.crittscott.somestacks.block.BarColumn;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesColumn;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.server.RightClickBlockSuppressor;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.ViewRay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PlaceAndDepositPkt {
    private final BlockType blockType;
    private final Direction face;
    private final InteractionHand hand;
    private final BlockPos pos;

    public PlaceAndDepositPkt(BlockType blockType, Direction face, InteractionHand hand, BlockPos pos) {
        this.blockType = blockType;
        this.face = face;
        this.hand = hand;
        this.pos = pos;
    }

    public static void encode(PlaceAndDepositPkt msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.blockType.ordinal());
        buf.writeEnum(msg.face);
        buf.writeEnum(msg.hand);
        buf.writeBlockPos(msg.pos);
    }

    public static PlaceAndDepositPkt decode(FriendlyByteBuf buf) {
        return new PlaceAndDepositPkt(
                BlockType.fromOrdinal(buf.readByte()),
                buf.readEnum(Direction.class),
                buf.readEnum(InteractionHand.class),
                buf.readBlockPos()
        );
    }

    public static void handle(PlaceAndDepositPkt msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = PacketBoundary.validate(ctx, msg.pos);
            if (sp == null) {
                return;
            }
            apply(sp, msg);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void apply(ServerPlayer sp, PlaceAndDepositPkt msg) {
        // The interaction happened against the block the player clicked, which is the one the
        // new block will rest against; msg.pos is the replaceable position it goes into and has
        // no shape to report. The client sends msg.pos as the clicked position offset along
        // msg.face, so backing out along that face recovers it.
        BlockPos clicked = msg.pos.relative(msg.face.getOpposite());

        ItemStack handStack = sp.getItemInHand(msg.hand);
        if (handStack.isEmpty()) {
            return;
        }

        if (Protection.isProtected(sp, msg.pos)) {
            return;
        }

        if (!Protection.mayPlaceAgainst(sp, clicked, msg.hand)) {
            return;
        }

        // The gesture claimed the click, so the vanilla interaction the client still sends for it
        // must not also run against the clicked block: the deposit can leave items in the hand for
        // it to use, and a placement rolled back below leaves the clicked block exposed to them.
        //
        // It goes here rather than earlier because the consult above fires the very event the mark
        // vetoes, and the placement would otherwise refuse itself.
        Level level = sp.level();
        RightClickBlockSuppressor.suppress(sp, clicked, level);

        if (!level.getBlockState(msg.pos).canBeReplaced()) {
            return;
        }

        if (!sp.mayUseItemAt(msg.pos, msg.face, handStack)) {
            return;
        }

        if (ItemOps.checkDisabledModAndNotify(handStack, sp)) {
            return;
        }

        if (ItemOps.checkDisabledItemAndNotify(handStack, sp)) {
            return;
        }

        if (!msg.blockType.getConfigValue().get()) {
            return;
        }

        // Tested against the whole column this would form, so a block dropped into the gap
        // between two piles cannot join them into an over-tall one.
        boolean columnFull = switch (msg.blockType) {
            case STORAGE_STACK -> !StoragePile.columnHasRoomFor(level, msg.pos);
            case SINGLES_STACK -> !SinglesColumn.columnHasRoomFor(level, msg.pos);
            case BAR_STACK -> !BarColumn.columnHasRoomFor(level, msg.pos);
        };
        if (columnFull) {
            sp.displayClientMessage(
                    Component.literal("Stack is at its maximum height of " + StoragePile.maxHeight()),
                    true
            );
            return;
        }

        ViewRay view = ViewRay.of(sp);
        int depositIndex = switch (msg.blockType) {
            case STORAGE_STACK -> -1;
            case SINGLES_STACK ->
                    SinglesCubeIdx.calculateDepositIndex(view, msg.pos, 0);
            case BAR_STACK -> BarCubeIdx.traceAllPositions(view, msg.pos);
        };
        if (msg.blockType != BlockType.STORAGE_STACK && depositIndex < 0) {
            return;
        }

        if (msg.blockType == BlockType.SINGLES_STACK && msg.face == Direction.UP) {
            BlockPos below = msg.pos.below();
            var beBelow = level.getBlockEntity(below);

            if (beBelow instanceof SinglesStackBE ssbeBelow) {
                boolean[] seam = SinglesCubeIdx.topLayerOccupancy(ssbeBelow.getItems(), ssbeBelow.getRotation());

                if (!SinglesCubeIdx.freshBlockSupports(depositIndex, seam)) {
                    return;
                }
            }
        }

        if (msg.blockType == BlockType.BAR_STACK && msg.face == Direction.UP) {
            BlockPos below = msg.pos.below();
            var beBelow = level.getBlockEntity(below);

            if (beBelow instanceof BarStackBE barBeBelow) {
                boolean[] seam = BarCubeIdx.topLayerOccupancy(barBeBelow.getItems());

                if (!BarCubeIdx.freshBlockSupports(depositIndex, seam)) {
                    return;
                }
            }
        }

        BlockState state = msg.blockType.getBlock().defaultBlockState();
        VoxelShape finalCollision = switch (msg.blockType) {
            case STORAGE_STACK ->
                    state.getCollisionShape(level, msg.pos, CollisionContext.empty());
            case SINGLES_STACK -> SinglesCubeIdx.shapeFor(depositIndex, 0);
            case BAR_STACK -> BarCubeIdx.shapeFor(depositIndex);
        };

        if (!Protection.placeChecked(
                sp, sp.serverLevel(), msg.pos, state, msg.face.getOpposite(), finalCollision)) {
            return;
        }

        boolean depositSucceeded = switch (msg.blockType) {
            case STORAGE_STACK -> {
                StorageStackBE sbe = (StorageStackBE) level.getBlockEntity(msg.pos);

                int deposited = sbe.deposit(handStack, sp);
                sp.setItemInHand(msg.hand, handStack);

                if (deposited > 0) {
                    level.playSound(null, msg.pos, ModSounds.STORAGE_DEPOSIT,
                            SoundSource.BLOCKS, 0.5f, 1.0f);
                }
                yield deposited > 0;
            }
            case SINGLES_STACK -> depositIntoSingles(
                    (SinglesStackBE) level.getBlockEntity(msg.pos),
                    sp, msg, handStack, level, depositIndex);
            case BAR_STACK -> depositIntoBar(
                    (BarStackBE) level.getBlockEntity(msg.pos),
                    sp, msg, handStack, level, depositIndex);
        };

        if (!depositSucceeded) {
            level.removeBlock(msg.pos, false);
            }
    }

    private static boolean depositIntoSingles(SinglesStackBE ssbe, ServerPlayer sp, PlaceAndDepositPkt msg,
                                              ItemStack handStack, Level level, int index) {
        IItemHandler handler = ssbe.getItems();

        if (index < 0 || !handler.getStackInSlot(index).isEmpty()) {
            return false;
        }

        // Grounding is left to depositAt, which is the only caller holding the seam beneath.
        boolean deposited = ssbe.depositAt(index, handStack);
        sp.setItemInHand(msg.hand, handStack);

        if (deposited) {
            level.playSound(null, msg.pos, ModSounds.SINGLES_DEPOSIT, SoundSource.BLOCKS, 0.5f, 1.0f);
        }
        return deposited;
    }

    private static boolean depositIntoBar(BarStackBE barbe, ServerPlayer sp, PlaceAndDepositPkt msg,
                                          ItemStack handStack, Level level, int index) {
        IItemHandler handler = barbe.getItems();

        if (index < 0 || !handler.getStackInSlot(index).isEmpty()) {
            return false;
        }

        // Grounding is left to depositAt, which is the only caller holding the seam beneath.
        boolean deposited = barbe.depositAt(index, handStack);
        sp.setItemInHand(msg.hand, handStack);

        if (deposited) {
            level.playSound(null, msg.pos, ModSounds.BAR_DEPOSIT, SoundSource.BLOCKS, 0.5f, 1.0f);
        }
        return deposited;
    }
}
