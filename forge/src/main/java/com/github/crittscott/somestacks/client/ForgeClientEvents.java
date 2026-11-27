package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.network.ForgeNetworking;
import com.github.crittscott.somestacks.util.BlockType;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The client's gesture entry point: the three right-click events feed {@link ClientGestures}. A
 * matched block rule is carried by the normal vanilla use packet; the custom packet synchronizes
 * only the selected mode and modifier state.
 *
 * <p>A canceled event denies both the block use and the item use, so a gesture never also spends
 * what the player is holding.
 */
@Mod.EventBusSubscriber(
        modid = SomeStacksCommon.MODID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeClientEvents {
    private ForgeClientEvents() {}

    /** Installs the sender that carries gesture state to the server. Runs once at client setup. */
    public static void init() {
        ClientGestures.setSender(
                payload -> ForgeNetworking.CHANNEL.send(payload, PacketDistributor.SERVER.noArg()));
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
    @SubscribeEvent(priority = Priority.HIGHEST)
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty evt) {
        // RightClickEmpty is a pure notification: there is no vanilla action behind an empty-hand
        // air click to suppress, so the rules run but nothing is canceled.
        ClientGestures.syncState(ForgeClientSetup.STACK_MODE_KEY.isDown());
        if (ClientGestures.isHittingBlock(Minecraft.getInstance().hitResult)) return;
        ClientGestures.processClick(
                evt.getEntity(),
                evt.getHand(),
                null,
                false,
                ForgeClientSetup.STACK_MODE_KEY.isDown());
    }

    /**
     * Classifies an air click with a held item, syncs gesture state, and cancels the item use when
     * the gesture claims the click. {@code HIGHEST}, so another mod's listener does not use the
     * item before a claiming gesture cancels it.
     */
    @SubscribeEvent(priority = Priority.HIGHEST)
    public static boolean onRightClickItem(PlayerInteractEvent.RightClickItem evt) {
        if (!evt.getLevel().isClientSide()) return false;

        ClientGestures.syncState(ForgeClientSetup.STACK_MODE_KEY.isDown());
        if (ClientGestures.isHittingBlock(Minecraft.getInstance().hitResult)) return false;
        var gesture =
                ClientGestures.processClick(
                        evt.getEntity(),
                        evt.getHand(),
                        null,
                        false,
                        ForgeClientSetup.STACK_MODE_KEY.isDown());
        return gesture.consumesLocalUse(evt.getEntity().getMainHandItem());
    }

    /**
     * Synchronizes gesture state for every client block click, including one a later listener
     * cancels, because the vanilla block-use packet that follows still carries the action to the
     * server. Forge's event bus does not deliver a canceled event to later listeners, so this runs
     * apart from {@link #onRightClick}, first and without cancelling.
     */
    @SubscribeEvent(priority = Priority.HIGHEST)
    public static void onRightClickSync(PlayerInteractEvent.RightClickBlock evt) {
        if (evt.getLevel().isClientSide()) {
            ClientGestures.syncState(ForgeClientSetup.STACK_MODE_KEY.isDown());
        }
    }

    /**
     * Runs last, as the server listener and Fabric's gesture phase do, so a listener that handles
     * the click earlier keeps it.
     */
    @SubscribeEvent(priority = Priority.LOWEST)
    public static boolean onRightClick(PlayerInteractEvent.RightClickBlock evt) {
        // Only the client-side firing drives the gesture rules. In single player the integrated
        // server shares this event bus, and adjacent protection consultation re-fires
        // RightClickBlock; handling that server-side re-fire would re-run the client rules.
        if (!evt.getLevel().isClientSide()) {
            return false;
        }

        var gesture =
                ClientGestures.processClick(
                        evt.getEntity(),
                        evt.getHand(),
                        BlockType.of(evt.getLevel().getBlockState(evt.getPos()).getBlock()),
                        true,
                        ForgeClientSetup.STACK_MODE_KEY.isDown());

        boolean cancel = gesture.consumesLocalUse(evt.getEntity().getMainHandItem());
        if (cancel) {
            evt.setUseBlock(Result.DENY);
            evt.setUseItem(Result.DENY);
            // The cancellation result is what the client's own interaction chain sees. Left at the
            // default PASS it reads as "nobody handled this" and the click falls through to using
            // the held item, so a bucket empties itself into the space the gesture is aiming at.
            evt.setCancellationResult(InteractionResult.SUCCESS);
        }
        return cancel;
    }
}
