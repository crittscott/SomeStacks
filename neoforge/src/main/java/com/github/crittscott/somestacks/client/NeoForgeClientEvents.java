package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.util.BlockType;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The client's gesture entry point: the three right-click events feed {@link ClientGestures}. A
 * matched block rule is carried by the normal vanilla use packet; the custom packet synchronizes
 * only the selected mode and modifier state. A canceled event denies both block and item use, so a
 * gesture never also spends what the player is holding.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class NeoForgeClientEvents {
    private NeoForgeClientEvents() {}

    /** Installs the sender that carries gesture state to the server. Runs once at client setup. */
    public static void init() {
        ClientGestures.setSender(packet -> ClientPacketDistributor.sendToServer(packet));
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
     * Classifies an empty-hand air click and syncs gesture state. {@code HIGHEST}, like the item
     * click, so the gesture sees the click before any other mod's listener.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty evt) {
        // RightClickEmpty is a pure notification on NeoForge: there is no vanilla action behind an
        // empty-hand air click to suppress, so the rules run but nothing is canceled.
        ClientGestures.syncState(NeoForgeClientSetup.STACK_MODE_KEY.isDown());
        if (ClientGestures.isHittingBlock(Minecraft.getInstance().hitResult)) return;
        ClientGestures.processClick(
                evt.getEntity(),
                evt.getHand(),
                null,
                false,
                NeoForgeClientSetup.STACK_MODE_KEY.isDown());
    }

    /**
     * Classifies an air click with a held item, syncs gesture state, and cancels the item use when
     * the gesture claims the click. {@code HIGHEST}, so another mod's listener does not use the
     * item before a claiming gesture cancels it.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem evt) {
        if (!evt.getLevel().isClientSide()) return;

        ClientGestures.syncState(NeoForgeClientSetup.STACK_MODE_KEY.isDown());
        if (ClientGestures.isHittingBlock(Minecraft.getInstance().hitResult)) return;
        var gesture =
                ClientGestures.processClick(
                        evt.getEntity(),
                        evt.getHand(),
                        null,
                        false,
                        NeoForgeClientSetup.STACK_MODE_KEY.isDown());

        if (gesture.consumesLocalUse(evt.getEntity().getMainHandItem())) {
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
        ClientGestures.syncState(NeoForgeClientSetup.STACK_MODE_KEY.isDown());
        if (evt.isCanceled()) return;

        var gesture =
                ClientGestures.processClick(
                        evt.getEntity(),
                        evt.getHand(),
                        BlockType.of(evt.getLevel().getBlockState(evt.getPos()).getBlock()),
                        true,
                        NeoForgeClientSetup.STACK_MODE_KEY.isDown());

        if (gesture.consumesLocalUse(evt.getEntity().getMainHandItem())) {
            evt.setCanceled(true);
            evt.setUseBlock(TriState.FALSE);
            evt.setUseItem(TriState.FALSE);
            // Left at the default PASS the click falls through to using the held item, so a bucket
            // empties itself into the space the gesture is aiming at.
            evt.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
