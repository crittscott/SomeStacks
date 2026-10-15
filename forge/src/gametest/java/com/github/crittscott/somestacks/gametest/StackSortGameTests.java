package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraftforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestNamespace;

/** Storage settling order across every item-identity component used by the comparator. */
@GameTestNamespace(SomeStacks.MODID)
public final class StackSortGameTests {
    public StackSortGameTests() {}

    /** See {@link StackSortChecks#comparatorOrdersEveryIdentityComponent}. */
    @GameTest(structure = GameTestSupport.TEMPLATE)
    public static void comparatorOrdersEveryIdentityComponent(GameTestHelper helper) {
        StackSortChecks.comparatorOrdersEveryIdentityComponent(helper);
    }
}
