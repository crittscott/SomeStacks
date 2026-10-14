package com.github.crittscott.somestacks.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Storage settling order across every item-identity component used by the comparator. */
public final class StackSortGameTests implements FabricGameTest {
    /**
     * Loader entry point for {@link StackSortChecks#comparatorOrdersEveryIdentityComponent}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorOrdersEveryIdentityComponent(GameTestHelper helper) {
        StackSortChecks.comparatorOrdersEveryIdentityComponent(helper);
    }
}
