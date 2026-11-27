package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.client.interaction.InteractionContext;
import com.github.crittscott.somestacks.client.interaction.InteractionRuleRegistry;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.ForgeNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Recognizes local gestures and synchronizes state before ordinary block-use packets. */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public final class ForgeClientEvents {
    private ForgeClientEvents() {}

    /** Routes the shared gesture-state sender through the Forge channel; requires the channel. */
    public static void init() {
        ClientGestures.setSender(ForgeNetworking.CHANNEL::sendToServer);
    }

    /**
     * Registers the client-local render commands. Forge hands a command to the server when the
     * client tree does not know it, so the server's own {@code ss} forms still reach it.
     */
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        ClientRenderCommands.register(
                event.getDispatcher(),
                new ClientRenderCommands.Feedback<>() {
                    @Override
                    public void success(CommandSourceStack source, Component message) {
                        source.sendSuccess(() -> message, false);
                    }

                    @Override
                    public void failure(CommandSourceStack source, Component message) {
                        source.sendFailure(message);
                    }
                });
    }

    /**
     * Installs a received server policy snapshot on the client thread, for whichever local player
     * exists at that moment. The packet handler that calls this also loads on a dedicated server,
     * so the client-only player lookup lives here, behind its {@code Dist.CLIENT} guard.
     */
    public static void applyServerSettings(ConfigSyncPkt packet) {
        ClientServerSettings.apply(packet, Minecraft.getInstance().player);
    }

    /**
     * Runs the air-click rules for an empty-handed click at nothing, then sends changed gesture
     * state. {@code HIGHEST}, like {@link #onRightClickItem}, so the air rules see the click before
     * any other mod's listener.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty evt) {
        InteractionContext ctx =
                InteractionContext.forAirClick(
                                evt.getEntity(),
                                evt.getHand(),
                                ClientGestures.currentMode(),
                                ForgeClientRendering.STACK_MODE_KEY.isDown())
                        .withHittingBlock(ClientGestures.isHittingBlock());
        InteractionRuleRegistry.processAirRules(ctx);
        ClientGestures.syncState(ForgeClientRendering.STACK_MODE_KEY.isDown());
    }

    /**
     * Client side: runs the air-click rules for an item use, sends changed gesture state, and
     * cancels the use with {@code CONSUME} when a rule claims it (the Stack Modifier is down).
     * {@code HIGHEST} so the cancellation lands before any other mod acts on the item use.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem evt) {
        if (!evt.getLevel().isClientSide()) return;

        InteractionContext ctx =
                InteractionContext.forAirClick(
                                evt.getEntity(),
                                evt.getHand(),
                                ClientGestures.currentMode(),
                                ForgeClientRendering.STACK_MODE_KEY.isDown())
                        .withHittingBlock(ClientGestures.isHittingBlock());
        InteractionRuleRegistry.processAirRules(ctx);
        ClientGestures.syncState(ForgeClientRendering.STACK_MODE_KEY.isDown());

        if (ctx.shouldCancel()) {
            evt.setCanceled(true);
            // CONSUME ends the client's hand loop without a swing; left at PASS the off hand's item
            // would be used next. Forge still sends the use packet, which the server refuses.
            evt.setCancellationResult(InteractionResult.CONSUME);
        }
    }

    /**
     * Runs after every other listener, as the Forge server listener and both Fabric callbacks do,
     * so an earlier listener that takes the click keeps it on both sides. The listener still
     * receives a click another listener canceled, because vanilla sends the block-use packet either
     * way and the server must read it with the current mode and modifier state.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock evt) {
        if (!evt.getLevel().isClientSide()) {
            return;
        }
        ClientGestures.syncState(ForgeClientRendering.STACK_MODE_KEY.isDown());
        if (evt.isCanceled()) return;

        InteractionContext ctx =
                InteractionContext.forBlockClick(
                        evt.getEntity(),
                        evt.getLevel(),
                        evt.getHand(),
                        evt.getPos(),
                        ClientGestures.currentMode(),
                        ForgeClientRendering.STACK_MODE_KEY.isDown());
        InteractionRuleRegistry.processBlockRules(ctx);

        if (ctx.shouldCancel()) {
            evt.setCanceled(true);
            evt.setUseBlock(Event.Result.DENY);
            evt.setUseItem(Event.Result.DENY);
            // The cancellation result is what the client's own interaction chain sees. Left at the
            // default PASS it reads as "nobody handled this" and the click falls through to using
            // the held item, so a bucket empties itself into the space the gesture is aiming at.
            evt.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** Forgets the state last sent, so the next click sends the new connection current state. */
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientGestures.resetSync();
    }

    /** Forgets the state last sent and saves measured profiles when leaving a world or server. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientGestures.resetSync();
        AutoRenderProfiles.saveCache();
    }

    /** Saves measured profiles at shutdown, including measurements made outside a world. */
    @SubscribeEvent
    public static void onGameShuttingDown(GameShuttingDownEvent event) {
        AutoRenderProfiles.saveCache();
    }
}
