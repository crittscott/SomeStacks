package com.github.crittscott.somestacks.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Loader-specific services used by shared client rendering code, and the vanilla item-rendering
 * facts the renderers and model measurers must agree on.
 */
public final class ClientRenderPlatform {
    /** Fixed quad-selection seed used by ItemRenderer for inventory model variants. */
    public static final long ITEM_MODEL_SEED = 42L;

    /** Vertices in one baked quad. */
    public static final int QUAD_VERTICES = 4;

    /**
     * Ints per vertex in a baked quad's vertex data, which is laid out in the block vertex format.
     * Position occupies the first three.
     */
    public static final int VERTEX_STRIDE =
            DefaultVertexFormat.BLOCK.getVertexSize() / Integer.BYTES;

    /**
     * Offset of a vertex's texture coordinates within its block-format data: after the three
     * position floats and the packed color.
     */
    public static final int VERTEX_UV_OFFSET = 4;

    private static Backend backend;

    private ClientRenderPlatform() {}

    public interface Backend {
        /**
         * The sequence of baked models to render for {@code stack} in {@code context}, resolved
         * through the loader's own item renderer so multi-layer items (e.g. a fabulous-graphics
         * double pass) render exactly as they would through vanilla's item rendering path.
         */
        List<BakedModel> renderPasses(
                BakedModel model, ItemStack stack, ItemDisplayContext context);

        /**
         * The tint for layer {@code tintIndex} of {@code stack}, or opaque white when the item has
         * no color provider. A loader seam because vanilla's {@code Minecraft} exposes no item
         * colors accessor in common code; each loader supplies its own route to the registered
         * providers.
         */
        int itemColor(ItemStack stack, int tintIndex);
    }

    /**
     * Turns the pose so that an item drawn with the GUI display context faces as it does in a FIXED
     * display instead of tilted like an inventory icon: it cancels the 30-degree tilt and 45
     * degrees of the 225-degree turn of the GUI transform vanilla's block model parent gives. The
     * {@code gui} render path and both loaders' GUI measurement call this, so an item is fitted at
     * the angle it is drawn.
     */
    public static void applyGuiCounterRotation(PoseStack pose) {
        pose.mulPose(Axis.YP.rotationDegrees(-45.0f));
        pose.mulPose(Axis.XP.rotationDegrees(-30.0f));
    }

    /**
     * @see Backend#itemColor
     */
    public static int itemColor(ItemStack stack, int tintIndex) {
        return backend.itemColor(stack, tintIndex);
    }

    /**
     * @see Backend#renderPasses
     */
    public static List<BakedModel> renderPasses(
            BakedModel model, ItemStack stack, ItemDisplayContext context) {
        return backend.renderPasses(model, stack, context);
    }

    /** Installs the loader-specific implementation; called once during client setup. */
    public static void setBackend(Backend backend) {
        ClientRenderPlatform.backend = backend;
    }
}
