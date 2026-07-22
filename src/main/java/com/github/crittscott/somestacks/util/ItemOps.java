package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

public final class ItemOps {
    private ItemOps(){}

    public static boolean canTakeIntoHand(ItemStack hand, ItemStack offer) {
        if (offer.isEmpty()) return false;
        if (hand.isEmpty()) return true;
        return ItemStack.isSameItemSameTags(hand, offer) && hand.getCount() < hand.getMaxStackSize();
    }

    public static int mergeIntoStack(ItemStack hand, ItemStack incoming) {
        if (hand.isEmpty() || incoming.isEmpty()) return 0;
        if (!ItemStack.isSameItemSameTags(hand, incoming)) return 0;
        int can = Math.min(incoming.getCount(), hand.getMaxStackSize() - hand.getCount());
        hand.grow(can);
        return can;
    }

    public static void dropAllItems(IItemHandler handler, Level level, BlockPos pos) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }

    public static void giveToPlayerOrDrop(Player player, InteractionHand hand, ItemStack taken) {
        ItemStack handStack = player.getItemInHand(hand);

        if (handStack.isEmpty()) {
            player.setItemInHand(hand, taken);
            taken = ItemStack.EMPTY;
        } else {
            int moved = mergeIntoStack(handStack, taken);
            taken.shrink(moved);
            player.setItemInHand(hand, handStack);
        }

        if (!taken.isEmpty()) {
            player.drop(taken, false);
        }
    }

    public static boolean isHandlerEmpty(IItemHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static int calculateLightLevelFromItems(IItemHandler handler) {
        int totalLight = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem bi) {
                int blockLight = bi.getBlock().defaultBlockState().getLightEmission();
                totalLight += blockLight / 4;
            }
        }
        return Math.min(totalLight, 15);
    }

    /**
     * Check if an item is from a mod that has been disabled in the server config.
     * Items from disabled mods cannot be added to stacks, but can be removed.
     *
     * @param stack The item stack to check
     * @return true if the item is from a disabled mod, false otherwise
     */
    public static boolean isItemFromDisabledMod(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) {
            return false;
        }

        return ServerConfig.isModDisabled(itemId.getNamespace());
    }

    /**
     * Check if an item is from a disabled mod and notify the player if so.
     * Call this before attempting to deposit an item into a stack.
     *
     * @param stack The item stack being deposited
     * @param player The player attempting the deposit
     * @return true if the item is from a disabled mod (and message was sent), false if allowed
     */
    public static boolean checkDisabledModAndNotify(ItemStack stack, Player player) {
        if (isItemFromDisabledMod(stack)) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (itemId != null) {
                String modId = itemId.getNamespace();
                player.displayClientMessage(
                        Component.literal("Items from mod '" + modId + "' are disabled in server config"),
                        true
                );
            }
            return true;
        }
        return false;
    }

    /**
     * Check if a specific item has been disabled in the server config.
     * Disabled items cannot be added to stacks, but can be removed.
     *
     * @param stack The item stack to check
     * @return true if the specific item is disabled, false otherwise
     */
    public static boolean isItemDisabled(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) {
            return false;
        }

        return ServerConfig.isItemDisabled(itemId);
    }

    /**
     * Check if a specific item is disabled and notify the player if so.
     * Call this before attempting to deposit an item into a stack.
     *
     * @param stack The item stack being deposited
     * @param player The player attempting the deposit
     * @return true if the item is disabled (and message was sent), false if allowed
     */
    public static boolean checkDisabledItemAndNotify(ItemStack stack, Player player) {
        if (isItemDisabled(stack)) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (itemId != null) {
                player.displayClientMessage(
                        Component.literal("Item '" + itemId + "' is disabled in server config"),
                        true
                );
            }
            return true;
        }
        return false;
    }
}
