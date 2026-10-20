package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.client.ItemRenderOverrides;
import com.github.crittscott.somestacks.client.RenderProfile;
import com.github.crittscott.somestacks.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.renderconfig.RenderMode;
import com.github.crittscott.somestacks.renderconfig.RenderOffset;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
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
     * transitions against scratch layers; the player's overrides and files are restored untouched.
     */
    public static void run() {
        ItemStack stack = new ItemStack(Items.STONE);
        Identifier id = BuiltInRegistries.ITEM.getKey(Items.STONE);
        RenderProfileChecks listener = new RenderProfileChecks();
        ItemRenderConfig resource = new ItemRenderConfig(
                RenderMode.GUI, 0.8f, new RenderOffset(0.1f, 0.2f, 0.3f));
        ItemRenderConfig user = new ItemRenderConfig(
                RenderMode.TWO_D, 0.6f, new RenderOffset(-0.1f, -0.2f, -0.3f));
        Path directory = null;
        try {
            directory = Files.createTempDirectory("somestacks-profiles");
            try (ItemRenderOverrides.Sandbox ignored = new ItemRenderOverrides.Sandbox(directory)) {
                listener.applyResources(Map.of(id, resource));
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
                listener.applyResources(Map.of(id, replacement));
                assertProfile(stack, replacement, "Resource replacement invalidation");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Profile precedence check failed: " + e.getMessage(), e);
        } finally {
            if (directory != null) {
                try (var files = Files.walk(directory)) {
                    files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
                } catch (IOException ignored) {
                    // The scratch directory is in the system temp area.
                }
            }
        }
    }

    private void applyResources(Map<Identifier, ItemRenderConfig> resources) {
        apply(resources, Minecraft.getInstance().getResourceManager(), InactiveProfiler.INSTANCE);
    }

    private static void assertProfile(ItemStack stack, ItemRenderConfig expected, String message) {
        checkEquals(new RenderProfile(expected.mode(), expected.scale(), expected.offset()), resolve(stack), message);
    }
}
