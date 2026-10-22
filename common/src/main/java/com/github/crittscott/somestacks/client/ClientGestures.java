package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.network.GestureStatePkt;
import com.github.crittscott.somestacks.util.StackMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.Consumer;

/** Shared client gesture state and the loader-specific packet sender behind its rules. */
public final class ClientGestures {
    private ClientGestures() {}

    public static final String STACK_MODIFIER_KEY_ID = "key.somestacks.stack_mode";
    public static final String KEY_CATEGORY = "key.categories.somestacks";
    public static final int DEFAULT_STACK_MODIFIER_KEY = GLFW.GLFW_KEY_V;

    private static StackMode stackMode = StackMode.STORAGE_STACK;
    private static StackMode lastSentMode;
    private static boolean lastSentModifier;
    private static Consumer<GestureStatePkt> sender;

    /**
     * Installs the loader packet sender and resets synchronization so the next state sync sends
     * even unchanged mode/modifier state. Does not change the selected mode.
     *
     * @param sender sender to install, or null to clear it; syncing requires an installed sender
     * @return the previous sender, which may be null
     */
    public static Consumer<GestureStatePkt> setSender(Consumer<GestureStatePkt> sender) {
        Consumer<GestureStatePkt> previous = ClientGestures.sender;
        ClientGestures.sender = sender;
        resetSync();
        return previous;
    }

    public static StackMode currentMode() {
        return stackMode;
    }

    /**
     * Advances the selected mode, skipping server-disabled block types. Toggle Permanent remains
     * available. This changes local selection only; it neither sends state nor displays a message.
     */
    public static void cycleMode() {
        StackMode startMode = stackMode;
        do {
            stackMode = StackMode.fromOrdinal((stackMode.ordinal() + 1) % StackMode.values().length);
            if (stackMode == StackMode.TOGGLE_PERMANENT) break;
            if (stackMode.isBlockType() && ClientServerSettings.isBlockTypeEnabled(stackMode.toBlockType())) break;
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
