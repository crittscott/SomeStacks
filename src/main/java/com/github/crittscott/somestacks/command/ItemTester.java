package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber
public final class ItemTester {
    private static String targetModId = "minecraft";

    private ItemTester() {}

    public static void setTargetModId(String modId) {
        targetModId = modId;
    }

    public static String getTargetModId() {
        return targetModId;
    }

    public static List<String> getModIdsWithItems() {
        return ForgeRegistries.ITEMS.getEntries().stream()
                .map(entry -> entry.getKey().location().getNamespace())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();

        if (level.isClientSide) {
            return;
        }

        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!held.is(Items.STICK)) {
            return;
        }

        if (!player.isShiftKeyDown()) {
            return;
        }

        if (ServerConfig.DISABLE_MODS.get().contains(targetModId)) {
            player.displayClientMessage(Component.literal("Mod '" + targetModId + "' is disabled in config"), false);
            event.setCanceled(true);
            return;
        }

        List<Item> modItems = collectModItems(targetModId);

        if (modItems.isEmpty()) {
            player.displayClientMessage(Component.literal("Mod '" + targetModId + "' not loaded or has no items"), false);
            return;
        }

        BlockPos startPos = event.getPos().above();
        int stacksCreated = createStorageStacks(level, startPos, modItems);

        player.displayClientMessage(
                Component.literal("Created " + stacksCreated + " StorageStacks with " + modItems.size() + " items from '" + targetModId + "'"),
                false
        );

        event.setCanceled(true);
    }

    public static List<Item> collectModItems(String modId) {
        List<Item> items = new ArrayList<>();

        ForgeRegistries.ITEMS.getEntries().forEach(entry -> {
            if (entry.getKey().location().getNamespace().equals(modId)) {
                items.add(entry.getValue());
            }
        });

        return items;
    }

    public static int createStorageStacks(Level level, BlockPos startPos, List<Item> items) {
        int stacksCreated = 0;
        BlockPos currentPos = startPos;

        for (int i = 0; i < items.size(); i += 9) {
            BlockState stackState = ModRegistry.STORAGE_STACK_BLOCK.get().defaultBlockState();
            level.setBlock(currentPos, stackState, Block.UPDATE_ALL);

            var blockEntity = level.getBlockEntity(currentPos);
            if (blockEntity instanceof StorageStackBE sbe) {
                int endIndex = Math.min(i + 9, items.size());
                for (int j = i; j < endIndex; j++) {
                    Item item = items.get(j);
                    ItemStack stack = new ItemStack(item, 1);
                    sbe.deposit(stack);
                }
                stacksCreated++;
            }

            currentPos = currentPos.north();
        }

        return stacksCreated;
    }
}
