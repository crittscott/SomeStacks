package com.github.crittscott.somestacks.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/** The mod's key bindings. */
public final class NeoForgeKeyMappings {
    private NeoForgeKeyMappings() {}

    /** The mod's key-binding category, registered through {@code RegisterKeyMappingsEvent}. */
    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(ClientGestures.KEY_CATEGORY_ID);

    /**
     * The stack modifier, {@code V} by default and rebindable. Held rather than pressed: every
     * gesture asks whether it is down at the moment of the click.
     */
    public static final KeyMapping STACK_MODE_KEY = new KeyMapping(
            ClientGestures.STACK_MODE_KEY_TRANSLATION_KEY,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_V,
            CATEGORY
    );
}
