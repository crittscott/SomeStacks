package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.util.StackItemStorage;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;

/**
 * Brings a stack block entity's saved tag up to the running game's data version before the block
 * entity reads any of it. Vanilla's chunk upgrade does not reach into block entity types it does
 * not know, so the item stacks these block entities hold are run through the DataFixerUpper here.
 *
 * <p>Every save carries a {@code DataVersion}. A tag without one was written by Some Stacks for
 * Minecraft 1.21.1, which did not record it. Only the item stacks need fixing; rotations and
 * permanence are plain numbers whose format has not changed.
 */
public final class StackDataMigration {
    /** Data version of Minecraft 1.21.1, assumed for tags saved without one. */
    private static final int UNVERSIONED_DATA_VERSION = 3955;

    private static long sessionUpgradeCount;
    private static int sessionTargetVersion;

    private StackDataMigration() {
    }

    /** Starts migration accounting for a server session. */
    public static synchronized void beginSession() {
        sessionUpgradeCount = 0;
        sessionTargetVersion = 0;
    }

    /** Logs the completed migration count, if this server session upgraded any block data. */
    public static synchronized void endSession() {
        if (sessionUpgradeCount > 0) {
            SomeStacksCommon.LOGGER.info("Upgraded {} Some Stacks block(s) to data version {}",
                    sessionUpgradeCount, sessionTargetVersion);
        }
        sessionUpgradeCount = 0;
        sessionTargetVersion = 0;
    }

    /** Stamps the running game's data version on a tag being saved. */
    static void stampVersion(CompoundTag tag) {
        NbtUtils.addCurrentDataVersion(tag);
    }

    /**
     * Upgrades {@code tag} in place to the running game's data version. Returns whether anything
     * changed, so the caller can mark its block entity dirty and persist the upgraded form.
     */
    static boolean upgrade(CompoundTag tag, HolderLookup.Provider registries) {
        int saved = NbtUtils.getDataVersion(tag, UNVERSIONED_DATA_VERSION);
        int current = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        if (saved >= current) {
            return false;
        }
        if (tag.contains(StackBlockEntity.TAG_ITEMS, Tag.TAG_COMPOUND)) {
            CompoundTag storage = tag.getCompound(StackBlockEntity.TAG_ITEMS);
            upgradeItemList(storage.getList(StackItemStorage.TAG_ITEMS, Tag.TAG_COMPOUND), registries, saved, current);
            upgradeItemList(storage.getList(StackItemStorage.TAG_SET_ASIDE, Tag.TAG_COMPOUND), registries, saved, current);
        }
        stampVersion(tag);
        recordUpgrade(saved, current);
        return true;
    }

    private static synchronized void recordUpgrade(int from, int to) {
        if (sessionUpgradeCount == 0) {
            SomeStacksCommon.LOGGER.info(
                    "Upgrading Some Stacks block data from data version {} to {}", from, to);
        }
        sessionUpgradeCount++;
        sessionTargetVersion = to;
    }

    private static void upgradeItemList(ListTag list, HolderLookup.Provider registries, int from, int to) {
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag itemTag = list.getCompound(i);
            int slot = itemTag.getInt(StackItemStorage.TAG_SLOT);
            Dynamic<Tag> fixed = DataFixers.getDataFixer().update(
                    References.ITEM_STACK, new Dynamic<>(ops, itemTag), from, to);
            CompoundTag fixedTag = (CompoundTag) fixed.getValue();
            fixedTag.putInt(StackItemStorage.TAG_SLOT, slot);
            list.set(i, fixedTag);
        }
    }
}
