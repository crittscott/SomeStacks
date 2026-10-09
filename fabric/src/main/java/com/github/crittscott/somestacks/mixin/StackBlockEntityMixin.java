package com.github.crittscott.somestacks.mixin;

import com.github.crittscott.somestacks.block.FabricRunItemStorage;
import com.github.crittscott.somestacks.block.FabricStorageOwner;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Keeps the Fabric adapter on its owning block entity, outside common code. */
@Mixin(value = StackBlockEntity.class, remap = false)
public abstract class StackBlockEntityMixin implements FabricStorageOwner {
    @Unique
    private FabricRunItemStorage someStacksStorage;

    @Override
    public FabricRunItemStorage someStacksStorage() {
        if (someStacksStorage == null) {
            someStacksStorage = new FabricRunItemStorage((StackBlockEntity) (Object) this);
        }
        return someStacksStorage;
    }
}
