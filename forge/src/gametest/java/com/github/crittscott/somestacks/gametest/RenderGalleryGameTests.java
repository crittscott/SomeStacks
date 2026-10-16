package com.github.crittscott.somestacks.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * The render-gallery world builder: that a queued gallery lays out its floor and rows as described
 * and reports the totals it finished with, spread across ticks by the configured placement limit.
 */
public final class RenderGalleryGameTests {
    public RenderGalleryGameTests() {}

    /** See {@link RenderGalleryChecks#queuedStorageGalleryBuildsFloorRowsAndCompletionTotals}. */
    public static void queuedStorageGalleryBuildsFloorRowsAndCompletionTotals(
            GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.fakePlayer(helper.getLevel());
        RenderGalleryChecks.queuedStorageGalleryBuildsFloorRowsAndCompletionTotals(helper, player);
    }

    /** See {@link RenderGalleryChecks#queuedStorageGallerySpreadsWorkAcrossTicks}. */
    public static void queuedStorageGallerySpreadsWorkAcrossTicks(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.fakePlayer(helper.getLevel());
        RenderGalleryChecks.queuedStorageGallerySpreadsWorkAcrossTicks(helper, player);
    }

    /** See {@link RenderGalleryChecks#queuedBarGalleryBuildsRowsAndFillsBars}. */
    public static void queuedBarGalleryBuildsRowsAndFillsBars(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.fakePlayer(helper.getLevel());
        RenderGalleryChecks.queuedBarGalleryBuildsRowsAndFillsBars(helper, player);
    }
}
