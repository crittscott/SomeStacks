package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.util.StackItemStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;

import java.util.ArrayList;
import java.util.List;

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
    private static final String ITEM_COUNT_FIELD = "count";
    private static final String[] PERSISTED_FIELDS = {
            StackBlockEntity.TAG_DATA_VERSION, StackBlockEntity.TAG_ITEMS, StorageStackBE.TAG_PERMANENT,
            SinglesStackBE.TAG_CUBE_ROTATIONS, StackItemStorage.TAG_SET_ASIDE
    };

    /** Copies only stack-owned fields at the value-I/O boundary, without typed-list filtering. */
    static CompoundTag readTag(ValueInput input) {
        CompoundTag tag = new CompoundTag();
        for (String field : PERSISTED_FIELDS) {
            input.read(field, RAW_TAG).ifPresent(value -> tag.put(field, value.copy()));
        }
        return tag;
    }

    /**
     * Splits the preceding release's over-cap Storage entries before normal inventory loading.
     * Occupied slots keep priority. Overflow uses free local slots, then the existing SetAside
     * envelope with its original slot for retry; no world edits or item admission are involved.
     */
    static boolean normalizeStorageInventory(CompoundTag inventory, HolderLookup.Provider registries) {
        ListTag original = inventory.getListOrEmpty(StackItemStorage.TAG_ITEMS);
        ListTag retries = inventory.getListOrEmpty(StackItemStorage.TAG_SET_ASIDE);
        boolean overCap = false;
        for (ListTag list : List.of(original, retries)) {
            for (Tag entry : list) {
                if (entry instanceof CompoundTag item
                        && item.getInt(ITEM_COUNT_FIELD).orElse(1) > StackItemStorage.STORAGE_SLOT_LIMIT) {
                    overCap = true;
                    break;
                }
            }
        }
        if (!overCap) return false;
        boolean[] occupied = new boolean[StorageStackBE.SLOTS];
        for (Tag entry : original) {
            if (entry instanceof CompoundTag item && item.get(StackItemStorage.TAG_SLOT) instanceof IntTag slot
                    && slot.intValue() >= 0 && slot.intValue() < occupied.length) occupied[slot.intValue()] = true;
        }
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        ListTag items = new ListTag();
        ListTag retained = new ListTag();
        List<Tag> extraItems = new ArrayList<>();
        boolean changed = false;
        for (int source = 0; source < 2; source++) {
            for (Tag entry : source == 0 ? original : retries) {
                ListTag destination = source == 0 ? items : retained;
                if (!(entry instanceof CompoundTag item)
                        || !(item.get(StackItemStorage.TAG_SLOT) instanceof IntTag slotTag)
                        || slotTag.intValue() < 0 || slotTag.intValue() >= occupied.length) {
                    destination.add(entry.copy());
                    continue;
                }
                var parsed = ItemStack.CODEC.parse(ops, item).result();
                if (parsed.isEmpty() || parsed.get().isEmpty()
                        || parsed.get().getCount() <= StackItemStorage.storageSlotLimit(parsed.get())) {
                    destination.add(entry.copy());
                    continue;
                }
                ItemStack stack = parsed.get();
                int limit = StackItemStorage.storageSlotLimit(stack);
                int remaining = stack.getCount();
                occupied[slotTag.intValue()] = true;
                boolean first = true;
                while (remaining > 0) {
                    int count = Math.min(remaining, limit);
                    Tag encoded;
                    try {
                        encoded = ItemStack.CODEC.encodeStart(ops, stack.copyWithCount(count)).getOrThrow();
                    } catch (RuntimeException failure) {
                        throw new IllegalStateException("Cannot normalize saved Storage slot " + slotTag.intValue(), failure);
                    }
                    if (!(encoded instanceof CompoundTag piece)) {
                        throw new IllegalStateException("Storage item codec did not produce a compound for slot " + slotTag.intValue());
                    }
                    int slot = slotTag.intValue();
                    if (first) {
                        piece.putInt(StackItemStorage.TAG_SLOT, slot);
                        destination.add(piece);
                        first = false;
                    } else {
                        int free = 0;
                        while (free < occupied.length && occupied[free]) free++;
                        if (free < occupied.length) {
                            occupied[free] = true;
                            piece.putInt(StackItemStorage.TAG_SLOT, free);
                            extraItems.add(piece);
                        } else {
                            piece.putInt(StackItemStorage.TAG_SLOT, slot);
                            retained.add(piece);
                        }
                    }
                    remaining -= count;
                }
                changed = true;
            }
        }
        if (changed) {
            extraItems.forEach(items::add);
            inventory.put(StackItemStorage.TAG_ITEMS, items);
            if (retained.isEmpty()) inventory.remove(StackItemStorage.TAG_SET_ASIDE);
            else inventory.put(StackItemStorage.TAG_SET_ASIDE, retained);
        }
        return changed;
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
