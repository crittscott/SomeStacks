package com.github.crittscott.somestacks.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Fabric registration for the shared gesture modifier. */
public final class FabricKeyMappings {
    private FabricKeyMappings() {}

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(ClientGestures.KEY_CATEGORY_ID);

    public static final KeyMapping STACK_MODE_KEY = new KeyMapping(
            ClientGestures.STACK_MODE_KEY_TRANSLATION_KEY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY);

    public static void init() {
        KeyMappingHelper.registerKeyMapping(STACK_MODE_KEY);
    }
}
