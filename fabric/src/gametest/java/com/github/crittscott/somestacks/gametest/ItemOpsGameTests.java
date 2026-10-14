package com.github.crittscott.somestacks.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The shared item helpers, exercised against real registry items: what a hand will accept, what a
 * merge moves and reports, empty-handler detection, and the per-slot light contribution and its cap.
 */
public final class ItemOpsGameTests implements FabricGameTest {
    /**
     * Loader entry point for {@link ItemOpsChecks#handCompatibilityUsesExactItemTagsAndCapacity};
     * see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void handCompatibilityUsesExactItemTagsAndCapacity(GameTestHelper helper) {
        ItemOpsChecks.handCompatibilityUsesExactItemTagsAndCapacity(helper);
    }

    /**
     * Loader entry point for {@link ItemOpsChecks#mergeReturnsMovedCountAndDoesNotShrinkIncoming};
     * see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void mergeReturnsMovedCountAndDoesNotShrinkIncoming(GameTestHelper helper) {
        ItemOpsChecks.mergeReturnsMovedCountAndDoesNotShrinkIncoming(helper);
    }

    /**
     * Loader entry point for {@link ItemOpsChecks#mergeRejectsDifferentItemsAndTags}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void mergeRejectsDifferentItemsAndTags(GameTestHelper helper) {
        ItemOpsChecks.mergeRejectsDifferentItemsAndTags(helper);
    }

    /**
     * Loader entry point for {@link ItemOpsChecks#handlerEmptyDetectionScansEverySlot}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void handlerEmptyDetectionScansEverySlot(GameTestHelper helper) {
        ItemOpsChecks.handlerEmptyDetectionScansEverySlot(helper);
    }

    /**
     * Loader entry point for {@link ItemOpsChecks#lightIsPerOccupiedSlotIntegerDividedAndCapped};
     * see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void lightIsPerOccupiedSlotIntegerDividedAndCapped(GameTestHelper helper) {
        ItemOpsChecks.lightIsPerOccupiedSlotIntegerDividedAndCapped(helper);
    }
}
