package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.client.ClientGestures;
import com.github.crittscott.somestacks.client.ClientRenderPacketSink;
import com.github.crittscott.somestacks.util.StackMode;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** Fabric client receivers and the packet sender used by the shared gesture rules. */
public final class FabricClientNetworking implements ClientGestures.Sender {
    private FabricClientNetworking() {}

    public static void init() {
        ClientGestures.setSender(new FabricClientNetworking());

        ClientPlayNetworking.registerGlobalReceiver(FabricNetworking.PROTOCOL,
                (client, handler, buf, responseSender) -> { });
        registerClient(FabricNetworking.CONFIG_SYNC, ConfigSyncPkt::decode,
                ClientRenderPacketSink::apply);
        registerClient(FabricNetworking.RENDER_OVERRIDE, RenderOverridePkt::decode,
                ClientRenderPacketSink::apply);
        registerClient(FabricNetworking.WRITE_OVERRIDES, WriteOverridesPkt::decode,
                ClientRenderPacketSink::apply);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!ClientPlayNetworking.canSend(FabricNetworking.PLACE_AND_DEPOSIT)) {
                handler.getConnection().disconnect(
                        Component.translatable("somestacks.disconnect.protocol"));
            }
        });
    }

    @Override
    public void sendTogglePermanent(BlockPos pos) {
        send(FabricNetworking.TOGGLE_PERMANENT, new TogglePermanentPkt(pos),
                TogglePermanentPkt::encode);
    }

    @Override
    public void sendPlaceAndDeposit(StackMode mode, BlockPos placePos, Direction face) {
        send(FabricNetworking.PLACE_AND_DEPOSIT,
                new PlaceAndDepositPkt(mode.toBlockType(), face, placePos),
                PlaceAndDepositPkt::encode);
    }

    @Override
    public void sendDeposit(BlockPos pos, BlockPos clickedPos) {
        send(FabricNetworking.DEPOSIT, new DepositPkt(pos, clickedPos), DepositPkt::encode);
    }

    @Override
    public void sendExtract(BlockPos pos, int index) {
        send(FabricNetworking.EXTRACT, new ExtractPkt(pos, index), ExtractPkt::encode);
    }

    @Override
    public void sendRotateBlock(BlockPos pos) {
        send(FabricNetworking.ROTATE_BLOCK, new RotateBlockPkt(pos), RotateBlockPkt::encode);
    }

    @Override
    public void sendRotateItem(BlockPos pos, int slotIndex) {
        send(FabricNetworking.ROTATE_ITEM, new RotateItemPkt(pos, slotIndex), RotateItemPkt::encode);
    }

    private static <T> void registerClient(
            ResourceLocation channel, Function<FriendlyByteBuf, T> decoder,
            java.util.function.Consumer<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(channel,
                (client, networkHandler, buf, responseSender) -> {
                    T packet = decoder.apply(buf);
                    client.execute(() -> handler.accept(packet));
                });
    }

    private static <T> void send(
            ResourceLocation channel, T packet, BiConsumer<T, FriendlyByteBuf> encoder) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        encoder.accept(packet, buf);
        ClientPlayNetworking.send(channel, buf);
    }
}
