package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.client.interaction.InteractionContext;
import com.github.crittscott.somestacks.client.interaction.InteractionRuleRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The client's gesture entry point: the three right-click events feed
 * {@link InteractionRuleRegistry}. A matched block rule is carried by the normal vanilla use
 * packet; the custom packet synchronizes only the selected mode and modifier state. A canceled
 * event denies both block and item use, so a gesture never also spends what the player is holding.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    public static void init() {
        ClientGestures.setSender(packet -> PacketDistributor.sendToServer(packet));
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        ClientRenderCommands.register(event.getDispatcher(), new ClientRenderCommands.Feedback<>() {
            @Override
            public void success(CommandSourceStack source,
                                Component message) {
                source.sendSuccess(() -> message, false);
            }

            @Override
            public void failure(CommandSourceStack source,
                                Component message) {
                source.sendFailure(message);
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty evt) {
        // RightClickEmpty is a pure notification on NeoForge: there is no vanilla action behind an
        // empty-hand air click to suppress, so the rules run but nothing is canceled.
        InteractionContext ctx = InteractionContext.forAirClick(
                evt.getEntity(), evt.getLevel(), evt.getHand(),
                ClientGestures.currentMode(), KeyMappings.STACK_MODE_KEY.isDown(),
                ClientGestures.isHittingBlock(Minecraft.getInstance().hitResult));
        ClientGestures.syncState(KeyMappings.STACK_MODE_KEY.isDown());
        InteractionRuleRegistry.processEmptyHandRules(ctx);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem evt) {
        if (!evt.getLevel().isClientSide()) return;
        if (evt.isCanceled()) return;

        InteractionContext ctx = InteractionContext.forAirClick(
                evt.getEntity(), evt.getLevel(), evt.getHand(),
                ClientGestures.currentMode(), KeyMappings.STACK_MODE_KEY.isDown(),
                ClientGestures.isHittingBlock(Minecraft.getInstance().hitResult));
        ClientGestures.syncState(KeyMappings.STACK_MODE_KEY.isDown());
        InteractionRuleRegistry.processItemRules(ctx);

        if (ctx.shouldCancel()) {
            evt.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock evt) {
        // Only the client-side firing drives the gesture rules. In single player the integrated
        // server shares this event bus, and adjacent protection consultation re-fires
        // RightClickBlock; handling that server-side re-fire would re-run the client rules.
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
            evt.setUseBlock(TriState.FALSE);
            evt.setUseItem(TriState.FALSE);
            // Left at the default PASS the click falls through to using the held item, so a bucket
            // empties itself into the space the gesture is aiming at.
            evt.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
