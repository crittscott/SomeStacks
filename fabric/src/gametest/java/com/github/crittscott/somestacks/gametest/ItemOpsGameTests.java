package com.github.crittscott.somestacks.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The shared item helpers, exercised against real registry items: what a hand will accept, what a
 * merge moves and reports, empty-handler detection, and the per-slot light contribution and its cap.
 */
public final class ItemOpsGameTests implements FabricGameTest {
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void handCompatibilityUsesExactItemTagsAndCapacity(GameTestHelper helper) {
        ItemOpsChecks.handCompatibilityUsesExactItemTagsAndCapacity(helper);
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void mergeReturnsMovedCountAndDoesNotShrinkIncoming(GameTestHelper helper) {
        ItemOpsChecks.mergeReturnsMovedCountAndDoesNotShrinkIncoming(helper);
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void mergeRejectsDifferentItemsAndTags(GameTestHelper helper) {
        ItemOpsChecks.mergeRejectsDifferentItemsAndTags(helper);
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void handlerEmptyDetectionScansEverySlot(GameTestHelper helper) {
        ItemOpsChecks.handlerEmptyDetectionScansEverySlot(helper);
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void lightIsPerOccupiedSlotIntegerDividedAndCapped(GameTestHelper helper) {
        ItemOpsChecks.lightIsPerOccupiedSlotIntegerDividedAndCapped(helper);
    }
}
