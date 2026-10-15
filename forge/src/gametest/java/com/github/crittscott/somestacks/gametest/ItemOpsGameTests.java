package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraftforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestNamespace;

/**
 * The shared item helpers, exercised against real registry items: what a hand will accept, what a
 * merge moves and reports, empty-handler detection, and the per-slot light contribution and its cap.
 */
@GameTestNamespace(SomeStacks.MODID)
public final class ItemOpsGameTests {
    public ItemOpsGameTests() {}

    /** See {@link ItemOpsChecks#handCompatibilityUsesExactItemComponentsAndCapacity}. */
    @GameTest(structure = GameTestSupport.TEMPLATE)
    public static void handCompatibilityUsesExactItemComponentsAndCapacity(GameTestHelper helper) {
        ItemOpsChecks.handCompatibilityUsesExactItemComponentsAndCapacity(helper);
    }

    /** See {@link ItemOpsChecks#mergeReturnsMovedCountAndDoesNotShrinkIncoming}. */
    @GameTest(structure = GameTestSupport.TEMPLATE)
    public static void mergeReturnsMovedCountAndDoesNotShrinkIncoming(GameTestHelper helper) {
        ItemOpsChecks.mergeReturnsMovedCountAndDoesNotShrinkIncoming(helper);
    }

    /** See {@link ItemOpsChecks#lightIsPerOccupiedSlotIntegerDividedAndCapped}. */
    @GameTest(structure = GameTestSupport.TEMPLATE)
    public static void lightIsPerOccupiedSlotIntegerDividedAndCapped(GameTestHelper helper) {
        ItemOpsChecks.lightIsPerOccupiedSlotIntegerDividedAndCapped(helper);
    }
}
