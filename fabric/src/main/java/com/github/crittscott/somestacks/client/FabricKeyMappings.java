package com.github.crittscott.somestacks.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Fabric registration for the shared gesture modifier. */
public final class FabricKeyMappings {
    private FabricKeyMappings() {}

    public static final KeyMapping STACK_MODE_KEY = new KeyMapping(
            "key.somestacks.stack_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.somestacks");

    public static void init() {
        KeyBindingHelper.registerKeyBinding(STACK_MODE_KEY);
    }
}
