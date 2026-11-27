package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.network.GestureStatePkt;
import com.github.crittscott.somestacks.util.StackMode;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;

import org.lwjgl.glfw.GLFW;

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
     * Advances the selected mode along {@link StackMode#next()}, skipping server-disabled block
     * types. Toggle Permanent is always available, so the walk ends there at the latest. This
     * changes local selection only; it neither sends state nor displays a message.
     */
    public static void cycleMode() {
        do {
            stackMode = stackMode.next();
        } while (!isSelectable(stackMode));
    }

    public static void displayModeMessage(Player player) {
        Component modeComponent = Component.translatable(stackMode.getTranslationKey());
        player.displayClientMessage(
                Component.translatable("somestacks.message.storage_mode", modeComponent), true);
    }

    /**
     * Whether the player's crosshair is on a block. Distinguishes a click at open air, which cycles
     * the placement mode, from an item use that follows a block click no gesture claimed.
     */
    public static boolean isHittingBlock() {
        HitResult hit = Minecraft.getInstance().hitResult;
        return hit != null && hit.getType() == HitResult.Type.BLOCK;
    }

    /**
     * Moves the selection off a block type the current server policy disables, as {@link
     * #cycleMode()} would, so a placement never names a type the server refuses.
     *
     * @return whether the selected mode changed
     */
    public static boolean leaveDisabledMode() {
        if (isSelectable(stackMode)) {
            return false;
        }
        cycleMode();
        return true;
    }

    private static boolean isSelectable(StackMode mode) {
        return !mode.isBlockType() || ClientServerSettings.isBlockTypeEnabled(mode.toBlockType());
    }

    /** Sends changed state before the loader allows the ordinary use packet to proceed. */
    public static void syncState(boolean modifierDown) {
        if (lastSentMode == stackMode && lastSentModifier == modifierDown) return;
        sender.accept(new GestureStatePkt(stackMode, modifierDown));
        lastSentMode = stackMode;
        lastSentModifier = modifierDown;
    }

    /** A new connection must establish state even when the selected mode has not changed. */
    public static void resetSync() {
        lastSentMode = null;
    }
}
