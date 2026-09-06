package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * The render-gallery world builder: that a queued gallery lays out its floor and rows as described
 * and reports the totals it finished with, spread across ticks by the configured placement limit.
 */
@GameTestHolder(SomeStacks.MODID)
public final class RenderGalleryGameTests {
    private RenderGalleryGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = 100)
    public static void queuedStorageGalleryBuildsFloorRowsAndCompletionTotals(
            GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.fakePlayer(helper.getLevel());
        RenderGalleryChecks.queuedStorageGalleryBuildsFloorRowsAndCompletionTotals(helper, player);
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void queuedStorageGallerySpreadsWorkAcrossTicks(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.fakePlayer(helper.getLevel());
        RenderGalleryChecks.queuedStorageGallerySpreadsWorkAcrossTicks(helper, player);
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void queuedBarGalleryBuildsRowsAndFillsBars(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.fakePlayer(helper.getLevel());
        RenderGalleryChecks.queuedBarGalleryBuildsRowsAndFillsBars(helper, player);
    }
}
