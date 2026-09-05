package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacksNeoForge;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The shared item helpers, exercised against real registry items: what a hand will accept, what a
 * merge moves and reports, empty-handler detection, and the per-slot light contribution and its cap.
 */
@GameTestHolder(SomeStacksNeoForge.MODID)
@PrefixGameTestTemplate(false)
public final class ItemOpsGameTests {
    private ItemOpsGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void handCompatibilityUsesExactItemTagsAndCapacity(GameTestHelper helper) {
        ItemOpsChecks.handCompatibilityUsesExactItemTagsAndCapacity(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void mergeReturnsMovedCountAndDoesNotShrinkIncoming(GameTestHelper helper) {
        ItemOpsChecks.mergeReturnsMovedCountAndDoesNotShrinkIncoming(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void mergeRejectsDifferentItemsAndTags(GameTestHelper helper) {
        ItemOpsChecks.mergeRejectsDifferentItemsAndTags(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void handlerEmptyDetectionScansEverySlot(GameTestHelper helper) {
        ItemOpsChecks.handlerEmptyDetectionScansEverySlot(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void lightIsPerOccupiedSlotIntegerDividedAndCapped(GameTestHelper helper) {
        ItemOpsChecks.lightIsPerOccupiedSlotIntegerDividedAndCapped(helper);
    }
}
