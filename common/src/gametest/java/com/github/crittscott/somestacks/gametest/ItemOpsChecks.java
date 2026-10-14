package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.StackItemStorage;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/**
 * The shared item helpers, exercised against real registry items: what a hand will accept, what a
 * merge moves and reports, empty-handler detection, and the per-slot light contribution and its cap.
 */
public final class ItemOpsChecks {
    private ItemOpsChecks() {}

    /**
     * Internal hand-compatibility check: reject empty offers, full hands, different items, and
     * different NBT; accept matching stone below capacity and an empty hand.
     *
     * <p>To check manually, store stone and plain-right-click it with an empty hand, 63 stone, 64
     * stone, dirt, then differently tagged stone. Only empty or matching hands with space can
     * extract.
     */
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
        taggedHand.setTag(tag("variant", 1));
        ItemStack taggedOffer = new ItemStack(Items.STONE);
        taggedOffer.setTag(tag("variant", 2));
        check(!ItemOps.canTakeIntoHand(taggedHand, taggedOffer),
                "Different tags were accepted");
        helper.succeed();
    }

    /**
     * Internal merge contract with no direct manual reproduction: merge a ten-stone offer into 60
     * stone, asserting a moved count of four, a full destination, and an unchanged source count of
     * ten.
     *
     * <p>Nearest visible check: extract stored stone while holding 60 matching stone; the hand
     * fills to 64 and the untransferred contents remain stored.
     */
    public static void mergeReturnsMovedCountAndDoesNotShrinkIncoming(GameTestHelper helper) {
        ItemStack hand = new ItemStack(Items.STONE, 60);
        ItemStack incoming = new ItemStack(Items.STONE, 10);

        int moved = ItemOps.mergeIntoStack(hand, incoming);

        checkEquals(4, moved, "Moved count");
        checkEquals(64, hand.getCount(), "Hand count");
        checkEquals(10, incoming.getCount(), "Incoming count");
        helper.succeed();
    }

    /**
     * Internal merge check: different item types and different NBT variants return zero moved items.
     *
     * <p>To check manually, store tagged stone, then attempt plain-click extraction while holding
     * dirt or stone with different custom NBT. Neither hand nor contents should change.
     */
    public static void mergeRejectsDifferentItemsAndTags(GameTestHelper helper) {
        checkEquals(0, ItemOps.mergeIntoStack(
                new ItemStack(Items.STONE), new ItemStack(Items.DIRT)),
                "Different-item merge");

        ItemStack hand = new ItemStack(Items.STONE);
        hand.setTag(tag("variant", 1));
        ItemStack incoming = new ItemStack(Items.STONE);
        incoming.setTag(tag("variant", 2));
        checkEquals(0, ItemOps.mergeIntoStack(hand, incoming),
                "Different-tag merge");
        helper.succeed();
    }

    /**
     * Internal empty-detection check with no direct manual reproduction: a three-slot inventory is
     * empty until its final slot receives stone.
     *
     * <p>Nearest visible check: extract the last occupied item from a temporary Storage pile; it
     * should disappear after settlement regardless of the item's former slot.
     */
    public static void handlerEmptyDetectionScansEverySlot(GameTestHelper helper) {
        StackItemStorage handler = new StackItemStorage(3);
        check(ItemOps.isHandlerEmpty(handler), "Empty handler was reported occupied");

        handler.setStackInSlot(2, new ItemStack(Items.STONE));
        check(!ItemOps.isHandlerEmpty(handler), "Occupied final slot was not detected");
        helper.succeed();
    }

    /**
     * Internal light calculation: one slot with 64 glowstone contributes 3; six occupied glowstone
     * slots cap the result at 15.
     *
     * <p>To check manually, place glowstone in one Singles cell, inspect the block light, then
     * occupy five additional cells. Light rises per occupied cell and stops at 15; adding count to
     * a single Storage slot does not increase its contribution.
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
