package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.StackItemStorage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/**
 * The shared item helpers, exercised against real registry items: what a hand will accept, what a
 * merge moves and reports, empty-handler detection, and the per-slot light contribution and its cap.
 */
public final class ItemOpsChecks {
    private ItemOpsChecks() {}

    /**
     * A hand accepts an extraction only when empty or holding the exact item and data components
     * with room remaining. To reproduce in-game: extract toward an empty hand, a compatible partial
     * stack, a full matching stack, a different item, and a component-distinct variant. Only the
     * first two succeed.
     */
    public static void handCompatibilityUsesExactItemComponentsAndCapacity(GameTestHelper helper) {
        ItemStack offer = new ItemStack(Items.STONE, 1);

        check(!ItemOps.canTakeIntoHand(ItemStack.EMPTY, ItemStack.EMPTY),
                "Empty offer was accepted");
        check(ItemOps.canTakeIntoHand(ItemStack.EMPTY, offer),
                "Offer was rejected for an empty hand");
        check(ItemOps.canTakeIntoHand(new ItemStack(Items.STONE, 63), offer),
                "Offer was rejected below capacity");
        check(!ItemOps.canTakeIntoHand(new ItemStack(Items.STONE, 64), offer),
                "Offer was accepted at capacity");
        check(!ItemOps.canTakeIntoHand(new ItemStack(Items.DIRT), offer),
                "Different item was accepted");

        ItemStack componentHand = new ItemStack(Items.STONE);
        componentHand.set(DataComponents.CUSTOM_DATA, CustomData.of(tag("variant", 1)));
        ItemStack componentOffer = new ItemStack(Items.STONE);
        componentOffer.set(DataComponents.CUSTOM_DATA, CustomData.of(tag("variant", 2)));
        check(!ItemOps.canTakeIntoHand(componentHand, componentOffer),
                "Different components were accepted");
        helper.succeed();
    }

    /**
     * The merge helper grows the destination and reports the moved count without shrinking its
     * source argument. No in-game reproduction applies: callers use the count to mutate their
     * actual source after this helper returns.
     */
    public static void mergeReturnsMovedCountAndDoesNotShrinkIncoming(GameTestHelper helper) {
        ItemStack hand = new ItemStack(Items.STONE, 60);
        ItemStack incoming = new ItemStack(Items.STONE, 10);

        int moved = ItemOps.mergeIntoStack(hand, incoming);

        checkEquals(4, moved, "Moved count");
        checkEquals(64, hand.getCount(), "Hand count");
        checkEquals(10, incoming.getCount(), "Incoming count");

        ItemStack componentHand = new ItemStack(Items.STONE);
        componentHand.set(DataComponents.CUSTOM_DATA, CustomData.of(tag("variant", 1)));
        ItemStack componentIncoming = new ItemStack(Items.STONE);
        componentIncoming.set(DataComponents.CUSTOM_DATA, CustomData.of(tag("variant", 2)));
        checkEquals(0, ItemOps.mergeIntoStack(componentHand, componentIncoming),
                "Different-component merge");
        helper.succeed();
    }

    /**
     * Each occupied glowing BlockItem slot contributes one quarter of its block light, independent
     * of item count, and the total caps at 15. To reproduce in-game: deposit glowing blocks into
     * separate cells or slots and measure emitted light as positions are filled. A stack of 64 in
     * one Storage slot contributes once, and enough occupied positions cap at full light.
     */
    public static void lightIsPerOccupiedSlotIntegerDividedAndCapped(GameTestHelper helper) {
        StackItemStorage handler = new StackItemStorage(6);

        handler.setStackInSlot(0, new ItemStack(Items.GLOWSTONE, 64));
        checkEquals(3, ItemOps.calculateLightLevelFromItems(handler),
                "Single occupied slot light");

        for (int slot = 1; slot < 6; slot++) {
            handler.setStackInSlot(slot, new ItemStack(Items.GLOWSTONE));
        }
        checkEquals(15, ItemOps.calculateLightLevelFromItems(handler),
                "Capped light");
        helper.succeed();
    }

    private static CompoundTag tag(String key, int value) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(key, value);
        return tag;
    }
}
