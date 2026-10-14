package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The shared item helpers, exercised against real registry items: what a hand will accept, what a
 * merge moves and reports, empty-handler detection, and the per-slot light contribution and its cap.
 */
@GameTestHolder(SomeStacks.MODID)
@PrefixGameTestTemplate(false)
public final class ItemOpsGameTests {
    private ItemOpsGameTests() {}

    /**
     * Loader entry point for {@link ItemOpsChecks#handCompatibilityUsesExactItemTagsAndCapacity};
     * see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void handCompatibilityUsesExactItemTagsAndCapacity(GameTestHelper helper) {
        ItemOpsChecks.handCompatibilityUsesExactItemTagsAndCapacity(helper);
    }

    /**
     * Loader entry point for {@link ItemOpsChecks#mergeReturnsMovedCountAndDoesNotShrinkIncoming};
     * see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void mergeReturnsMovedCountAndDoesNotShrinkIncoming(GameTestHelper helper) {
        ItemOpsChecks.mergeReturnsMovedCountAndDoesNotShrinkIncoming(helper);
    }

    /**
     * Loader entry point for {@link ItemOpsChecks#mergeRejectsDifferentItemsAndTags}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void mergeRejectsDifferentItemsAndTags(GameTestHelper helper) {
        ItemOpsChecks.mergeRejectsDifferentItemsAndTags(helper);
    }

    /**
     * Loader entry point for {@link ItemOpsChecks#handlerEmptyDetectionScansEverySlot}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void handlerEmptyDetectionScansEverySlot(GameTestHelper helper) {
        ItemOpsChecks.handlerEmptyDetectionScansEverySlot(helper);
    }

    /**
     * Loader entry point for {@link ItemOpsChecks#lightIsPerOccupiedSlotIntegerDividedAndCapped};
     * see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void lightIsPerOccupiedSlotIntegerDividedAndCapped(GameTestHelper helper) {
        ItemOpsChecks.lightIsPerOccupiedSlotIntegerDividedAndCapped(helper);
    }
}
