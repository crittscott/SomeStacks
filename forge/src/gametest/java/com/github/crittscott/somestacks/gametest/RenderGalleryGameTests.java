package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraftforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestNamespace;

/**
 * The render-gallery world builder: that a queued gallery lays out its floor and rows as described
 * and reports the totals it finished with, spread across ticks by the configured placement limit.
 */
@GameTestNamespace(SomeStacks.MODID)
public final class RenderGalleryGameTests {
    public RenderGalleryGameTests() {}

    /** See {@link RenderGalleryChecks#queuedStorageGalleryBuildsFloorRowsAndCompletionTotals}. */
    @GameTest(structure = GameTestSupport.TEMPLATE, maxTicks = 100)
    public static void queuedStorageGalleryBuildsFloorRowsAndCompletionTotals(
            GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.fakePlayer(helper.getLevel());
        RenderGalleryChecks.queuedStorageGalleryBuildsFloorRowsAndCompletionTotals(helper, player);
    }

    /** See {@link RenderGalleryChecks#queuedStorageGallerySpreadsWorkAcrossTicks}. */
    @GameTest(structure = GameTestSupport.TEMPLATE)
    public static void queuedStorageGallerySpreadsWorkAcrossTicks(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.fakePlayer(helper.getLevel());
        RenderGalleryChecks.queuedStorageGallerySpreadsWorkAcrossTicks(helper, player);
    }

    /** See {@link RenderGalleryChecks#queuedBarGalleryBuildsRowsAndFillsBars}. */
    @GameTest(structure = GameTestSupport.TEMPLATE)
    public static void queuedBarGalleryBuildsRowsAndFillsBars(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.fakePlayer(helper.getLevel());
        RenderGalleryChecks.queuedBarGalleryBuildsRowsAndFillsBars(helper, player);
    }
}
