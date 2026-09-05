package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacksNeoForge;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The render-gallery world builder: that a queued gallery lays out its floor and rows as described
 * and reports the totals it finished with, spread across ticks by the configured placement limit.
 */
@GameTestHolder(SomeStacksNeoForge.MODID)
@PrefixGameTestTemplate(false)
public final class RenderGalleryGameTests {
    private RenderGalleryGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = 100)
    public static void queuedStorageGalleryBuildsFloorRowsAndCompletionTotals(
            GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.fakePlayer(helper.getLevel());
        RenderGalleryChecks.queuedStorageGalleryBuildsFloorRowsAndCompletionTotals(helper, player);
    }
}
