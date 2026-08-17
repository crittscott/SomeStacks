package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Storage settling order across every item-identity component used by the comparator. */
@GameTestHolder(SomeStacks.MODID)
@PrefixGameTestTemplate(false)
public final class StackSortGameTests {
    private StackSortGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorOrdersEveryIdentityComponent(GameTestHelper helper) {
        StackSortChecks.comparatorOrdersEveryIdentityComponent(helper);
    }
}
