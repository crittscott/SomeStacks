package com.github.crittscott.somestacks.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;

/** The mod's key bindings. */
public final class KeyMappings {
    private KeyMappings(){}

    /**
     * The stack modifier, {@code V} by default and rebindable. Held rather than pressed: every
     * gesture asks whether it is down at the moment of the click.
     */
    public static final KeyMapping STACK_MODE_KEY = new KeyMapping(
            ClientGestures.STACK_MODIFIER_KEY_ID,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            ClientGestures.DEFAULT_STACK_MODIFIER_KEY,
            ClientGestures.KEY_CATEGORY
    );
}
