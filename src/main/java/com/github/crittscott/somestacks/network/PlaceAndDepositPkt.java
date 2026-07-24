package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ModSounds;
import com.github.crittscott.somestacks.block.BarColumn;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
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
import net.minecraft.world.phys.Vec3;
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

            if (Protection.isProtected(sp, msg.pos)) {
                return;
            }

            Level level = sp.level();
            if (!level.getBlockState(msg.pos).canBeReplaced()) {
                return;
            }

            ItemStack handStack = sp.getItemInHand(msg.hand);

            if (!sp.mayUseItemAt(msg.pos, msg.face, handStack)) {
                return;
            }

            // Check if item is from a disabled mod and notify player
            if (ItemOps.checkDisabledModAndNotify(handStack, sp)) {
                return;
            }

            // Check if specific item is disabled and notify player
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
                case BAR_STACK -> !BarColumn.columnHasRoomFor(level, msg.pos);
                case SINGLES_STACK -> false;
            };
            if (columnFull) {
                sp.displayClientMessage(
                        Component.literal("Stack is at its maximum height of " + StoragePile.maxHeight()),
                        true
                );
                return;
            }

            if (msg.blockType == BlockType.SINGLES_STACK && msg.face == Direction.UP) {
                BlockPos below = msg.pos.below();
                var beBelow = level.getBlockEntity(below);

                if (beBelow instanceof SinglesStackBE ssbeBelow) {
                    Vec3 eyePos = sp.getEyePosition(1.0f);
                    Vec3 lookDir = sp.getLookAngle();
                    int depositIndex = SinglesCubeIdx.calculateDepositIndex(eyePos, lookDir, msg.pos, ssbeBelow.getRotation());

                    if (depositIndex >= 0) {
                        int[] xyz = SinglesCubeIdx.xyzFromIndex(depositIndex);
                        int x = xyz[0];
                        int z = xyz[2];
                        int lowerIndex = 3 * 16 + z * 4 + x;

                        boolean isGrounded = !ssbeBelow.getItems().getStackInSlot(lowerIndex).isEmpty();

                        if (!isGrounded) {
                            return;
                        }
                    }
                }
            }

            if (msg.blockType == BlockType.BAR_STACK && msg.face == Direction.UP) {
                BlockPos below = msg.pos.below();
                var beBelow = level.getBlockEntity(below);

                if (beBelow instanceof BarStackBE barBeBelow) {
                    Vec3 eyePos = sp.getEyePosition(1.0f);
                    Vec3 lookDir = sp.getLookAngle();

                    // Targeted through the same trace the deposit below will use, against a block
                    // that is still empty, so the two cannot disagree about which cell is meant.
                    int depositIndex = BarCubeIdx.traceAllPositions(eyePos, lookDir, msg.pos);
                    boolean[] seam = BarCubeIdx.topLayerOccupancy(barBeBelow.getItems());

                    if (depositIndex >= 0 && !BarCubeIdx.freshBlockSupports(depositIndex, seam)) {
                        return;
                    }
                }
            }

            BlockState state = msg.blockType.getBlock().defaultBlockState();

            if (!Protection.placeChecked(sp, sp.serverLevel(), msg.pos, state, msg.face.getOpposite())) {
                return;
            }

            var be = level.getBlockEntity(msg.pos);

            boolean depositSucceeded = false;

            if (be instanceof StorageStackBE sbe) {
                // A block placed onto a pile joins it, so it takes the pile's mode rather than
                // imposing a fresh one — placing beneath a permanent pile makes this the base.
                StoragePile.adoptNeighbourState(level, msg.pos, sbe);

                int deposited = sbe.deposit(handStack, sp);
                sp.setItemInHand(msg.hand, handStack);

                if (deposited > 0) {
                    level.playSound(null, msg.pos, ModSounds.STORAGE_DEPOSIT, SoundSource.BLOCKS, 0.5f, 1.0f);
                    depositSucceeded = true;
                }
            } else if (be instanceof SinglesStackBE ssbe) {
                depositSucceeded = depositIntoSingles(ssbe, sp, msg, handStack, level);
            } else if (be instanceof BarStackBE barbe) {
                depositSucceeded = depositIntoBar(barbe, sp, msg, handStack, level);
            }

            if (!depositSucceeded) {
                level.removeBlock(msg.pos, false);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static boolean depositIntoSingles(SinglesStackBE ssbe, ServerPlayer sp, PlaceAndDepositPkt msg,
                                              ItemStack handStack, Level level) {
        IItemHandler handler = ssbe.getItems();
        Vec3 eyePos = sp.getEyePosition(1.0f);
        Vec3 lookDir = sp.getLookAngle();
        int index = SinglesCubeIdx.traceAllPositions(eyePos, lookDir, msg.pos, handler, 0);

        if (index < 0 || !handler.getStackInSlot(index).isEmpty() || !SinglesCubeIdx.isGrounded(index, handler)) {
            return false;
        }

        boolean deposited = ssbe.depositAt(index, handStack);
        sp.setItemInHand(msg.hand, handStack);

        if (deposited) {
            level.playSound(null, msg.pos, ModSounds.SINGLES_DEPOSIT, SoundSource.BLOCKS, 0.5f, 1.0f);
        }
        return deposited;
    }

    private static boolean depositIntoBar(BarStackBE barbe, ServerPlayer sp, PlaceAndDepositPkt msg,
                                          ItemStack handStack, Level level) {
        IItemHandler handler = barbe.getItems();
        Vec3 eyePos = sp.getEyePosition(1.0f);
        Vec3 lookDir = sp.getLookAngle();
        int index = BarCubeIdx.traceAllPositions(eyePos, lookDir, msg.pos, handler);

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
