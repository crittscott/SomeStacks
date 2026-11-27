package com.github.crittscott.somestacks.client.measure;

import com.github.crittscott.somestacks.client.ClientRenderPlatform;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** Measures ordinary quads exposed by Fabric item models after their display transform. */
public final class FabricModelMeasurer implements ModelMeasurement.Backend {
    private static final AABB SAFE_FALLBACK_BOUNDS = new AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5);

    @Override
    public ModelMeasurement.Result measure(ItemStack stack) {
        return measure(stack, ItemDisplayContext.FIXED, false);
    }

    @Override
    public ModelMeasurement.Result measureGui(ItemStack stack) {
        return measure(stack, ItemDisplayContext.GUI, true);
    }

    private static ModelMeasurement.Result measure(
            ItemStack stack, ItemDisplayContext context, boolean counterRotate) {
        boolean gui3d = true;
        try {
            BakedModel model =
                    Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0);
            gui3d = model.isGui3d();

            if (model.isCustomRenderer()) {
                return new ModelMeasurement.Result(gui3d, false, SAFE_FALLBACK_BOUNDS);
            }

            PoseStack pose = new PoseStack();
            if (counterRotate) {
                ClientRenderPlatform.applyGuiCounterRotation(pose);
            }
            model.getTransforms().getTransform(context).apply(false, pose);
            pose.translate(-0.5f, -0.5f, -0.5f);
            AABB bounds = ModelMeasurement.quadBounds(stack, model, context, pose);
            if (bounds == null) {
                // A model with no directly exposed geometry may draw only through Fabric's
                // enhanced item-quad path. ItemRenderer remains safe; the shared 2-D projection
                // would be blank.
                return new ModelMeasurement.Result(gui3d, false, SAFE_FALLBACK_BOUNDS);
            }
            return new ModelMeasurement.Result(gui3d, true, bounds);
        } catch (Exception e) {
            ModelMeasurement.reportFailure(stack, context, e);
            return new ModelMeasurement.Result(gui3d, false, null);
        }
    }
}
