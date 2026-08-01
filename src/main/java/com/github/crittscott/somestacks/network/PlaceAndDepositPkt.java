package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ModSounds;
import com.github.crittscott.somestacks.block.BarColumn;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesColumn;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.StackPlacement;
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
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public class PlaceAndDepositPkt {
    private final BlockType blockType;
    private final Direction face;
    private final BlockPos pos;

    public PlaceAndDepositPkt(BlockType blockType, Direction face, BlockPos pos) {
        this.blockType = blockType;
        this.face = face;
        this.pos = pos;
    }

    public static void encode(PlaceAndDepositPkt msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.blockType.ordinal());
        buf.writeByte(msg.face.ordinal());
        buf.writeBlockPos(msg.pos);
    }

    /**
     * Decoding runs on the network thread, ahead of every check the mod makes. Both enums arrive as
     * indexes, so each is range-checked here and one naming no constant is carried through as null
     * for {@link #handle} to drop, rather than thrown out of the decoder.
     */
    public static PlaceAndDepositPkt decode(FriendlyByteBuf buf) {
        BlockType blockType = BlockType.fromOrdinal(buf.readByte());
        Direction face = faceFromOrdinal(buf.readByte());
        return new PlaceAndDepositPkt(blockType, face, buf.readBlockPos());
    }

    @Nullable
    private static Direction faceFromOrdinal(int ordinal) {
        Direction[] values = Direction.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    public static void handle(PlaceAndDepositPkt msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (msg.blockType == null || msg.face == null) {
                return;
            }
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

        // A creative player keeps what they deposit, the way vanilla placement leaves their stack
        // untouched, so the deposit works from a copy and the hand is never written back.
        boolean creative = sp.getAbilities().instabuild;
        ItemStack held = sp.getMainHandItem();
        ItemStack handStack = creative ? held.copy() : held;
        if (handStack.isEmpty()) {
            return;
        }

        // The gesture claimed the click, so the vanilla interaction the client still sends for it
        // must not also run against the clicked block: the deposit can leave items in the hand for
        // it to use, and a gesture that goes no further leaves the whole hand for them.
        if (!Protection.claimPlacement(sp, clicked, msg.pos)) {
            return;
        }

        Level level = sp.level();
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
            case SINGLES_STACK -> SinglesCubeIdx.traceAllPositions(view, msg.pos);
            case BAR_STACK -> BarCubeIdx.traceAllPositions(view, msg.pos);
        };
        if (msg.blockType != BlockType.STORAGE_STACK && depositIndex < 0) {
            return;
        }

        if (!firstDepositWouldSucceed(msg.blockType, level, msg.pos, handStack, depositIndex)) {
            return;
        }

        BlockState state = StackPlacement.stateFor(msg.blockType.getBlock(), level, msg.pos);
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

        switch (msg.blockType) {
            case STORAGE_STACK -> {
                StorageStackBE sbe = (StorageStackBE) level.getBlockEntity(msg.pos);
                sbe.deposit(handStack, sp);
                level.playSound(null, msg.pos, ModSounds.STORAGE_DEPOSIT,
                        SoundSource.BLOCKS, 0.5f, 1.0f);
            }
            case SINGLES_STACK -> {
                SinglesStackBE ssbe = (SinglesStackBE) level.getBlockEntity(msg.pos);
                ssbe.depositAt(depositIndex, handStack);
                level.playSound(null, msg.pos, ModSounds.SINGLES_DEPOSIT,
                        SoundSource.BLOCKS, 0.5f, 1.0f);
            }
            case BAR_STACK -> {
                BarStackBE barbe = (BarStackBE) level.getBlockEntity(msg.pos);
                barbe.depositAt(depositIndex, handStack);
                level.playSound(null, msg.pos, ModSounds.BAR_DEPOSIT,
                        SoundSource.BLOCKS, 0.5f, 1.0f);
            }
        }
        if (!creative) {
            sp.setItemInHand(InteractionHand.MAIN_HAND, handStack);
        }
    }

    /**
     * Whether the deposit that justifies this placement would take an item, asked of a block that
     * does not exist yet. The placement is weighed on this rather than undone behind a deposit that
     * failed: setting the block fires {@code EntityPlaceEvent}, and a block set and then removed is
     * a placement the claim and logging mods listening to it were told about and never saw undone.
     *
     * <p>Every cell of a fresh block is empty and the block is unrotated, so what the item is and
     * what seam the block would stand on are all that remain to ask. The seam is the block below
     * when it is a stack of the same type, and nothing when it is not, which is the same reading
     * {@link SinglesStackBE#depositAt} and {@link BarStackBE#depositAt} take once the block stands.
     */
    private static boolean firstDepositWouldSucceed(BlockType blockType, Level level, BlockPos pos,
                                                    ItemStack handStack, int depositIndex) {
        return switch (blockType) {
            case STORAGE_STACK -> StorageStackBE.isValidStorageItem(handStack);
            case SINGLES_STACK -> SinglesStackBE.isValidSinglesItem(handStack)
                    && SinglesCubeIdx.freshBlockSupports(
                            depositIndex,
                            level.getBlockEntity(pos.below()) instanceof SinglesStackBE below
                                    ? SinglesCubeIdx.topLayerOccupancy(below.getItems(), below.getRotation())
                                    : null);
            case BAR_STACK -> BarStackBE.isValidBarItem(handStack)
                    && BarCubeIdx.freshBlockSupports(
                            depositIndex,
                            level.getBlockEntity(pos.below()) instanceof BarStackBE below
                                    ? BarCubeIdx.topLayerOccupancy(below.getItems())
                                    : null);
        };
    }
}
