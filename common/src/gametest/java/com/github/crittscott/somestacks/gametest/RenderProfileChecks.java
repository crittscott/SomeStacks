package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.client.ItemRenderOverrides;
import com.github.crittscott.somestacks.client.RenderProfile;
import com.github.crittscott.somestacks.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.renderconfig.RenderMode;
import com.github.crittscott.somestacks.renderconfig.RenderOffset;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/** Development-client checks through the real profile resolution and mutation entry points. */
public final class RenderProfileChecks extends ItemRenderOverrides {
    private RenderProfileChecks() {}

    /**
     * To reproduce in-game: give Stone distinct user and resource-pack profiles, edit the user
     * profile, reset it, and reload a changed resource pack. Each change immediately updates the
     * displayed mode, scale, and offset. A user entry containing only a mode uses scale one and
     * zero offset, rather than inheriting the pack's fields. Run /ssclienttest to check these
     * transitions without writing files; all in-memory overrides are restored afterward.
     */
    public static void run() {
        ItemStack stack = new ItemStack(Items.STONE);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(Items.STONE);
        // Resolving loads the existing user layer before its selected entry is saved for restoration.
        ItemRenderOverrides.resolve(stack);
        ItemRenderConfig originalUser = userEntry(id);
        Map<ResourceLocation, ItemRenderConfig> originalResources = new HashMap<>(CONFIG_MAP);
        RenderProfileChecks listener = new RenderProfileChecks();
        ItemRenderConfig resource = new ItemRenderConfig(
                RenderMode.GUI, 0.8f, new RenderOffset(0.1f, 0.2f, 0.3f));
        ItemRenderConfig user = new ItemRenderConfig(
                RenderMode.TWO_D, 0.6f, new RenderOffset(-0.1f, -0.2f, -0.3f));
        try {
            Map<ResourceLocation, ItemRenderConfig> resources = new HashMap<>(originalResources);
            resources.put(id, resource);
            listener.applyResources(resources);
            putUser(id, user);
            assertProfile(stack, user, "User precedence");

            ItemRenderConfig changed = new ItemRenderConfig(
                    RenderMode.THREE_D, 0.9f, new RenderOffset(0.2f, 0.0f, -0.2f));
            putUser(id, changed);
            assertProfile(stack, changed, "User edit invalidation");

            putUser(id, new ItemRenderConfig(RenderMode.BLOCK, null, null));
            assertProfile(stack, new ItemRenderConfig(RenderMode.BLOCK, 1.0f, RenderOffset.ZERO),
                    "Partial user entry owns the complete profile");

            removeUser(id);
            assertProfile(stack, resource, "User removal invalidation");

            ItemRenderConfig replacement = new ItemRenderConfig(
                    RenderMode.THREE_D, 0.4f, new RenderOffset(0.0f, 0.1f, 0.0f));
            resources.put(id, replacement);
            listener.applyResources(resources);
            assertProfile(stack, replacement, "Resource replacement invalidation");
        } finally {
            listener.applyResources(originalResources);
            if (originalUser == null) removeUser(id);
            else putUser(id, originalUser);
        }
    }

    private void applyResources(Map<ResourceLocation, ItemRenderConfig> resources) {
        apply(resources, Minecraft.getInstance().getResourceManager(), InactiveProfiler.INSTANCE);
    }

    private static void assertProfile(ItemStack stack, ItemRenderConfig expected, String message) {
        RenderProfile actual = resolve(stack);
        checkEquals(new RenderProfile(expected.mode(), expected.scale(), expected.offset()), actual, message);
    }

    /** Reads only the restoration value; production mutation still goes through putUser/removeUser. */
    @SuppressWarnings("unchecked")
    private static ItemRenderConfig userEntry(ResourceLocation id) {
        try {
            Field field = ItemRenderOverrides.class.getDeclaredField("USER_OVERRIDES");
            field.setAccessible(true);
            return ((Map<ResourceLocation, ItemRenderConfig>) field.get(null)).get(id);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not preserve the user render override", failure);
        }
    }
}
