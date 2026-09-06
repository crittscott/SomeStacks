package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.util.StackMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Shared client gesture state and the loader-specific packet sender behind its rules. */
public final class ClientGestures {
    private ClientGestures() {}

    public static final String STACK_MODE_KEY_TRANSLATION_KEY =
            "key." + SomeStacksCommon.MODID + ".stack_mode";
    public static final String KEY_CATEGORY_TRANSLATION_KEY =
            "key.categories." + SomeStacksCommon.MODID;

    public interface Sender {
        void sendTogglePermanent(BlockPos pos);

        void sendPlaceAndDeposit(StackMode mode, BlockPos placePos, Direction face);

        void sendDeposit(BlockPos pos, BlockPos clickedPos);

        void sendExtract(BlockPos pos, int index);

        void sendRotateBlock(BlockPos pos);

        void sendRotateItem(BlockPos pos, int slotIndex);
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
        sender.sendTogglePermanent(pos);
    }

    public static void sendPlaceAndDeposit(BlockPos placePos, Direction face) {
        sender.sendPlaceAndDeposit(stackMode, placePos, face);
    }

    public static void sendDeposit(BlockPos pos, BlockPos clickedPos) {
        sender.sendDeposit(pos, clickedPos);
    }

    public static void sendExtract(BlockPos pos, int index) {
        sender.sendExtract(pos, index);
    }

    public static void sendRotateBlock(BlockPos pos) {
        sender.sendRotateBlock(pos);
    }

    public static void sendRotateItem(BlockPos pos, int slotIndex) {
        sender.sendRotateItem(pos, slotIndex);
    }
}
