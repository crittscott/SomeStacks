package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;

/** Gives the shared sound-data loader the identity Fabric's reload API requires. */
public final class FabricStackSoundData extends StackSoundData
        implements IdentifiableResourceReloadListener {
    private static final ResourceLocation ID =
            new ResourceLocation(SomeStacksCommon.MODID, "stack_sounds");

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }
}
