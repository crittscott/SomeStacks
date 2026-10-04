package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.network.GestureStatePkt;
import com.github.crittscott.somestacks.util.StackMode;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;
import java.util.function.Consumer;

/** Shared client gesture state and its loader-specific state-sync sender. */
public final class ClientGestures {
    private ClientGestures() {}

    public static final String STACK_MODE_KEY_TRANSLATION_KEY =
            "key." + SomeStacksCommon.MODID + ".stack_mode";
    public static final String KEY_CATEGORY_TRANSLATION_KEY =
            "key.categories." + SomeStacksCommon.MODID;

    private static StackMode stackMode = StackMode.STORAGE_STACK;
    private static StackMode lastSentMode;
    private static boolean lastSentModifier;
    private static Consumer<CustomPacketPayload> sender;

    public static void setSender(Consumer<CustomPacketPayload> sender) {
        ClientGestures.sender = Objects.requireNonNull(sender);
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

    /** Sends only state that changed since the last interaction on this connection. */
    public static void syncState(boolean modifierDown) {
        if (lastSentMode == stackMode && lastSentModifier == modifierDown) {
            return;
        }
        sender.accept(new GestureStatePkt(stackMode, modifierDown));
        lastSentMode = stackMode;
        lastSentModifier = modifierDown;
    }

    /** Forces the next interaction to establish state on a new connection. */
    public static void resetSync() {
        lastSentMode = null;
    }
}
