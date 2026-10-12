package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.util.StackMode;
import com.github.crittscott.somestacks.network.GestureStatePkt;
import java.util.function.Consumer;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Shared client gesture state and the loader-specific packet sender behind its rules. */
public final class ClientGestures {
    private ClientGestures() {}

    private static StackMode stackMode = StackMode.STORAGE_STACK;
    private static StackMode lastSentMode;
    private static boolean lastSentModifier;
    private static Consumer<GestureStatePkt> sender;

    public static Consumer<GestureStatePkt> setSender(Consumer<GestureStatePkt> sender) {
        Consumer<GestureStatePkt> previous = ClientGestures.sender;
        ClientGestures.sender = sender;
        resetSync();
        return previous;
    }

    public static StackMode currentMode() {
        return stackMode;
    }

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

    /** Sends changed state before the loader allows the ordinary use packet to proceed. */
    public static void syncState(boolean modifierDown) {
        if (lastSentMode == stackMode && lastSentModifier == modifierDown) return;
        Objects.requireNonNull(sender, "Client gesture sender is not installed")
                .accept(new GestureStatePkt(stackMode, modifierDown));
        lastSentMode = stackMode;
        lastSentModifier = modifierDown;
    }

    /** A new connection must establish state even when the selected mode has not changed. */
    public static void resetSync() {
        lastSentMode = null;
    }
}
