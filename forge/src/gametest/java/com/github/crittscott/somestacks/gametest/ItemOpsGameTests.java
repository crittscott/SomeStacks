package com.github.crittscott.somestacks.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The shared item helpers, exercised against real registry items: what a hand will accept, what a
 * merge moves and reports, empty-handler detection, and the per-slot light contribution and its cap.
 */
public final class ItemOpsGameTests {
    public ItemOpsGameTests() {}

    /** See {@link ItemOpsChecks#handCompatibilityUsesExactItemComponentsAndCapacity}. */
    public static void handCompatibilityUsesExactItemComponentsAndCapacity(GameTestHelper helper) {
        ItemOpsChecks.handCompatibilityUsesExactItemComponentsAndCapacity(helper);
    }

    /** See {@link ItemOpsChecks#mergeReturnsMovedCountAndDoesNotShrinkIncoming}. */
    public static void mergeReturnsMovedCountAndDoesNotShrinkIncoming(GameTestHelper helper) {
        ItemOpsChecks.mergeReturnsMovedCountAndDoesNotShrinkIncoming(helper);
    }

    /** See {@link ItemOpsChecks#lightIsPerOccupiedSlotIntegerDividedAndCapped}. */
    public static void lightIsPerOccupiedSlotIntegerDividedAndCapped(GameTestHelper helper) {
        ItemOpsChecks.lightIsPerOccupiedSlotIntegerDividedAndCapped(helper);
    }
}
