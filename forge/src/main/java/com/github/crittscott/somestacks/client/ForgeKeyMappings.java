package com.github.crittscott.somestacks.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** The mod's key bindings. */
public final class ForgeKeyMappings {
    private ForgeKeyMappings(){}

    /** The mod's key-binding category, registered with vanilla when this class loads. */
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(ClientGestures.KEY_CATEGORY_ID);

    /** Position within the category; the category holds only the stack modifier. */
    private static final int STACK_MODE_KEY_SORT_ORDER = 0;

    /**
     * The stack modifier, {@code V} by default and rebindable. Held rather than pressed: every
     * gesture asks whether it is down at the moment of the click.
     */
    public static final KeyMapping STACK_MODE_KEY = new KeyMapping(
            ClientGestures.STACK_MODE_KEY_TRANSLATION_KEY,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_V),
            CATEGORY,
            STACK_MODE_KEY_SORT_ORDER
    );
}
