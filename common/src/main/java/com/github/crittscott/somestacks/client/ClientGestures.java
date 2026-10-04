package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.network.DepositPkt;
import com.github.crittscott.somestacks.network.ExtractPkt;
import com.github.crittscott.somestacks.network.PlaceAndDepositPkt;
import com.github.crittscott.somestacks.network.RotateBlockPkt;
import com.github.crittscott.somestacks.network.RotateItemPkt;
import com.github.crittscott.somestacks.network.TogglePermanentPkt;
import com.github.crittscott.somestacks.util.StackMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/** Shared client gesture state and the loader-specific packet sender behind its rules. */
public final class ClientGestures {
    private ClientGestures() {}

    public static final String STACK_MODE_KEY_TRANSLATION_KEY =
            "key." + SomeStacksCommon.MODID + ".stack_mode";
    public static final String KEY_CATEGORY_TRANSLATION_KEY =
            "key.categories." + SomeStacksCommon.MODID;

    public interface Sender {
        void send(CustomPacketPayload payload);
    }

    private static StackMode stackMode = StackMode.STORAGE_STACK;
    private static Sender sender;

    public static void setSender(Sender sender) {
        ClientGestures.sender = sender;
    }

    public static StackMode currentMode() {
        return stackMode;
    }

    /** Advances to the next mode, skipping disabled block types but always retaining permanence. */
    public static void cycleMode() {
        StackMode startMode = stackMode;
        do {
            stackMode = StackMode.fromOrdinal((stackMode.ordinal() + 1) % StackMode.values().length);
            if (stackMode == StackMode.TOGGLE_PERMANENT) break;
            if (stackMode.isBlockType() && StackState.isBlockTypeEnabled(stackMode.toBlockType())) break;
            if (stackMode == startMode) break;
        } while (true);
    }

    public static void displayModeMessage(Player player) {
        Component modeComponent = Component.translatable(stackMode.getTranslationKey());
        player.displayClientMessage(
                Component.translatable("somestacks.message.storage_mode", modeComponent), true);
    }

    public static void sendTogglePermanent(BlockPos pos) {
        sender.send(new TogglePermanentPkt(pos));
    }

    public static void sendPlaceAndDeposit(BlockPos placePos, Direction face) {
        sender.send(new PlaceAndDepositPkt(stackMode.toBlockType(), face, placePos));
    }

    public static void sendDeposit(BlockPos pos, BlockPos clickedPos) {
        sender.send(new DepositPkt(pos, clickedPos));
    }

    public static void sendExtract(BlockPos pos, int index) {
        sender.send(new ExtractPkt(pos, index));
    }

    public static void sendRotateBlock(BlockPos pos) {
        sender.send(new RotateBlockPkt(pos));
    }

    public static void sendRotateItem(BlockPos pos, int slotIndex) {
        sender.send(new RotateItemPkt(pos, slotIndex));
    }
}
