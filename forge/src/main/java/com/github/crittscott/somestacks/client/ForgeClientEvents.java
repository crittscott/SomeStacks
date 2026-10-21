package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.network.ForgeNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The client's gesture entry point: the three right-click events feed
 * {@link ClientGestures}. A matched block rule is carried by the normal vanilla use
 * packet; the custom packet synchronizes only the selected mode and modifier state.
 *
 * <p>A canceled event denies both the block use and the item use, so a gesture never also spends
 * what the player is holding.
 */
@Mod.EventBusSubscriber(modid = SomeStacksCommon.MODID,
        value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeClientEvents {
    private ForgeClientEvents() {}

    public static void init() {
        ClientGestures.setSender(payload ->
                ForgeNetworking.CHANNEL.send(payload, PacketDistributor.SERVER.noArg()));
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

    @SubscribeEvent(priority = Priority.HIGHEST)
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty evt) {
        // RightClickEmpty is a pure notification: there is no vanilla action behind an empty-hand
        // air click to suppress, so the rules run but nothing is canceled.
        ClientGestures.syncState(ForgeKeyMappings.STACK_MODE_KEY.isDown());
        if (ClientGestures.isHittingBlock(Minecraft.getInstance().hitResult)) return;
        ClientGestures.processClick(evt.getEntity(), evt.getHand(), null, false,
                ForgeKeyMappings.STACK_MODE_KEY.isDown());
    }

    @SubscribeEvent(priority = Priority.HIGHEST)
    public static boolean onRightClickItem(PlayerInteractEvent.RightClickItem evt) {
        if (!evt.getLevel().isClientSide()) return false;

        ClientGestures.syncState(ForgeKeyMappings.STACK_MODE_KEY.isDown());
        if (ClientGestures.isHittingBlock(Minecraft.getInstance().hitResult)) return false;
        var gesture = ClientGestures.processClick(evt.getEntity(), evt.getHand(), null, false,
                ForgeKeyMappings.STACK_MODE_KEY.isDown());
        return gesture.consumesLocalUse(evt.getEntity().getMainHandItem());
    }

    @SubscribeEvent(priority = Priority.HIGHEST)
    public static boolean onRightClick(PlayerInteractEvent.RightClickBlock evt) {
        // Only the client-side firing drives the gesture rules. In single player the integrated
        // server shares this event bus, and adjacent protection consultation re-fires
        // RightClickBlock; handling that server-side re-fire would re-run the client rules.
        if (!evt.getLevel().isClientSide()) {
            return false;
        }
        if (evt.getFace() == null) return false;

        ClientGestures.syncState(ForgeKeyMappings.STACK_MODE_KEY.isDown());
        var gesture = ClientGestures.processClick(evt.getEntity(), evt.getHand(),
                BlockType.of(evt.getLevel().getBlockState(evt.getPos()).getBlock()), true,
                ForgeKeyMappings.STACK_MODE_KEY.isDown());

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
