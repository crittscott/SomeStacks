package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.StackBlock;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.util.StackItemStorage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import com.github.crittscott.somestacks.client.BarStackBER;
import com.github.crittscott.somestacks.client.ClientRenderCommands;
import com.github.crittscott.somestacks.client.CubeRenderHelper;
import com.github.crittscott.somestacks.client.ItemRenderOverrides;
import com.github.crittscott.somestacks.client.RenderProfile;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import com.github.crittscott.somestacks.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.renderconfig.OverrideJsonCodec;
import com.github.crittscott.somestacks.renderconfig.RenderMode;
import com.github.crittscott.somestacks.renderconfig.RenderOffset;
import com.github.crittscott.somestacks.util.BlockType;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
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

/** Development-client assertions for the loader callback's cancellation result. */
public final class ClientCallbackChecks {
    private ClientCallbackChecks() {}

    /** Observable local callback outputs; none of these performs the server's world action. */
    public record Result(boolean consumed, boolean blockDenied, boolean itemDenied) {}

    /** Loader-native chunk or block update on an actual client-attached registered entity. */
    @FunctionalInterface
    public interface ClientUpdate {
        void load(StackBlockEntity blockEntity, CompoundTag tag, boolean initial);
    }

    /**
     * To reproduce in-game: launch the loader's runClientGameTests client, join a world, aim at any
     * stack, release Shift and the Stack Modifier, and run /ssclienttest. It invokes the actual
     * loader callback for main/off hand and checks consumption plus block/item suppression. It
     * does not send a vanilla use packet or extract contents. A translated success/failure message
     * reports the assertions; server GameTests cannot exercise this client-only boundary. It also
     * checks Bar presentation reuse and invalidation, render-profile layer precedence and
     * invalidation, and the profile commands. The command also feeds valid and malformed
     * initial/subsequent updates through each loader's native client entry point on scratch
     * entities, checking inventory and inherited components. Ordinary clients cannot reproduce
     * malformed wire data through player actions.
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
            checkBarPresentationCache(client, hit);
            checkClientUpdateIngress(client, hit, update);
            checkProfileCommands();
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


    /**
     * Draws a scratch Bar Stack into a discarded buffer: unchanged frames reuse its prepared bars,
     * while a resource reload or a valid client inventory update discards them.
     */
    private static void checkBarPresentationCache(Minecraft client, BlockHitResult hit) {
        BarStackBE bars = new BarStackBE(hit.getBlockPos(), CommonRegistry.barStackBlock().defaultBlockState());
        bars.getItems().setStackInSlot(0, new ItemStack(Items.RESIN_BRICK));
        bars.setLevel(client.level);
        BarStackBER renderer = new BarStackBER(null);
        try (ByteBufferBuilder scratch = new ByteBufferBuilder(4096)) {
            MultiBufferSource buffers = MultiBufferSource.immediate(scratch);
            Runnable frame = () -> renderer.render(bars, 0, new PoseStack(), buffers,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, client.player.getEyePosition());
            frame.run();
            Object first = bars.renderCache();
            GameTestScaffold.check(first != null, "Bar presentation was not cached");
            frame.run();
            GameTestScaffold.check(first == bars.renderCache(), "Bar presentation rebuilt without changes");
            CubeRenderHelper.onResourceReload();
            frame.run();
            GameTestScaffold.check(first != bars.renderCache(), "Bar presentation survived resource invalidation");
            var update = bars.getUpdateTag(client.level.registryAccess());
            GameTestScaffold.check(bars.loadClientUpdate(update, client.level.registryAccess()),
                    "Valid Bar update was rejected");
            GameTestScaffold.check(bars.renderCache() == null, "Bar presentation survived an inventory update");
            frame.run();
            GameTestScaffold.check(bars.renderCache() != null, "Bar presentation did not rebuild after update");
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
                checkProfileLayers(stone, stoneId);
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

    /**
     * Runs inside a sandbox whose layers start empty, and leaves them empty. To reproduce in-game:
     * give Stone distinct user and resource-pack profiles, edit the user profile, reset it, and
     * reload a changed resource pack. Each change immediately updates the displayed mode, scale,
     * and offset; the user layer wins over the pack, which wins over measurement. A user entry
     * containing only a mode uses scale one and zero offset rather than the pack's fields.
     */
    private static void checkProfileLayers(ItemStack stone, ResourceLocation id) {
        RenderProfile measured = AutoRenderProfiles.get(stone);
        GameTestScaffold.checkEquals(measured, ItemRenderOverrides.resolve(stone),
                "An unconfigured item did not use its measured profile");

        ItemRenderConfig pack = new ItemRenderConfig(RenderMode.GUI, 0.8f, new RenderOffset(0.1f, 0.2f, 0.3f));
        ItemRenderOverrides.CONFIG_MAP.put(id, pack);
        CubeRenderHelper.onResourceReload();
        GameTestScaffold.checkEquals(profileOf(pack), ItemRenderOverrides.resolve(stone),
                "A resource reload did not apply the resource-pack layer");

        ItemRenderConfig user = new ItemRenderConfig(RenderMode.TWO_D, 0.6f, new RenderOffset(-0.1f, -0.2f, -0.3f));
        ItemRenderOverrides.putUser(id, user);
        GameTestScaffold.checkEquals(profileOf(user), ItemRenderOverrides.resolve(stone),
                "The user layer did not take priority over the resource-pack layer");

        ItemRenderConfig edited = new ItemRenderConfig(RenderMode.THREE_D, 0.9f, new RenderOffset(0.2f, 0.0f, -0.2f));
        ItemRenderOverrides.putUser(id, edited);
        GameTestScaffold.checkEquals(profileOf(edited), ItemRenderOverrides.resolve(stone),
                "Editing a user override left the previous profile resolved");

        ItemRenderOverrides.putUser(id, new ItemRenderConfig(RenderMode.BLOCK, null, null));
        GameTestScaffold.checkEquals(new RenderProfile(RenderMode.BLOCK, 1.0f, RenderOffset.ZERO),
                ItemRenderOverrides.resolve(stone), "A mode-only user entry took the pack's scale or offset");

        ItemRenderOverrides.removeUser(id);
        GameTestScaffold.checkEquals(profileOf(pack), ItemRenderOverrides.resolve(stone),
                "Resetting a user override did not return to the resource-pack layer");

        ItemRenderConfig replacement = new ItemRenderConfig(RenderMode.THREE_D, 0.4f, new RenderOffset(0.0f, 0.1f, 0.0f));
        ItemRenderOverrides.CONFIG_MAP.put(id, replacement);
        CubeRenderHelper.onResourceReload();
        GameTestScaffold.checkEquals(profileOf(replacement), ItemRenderOverrides.resolve(stone),
                "A resource reload left the replaced pack profile resolved");

        ItemRenderOverrides.CONFIG_MAP.remove(id);
        CubeRenderHelper.onResourceReload();
    }

    private static RenderProfile profileOf(ItemRenderConfig config) {
        return new RenderProfile(config.mode(), config.scale(), config.offset());
    }

    private static Map<?, ItemRenderConfig> readUserFile(Path file) throws IOException {
        JsonObject json = new Gson().fromJson(Files.readString(file), JsonObject.class);
        return OverrideJsonCodec.parse(json, file.toString());
    }
}
