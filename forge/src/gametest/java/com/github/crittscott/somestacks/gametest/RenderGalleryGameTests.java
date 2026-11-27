package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacksCommon;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The render-gallery world builder: that a queued gallery lays out its floor and rows as described
 * and reports the totals it finished with, spread across ticks by the configured placement limit.
 * Also the render tools' client file: the user override file that {@code /ss write changed} saves.
 */
@GameTestHolder(SomeStacksCommon.MODID)
@PrefixGameTestTemplate(false)
public final class RenderGalleryGameTests {
    private RenderGalleryGameTests() {}

    /**
     * Loader entry point for {@link
     * RenderGalleryChecks#queuedStorageGalleryBuildsFloorRowsAndCompletionTotals}; see that method
     * for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE, timeoutTicks = 100)
    public static void queuedStorageGalleryBuildsFloorRowsAndCompletionTotals(
            GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        RenderGalleryChecks.queuedStorageGalleryBuildsFloorRowsAndCompletionTotals(helper, player);
    }

    /**
     * See {@link RenderGalleryChecks#commandsEnforcePolicyAndGalleryPermissions} for permissions
     * and manual checks.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE, batch = "somestacks_commands")
    public static void commandsEnforcePolicyAndGalleryPermissions(GameTestHelper helper) {
        RenderGalleryChecks.commandsEnforcePolicyAndGalleryPermissions(
                helper, FakePlayerFactory.getMinecraft(helper.getLevel()));
    }

    /** See {@link RenderGalleryChecks#galleryItemsAreInIdOrderWithoutDeniedItems}. */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE, batch = "somestacks_commands")
    public static void galleryItemsAreInIdOrderWithoutDeniedItems(GameTestHelper helper) {
        RenderGalleryChecks.galleryItemsAreInIdOrderWithoutDeniedItems(helper);
    }

    /** See {@link RenderOverrideChecks#malformedFieldsKeepTheEntrysValidFields}. */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void malformedFieldsKeepTheEntrysValidFields(GameTestHelper helper) {
        RenderOverrideChecks.malformedFieldsKeepTheEntrysValidFields(helper);
    }

    /**
     * See {@link
     * RenderOverrideChecks#userFileIsNeverOverwrittenWhenUnreadableAndKeepsMalformedEntries}.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void userFileIsNeverOverwrittenWhenUnreadableAndKeepsMalformedEntries(
            GameTestHelper helper) {
        RenderOverrideChecks.userFileIsNeverOverwrittenWhenUnreadableAndKeepsMalformedEntries(
                helper);
    }
}
