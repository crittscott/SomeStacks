package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.StackItemStorage;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import com.github.crittscott.somestacks.client.CubeRenderHelper;
import com.github.crittscott.somestacks.client.ItemCapture;
import com.github.crittscott.somestacks.client.measure.ModelMeasurement;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

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
     * reports the assertions. It also checks item capture, model measurement, and prepared grid
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
        var blockModels = new BlockModelResolver(client.getModelManager());
        CubeRenderHelper.extractGridItems(items, 1, StorageCubeIdx.GRID, 0,
                CubeRenderHelper.STORAGE_CELL_RENDER_SCALE, index -> 0, blockModels, client.level,
                hit.getBlockPos(), client.player.getEyePosition(), state);
        items.setStackInSlot(0, ItemStack.EMPTY);
        SubmitNodeStorage submitted = new SubmitNodeStorage();
        CubeRenderHelper.submitGridItems(state, new PoseStack(), submitted);
        GameTestScaffold.check(!submitted.getSubmitsPerOrder().isEmpty(),
                "Prepared grid read the emptied inventory during submission");
        CubeRenderHelper.extractGridItems(items, 1, StorageCubeIdx.GRID, 0,
                CubeRenderHelper.STORAGE_CELL_RENDER_SCALE, index -> 0, blockModels, client.level,
                hit.getBlockPos(), client.player.getEyePosition(), state);
        SubmitNodeStorage empty = new SubmitNodeStorage();
        CubeRenderHelper.submitGridItems(state, new PoseStack(), empty);
        GameTestScaffold.check(empty.getSubmitsPerOrder().isEmpty(),
                "Empty extraction retained cells from the previous frame");
        submitted.clear();
        empty.clear();
    }

}
