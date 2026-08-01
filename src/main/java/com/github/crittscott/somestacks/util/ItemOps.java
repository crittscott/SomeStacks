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

/**
 * Item handling shared by all three stack types: what a hand can accept, how a taken stack reaches
 * the player, how a broken block sheds its contents, and how stored items add up to a light level.
 */
public final class ItemOps {
    private ItemOps(){}

    /** The brightest a block can be, and so the top of each stack block's light property. */
    public static final int MAX_LIGHT_LEVEL = 15;

    /** Share of a stored block's own emission that a cell holding it contributes. */
    private static final int LIGHT_SHARE_DIVISOR = 4;

    /**
     * Whether an extraction may land in this hand: it must be empty, or hold the same item and tags
     * with room left. This is the rule that keeps a player's hand from being swapped out mid-gesture.
     */
    public static boolean canTakeIntoHand(ItemStack hand, ItemStack offer) {
        if (offer.isEmpty()) return false;
        if (hand.isEmpty()) return true;
        return ItemStack.isSameItemSameTags(hand, offer) && hand.getCount() < hand.getMaxStackSize();
    }

    /**
     * Moves as much of {@code incoming} into {@code hand} as it will hold, growing the hand in
     * place. The caller shrinks the source by the amount reported.
     *
     * @return how many items moved, zero when the two do not stack
     */
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

    /**
     * Puts an extracted stack in the player's hand, merging with what is already there, and drops
     * whatever will not fit at their feet.
     */
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
                totalLight += blockLight / LIGHT_SHARE_DIVISOR;
            }
        }
        return Math.min(totalLight, MAX_LIGHT_LEVEL);
    }

    /** Whether the server config bars this item's namespace. Disabled contents may still be removed. */
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

    /** {@link #isItemFromDisabledMod} for a deposit path, telling the player when it refuses. */
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

    /** Whether the server config bars this item by id. Disabled contents may still be removed. */
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

    /** {@link #isItemDisabled} for a deposit path, telling the player when it refuses. */
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
