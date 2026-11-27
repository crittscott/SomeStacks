package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

import com.github.crittscott.somestacks.block.StorageStackBE;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What a stack block's update tag, the data every nearby client receives, carries about the items
 * it stores.
 */
public final class ClientUpdateChecks {
    private ClientUpdateChecks() {}

    /**
     * No in-game form: a player cannot see the update tag. Guards that a Storage Stack holding a
     * filled shulker box, a written book, and a patterned banner sends nearby clients the shulker
     * box without its contents and the book without its pages, but keeps the book's title and the
     * banner's patterns, which the renderer may use; that the stored stacks and the saved block
     * data keep everything; and that a client accepts the stripped update.
     */
    public static void updateTagOmitsContainerContentsAndBookPages(GameTestHelper helper) {
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        ListTag contents = new ListTag();
        CompoundTag diamonds = new ItemStack(Items.DIAMOND, 5).save(new CompoundTag());
        diamonds.putByte("Slot", (byte) 0);
        contents.add(diamonds);
        CompoundTag shulkerData = new CompoundTag();
        shulkerData.put("Items", contents);
        shulker.getOrCreateTag().put("BlockEntityTag", shulkerData);

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        ListTag pages = new ListTag();
        pages.add(StringTag.valueOf("{\"text\":\"page one\"}"));
        book.getOrCreateTag().put("pages", pages);
        book.getOrCreateTag().putString("title", "Ledger");
        book.getOrCreateTag().putString("author", "Clerk");

        ItemStack banner = new ItemStack(Items.WHITE_BANNER);
        ListTag patterns = new ListTag();
        CompoundTag border = new CompoundTag();
        border.putString("Pattern", "bo");
        border.putInt("Color", 14);
        patterns.add(border);
        CompoundTag bannerData = new CompoundTag();
        bannerData.put("Patterns", patterns);
        banner.getOrCreateTag().put("BlockEntityTag", bannerData);

        StorageStackBE storage = placeStorage(helper, ORIGIN);
        storage.getItems().setStackInSlot(0, shulker);
        storage.getItems().setStackInSlot(1, book);
        storage.getItems().setStackInSlot(2, banner);

        CompoundTag update = storage.getUpdateTag();
        ListTag sent = update.getCompound("Items").getList("Items", Tag.TAG_COMPOUND);
        checkEquals(3, sent.size(), "Items sent");
        CompoundTag sentShulker = itemData(sent, "minecraft:shulker_box");
        CompoundTag sentBook = itemData(sent, "minecraft:written_book");
        CompoundTag sentBanner = itemData(sent, "minecraft:white_banner");
        check(
                !sentShulker.getCompound("BlockEntityTag").contains("Items"),
                "Shulker box contents were sent");
        check(!sentBook.contains("pages"), "Book pages were sent");
        checkEquals("Ledger", sentBook.getString("title"), "Sent book title");
        check(
                sentBanner.getCompound("BlockEntityTag").contains("Patterns"),
                "Banner patterns were stripped");

        check(
                shulker.getTag().getCompound("BlockEntityTag").contains("Items"),
                "Stored shulker box lost its contents");
        check(book.getTag().contains("pages"), "Stored book lost its pages");
        ListTag saved =
                storage.saveWithoutMetadata()
                        .getCompound("Items")
                        .getList("Items", Tag.TAG_COMPOUND);
        check(
                itemData(saved, "minecraft:shulker_box")
                        .getCompound("BlockEntityTag")
                        .contains("Items"),
                "Saved shulker box lost its contents");
        check(itemData(saved, "minecraft:written_book").contains("pages"), "Saved book lost pages");

        StorageStackBE receiver = placeStorage(helper, ORIGIN.east());
        check(receiver.loadClientUpdate(update), "Client refused the stripped update");
        checkEquals(
                Items.SHULKER_BOX,
                receiver.getItems().getStackInSlot(0).getItem(),
                "Client shulker box");
        checkEquals(
                Items.WRITTEN_BOOK, receiver.getItems().getStackInSlot(1).getItem(), "Client book");
        helper.succeed();
    }

    /** The {@code tag} compound of the one serialized entry with this item id. */
    private static CompoundTag itemData(ListTag entries, String id) {
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.getString("id").equals(id)) {
                return entry.getCompound("tag");
            }
        }
        check(false, "No " + id + " entry");
        return new CompoundTag();
    }
}
