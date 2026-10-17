package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.util.StackItemStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;

/**
 * Brings a stack block entity's saved tag up to the running game's data version before the block
 * entity reads any of it. Vanilla's chunk upgrade does not reach into block entity types it does
 * not know, so the item stacks these block entities hold are run through the DataFixerUpper here.
 *
 * <p>Every save carries a {@code DataVersion}. The preceding release, Some Stacks 0.8.3 for Minecraft
 * 26.1, writes the current field layout and block states, so only its saved items need upgrading. A tag without a
 * {@code DataVersion} is read as current; any entry that then fails to decode is set aside by the
 * inventory loader rather than discarded.
 */
public final class StackDataMigration {
    // Read raw values so malformed inventory entries and wire field types remain observable.
    private static final Codec<Tag> RAW_TAG = Codec.PASSTHROUGH.xmap(
            dynamic -> dynamic.convert(NbtOps.INSTANCE).getValue(),
            tag -> new Dynamic<>(NbtOps.INSTANCE, tag));
    private static final String[] PERSISTED_FIELDS = {
            "DataVersion", "Items", "Permanent", "CubeRotations", "SetAside"
    };

    /** Copies only stack-owned fields at the value-I/O boundary, without typed-list filtering. */
    static CompoundTag readTag(ValueInput input) {
        CompoundTag tag = new CompoundTag();
        for (String field : PERSISTED_FIELDS) {
            input.read(field, RAW_TAG).ifPresent(value -> tag.put(field, value.copy()));
        }
        return tag;
    }

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
        int current = SharedConstants.getCurrentVersion().dataVersion().version();
        int saved = NbtUtils.getDataVersion(tag, current);
        if (saved >= current) {
            return false;
        }
        if (tag.get(StackBlockEntity.TAG_ITEMS) instanceof CompoundTag storage) {
            upgradeItemList(storage.getListOrEmpty(StackItemStorage.TAG_ITEMS), registries, saved, current);
            upgradeItemList(storage.getListOrEmpty(StackItemStorage.TAG_SET_ASIDE), registries, saved, current);
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
            if (!(list.get(i) instanceof CompoundTag itemTag)) continue;
            Tag slot = itemTag.get(StackItemStorage.TAG_SLOT);
            Dynamic<Tag> fixed = DataFixers.getDataFixer().update(
                    References.ITEM_STACK, new Dynamic<>(ops, itemTag), from, to);
            CompoundTag fixedTag = (CompoundTag) fixed.getValue();
            if (slot != null) fixedTag.put(StackItemStorage.TAG_SLOT, slot.copy());
            list.set(i, fixedTag);
        }
    }
}
