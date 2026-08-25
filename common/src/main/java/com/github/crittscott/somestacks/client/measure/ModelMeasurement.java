package com.github.crittscott.somestacks.client.measure;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/** Loader-neutral access to model geometry measurement. */
public final class ModelMeasurement {
    private ModelMeasurement() {}

    /**
     * @param gui3d the resolved baked model's own dimensionality signal
     * @param flatProjectionAvailable whether the shared 2-D renderer can draw the geometry that
     *                                produced these bounds
     */
    public record Result(
            boolean gui3d, boolean flatProjectionAvailable,
            @Nullable AABB bounds, @Nullable String failure) {}

    public interface Backend {
        Result measure(ItemStack stack);

        Result measureGui(ItemStack stack);
    }

    private static Backend backend;

    public static void setBackend(Backend backend) {
        ModelMeasurement.backend = backend;
    }

    public static Result measure(ItemStack stack) {
        return backend.measure(stack);
    }

    public static Result measureGui(ItemStack stack) {
        return backend.measureGui(stack);
    }
}
