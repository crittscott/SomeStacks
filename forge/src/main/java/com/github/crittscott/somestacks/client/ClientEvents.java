package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.client.interaction.InteractionContext;
import com.github.crittscott.somestacks.client.interaction.InteractionRuleRegistry;
import com.github.crittscott.somestacks.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Recognizes local gestures and synchronizes state before ordinary block-use packets. */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    public static void init() {
        ClientGestures.setSender(ModNetworking.CHANNEL::sendToServer);
    }

    /**
     * Registers the client-local render commands. Forge hands a command to the server when the
     * client tree does not know it, so the server's own {@code ss} forms still reach it.
     */
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        ClientRenderCommands.register(event.getDispatcher(), new ClientRenderCommands.Feedback<>() {
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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty evt) {
        if (!evt.getLevel().isClientSide()) return;

        InteractionContext ctx = InteractionContext.forAirClick(
                evt.getEntity(), evt.getLevel(), evt.getHand(),
                ClientGestures.currentMode(), KeyMappings.STACK_MODE_KEY.isDown())
                .withHittingBlock(isHittingBlock());
        InteractionRuleRegistry.processEmptyHandRules(ctx);
        ClientGestures.syncState(KeyMappings.STACK_MODE_KEY.isDown());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem evt) {
        if (!evt.getLevel().isClientSide()) return;
        if (evt.isCanceled()) return;

        InteractionContext ctx = InteractionContext.forAirClick(
                evt.getEntity(), evt.getLevel(), evt.getHand(),
                ClientGestures.currentMode(), KeyMappings.STACK_MODE_KEY.isDown())
                .withHittingBlock(isHittingBlock());
        InteractionRuleRegistry.processItemRules(ctx);
        ClientGestures.syncState(KeyMappings.STACK_MODE_KEY.isDown());

        if (ctx.shouldCancel()) {
            evt.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock evt) {
        if (!evt.getLevel().isClientSide()) {
            return;
        }
        if (evt.isCanceled()) return;
        if (evt.getFace() == null) return;

        InteractionContext ctx = InteractionContext.forBlockClick(
                evt.getEntity(), evt.getLevel(), evt.getHand(), evt.getPos(), evt.getFace(),
                ClientGestures.currentMode(), KeyMappings.STACK_MODE_KEY.isDown());
        ClientGestures.syncState(KeyMappings.STACK_MODE_KEY.isDown());
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

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientGestures.resetSync();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientGestures.resetSync();
        ClientRenderPacketSink.clear();
    }

    private static boolean isHittingBlock() {
        HitResult hit = Minecraft.getInstance().hitResult;
        return hit != null && hit.getType() == HitResult.Type.BLOCK;
    }
}
