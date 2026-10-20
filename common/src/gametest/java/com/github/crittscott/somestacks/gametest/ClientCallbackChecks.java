package com.github.crittscott.somestacks.gametest;

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

    /**
     * To reproduce in-game: launch the loader's runClientGameTests client, join a world, aim at any
     * stack, release Shift and the Stack Modifier, and run /ssclienttest. It invokes the actual
     * loader callback for main/off hand and checks consumption plus block/item suppression. It
     * does not send a vanilla use packet or extract contents. A translated success/failure message
     * reports the assertions; server GameTests cannot exercise this client-only boundary. It also
     * checks render-profile layer precedence and invalidation and the profile commands.
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
            GameTestScaffold.check(ItemStack.matches(main, client.player.getMainHandItem()),
                    "Local callback changed main-hand contents");
            GameTestScaffold.check(ItemStack.matches(off, client.player.getOffhandItem()),
                    "Local callback changed off-hand contents");
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
