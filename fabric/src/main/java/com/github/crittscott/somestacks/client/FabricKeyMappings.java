package com.github.crittscott.somestacks.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

/** Fabric registration for the shared gesture modifier. */
public final class FabricKeyMappings {
    private FabricKeyMappings() {}

    public static final KeyMapping STACK_MODE_KEY = new KeyMapping(
            ClientGestures.STACK_MODIFIER_KEY_ID,
            InputConstants.Type.KEYSYM,
            ClientGestures.DEFAULT_STACK_MODIFIER_KEY,
            ClientGestures.KEY_CATEGORY);

    public static void init() {
        KeyBindingHelper.registerKeyBinding(STACK_MODE_KEY);
    }
}
