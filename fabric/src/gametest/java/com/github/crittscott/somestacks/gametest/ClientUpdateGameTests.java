package com.github.crittscott.somestacks.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** What a stack block's update tag sends nearby clients about the items it stores. */
public final class ClientUpdateGameTests implements FabricGameTest {
    /** See {@link ClientUpdateChecks#updateTagOmitsContainerContentsAndBookPages}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void updateTagOmitsContainerContentsAndBookPages(GameTestHelper helper) {
        ClientUpdateChecks.updateTagOmitsContainerContentsAndBookPages(helper);
    }
}
