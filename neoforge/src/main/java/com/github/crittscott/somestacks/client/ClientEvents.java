package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.client.interaction.InteractionContext;
import com.github.crittscott.somestacks.client.interaction.InteractionRuleRegistry;
import com.github.crittscott.somestacks.network.DepositPkt;
import com.github.crittscott.somestacks.network.ExtractPkt;
import com.github.crittscott.somestacks.network.PlaceAndDepositPkt;
import com.github.crittscott.somestacks.network.RotateBlockPkt;
import com.github.crittscott.somestacks.network.RotateItemPkt;
import com.github.crittscott.somestacks.network.TogglePermanentPkt;
import com.github.crittscott.somestacks.util.StackMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The client's gesture entry point: the three right-click events feed
 * {@link InteractionRuleRegistry}, and the send methods here are how a matched rule reaches the
 * server. A canceled event denies both the block use and the item use, so a gesture never also
 * spends what the player is holding.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class ClientEvents implements ClientGestures.Sender {
    private ClientEvents() {}

    public static void init() {
        ClientGestures.setSender(new ClientEvents());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty evt) {
        // RightClickEmpty is a pure notification on NeoForge: there is no vanilla action behind an
        // empty-hand air click to suppress, so the rules run but nothing is canceled.
        InteractionContext ctx = InteractionContext.forAirClick(
                evt.getEntity(), evt.getLevel(), evt.getHand(),
                ClientGestures.currentMode(), KeyMappings.STACK_MODE_KEY.isDown());
        InteractionRuleRegistry.processEmptyHandRules(ctx);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem evt) {
        if (evt.isCanceled()) return;

        InteractionContext ctx = InteractionContext.forAirClick(
                evt.getEntity(), evt.getLevel(), evt.getHand(),
                ClientGestures.currentMode(), KeyMappings.STACK_MODE_KEY.isDown());
        InteractionRuleRegistry.processItemRules(ctx);

        if (ctx.shouldCancel()) {
            evt.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock evt) {
        // Only the client-side firing drives the gesture rules. In single player the integrated
        // server shares this event bus, and the server re-fires RightClickBlock when consulting
        // protection for our own deposit/extract packets; handling that re-fire would re-run the
        // rules and cancel the very interaction being validated.
        if (!evt.getLevel().isClientSide()) {
            return;
        }
        if (evt.isCanceled()) return;
        if (evt.getFace() == null) return;

        InteractionContext ctx = InteractionContext.forBlockClick(
                evt.getEntity(), evt.getLevel(), evt.getHand(), evt.getPos(), evt.getFace(),
                ClientGestures.currentMode(), KeyMappings.STACK_MODE_KEY.isDown());
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

    @Override
    public void sendTogglePermanent(BlockPos pos) {
        PacketDistributor.sendToServer(new TogglePermanentPkt(pos));
    }

    @Override
    public void sendPlaceAndDeposit(StackMode mode, BlockPos placePos, Direction face) {
        PacketDistributor.sendToServer(new PlaceAndDepositPkt(mode.toBlockType(), face, placePos));
    }

    @Override
    public void sendDeposit(BlockPos pos, BlockPos clickedPos) {
        PacketDistributor.sendToServer(new DepositPkt(pos, clickedPos));
    }

    @Override
    public void sendExtract(BlockPos pos, int index) {
        PacketDistributor.sendToServer(new ExtractPkt(pos, index));
    }

    @Override
    public void sendRotateBlock(BlockPos pos) {
        PacketDistributor.sendToServer(new RotateBlockPkt(pos));
    }

    @Override
    public void sendRotateItem(BlockPos pos, int slotIndex) {
        PacketDistributor.sendToServer(new RotateItemPkt(pos, slotIndex));
    }
}
