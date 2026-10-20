package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.StackBlock;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.client.BarStackBER;
import com.github.crittscott.somestacks.client.BarTextureStore;
import com.github.crittscott.somestacks.client.ClientRenderCommands;
import com.github.crittscott.somestacks.client.CubeRenderHelper;
import com.github.crittscott.somestacks.client.ItemCapture;
import com.github.crittscott.somestacks.client.ItemRenderOverrides;
import com.github.crittscott.somestacks.client.RenderProfile;
import com.github.crittscott.somestacks.client.measure.ModelMeasurement;
import com.github.crittscott.somestacks.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.renderconfig.OverrideJsonCodec;
import com.github.crittscott.somestacks.renderconfig.RenderMode;
import com.github.crittscott.somestacks.renderconfig.RenderOffset;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.StackItemStorage;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/** Development-client assertions for callback cancellation and prepared rendering. */
public final class ClientCallbackChecks {
    private ClientCallbackChecks() {}

    /** Observable local callback outputs; none of these performs the server's world action. */
    public record Result(boolean consumed, boolean blockDenied, boolean itemDenied) {}

    /** Loader-native chunk or block update on a client-attached registered scratch entity. */
    @FunctionalInterface
    public interface ClientUpdate {
        void load(StackBlockEntity blockEntity, CompoundTag tag, boolean initial);
    }

    /**
     * To reproduce in-game: launch the loader's runClientGameTests client, join a world, aim at any
     * stack, release Shift and the Stack Modifier, and run /ssclienttest. It invokes the actual
     * loader callback for main/off hand and checks consumption plus block/item suppression. It
     * does not send a vanilla use packet or extract contents. A translated success/failure message
     * reports the assertions. It also checks item capture, model measurement, and prepared grid
     * submission, and render-profile precedence and invalidation through
     * {@link RenderProfileChecks#run()}; server GameTests cannot exercise these client-only boundaries. With default
     * resources, view Stone, an Apple, and a Chest in 3d/gui/block/2d profiles, rotate Singles items,
     * walk around every face, and reload resources. Models must remain fitted and correctly tinted,
     * with special models staying on the 3-D path unless explicitly overridden. With default
     * resources, deposit resin bricks into a Bar Stack: the bars must be orange on a fresh launch
     * and after F3+T. Run /ssclienttest before and after reload to check the resolved resin tint,
     * Bar presentation reuse, and invalidation on resource reload and inventory updates. It also
     * checks valid and malformed initial/subsequent native client updates on scratch entities,
     * including atomic rejection of inventory, inherited components, and render-cache changes.
     */
    public static int run(boolean modifierDown, BiFunction<InteractionHand, BlockHitResult, Result> callback,
                          ClientUpdate update) {
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
            GameTestScaffold.check(ItemStack.matches(main, client.player.getMainHandItem()),
                    "Local callback changed main-hand contents");
            GameTestScaffold.check(ItemStack.matches(off, client.player.getOffhandItem()),
                    "Local callback changed off-hand contents");
            checkRendering(client, hit);
            checkProfileLifecycle();
            RenderProfileChecks.run();
            checkBarPresentationCache(client, hit);
            checkClientUpdateIngress(client, hit, update);
            client.player.sendSystemMessage(Component.translatable("somestacks_gametest.message.client_callback_passed"));
            return 1;
        } catch (RuntimeException failure) {
            if (client.player != null) {
                client.player.sendSystemMessage(Component.translatable(
                        "somestacks_gametest.message.client_callback_failed", failure.getMessage()));
            }
            return 0;
        }
    }

    private static CompoundTag namedComponents(Minecraft client, String name) {
        ItemStack named = new ItemStack(Items.STONE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        CompoundTag encoded = (CompoundTag) ItemStack.CODEC.encodeStart(
                RegistryOps.create(NbtOps.INSTANCE, client.level.registryAccess()), named).getOrThrow();
        return encoded.getCompound("components").orElseThrow();
    }

    private static void checkClientUpdateIngress(Minecraft client, BlockHitResult hit, ClientUpdate update) {
        var registries = client.level.registryAccess();
        for (BlockType type : BlockType.values()) {
            var state = type.getBlock().defaultBlockState();
            StackBlockEntity be = (StackBlockEntity) ((StackBlock) type.getBlock()).newBlockEntity(hit.getBlockPos(), state);
            be.getItems().setStackInSlot(0, new ItemStack(Items.STONE));
            be.setLevel(client.level);
            CompoundTag valid = be.getUpdateTag(registries);
            valid.put("components", namedComponents(client, "original"));
            update.load(be, valid, true);
            CompoundTag baseline = be.saveWithoutMetadata(registries);
            GameTestScaffold.check(baseline.contains("components"), "Inherited update components were not loaded");
            GameTestScaffold.checkEquals(Items.STONE, be.getItems().getStackInSlot(0).getItem(),
                    "Initial client update lost its inventory");
            for (boolean initial : new boolean[] {true, false}) {
                for (int malformed = 0; malformed < 4; malformed++) {
                    CompoundTag invalid = valid.copy();
                    invalid.put("components", namedComponents(client, "rejected"));
                    switch (malformed) {
                        case 0 -> invalid.remove(StackBlockEntity.TAG_DATA_VERSION);
                        case 1 -> invalid.putString(StackBlockEntity.TAG_ITEMS, "not an inventory");
                        case 2 -> invalid.putBoolean("Permanent", true);
                        case 3 -> invalid.getCompound(StackBlockEntity.TAG_ITEMS).orElseThrow()
                                .putString(StackItemStorage.TAG_SET_ASIDE, "server-only recovery data");
                        default -> throw new IllegalStateException();
                    }
                    Object cached = new Object();
                    be.setRenderCache(cached);
                    update.load(be, invalid, initial);
                    GameTestScaffold.checkEquals(baseline, be.saveWithoutMetadata(registries),
                            "Rejected native update changed state for " + type + " / " + initial);
                    GameTestScaffold.check(be.renderCache() == cached,
                            "Rejected native update discarded prepared presentation");
                }
            }
            StackItemStorage replacement = new StackItemStorage(be.getItems().getSlots());
            replacement.setStackInSlot(1, new ItemStack(Items.DIAMOND));
            CompoundTag changed = valid.copy();
            changed.put(StackBlockEntity.TAG_ITEMS, replacement.save(registries));
            changed.put("components", namedComponents(client, "replacement"));
            update.load(be, changed, false);
            GameTestScaffold.check(be.getItems().getStackInSlot(0).isEmpty(), "Native update retained an old slot");
            GameTestScaffold.checkEquals(Items.DIAMOND, be.getItems().getStackInSlot(1).getItem(),
                    "Subsequent client update did not replace inventory");
            GameTestScaffold.checkEquals(changed.getCompound("components").orElseThrow(),
                    be.saveWithoutMetadata(registries).getCompound("components").orElseThrow(),
                    "Subsequent client update did not replace inherited components");
            GameTestScaffold.check(be.renderCache() == null, "Accepted native update retained prepared presentation");
        }
    }

    private static void checkBarPresentationCache(Minecraft client, BlockHitResult hit) {
        BarStackBE bars = new BarStackBE(hit.getBlockPos(), CommonRegistry.barStackBlock().defaultBlockState());
        bars.getItems().setStackInSlot(0, new ItemStack(Items.RESIN_BRICK));
        bars.setLevel(client.level);
        var renderer = new BarStackBER(null);
        var state = renderer.createRenderState();
        renderer.extractRenderState(bars, state, 0, client.player.getEyePosition(), null);
        Object first = bars.renderCache();
        GameTestScaffold.check(first != null, "Bar presentation was not cached");
        renderer.extractRenderState(bars, state, 0, client.player.getEyePosition(), null);
        GameTestScaffold.check(first == bars.renderCache(), "Bar presentation rebuilt without changes");
        CubeRenderHelper.onResourceReload();
        renderer.extractRenderState(bars, state, 0, client.player.getEyePosition(), null);
        GameTestScaffold.check(first != bars.renderCache(), "Bar presentation survived resource invalidation");
        var update = bars.getUpdateTag(client.level.registryAccess());
        GameTestScaffold.check(bars.loadClientUpdate(update, client.level.registryAccess()),
                "Valid Bar update was rejected");
        GameTestScaffold.check(bars.renderCache() == null, "Bar presentation survived an inventory update");
        renderer.extractRenderState(bars, state, 0, client.player.getEyePosition(), null);
        GameTestScaffold.check(bars.renderCache() != null, "Bar presentation did not rebuild after update");
    }

    /**
     * Runs the real {@code /ss item} and {@code /ss write changed} commands against scratch layers
     * and a temporary directory, so the player's own override file is never read or written.
     * To reproduce in-game: {@code /ss item minecraft:stone 3d 2} makes Stone render at double
     * size; {@code /ss write changed} saves it to {@code item_overrides.json}; {@code /ss item
     * minecraft:stone reset} followed by another write removes the entry and Stone is measured
     * again. A bundled resource override applies until the user sets one.
     */
    private static void checkProfileLifecycle() {
        Identifier stoneId = BuiltInRegistries.ITEM.getKey(Items.STONE);
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
                ItemRenderOverrides.CONFIG_MAP.put(stoneId, new ItemRenderConfig(RenderMode.BLOCK, 2.0f, null));
                ItemRenderOverrides.removeUser(stoneId);
                RenderProfile bundled = ItemRenderOverrides.resolve(stone);
                GameTestScaffold.check(bundled.mode() == RenderMode.BLOCK && bundled.scale() == 2.0f
                                && bundled.offset().equals(RenderOffset.ZERO),
                        "A bundled resource override did not take precedence over measurement");

                dispatcher.execute("ss item minecraft:stone 2d 1.5 0.25 0.5", new Object());
                RenderProfile edited = ItemRenderOverrides.resolve(stone);
                GameTestScaffold.check(edited.mode() == RenderMode.TWO_D && edited.scale() == 1.5f
                                && edited.offset().equals(new RenderOffset(0.25f, 0.5f, 0.0f)),
                        "The item command did not override the bundled layer");

                dispatcher.execute("ss write changed", new Object());
                Map<Identifier, ItemRenderConfig> saved = readUserFile(sandbox.userFile());
                GameTestScaffold.checkEquals(Set.of(stoneId), saved.keySet(),
                        "The user file held more than the explicit user settings");
                sandbox.reloadUserFile();
                GameTestScaffold.checkEquals(edited, ItemRenderOverrides.resolve(stone),
                        "The saved user override did not survive a reload");

                dispatcher.execute("ss item minecraft:stone reset", new Object());
                GameTestScaffold.checkEquals(bundled, ItemRenderOverrides.resolve(stone),
                        "Reset left the user override active");
                dispatcher.execute("ss write changed", new Object());
                GameTestScaffold.check(readUserFile(sandbox.userFile()).isEmpty(),
                        "Reset left its entry in the user file");
            }
        } catch (IOException | CommandSyntaxException e) {
            throw new IllegalStateException("Profile lifecycle check failed: " + e.getMessage(), e);
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

    private static Map<Identifier, ItemRenderConfig> readUserFile(Path file) throws IOException {
        JsonObject json = new Gson().fromJson(Files.readString(file), JsonObject.class);
        return OverrideJsonCodec.parse(json, file.toString());
    }

    private static void checkRendering(Minecraft client, BlockHitResult hit) {
        var resin = BarTextureStore.getTexture(new ItemStack(Items.RESIN_BRICK));
        GameTestScaffold.check(!resin.autoTint() && resin.red() > resin.green()
                        && resin.green() > resin.blue(),
                "Resin bar did not resolve an orange tint (requires default resources)");

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
        var blockModels = new BlockModelResolver(client.getModelManager());
        CubeRenderHelper.extractGridItems(null, items, 1, StorageCubeIdx.GRID, 0,
                CubeRenderHelper.STORAGE_CELL_RENDER_SCALE, index -> 0, blockModels, client.level,
                hit.getBlockPos(), client.player.getEyePosition(), state);
        items.setStackInSlot(0, ItemStack.EMPTY);
        SubmitNodeStorage submitted = new SubmitNodeStorage();
        CubeRenderHelper.submitGridItems(state, new PoseStack(), submitted);
        GameTestScaffold.check(!submitted.getSubmitsPerOrder().isEmpty(),
                "Prepared grid read the emptied inventory during submission");
        CubeRenderHelper.extractGridItems(null, items, 1, StorageCubeIdx.GRID, 0,
                CubeRenderHelper.STORAGE_CELL_RENDER_SCALE, index -> 0, blockModels, client.level,
                hit.getBlockPos(), client.player.getEyePosition(), state);
        SubmitNodeStorage empty = new SubmitNodeStorage();
        CubeRenderHelper.submitGridItems(state, new PoseStack(), empty);
        GameTestScaffold.check(empty.getSubmitsPerOrder().isEmpty(),
                "Empty extraction retained cells from the previous frame");
    }

}
