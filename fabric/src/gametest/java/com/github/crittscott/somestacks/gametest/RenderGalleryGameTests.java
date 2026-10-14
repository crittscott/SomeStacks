package com.github.crittscott.somestacks.gametest;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * The render-gallery world builder: that a queued gallery lays out its floor and rows as described
 * and reports the totals it finished with, spread across ticks by the configured placement limit.
 */
public final class RenderGalleryGameTests implements FabricGameTest {
    /**
     * Loader entry point for {@link
     * RenderGalleryChecks#queuedStorageGalleryBuildsFloorRowsAndCompletionTotals}; see that method
     * for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE, timeoutTicks = 100)
    public void queuedStorageGalleryBuildsFloorRowsAndCompletionTotals(GameTestHelper helper) {
        ServerPlayer player = FakePlayer.get(helper.getLevel());
        RenderGalleryChecks.queuedStorageGalleryBuildsFloorRowsAndCompletionTotals(helper, player);
    }

    /** See {@link RenderGalleryChecks#commandsEnforcePolicyAndGalleryPermissions} for permissions and manual checks. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE, batch = "somestacks_commands")
    public void commandsEnforcePolicyAndGalleryPermissions(GameTestHelper helper) {
        RenderGalleryChecks.commandsEnforcePolicyAndGalleryPermissions(helper, FakePlayer.get(helper.getLevel()));
    }
}
