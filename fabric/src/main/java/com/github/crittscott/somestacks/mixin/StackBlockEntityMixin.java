package com.github.crittscott.somestacks.mixin;

import com.github.crittscott.somestacks.block.FabricRunItemStorage;
import com.github.crittscott.somestacks.block.FabricStorageOwner;
import com.github.crittscott.somestacks.block.StackBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Keeps the Fabric adapter on its owning block entity, outside common code. Transfer API callers on
 * every side of a block must share one {@link FabricRunItemStorage}, a {@code SnapshotParticipant},
 * so a transaction has one ledger per entity; that needs a field whose lifetime is the entity's.
 * Common code cannot declare it, because the type is Fabric-only.
 *
 * <p>The expected alternative, a Fabric subclass of each block entity holding the field, is not
 * used because it costs three Fabric-only subclasses plus a change to how the common blocks build
 * their entities: {@code newBlockEntity} constructs the common classes directly, so it would have
 * to go through each loader's registered type instead. A side map keyed by entity would need its
 * own removal and chunk-unload cleanup. This Mixin targets only the mod's own class ({@code remap =
 * false}), adds one field and one accessor, and leaves every vanilla path untouched.
 */
@Mixin(value = StackBlockEntity.class, remap = false)
public abstract class StackBlockEntityMixin implements FabricStorageOwner {
    @Unique private FabricRunItemStorage someStacksStorage;

    @Override
    public FabricRunItemStorage someStacksStorage() {
        if (someStacksStorage == null) {
            someStacksStorage = new FabricRunItemStorage((StackBlockEntity) (Object) this);
        }
        return someStacksStorage;
    }
}
