package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.client.interaction.InteractionContext;
import com.github.crittscott.somestacks.client.interaction.InteractionRuleRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
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
 * The client's gesture entry point: the three right-click events feed {@link
 * InteractionRuleRegistry}. A matched block rule is carried by the normal vanilla use packet; the
 * custom packet synchronizes only the selected mode and modifier state. A canceled event denies
 * both block and item use, so a gesture never also spends what the player is holding.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    /** Installs the sender that carries gesture state to the server. Runs once at client setup. */
    public static void init() {
        ClientGestures.setSender(packet -> PacketDistributor.sendToServer(packet));
    }

    /** Registers the client-side {@code /ss} subcommands. */
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
     * Runs the air rules for an empty-hand air click and syncs gesture state. {@code HIGHEST}, like
     * the item click, so the rules see the click before any other mod's listener.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty evt) {
        // RightClickEmpty is a pure notification on NeoForge: there is no vanilla action behind an
        // empty-hand air click to suppress, so the rules run but nothing is canceled.
        InteractionContext ctx =
                InteractionContext.forAirClick(
                        evt.getEntity(),
                        evt.getHand(),
                        ClientGestures.currentMode(),
                        ClientSetup.STACK_MODE_KEY.isDown(),
                        ClientGestures.isHittingBlock(Minecraft.getInstance().hitResult));
        ClientGestures.syncState(ClientSetup.STACK_MODE_KEY.isDown());
        InteractionRuleRegistry.processAirRules(ctx);
    }

    /**
     * Runs the air rules for an air click with a held item, syncs gesture state, and cancels the
     * item use when a rule claims the click. {@code HIGHEST}, so the item is not used by another
     * mod's listener before a claiming rule cancels it.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem evt) {
        if (!evt.getLevel().isClientSide()) return;

        InteractionContext ctx =
                InteractionContext.forAirClick(
                        evt.getEntity(),
                        evt.getHand(),
                        ClientGestures.currentMode(),
                        ClientSetup.STACK_MODE_KEY.isDown(),
                        ClientGestures.isHittingBlock(Minecraft.getInstance().hitResult));
        ClientGestures.syncState(ClientSetup.STACK_MODE_KEY.isDown());
        InteractionRuleRegistry.processAirRules(ctx);

        if (ctx.shouldCancel()) {
            evt.setCanceled(true);
        }
    }

    /**
     * Runs last, as the server listener and Fabric's gesture phase do, so a listener that handles
     * the click earlier keeps it. It still receives a canceled click to synchronize gesture state,
     * because the vanilla block-use packet that follows carries the action to the server.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock evt) {
        // Only the client-side firing drives the gesture rules. In single player the integrated
        // server shares this event bus, and adjacent protection consultation re-fires
        // RightClickBlock; handling that server-side re-fire would re-run the client rules.
        if (!evt.getLevel().isClientSide()) {
            return;
        }
        ClientGestures.syncState(ClientSetup.STACK_MODE_KEY.isDown());
        if (evt.isCanceled()) return;

        InteractionContext ctx =
                InteractionContext.forBlockClick(
                        evt.getEntity(),
                        evt.getLevel(),
                        evt.getHand(),
                        evt.getPos(),
                        ClientGestures.currentMode(),
                        ClientSetup.STACK_MODE_KEY.isDown());
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
