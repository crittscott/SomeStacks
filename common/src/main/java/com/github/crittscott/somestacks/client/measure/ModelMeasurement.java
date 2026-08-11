package com.github.crittscott.somestacks.client.measure;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/** Loader-neutral access to model geometry measurement. */
public final class ModelMeasurement {
    private ModelMeasurement() {}

    public record Result(
            boolean customRenderer, boolean gui3d,
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
