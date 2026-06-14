package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ModSounds;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
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
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) {
                return;
            }

            Level level = sp.level();
            if (!level.isLoaded(msg.pos)) {
                return;
            }

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

                        boolean isGrounded = ssbeBelow.getCapability(ForgeCapabilities.ITEM_HANDLER)
                                .map(lowerHandler -> !lowerHandler.getStackInSlot(lowerIndex).isEmpty())
                                .orElse(false);

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
                    int depositIndex = BarCubeIdx.calculateDepositIndex(eyePos, lookDir, msg.pos);

                    if (depositIndex >= 0) {
                        int[] xyz = BarCubeIdx.xyzFromIndex(depositIndex);
                        int x = xyz[0];
                        int y = xyz[1];
                        int z = xyz[2];

                        double newMinX = BarCubeIdx.startPixelX(x, y);
                        double newMinZ = BarCubeIdx.startPixelZ(z, y);
                        double newMaxX = newMinX + BarCubeIdx.barWidth(y);
                        double newMaxZ = newMinZ + BarCubeIdx.barDepth(y);

                        boolean hasSupport = barBeBelow.getCapability(ForgeCapabilities.ITEM_HANDLER)
                                .map(lowerHandler -> {
                                    for (int i = 56; i < 64; i++) {
                                        if (!lowerHandler.getStackInSlot(i).isEmpty()) {
                                            int[] lowerXYZ = BarCubeIdx.xyzFromIndex(i);
                                            double lowerMinX = BarCubeIdx.startPixelX(lowerXYZ[0], lowerXYZ[1]);
                                            double lowerMinZ = BarCubeIdx.startPixelZ(lowerXYZ[2], lowerXYZ[1]);
                                            double lowerMaxX = lowerMinX + BarCubeIdx.barWidth(lowerXYZ[1]);
                                            double lowerMaxZ = lowerMinZ + BarCubeIdx.barDepth(lowerXYZ[1]);

                                            boolean overlaps = !(newMaxX <= lowerMinX || newMinX >= lowerMaxX ||
                                                    newMaxZ <= lowerMinZ || newMinZ >= lowerMaxZ);

                                            if (overlaps) {
                                                return true;
                                            }
                                        }
                                    }
                                    return false;
                                })
                                .orElse(false);

                        if (!hasSupport) {
                            return;
                        }
                    }
                }
            }

            BlockState state = msg.blockType.getBlock().defaultBlockState();

            if (!level.setBlock(msg.pos, state, 3)) {
                return;
            }

            var be = level.getBlockEntity(msg.pos);

            boolean depositSucceeded = false;

            if (be instanceof StorageStackBE sbe) {
                int deposited = sbe.deposit(handStack);
                sp.setItemInHand(msg.hand, handStack);

                if (deposited > 0) {
                    level.playSound(null, msg.pos, ModSounds.STORAGE_DEPOSIT, SoundSource.BLOCKS, 0.5f, 1.0f);
                    depositSucceeded = true;
                }
            } else if (be instanceof SinglesStackBE ssbe) {
                boolean[] deposited = new boolean[1];
                ssbe.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                    Vec3 eyePos = sp.getEyePosition(1.0f);
                    Vec3 lookDir = sp.getLookAngle();
                    int index = SinglesCubeIdx.traceAllPositions(eyePos, lookDir, msg.pos, handler, 0);

                    if (index < 0) {
                        return;
                    }

                    if (!handler.getStackInSlot(index).isEmpty()) {
                        return;
                    }

                    if (!SinglesCubeIdx.isGrounded(index, handler)) {
                        return;
                    }

                    deposited[0] = ssbe.depositAt(index, handStack);
                    sp.setItemInHand(msg.hand, handStack);

                    if (deposited[0]) {
                        level.playSound(null, msg.pos, ModSounds.SINGLES_DEPOSIT, SoundSource.BLOCKS, 0.5f, 1.0f);
                    }
                });
                depositSucceeded = deposited[0];
            } else if (be instanceof BarStackBE barbe) {
                boolean[] deposited = new boolean[1];
                barbe.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                    Vec3 eyePos = sp.getEyePosition(1.0f);
                    Vec3 lookDir = sp.getLookAngle();
                    int index = BarCubeIdx.traceAllPositions(eyePos, lookDir, msg.pos, handler);

                    if (index < 0) {
                        return;
                    }

                    if (!handler.getStackInSlot(index).isEmpty()) {
                        return;
                    }

                    if (!BarCubeIdx.isGrounded(index, handler)) {
                        return;
                    }

                    deposited[0] = barbe.depositAt(index, handStack);
                    sp.setItemInHand(msg.hand, handStack);

                    if (deposited[0]) {
                        level.playSound(null, msg.pos, ModSounds.BAR_DEPOSIT, SoundSource.BLOCKS, 0.5f, 1.0f);
                    }
                });
                depositSucceeded = deposited[0];
            }

            if (!depositSucceeded) {
                level.removeBlock(msg.pos, false);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
