package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.client.interaction.InteractionContext;
import com.github.crittscott.somestacks.client.interaction.InteractionRuleRegistry;
import com.github.crittscott.somestacks.network.DepositPkt;
import com.github.crittscott.somestacks.network.ExtractPkt;
import com.github.crittscott.somestacks.network.ModNetworking;
import com.github.crittscott.somestacks.network.PlaceAndDepositPkt;
import com.github.crittscott.somestacks.network.RotateBlockPkt;
import com.github.crittscott.somestacks.network.RotateItemPkt;
import com.github.crittscott.somestacks.network.TogglePermanentPkt;
import com.github.crittscott.somestacks.util.StackMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    private static StackMode stackMode = StackMode.STORAGE_STACK;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty evt) {
        SomeStacks.LOGGER.debug("RightClickEmpty fired");
        if (evt.isCanceled()) return;

        InteractionContext ctx = InteractionContext.forEmptyHand(evt, stackMode);
        InteractionRuleRegistry.processEmptyHandRules(ctx);

        if (ctx.shouldCancel()) {
            evt.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem evt) {
        SomeStacks.LOGGER.debug("RightClickItem fired, canceled: " + evt.isCanceled());
        if (evt.isCanceled()) return;

        InteractionContext ctx = InteractionContext.forItemInHand(evt, stackMode);
        InteractionRuleRegistry.processItemRules(ctx);

        if (ctx.shouldCancel()) {
            evt.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock evt) {
        SomeStacks.LOGGER.debug(
                "RightClickBlock fired: blockstate {}, hand item {}",
                evt.getLevel().getBlockState(evt.getPos()),
                evt.getItemStack()
        );
        if (evt.isCanceled()) return;
        if (evt.getFace() == null) return;

        InteractionContext ctx = InteractionContext.forBlockClick(evt, stackMode);
        InteractionRuleRegistry.processBlockRules(ctx);

        if (ctx.shouldCancel()) {
            evt.setCanceled(true);
            evt.setUseBlock(Event.Result.DENY);
            evt.setUseItem(Event.Result.DENY);
        }
    }

    public static void cycleModeAllFour(Player player) {
        StackMode startMode = stackMode;
        do {
            stackMode = StackMode.fromOrdinal((stackMode.ordinal() + 1) % 4);
            if (stackMode == StackMode.TOGGLE_PERMANENT) break;
            if (stackMode.isBlockType() && StackState.isBlockTypeEnabled(stackMode.toBlockType())) break;
            if (stackMode == startMode) break;
        } while (true);
    }

    public static void displayModeMessage(Player player) {
        Component modeComponent = Component.translatable(stackMode.getTranslationKey());
        Component message = Component.translatable("somestacks.message.storage_mode", modeComponent);
        player.displayClientMessage(message, true);
    }

    public static void sendTogglePermanent(BlockPos pos) {
        ModNetworking.CHANNEL.sendToServer(new TogglePermanentPkt(pos));
    }

    public static void sendPlaceAndDeposit(BlockPos placePos, Direction face) {
        ModNetworking.CHANNEL.sendToServer(
                new PlaceAndDepositPkt(stackMode.toBlockType(), face, InteractionHand.MAIN_HAND, placePos)
        );
    }

    public static void sendDeposit(BlockPos pos) {
        ModNetworking.CHANNEL.sendToServer(new DepositPkt(InteractionHand.MAIN_HAND, pos));
    }

    public static void sendExtract(BlockPos pos, int index) {
        ModNetworking.CHANNEL.sendToServer(new ExtractPkt(InteractionHand.MAIN_HAND, pos, index));
    }

    public static void sendRotateBlock(BlockPos pos) {
        ModNetworking.CHANNEL.sendToServer(new RotateBlockPkt(pos));
    }

    public static void sendRotateItem(BlockPos pos, int slotIndex) {
        ModNetworking.CHANNEL.sendToServer(new RotateItemPkt(pos, slotIndex));
    }
}
