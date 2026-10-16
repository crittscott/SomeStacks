package com.github.crittscott.somestacks.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

/** Storage settling order across every item-identity component used by the comparator. */
public final class StackSortGameTests {
    public StackSortGameTests() {}

    /** See {@link StackSortChecks#comparatorOrdersEveryIdentityComponent}. */
    public static void comparatorOrdersEveryIdentityComponent(GameTestHelper helper) {
        StackSortChecks.comparatorOrdersEveryIdentityComponent(helper);
    }
}
