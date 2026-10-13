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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.count;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

/**
 * The automation surface and the saved state behind it: the item handler exposed on every side,
 * a run advertising its reachable headroom, and each type's update tag round-tripping the contents
 * and presentation state it is responsible for.
 */
@GameTestHolder(SomeStacks.MODID)
@PrefixGameTestTemplate(false)
public final class CapabilityAndPersistenceGameTests {
    private CapabilityAndPersistenceGameTests() {}

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

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void storageUpdateTagRoundTripsItemsRotationAndPermanence(
            GameTestHelper helper) {
        CapabilityAndPersistenceChecks.storageUpdateTagRoundTripsItemsRotationAndPermanence(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void singlesUpdateTagRoundTripsItemsAndBothRotations(
            GameTestHelper helper) {
        CapabilityAndPersistenceChecks.singlesUpdateTagRoundTripsItemsAndBothRotations(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void barUpdateTagRoundTripsItems(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.barUpdateTagRoundTripsItems(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void cachedShapesInvalidateWhenContentsChange(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.cachedShapesInvalidateWhenContentsChange(helper);
    }

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

    /** To reproduce in-game: Perform several automation edits before a tick, including a nested Bar collapse; contents and light publish together. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void nestedBatchesPublishOnlyAfterTheOuterBatch(GameTestHelper helper) {
        StackFoundationChecks.nestedBatchesPublishOnlyAfterTheOuterBatch(helper);
    }

    /** To reproduce in-game: Connect automation to a stack, add a block above it, then remove that block; the slot view follows each edit immediately. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void structuralEditsInvalidateRunBoundsWithinTheTick(GameTestHelper helper) {
        StackFoundationChecks.structuralEditsInvalidateRunBoundsWithinTheTick(helper);
    }

    /** To reproduce in-game: Save and reload partly filled stacks; comparators retain their fill, and loading empty contents clears shapes and fill. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void loadedContentsRefreshComparatorFillAndShapes(GameTestHelper helper) {
        StackFoundationChecks.loadedContentsRefreshComparatorFillAndShapes(helper);
    }

    /** To reproduce in-game: Automate each stack type; Storage settles, Singles applies column gravity, and Bar backfills from its top position. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void runContractPreservesTypeSpecificMutations(GameTestHelper helper) {
        StackFoundationChecks.runContractPreservesTypeSpecificMutations(helper);
    }

    /** To reproduce in-game: reconnect and rotate a stack; gesture/sound pacing resets for the new session. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void installedServicesAndLogoutResetPacing(GameTestHelper helper) {
        ServiceAndConfigChecks.installedServicesAndLogoutResetPacing(helper, net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel()));
    }

    /** To reproduce in-game: add then remove a namespace with /ss deny mod; admission changes immediately without changing other policy. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void listEditsPublishImmutablePolicy(GameTestHelper helper) {
        ServiceAndConfigChecks.listEditsPublishImmutablePolicy(helper);
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

    /** To reproduce: edit render_gallery.gen_mods in somestacks-server.toml while the server runs; /ss gen list reflects Forge's reload without restart. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = 200)
    public static void nativeForgeReloadPublishesPolicy(GameTestHelper helper) {
        String marker = "somestacks_gametest_native_reload";
        var spec = ForgeServerConfig.SPEC;
        ForgeConfigSpec.ConfigValue<List<? extends String>> nativeValue =
                spec.getValues().get("render_gallery.gen_mods");
        List<String> before = List.copyOf(nativeValue.get());
        List<String> updated = new ArrayList<>(before);
        updated.add(marker);
        nativeValue.set(updated);
        spec.save();
        helper.runAfterDelay(60, () -> {
            try {
                check(ServerConfig.ListSetting.GEN_MODS.get().contains(marker),
                        "Native Forge reload did not publish policy");
            } finally {
                nativeValue.set(before);
                spec.save();
                ServerConfig.removeListEntry(
                        ServerConfig.ListSetting.GEN_MODS, marker);
            }
            helper.succeed();
        });
    }
}
