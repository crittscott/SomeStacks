package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.network.GestureStatePkt;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.StackGesture;
import com.github.crittscott.somestacks.util.StackMode;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;

import java.util.function.Consumer;

import javax.annotation.Nullable;

/** Shared client gesture state and its loader-specific state-sync sender. */
public final class ClientGestures {
    private ClientGestures() {}

    public static final String STACK_MODE_KEY_TRANSLATION_KEY =
            "key." + SomeStacksCommon.MODID + ".stack_mode";
    public static final Identifier KEY_CATEGORY_ID =
            Identifier.fromNamespaceAndPath(SomeStacksCommon.MODID, "main");

    private static StackMode stackMode = StackMode.STORAGE_STACK;
    private static StackMode lastSentMode;
    private static boolean lastSentModifier;
    private static Consumer<CustomPacketPayload> sender;

    /** Replaces the loader sender and returns its predecessor; null detaches the sender. */
    @Nullable
    public static Consumer<CustomPacketPayload> setSender(
            @Nullable Consumer<CustomPacketPayload> sender) {
        Consumer<CustomPacketPayload> previous = ClientGestures.sender;
        ClientGestures.sender = sender;
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
        player.sendOverlayMessage(
                Component.translatable("somestacks.message.storage_mode", modeComponent));
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

    /**
     * Sends only state that changed since the last interaction on this connection, through the
     * sender the loader's client initializer installed.
     */
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

    /** Recognizes client intent and performs the client-owned air-cycle action. */
    public static StackGesture processClick(
            Player player,
            InteractionHand hand,
            @Nullable BlockType clicked,
            boolean hittingBlock,
            boolean modifier) {
        StackGesture gesture =
                StackGesture.classify(
                        clicked,
                        stackMode,
                        modifier,
                        player.isShiftKeyDown(),
                        player.getMainHandItem(),
                        hittingBlock,
                        hand);
        if (gesture == StackGesture.CYCLE_MODE) {
            cycleMode();
            syncState(true);
            displayModeMessage(player);
        }
        return gesture;
    }

    /** Whether an input is the main-hand air click used for empty-hand placement-mode cycling. */
    public static boolean isEmptyMainHandAirClick(Player player, @Nullable HitResult hit) {
        return player.getMainHandItem().isEmpty()
                && hit != null
                && hit.getType() == HitResult.Type.MISS;
    }

    /** Classifies the loader's current crosshair hit without consulting the client singleton. */
    public static boolean isHittingBlock(@Nullable HitResult hit) {
        return hit != null && hit.getType() == HitResult.Type.BLOCK;
    }
}
