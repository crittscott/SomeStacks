package com.github.crittscott.somestacks.client;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class KeyMappings {
    private KeyMappings(){}

    public static final KeyMapping STACK_MODE_KEY = new KeyMapping(
            "key.somestacks.stack_mode",
            GLFW.GLFW_KEY_V,
            "key.categories.gameplay"
    );
}
