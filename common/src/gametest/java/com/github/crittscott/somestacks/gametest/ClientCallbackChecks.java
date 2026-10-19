package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.StackItemStorage;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import com.github.crittscott.somestacks.client.BarStackBER;
import com.github.crittscott.somestacks.client.ClientRenderCommands;
import com.github.crittscott.somestacks.client.CubeRenderHelper;
import com.github.crittscott.somestacks.client.ItemCapture;
import com.github.crittscott.somestacks.client.ItemRenderOverrides;
import com.github.crittscott.somestacks.client.RenderProfile;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import com.github.crittscott.somestacks.client.measure.ModelMeasurement;
import com.github.crittscott.somestacks.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.renderconfig.OverrideJsonCodec;
import com.github.crittscott.somestacks.renderconfig.RenderMode;
import com.github.crittscott.somestacks.renderconfig.RenderOffset;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;

/** Development-client assertions for callback cancellation and prepared rendering. */
public final class ClientCallbackChecks {
    private ClientCallbackChecks() {}

    /** Observable local callback outputs; none of these performs the server's world action. */
    public record Result(boolean consumed, boolean blockDenied, boolean itemDenied) {}

    /**
     * To reproduce in-game: launch the loader's runClientGameTests client, join a world, aim at any
     * stack, release Shift and the Stack Modifier, and run /ssclienttest. It invokes the actual
     * loader callback for main/off hand and checks consumption plus block/item suppression. It
     * does not send a vanilla use packet or extract contents. A translated success/failure message
     * reports the assertions. It also checks item capture, model measurement, render-profile layer
     * priority, the drawing work each forced render mode submits, and prepared grid and Bar
     * submission; server GameTests cannot exercise these client-only boundaries. With default
     * resources, view Stone, an Apple, and a Chest in 3d/gui/block/2d profiles, rotate Singles items,
     * walk around every face, and reload resources. Models must remain fitted and correctly tinted,
     * with special models staying on the 3-D path unless explicitly overridden.
     */
    public static int run(boolean modifierDown, BiFunction<InteractionHand, BlockHitResult, Result> callback) {
        Minecraft client = Minecraft.getInstance();
        try {
            if (client.player == null || client.level == null
                    || !(client.hitResult instanceof BlockHitResult hit)
                    || BlockType.of(client.level.getBlockState(hit.getBlockPos()).getBlock()) == null) {
                throw new IllegalStateException("Aim at a Some Stacks block in a loaded world");
            }
            if (modifierDown || client.player.isShiftKeyDown()) {
                throw new IllegalStateException("Release Shift and the Stack Modifier");
            }
            var main = client.player.getMainHandItem().copy();
            var off = client.player.getOffhandItem().copy();
            GameTestScaffold.checkEquals(new Result(true, true, true),
                    callback.apply(InteractionHand.MAIN_HAND, hit), "Main-hand callback outputs");
            GameTestScaffold.checkEquals(new Result(false, false, false),
                    callback.apply(InteractionHand.OFF_HAND, hit), "Off-hand callback outputs");
            GameTestScaffold.check(net.minecraft.world.item.ItemStack.matches(main, client.player.getMainHandItem()),
                    "Local callback changed main-hand contents");
            GameTestScaffold.check(net.minecraft.world.item.ItemStack.matches(off, client.player.getOffhandItem()),
                    "Local callback changed off-hand contents");
            checkRendering(client, hit);
            client.player.displayClientMessage(Component.translatable("somestacks_gametest.message.client_callback_passed"), false);
            return 1;
        } catch (RuntimeException failure) {
            if (client.player != null) {
                client.player.displayClientMessage(Component.translatable(
                        "somestacks_gametest.message.client_callback_failed", failure.getMessage()), false);
            }
            return 0;
        }
    }

    private static void checkRendering(Minecraft client, BlockHitResult hit) {
        ItemStack stone = new ItemStack(Items.STONE);
        ItemCapture captured = ItemCapture.capture(stone, ItemDisplayContext.FIXED,
                client.level, new PoseStack());
        var quads = captured.quads();
        var bounds = captured.bounds();
        GameTestScaffold.check(!quads.isEmpty() && !captured.hasSpecialGeometry(),
                "Stone did not submit ordinary item quads");
        GameTestScaffold.check(bounds != null && bounds.getXsize() > 0 && bounds.getYsize() > 0
                        && bounds.getZsize() > 0, "Stone model extents are empty");
        ItemCapture special = ItemCapture.capture(new ItemStack(Items.CHEST),
                ItemDisplayContext.FIXED, client.level, new PoseStack());
        GameTestScaffold.check(special.hasSpecialGeometry(), "Chest special geometry was treated as flat art");
        GameTestScaffold.checkEquals(quads, captured.quads(), "A later capture changed earlier item quads");
        GameTestScaffold.checkEquals(bounds, captured.bounds(), "A later capture changed earlier model extents");
        var gui = ModelMeasurement.measureGui(stone);
        GameTestScaffold.check(gui.failure() == null && gui.bounds() != null,
                "GUI model extents could not be measured: " + gui.failure());

        StackItemStorage items = new StackItemStorage(1);
        items.setStackInSlot(0, stone);
        var state = new CubeRenderHelper.GridRenderState();
        CubeRenderHelper.extractGridItems(items, 1, StorageCubeIdx.GRID, 0,
                CubeRenderHelper.STORAGE_CELL_RENDER_SCALE, index -> 0, client.level,
                hit.getBlockPos(), client.player.getEyePosition(), state);
        items.setStackInSlot(0, ItemStack.EMPTY);
        SubmitNodeStorage submitted = new SubmitNodeStorage();
        CubeRenderHelper.submitGridItems(state, new PoseStack(), submitted);
        GameTestScaffold.check(!submitted.getSubmitsPerOrder().isEmpty(),
                "Prepared grid read the emptied inventory during submission");
        CubeRenderHelper.extractGridItems(items, 1, StorageCubeIdx.GRID, 0,
                CubeRenderHelper.STORAGE_CELL_RENDER_SCALE, index -> 0, client.level,
                hit.getBlockPos(), client.player.getEyePosition(), state);
        SubmitNodeStorage empty = new SubmitNodeStorage();
        CubeRenderHelper.submitGridItems(state, new PoseStack(), empty);
        GameTestScaffold.check(empty.getSubmitsPerOrder().isEmpty(),
                "Empty extraction retained cells from the previous frame");
        submitted.clear();
        empty.clear();

        checkProfileLayers(stone);
        checkProfileCommands();
        checkRenderModes(client, hit, stone);
        checkBarRendering(client, hit);
    }

    /**
     * The user layer beats the resource-pack layer, which beats measurement; resetting a user entry
     * returns to the pack, a partial entry measures only its mode, and a resource reload discards
     * resolved profiles. Both layers are restored afterward; no override file is written.
     */
    private static void checkProfileLayers(ItemStack stone) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stone.getItem());
        ItemRenderConfig priorPack = ItemRenderOverrides.CONFIG_MAP.get(id);
        ItemRenderConfig priorUser = ItemRenderOverrides.userOverride(id);
        ItemRenderConfig pack = new ItemRenderConfig(RenderMode.GUI, 0.5f, new RenderOffset(0.1f, 0.0f, 0.0f));
        ItemRenderConfig user = new ItemRenderConfig(RenderMode.BLOCK, 0.75f, RenderOffset.ZERO);
        try {
            ItemRenderOverrides.CONFIG_MAP.remove(id);
            ItemRenderOverrides.removeUser(id);
            RenderProfile measured = AutoRenderProfiles.get(stone);
            GameTestScaffold.checkEquals(measured, ItemRenderOverrides.resolve(stone),
                    "An unconfigured item did not use its measured profile");

            ItemRenderOverrides.CONFIG_MAP.put(id, pack);
            CubeRenderHelper.onResourceReload();
            GameTestScaffold.checkEquals(profileOf(pack), ItemRenderOverrides.resolve(stone),
                    "A resource reload did not apply the resource-pack layer");

            ItemRenderOverrides.putUser(id, user);
            GameTestScaffold.checkEquals(profileOf(user), ItemRenderOverrides.resolve(stone),
                    "The user layer did not take priority over the resource-pack layer");

            ItemRenderOverrides.removeUser(id);
            GameTestScaffold.checkEquals(profileOf(pack), ItemRenderOverrides.resolve(stone),
                    "Resetting a user override did not return to the resource-pack layer");

            ItemRenderOverrides.putUser(id, new ItemRenderConfig(null, 2.0f, null));
            GameTestScaffold.checkEquals(new RenderProfile(measured.mode(), 2.0f, RenderOffset.ZERO),
                    ItemRenderOverrides.resolve(stone),
                    "A partial override did not measure only its mode");
        } finally {
            if (priorPack == null) {
                ItemRenderOverrides.CONFIG_MAP.remove(id);
            } else {
                ItemRenderOverrides.CONFIG_MAP.put(id, priorPack);
            }
            // Either user-layer call also discards resolved profiles, covering the pack restore.
            if (priorUser == null) {
                ItemRenderOverrides.removeUser(id);
            } else {
                ItemRenderOverrides.putUser(id, priorUser);
            }
        }
    }

    /**
     * Runs the real {@code /ss item} and {@code /ss write changed} commands against scratch layers
     * and a temporary directory, so the player's own override file is never read or written.
     * To reproduce in-game: {@code /ss item minecraft:stone 2d 1.5 0.25 0.5} changes how Stone
     * renders; {@code /ss write changed} saves it to {@code item_overrides.json}; {@code /ss item
     * minecraft:stone reset} followed by another write removes the entry and Stone is measured
     * again.
     */
    private static void checkProfileCommands() {
        var stoneId = BuiltInRegistries.ITEM.getKey(Items.STONE);
        ItemStack stone = new ItemStack(Items.STONE);
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        List<Component> feedback = new ArrayList<>();
        ClientRenderCommands.register(dispatcher, new ClientRenderCommands.Feedback<>() {
            @Override public void success(Object source, Component message) { feedback.add(message); }
            @Override public void failure(Object source, Component message) {
                throw new IllegalStateException("Command failed: " + message.getString());
            }
        });

        Path directory = null;
        try {
            directory = Files.createTempDirectory("somestacks-overrides");
            try (ItemRenderOverrides.Sandbox sandbox = new ItemRenderOverrides.Sandbox(directory)) {
                RenderProfile measured = ItemRenderOverrides.resolve(stone);

                dispatcher.execute("ss item minecraft:stone 2d 1.5 0.25 0.5", new Object());
                RenderProfile edited = ItemRenderOverrides.resolve(stone);
                GameTestScaffold.check(edited.mode() == RenderMode.TWO_D && edited.scale() == 1.5f
                                && edited.offset().equals(new RenderOffset(0.25f, 0.5f, 0.0f)),
                        "The item command did not override the measured profile");

                dispatcher.execute("ss write changed", new Object());
                GameTestScaffold.checkEquals(Set.of(stoneId), readUserFile(sandbox.userFile()).keySet(),
                        "The user file held more than the explicit user settings");
                sandbox.reloadUserFile();
                GameTestScaffold.checkEquals(edited, ItemRenderOverrides.resolve(stone),
                        "The saved user override did not survive a reload");

                dispatcher.execute("ss item minecraft:stone reset", new Object());
                GameTestScaffold.checkEquals(measured, ItemRenderOverrides.resolve(stone),
                        "Reset left the user override active");
                dispatcher.execute("ss write changed", new Object());
                GameTestScaffold.check(readUserFile(sandbox.userFile()).isEmpty(),
                        "Reset left its entry in the user file");
            }
        } catch (IOException | CommandSyntaxException e) {
            throw new IllegalStateException("Profile command check failed: " + e.getMessage(), e);
        } finally {
            if (directory != null) {
                try (var files = Files.walk(directory)) {
                    files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
                } catch (IOException ignored) {
                    // The scratch directory is in the system temp area.
                }
            }
        }
        GameTestScaffold.checkEquals(2, feedback.size(), "The item commands should each report once");
    }

    private static Map<?, ItemRenderConfig> readUserFile(Path file) throws IOException {
        JsonObject json = new Gson().fromJson(Files.readString(file), JsonObject.class);
        return OverrideJsonCodec.parse(json, file.toString());
    }

    private static RenderProfile profileOf(ItemRenderConfig config) {
        return new RenderProfile(config.mode(), config.scale(), config.offset());
    }

    /**
     * Each forced mode submits its own kind of drawing work: 2d its own projected geometry, 3d and
     * gui the item model in the FIXED or GUI context, and block the block model.
     */
    private static void checkRenderModes(Minecraft client, BlockHitResult hit, ItemStack stone) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stone.getItem());
        ItemRenderConfig priorUser = ItemRenderOverrides.userOverride(id);
        try {
            for (RenderMode mode : RenderMode.values()) {
                ItemRenderOverrides.putUser(id, new ItemRenderConfig(mode, null, null));
                StackItemStorage items = new StackItemStorage(1);
                items.setStackInSlot(0, stone);
                var state = new CubeRenderHelper.GridRenderState();
                CubeRenderHelper.extractGridItems(items, 1, StorageCubeIdx.GRID, 0,
                        CubeRenderHelper.STORAGE_CELL_RENDER_SCALE, index -> 0, client.level,
                        hit.getBlockPos(), client.player.getEyePosition(), state);
                RecordingSubmits submits = new RecordingSubmits();
                CubeRenderHelper.submitGridItems(state, new PoseStack(), submits);
                switch (mode) {
                    case TWO_D -> GameTestScaffold.check(submits.itemContexts.isEmpty()
                                    && submits.blocks == 0 && submits.otherSubmitted(),
                            "2d mode did not draw only its projected geometry");
                    case THREE_D -> GameTestScaffold.check(
                            submits.itemContexts.equals(Set.of(ItemDisplayContext.FIXED)) && submits.blocks == 0,
                            "3d mode did not submit the FIXED item model alone");
                    case GUI -> GameTestScaffold.check(
                            submits.itemContexts.equals(Set.of(ItemDisplayContext.GUI)) && submits.blocks == 0,
                            "gui mode did not submit the GUI item model alone");
                    case BLOCK -> GameTestScaffold.check(
                            submits.itemContexts.isEmpty() && submits.blocks > 0,
                            "block mode did not submit the block model alone");
                }
            }
        } finally {
            if (priorUser == null) {
                ItemRenderOverrides.removeUser(id);
            } else {
                ItemRenderOverrides.putUser(id, priorUser);
            }
        }
    }

    /**
     * Bar submission uses only prepared state: emptying the inventory after extraction leaves that
     * frame's bars drawn, and extracting the empty inventory draws nothing. The block entity is
     * detached from the world, so the aimed block is unaffected.
     */
    private static void checkBarRendering(Minecraft client, BlockHitResult hit) {
        BarStackBE bars = new BarStackBE(hit.getBlockPos(), CommonRegistry.barStackBlock().defaultBlockState());
        bars.getItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        bars.getItems().setStackInSlot(BarCubeIdx.LAYER_SIZE, new ItemStack(Items.IRON_INGOT));
        bars.setLevel(client.level);
        BarStackBER renderer = new BarStackBER(null);
        BarStackBER.BarRenderState state = renderer.createRenderState();
        renderer.extractRenderState(bars, state, 0.0f, client.player.getEyePosition(), null);
        bars.getItems().setStackInSlot(0, ItemStack.EMPTY);
        bars.getItems().setStackInSlot(BarCubeIdx.LAYER_SIZE, ItemStack.EMPTY);

        RecordingSubmits submitted = new RecordingSubmits();
        renderer.submit(state, new PoseStack(), submitted, new CameraRenderState());
        GameTestScaffold.check(submitted.otherSubmitted(),
                "Prepared bars read the emptied inventory during submission");

        renderer.extractRenderState(bars, state, 0.0f, client.player.getEyePosition(), null);
        RecordingSubmits empty = new RecordingSubmits();
        renderer.submit(state, new PoseStack(), empty, new CameraRenderState());
        GameTestScaffold.check(!empty.otherSubmitted(),
                "Empty bar extraction retained bars from the previous frame");
    }

    /**
     * Records the display context of each item-model submission and counts block submissions;
     * every other feature, such as custom geometry, reaches native storage and marks it used.
     * Nothing is drawn.
     */
    private static final class RecordingSubmits extends SubmitNodeStorage {
        private final Map<Integer, SubmitNodeCollection> orders = new TreeMap<>();
        private final Set<ItemDisplayContext> itemContexts = EnumSet.noneOf(ItemDisplayContext.class);
        private int blocks;

        @Override
        public SubmitNodeCollection order(int order) {
            return orders.computeIfAbsent(order, ignored -> new SubmitNodeCollection(this) {
                @Override
                public void submitItem(PoseStack pose, ItemDisplayContext context, int light,
                                       int overlay, int outline, int[] tints, List<BakedQuad> submitted,
                                       RenderType renderType, ItemStackRenderState.FoilType foil) {
                    itemContexts.add(context);
                }

                @Override
                public void submitBlock(PoseStack pose, BlockState block, int light, int overlay,
                                        int outline) {
                    blocks++;
                }
            });
        }

        boolean otherSubmitted() {
            return orders.values().stream().anyMatch(SubmitNodeCollection::wasUsed);
        }
    }
}
