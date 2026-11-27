package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacksCommon;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** What a stack block's update tag sends nearby clients about the items it stores. */
@GameTestHolder(SomeStacksCommon.MODID)
@PrefixGameTestTemplate(false)
public final class ClientUpdateGameTests {
    private ClientUpdateGameTests() {}

    /** See {@link ClientUpdateChecks#updateTagOmitsContainerContentsAndBookPages}. */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void updateTagOmitsContainerContentsAndBookPages(GameTestHelper helper) {
        ClientUpdateChecks.updateTagOmitsContainerContentsAndBookPages(helper);
    }
}
