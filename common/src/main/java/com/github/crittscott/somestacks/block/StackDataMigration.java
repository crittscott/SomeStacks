package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.util.QuarterTurns;
import com.github.crittscott.somestacks.util.StackItemStorage;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;

/**
 * Brings a stack block entity's saved tag up to the running game's data version before the block
 * entity reads any of it. Vanilla's chunk upgrade does not reach into block entity types it does
 * not know, so the item stacks these block entities hold are run through the DataFixerUpper here.
 *
 * <p>Every save carries a {@code DataVersion}. Unversioned saves use either Data Components or
 * the preceding release's 1.20.1 item NBT; each item identifies that format by its count field.
 * The immediately preceding release also stored an
 * outer block's {@code Rotation} in block-entity data; that field is consumed into block state on
 * load and is never written again.
 */
public final class StackDataMigration {
    /** Data version of Minecraft 1.21.1, assumed for tags saved without one. */
    private static final int UNVERSIONED_DATA_VERSION = 3955;
    private static final int LEGACY_ITEM_DATA_VERSION = 3465;
    private static final String LEGACY_TAG_ROTATION = "Rotation";

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
            SomeStacksCommon.LOGGER.info("Normalized {} saved Some Stacks block(s) for data version {}",
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
        boolean unversioned = !tag.contains("DataVersion", Tag.TAG_INT);
        if (!unversioned && saved >= current && !hasLegacyItems(tag)) {
            return false;
        }
        if (tag.contains(StackBlockEntity.TAG_ITEMS, Tag.TAG_COMPOUND)) {
            CompoundTag storage = tag.getCompound(StackBlockEntity.TAG_ITEMS);
            upgradeItemList(storage.getList(StackItemStorage.TAG_ITEMS, Tag.TAG_COMPOUND), registries, saved, current);
            upgradeItemList(storage.getList(StackItemStorage.TAG_SET_ASIDE, Tag.TAG_COMPOUND), registries, saved, current);
        }
        stampVersion(tag);
        recordUpgrade(current);
        return true;
    }

    /** Removes and returns the previous release's block-entity layout rotation, if present. */
    static Integer takeLegacyBlockRotation(CompoundTag tag) {
        if (!tag.contains(LEGACY_TAG_ROTATION, Tag.TAG_INT)) {
            return null;
        }
        int rotation = QuarterTurns.normalize(tag.getInt(LEGACY_TAG_ROTATION));
        tag.remove(LEGACY_TAG_ROTATION);
        return rotation;
    }

    private static synchronized void recordUpgrade(int to) {
        if (sessionUpgradeCount == 0) {
            SomeStacksCommon.LOGGER.info(
                    "Normalizing saved Some Stacks block data for data version {}", to);
        }
        sessionUpgradeCount++;
        sessionTargetVersion = to;
    }

    private static void upgradeItemList(ListTag list, HolderLookup.Provider registries, int from, int to) {
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag itemTag = list.getCompound(i);
            int itemVersion = itemTag.contains("Count", Tag.TAG_BYTE) && !itemTag.contains("count")
                    ? LEGACY_ITEM_DATA_VERSION : from;
            if (itemVersion >= to) continue;
            int slot = itemTag.getInt(StackItemStorage.TAG_SLOT);
            try {
                Dynamic<Tag> fixed = DataFixers.getDataFixer().update(
                        References.ITEM_STACK, new Dynamic<>(ops, itemTag.copy()), itemVersion, to);
                CompoundTag fixedTag = (CompoundTag) fixed.getValue();
                fixedTag.putInt(StackItemStorage.TAG_SLOT, slot);
                if (ItemStack.CODEC.parse(ops, fixedTag).result().isPresent()) {
                    list.set(i, fixedTag);
                }
            } catch (RuntimeException exception) {
                // Keep the original input so storage can retain it in SetAside for repair.
                SomeStacksCommon.LOGGER.warn("Could not upgrade saved item in slot {}; retaining its data", slot, exception);
            }
        }
    }

    private static boolean hasLegacyItems(CompoundTag tag) {
        CompoundTag storage = tag.getCompound(StackBlockEntity.TAG_ITEMS);
        for (String key : new String[]{StackItemStorage.TAG_ITEMS, StackItemStorage.TAG_SET_ASIDE}) {
            ListTag list = storage.getList(key, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag item = list.getCompound(i);
                if (item.contains("Count", Tag.TAG_BYTE) && !item.contains("count")) return true;
            }
        }
        return false;
    }
}
