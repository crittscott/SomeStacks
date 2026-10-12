package com.github.crittscott.somestacks.block;

/** Fabric-only storage attached to a stack block entity for its lifetime. */
public interface FabricStorageOwner {
    FabricRunItemStorage someStacksStorage();
}
