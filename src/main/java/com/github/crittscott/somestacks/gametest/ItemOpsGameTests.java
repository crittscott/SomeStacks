package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.util.ItemOps;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;

import static com.github.crittscott.somestacks.gametest.GameTestSupport.check;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.checkEquals;

@GameTestHolder(SomeStacks.MODID)
@PrefixGameTestTemplate(false)
public final class ItemOpsGameTests {
    private ItemOpsGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE)
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

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void mergeReturnsMovedCountAndDoesNotShrinkIncoming(GameTestHelper helper) {
        ItemStack hand = new ItemStack(Items.STONE, 60);
        ItemStack incoming = new ItemStack(Items.STONE, 10);

        int moved = ItemOps.mergeIntoStack(hand, incoming);

        checkEquals(4, moved, "Moved count");
        checkEquals(64, hand.getCount(), "Hand count");
        checkEquals(10, incoming.getCount(), "Incoming count");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
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

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void handlerEmptyDetectionScansEverySlot(GameTestHelper helper) {
        ItemStackHandler handler = new ItemStackHandler(3);
        check(ItemOps.isHandlerEmpty(handler), "Empty handler was reported occupied");

        handler.setStackInSlot(2, new ItemStack(Items.STONE));
        check(!ItemOps.isHandlerEmpty(handler), "Occupied final slot was not detected");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void lightIsPerOccupiedSlotIntegerDividedAndCapped(GameTestHelper helper) {
        ItemStackHandler handler = new ItemStackHandler(6);

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
