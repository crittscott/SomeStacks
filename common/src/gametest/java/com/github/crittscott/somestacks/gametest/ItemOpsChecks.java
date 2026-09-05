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

    public static void handCompatibilityUsesExactItemTagsAndCapacity(GameTestHelper helper) {
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

        ItemStack taggedHand = new ItemStack(Items.STONE);
        taggedHand.set(DataComponents.CUSTOM_DATA, CustomData.of(tag("variant", 1)));
        ItemStack taggedOffer = new ItemStack(Items.STONE);
        taggedOffer.set(DataComponents.CUSTOM_DATA, CustomData.of(tag("variant", 2)));
        check(!ItemOps.canTakeIntoHand(taggedHand, taggedOffer),
                "Different components were accepted");
        helper.succeed();
    }

    public static void mergeReturnsMovedCountAndDoesNotShrinkIncoming(GameTestHelper helper) {
        ItemStack hand = new ItemStack(Items.STONE, 60);
        ItemStack incoming = new ItemStack(Items.STONE, 10);

        int moved = ItemOps.mergeIntoStack(hand, incoming);

        checkEquals(4, moved, "Moved count");
        checkEquals(64, hand.getCount(), "Hand count");
        checkEquals(10, incoming.getCount(), "Incoming count");
        helper.succeed();
    }

    public static void mergeRejectsDifferentItemsAndTags(GameTestHelper helper) {
        checkEquals(0, ItemOps.mergeIntoStack(
                new ItemStack(Items.STONE), new ItemStack(Items.DIRT)),
                "Different-item merge");

        ItemStack hand = new ItemStack(Items.STONE);
        hand.set(DataComponents.CUSTOM_DATA, CustomData.of(tag("variant", 1)));
        ItemStack incoming = new ItemStack(Items.STONE);
        incoming.set(DataComponents.CUSTOM_DATA, CustomData.of(tag("variant", 2)));
        checkEquals(0, ItemOps.mergeIntoStack(hand, incoming),
                "Different-component merge");
        helper.succeed();
    }

    public static void handlerEmptyDetectionScansEverySlot(GameTestHelper helper) {
        StackItemStorage handler = new StackItemStorage(3);
        check(ItemOps.isHandlerEmpty(handler), "Empty handler was reported occupied");

        handler.setStackInSlot(2, new ItemStack(Items.STONE));
        check(!ItemOps.isHandlerEmpty(handler), "Occupied final slot was not detected");
        helper.succeed();
    }

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
