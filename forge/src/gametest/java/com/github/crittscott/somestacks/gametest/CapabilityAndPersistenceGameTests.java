package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.ForgeServerConfig;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.block.BarColumn;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesColumn;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.count;

/**
 * The automation surface and the saved state behind it: the item handler exposed on every side,
 * a run advertising its reachable headroom, and each type's update tag round-tripping the contents
 * and presentation state it is responsible for.
 */
@GameTestHolder(SomeStacks.MODID)
@PrefixGameTestTemplate(false)
public final class CapabilityAndPersistenceGameTests {
    private static final long CONFIG_RELOAD_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final int CONFIG_RELOAD_TIMEOUT_TICKS = 10000;
    private CapabilityAndPersistenceGameTests() {}

    /**
     * Internal Forge lookup check: query all six sides and the unsided capability of each stack
     * type; every lookup must expose item storage.
     *
     * <p>To check the sided behavior manually, place each type with one admissible item and attach
     * a compatible transfer machine to each face in turn. Each attachment can access contents. The
     * unsided lookup has no direct player gesture.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void itemCapabilityIsAvailableFromEverySide(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        SinglesStackBE singles = placeSingles(helper, ORIGIN.east(3));
        BarStackBE bar = placeBar(helper, ORIGIN.east(6));

        for (Direction side : Direction.values()) {
            check(storage.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent(),
                    "Storage capability missing on " + side);
            check(singles.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent(),
                    "Singles capability missing on " + side);
            check(bar.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent(),
                    "Bar capability missing on " + side);
        }
        check(storage.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent(),
                "Storage capability missing on null side");
        check(singles.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent(),
                "Singles capability missing on null side");
        check(bar.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent(),
                "Bar capability missing on null side");
        helper.succeed();
    }

    /**
     * Internal Forge slot-count check: single Storage, Singles, and Bar blocks advertise two blocks
     * of slots when maximum height exceeds one, otherwise one. This does not insert or grow.
     *
     * <p>Nearest manual check: configure max_pile_height above one, place one block of each type,
     * and use slot-addressing automation to inspect advertised capacity. Repeat with height one.
     * Actual growth is covered by the growth tests.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void singleBlockCapabilitiesAdvertiseConfiguredHeadroom(
            GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        SinglesStackBE singles = placeSingles(helper, ORIGIN.east(3));
        BarStackBE bar = placeBar(helper, ORIGIN.east(6));

        int storageLevels = StoragePile.maxHeight() > 1 ? 2 : 1;
        int singlesLevels = SinglesColumn.maxHeight() > 1 ? 2 : 1;
        int barLevels = BarColumn.maxHeight() > 1 ? 2 : 1;
        checkEquals(StorageStackBE.SLOTS * storageLevels,
                GameTestSupport.capability(storage).getSlots(),
                "Storage advertised slots");
        checkEquals(SinglesStackBE.SLOTS * singlesLevels,
                GameTestSupport.capability(singles).getSlots(),
                "Singles advertised slots");
        checkEquals(BarStackBE.SLOTS * barLevels,
                GameTestSupport.capability(bar).getSlots(),
                "Bar advertised slots");
        helper.succeed();
    }

    /** See {@link CapabilityAndPersistenceChecks#storageDiskSaveRoundTripsItemsRotationAndPermanence}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void storageDiskSaveRoundTripsItemsRotationAndPermanence(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.storageDiskSaveRoundTripsItemsRotationAndPermanence(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#singlesDiskSaveRoundTripsItemsAndBothRotations}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void singlesDiskSaveRoundTripsItemsAndBothRotations(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.singlesDiskSaveRoundTripsItemsAndBothRotations(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#barDiskSaveRoundTripsItems}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void barDiskSaveRoundTripsItems(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.barDiskSaveRoundTripsItems(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#updateTagsCarryRenderStateAndOmitServerOnlyData}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void updateTagsCarryRenderStateAndOmitServerOnlyData(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.updateTagsCarryRenderStateAndOmitServerOnlyData(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#unreadableSavedItemsAreKeptAsideOnDisk}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void unreadableSavedItemsAreKeptAsideOnDisk(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.unreadableSavedItemsAreKeptAsideOnDisk(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#setAsideItemsRecoverIntoFreeSlots}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void setAsideItemsRecoverIntoFreeSlots(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.setAsideItemsRecoverIntoFreeSlots(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#clientUpdatesRejectMalformedDataAtomically}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void clientUpdatesRejectMalformedDataAtomically(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.clientUpdatesRejectMalformedDataAtomically(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#clientSinglesUpdatesValidateRotationBeforeInventory}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void clientSinglesUpdatesValidateRotationBeforeInventory(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.clientSinglesUpdatesValidateRotationBeforeInventory(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#structureRotationTransformsRotatableStackFacing}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void structureRotationTransformsRotatableStackFacing(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.structureRotationTransformsRotatableStackFacing(helper);
    }

    /**
     * Loader entry point for {@link
     * CapabilityAndPersistenceChecks#cachedShapesInvalidateWhenContentsChange}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void cachedShapesInvalidateWhenContentsChange(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.cachedShapesInvalidateWhenContentsChange(helper);
    }

    /**
     * Internal Forge simulation check, exercising Singles only: simulate inserting five apples into
     * grounded slot 0. The remainder is four, the offer stays five, and storage stays empty.
     *
     * <p>No ordinary player gesture performs a capability simulation. Use a slot-addressing machine
     * or test probe that can simulate without committing; the nearest visible behavior is that a
     * dry-run transfer stores nothing.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void capabilitySimulationDoesNotMutateAnyStack(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        IItemHandler capability = GameTestSupport.capability(singles);
        ItemStack offered = new ItemStack(Items.APPLE, 5);

        // Cell 0 is a bottom-layer cell of a column standing on the world, so it is grounded
        // outright and the simulation has something to accept.
        ItemStack remainder = capability.insertItem(0, offered, true);

        checkEquals(5, offered.getCount(), "Simulation mutated its input");
        checkEquals(0, count(capability, Items.APPLE),
                "Simulation mutated the column");
        checkEquals(4, remainder.getCount(),
                "A cell takes one item, so one call should accept one");
        helper.succeed();
    }

    /**
     * Loader entry point for {@link
     * StackFoundationChecks#nestedBatchesPublishOnlyAfterTheOuterBatch}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void nestedBatchesPublishOnlyAfterTheOuterBatch(GameTestHelper helper) {
        StackFoundationChecks.nestedBatchesPublishOnlyAfterTheOuterBatch(helper);
    }

    /**
     * Loader entry point for {@link
     * StackFoundationChecks#structuralEditsInvalidateRunBoundsWithinTheTick}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void structuralEditsInvalidateRunBoundsWithinTheTick(GameTestHelper helper) {
        StackFoundationChecks.structuralEditsInvalidateRunBoundsWithinTheTick(helper);
    }

    /**
     * Loader entry point for {@link
     * StackFoundationChecks#loadedContentsRefreshComparatorFillAndShapes}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void loadedContentsRefreshComparatorFillAndShapes(GameTestHelper helper) {
        StackFoundationChecks.loadedContentsRefreshComparatorFillAndShapes(helper);
    }

    /**
     * Loader entry point for {@link
     * StackFoundationChecks#runContractPreservesTypeSpecificMutations}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void runContractPreservesTypeSpecificMutations(GameTestHelper helper) {
        StackFoundationChecks.runContractPreservesTypeSpecificMutations(helper);
    }

    /** See {@link StackFoundationChecks#cachedGeometryPreservesOccupiedUnions}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void cachedGeometryPreservesOccupiedUnions(GameTestHelper helper) {
        StackFoundationChecks.cachedGeometryPreservesOccupiedUnions(helper);
    }

    /**
     * Loader entry point for {@link ServiceAndConfigChecks#installedServicesAndLogoutResetPacing};
     * see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void installedServicesAndLogoutResetPacing(GameTestHelper helper) {
        ServiceAndConfigChecks.installedServicesAndLogoutResetPacing(helper, FakePlayerFactory.getMinecraft(helper.getLevel()));
    }

    /**
     * Loader entry point for {@link ServiceAndConfigChecks#listEditsPublishImmutablePolicy}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void listEditsPublishImmutablePolicy(GameTestHelper helper) {
        ServiceAndConfigChecks.listEditsPublishImmutablePolicy(helper);
    }

    /** See {@link NetworkPolicyChecks#publicationSyncsOnlyChangedClientPolicy}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void publicationSyncsOnlyChangedClientPolicy(GameTestHelper helper)
            throws ReflectiveOperationException {
        NetworkPolicyChecks.publicationSyncsOnlyChangedClientPolicy(helper);
    }

    /** See {@link NetworkPolicyChecks#admissionSeparatesPlayersAutomationAndTypes}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void admissionSeparatesPlayersAutomationAndTypes(GameTestHelper helper) {
        NetworkPolicyChecks.admissionSeparatesPlayersAutomationAndTypes(helper, FakePlayerFactory.getMinecraft(helper.getLevel()));
    }

    /** See {@link NetworkPolicyChecks#namespacePolicyIsPreparedAndBounded}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void namespacePolicyIsPreparedAndBounded(GameTestHelper helper) {
        NetworkPolicyChecks.namespacePolicyIsPreparedAndBounded(helper);
    }

    /** See {@link NetworkPolicyChecks#policyPacketsValidateBeforePublication}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void policyPacketsValidateBeforePublication(GameTestHelper helper) {
        NetworkPolicyChecks.policyPacketsValidateBeforePublication(helper);
    }

    /** See {@link ServiceAndConfigChecks#numericPolicyRangesAreShared}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void numericPolicyRangesAreShared(GameTestHelper helper) {
        ServiceAndConfigChecks.numericPolicyRangesAreShared(helper);
    }

    /** See {@link ServiceAndConfigChecks#reloadCommandPublishesStoredPolicy}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void reloadCommandPublishesStoredPolicy(GameTestHelper helper) {
        ServiceAndConfigChecks.reloadCommandPublishesStoredPolicy(helper);
    }

    /** To reproduce: use /ss deny mod add, inspect somestacks-server.toml, then remove the entry; command edits persist through Forge's native config. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void commandPolicyEditsPersistToNativeForgeConfig(GameTestHelper helper) throws IOException {
        String marker = "somestacks_gametest_native_save";
        var policy = ServerConfig.ListSetting.DISABLE_MODS;
        var spec = ForgeServerConfig.SPEC;
        check(spec.isLoaded(), "Forge server config is not loaded");
        check(ServerConfig.addListEntry(policy, marker), "Could not add marker");
        try {
            ForgeConfigSpec.ConfigValue<?> nativeValue =
                    spec.getValues().get("compatibility.disable_mods");
            check(((List<?>) nativeValue.get()).contains(marker), "Native config omitted command edit");
            var file = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("serverconfig/somestacks-server.toml");
            check(Files.readString(file).contains(marker), "TOML omitted command edit");
        } finally {
            ServerConfig.removeListEntry(policy, marker);
        }
        helper.succeed();
    }

    /**
     * To reproduce: edit render_gallery.gen_mods in the world's somestacks-server.toml while the
     * server runs; /ss gen mod list reflects Forge's reload without restart. The fixture edits
     * the file directly in its own batch and waits for native publication using a wall-clock
     * deadline, since accelerated GameTest ticks do not measure file-watcher latency. File,
     * native value, and common policy are restored on completion or failure. The edit overwrites
     * without truncation to avoid coalescing truncate/write notifications, which NightConfig's
     * watcher skips when their event count exceeds one.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, batch = "somestacks_native_reload",
            timeoutTicks = CONFIG_RELOAD_TIMEOUT_TICKS)
    public static void nativeForgeReloadPublishesPolicy(GameTestHelper helper) throws IOException {
        String marker = "somestacks_gametest_native_reload_" + UUID.randomUUID().toString().replace("-", "");
        var spec = ForgeServerConfig.SPEC;
        check(spec.isLoaded(), "Forge server config is not loaded");
        ForgeConfigSpec.ConfigValue<List<? extends String>> nativeValue =
                spec.getValues().get("render_gallery.gen_mods");
        List<String> before = List.copyOf(nativeValue.get());
        ServerConfig.Settings policyBefore = ServerConfig.settings();
        check(!policyBefore.genMods().contains(marker), "Reload marker already present");
        var file = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT)
                .resolve("serverconfig/somestacks-server.toml");
        String original = Files.readString(file);
        var entry = Pattern.compile("(?m)^([ \\t]*gen_mods[ \\t]*=[ \\t]*)\\[[^\\r\\n]*\\]([ \\t]*)$")
                .matcher(original);
        check(entry.find(), "Native TOML has no single-line gen_mods entry");
        String edited = original.substring(0, entry.start()) + entry.group(1)
                + "[\"" + marker + "\"]" + entry.group(2) + original.substring(entry.end());
        int padding = original.getBytes(StandardCharsets.UTF_8).length
                - edited.getBytes(StandardCharsets.UTF_8).length;
        if (padding > 0) edited += " ".repeat(padding);
        long startedAt = System.nanoTime();
        long deadline = startedAt + CONFIG_RELOAD_TIMEOUT_NANOS;
        Files.writeString(file, edited, StandardOpenOption.WRITE);
        helper.onEachTick(new Runnable() {
            private boolean finished;

            @Override
            public void run() {
                if (finished) return;
                boolean published = ServerConfig.ListSetting.GEN_MODS.get().equals(List.of(marker));
                if (!published && System.nanoTime() - deadline < 0
                        && helper.getTick() < CONFIG_RELOAD_TIMEOUT_TICKS - 2) {
                    return;
                }
                finished = true;
                boolean nativeLoaded = nativeValue.get().equals(List.of(marker));
                String timeoutDetail = " (native value loaded edit: " + nativeLoaded
                        + ", elapsed milliseconds: "
                        + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                        + ", test tick: " + helper.getTick() + ")";
                try {
                    nativeValue.set(before);
                    Files.writeString(file, original);
                } catch (IOException exception) {
                    helper.fail("Could not restore native TOML: " + exception.getMessage());
                    return;
                } finally {
                    ServerConfig.publish(policyBefore);
                }
                if (published) {
                    helper.succeed();
                } else {
                    helper.fail("Native Forge file reload did not publish policy before its wall-clock or tick limit"
                            + timeoutDetail);
                }
            }
        });
    }
}
