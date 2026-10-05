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
    /** See {@link RenderGalleryChecks#queuedStorageGalleryBuildsFloorRowsAndCompletionTotals}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE, timeoutTicks = 100)
    public void queuedStorageGalleryBuildsFloorRowsAndCompletionTotals(GameTestHelper helper) {
        ServerPlayer player = FakePlayer.get(helper.getLevel());
        RenderGalleryChecks.queuedStorageGalleryBuildsFloorRowsAndCompletionTotals(helper, player);
    }

    /** See {@link RenderGalleryChecks#queuedStorageGallerySpreadsWorkAcrossTicks}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void queuedStorageGallerySpreadsWorkAcrossTicks(GameTestHelper helper) {
        ServerPlayer player = FakePlayer.get(helper.getLevel());
        RenderGalleryChecks.queuedStorageGallerySpreadsWorkAcrossTicks(helper, player);
    }

    /** See {@link RenderGalleryChecks#queuedBarGalleryBuildsRowsAndFillsBars}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void queuedBarGalleryBuildsRowsAndFillsBars(GameTestHelper helper) {
        ServerPlayer player = FakePlayer.get(helper.getLevel());
        RenderGalleryChecks.queuedBarGalleryBuildsRowsAndFillsBars(helper, player);
    }
}
