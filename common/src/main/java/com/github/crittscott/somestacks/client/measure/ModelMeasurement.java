package com.github.crittscott.somestacks.client.measure;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

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
            @Nullable AABB bounds) {}

    public interface Backend {
        Result measure(ItemStack stack);

        Result measureGui(ItemStack stack);
    }

    private static Backend backend;
    private static final Set<Item> REPORTED_FAILURES = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Reports exceptional model failures once per item until resources are reloaded. */
    public static void reportFailure(ItemStack stack, ItemDisplayContext context, Exception exception) {
        if (REPORTED_FAILURES.add(stack.getItem())) {
            SomeStacksCommon.LOGGER.warn("Item model measurement failed for {} in {}; using fallback presentation",
                    BuiltInRegistries.ITEM.getKey(stack.getItem()), context, exception);
        }
    }

    /** New models may fail differently after a resource reload. */
    public static void onResourceReload() {
        REPORTED_FAILURES.clear();
    }

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
