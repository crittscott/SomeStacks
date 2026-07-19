package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.TestModsConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestCommand {
    /** Columns between the rows of adjacent mods. */
    private static final int MOD_SPACING = 2;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("somestacks")
                        .requires(source -> {
                            if (source.getEntity() instanceof ServerPlayer player) {
                                return player.isCreative();
                            }
                            return false;
                        })
                        .then(Commands.literal("test")
                                .executes(TestCommand::executeConfigured)
                                .then(Commands.argument("modid", StringArgumentType.string())
                                        .suggests((context, builder) ->
                                                SharedSuggestionProvider.suggest(
                                                        ItemTester.getModIdsWithItems(),
                                                        builder
                                                )
                                        )
                                        .executes(TestCommand::executeSingle)
                                )
                        )
        );
    }

    private static int executeSingle(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requireCreativePlayer(context);
        if (player == null) {
            return 0;
        }

        String modId = StringArgumentType.getString(context, "modid");

        if (ItemTester.collectModItems(modId).isEmpty()) {
            context.getSource().sendFailure(
                    Component.literal(modId + " is not loaded or has no items")
            );
            return 0;
        }

        if (ServerConfig.DISABLE_MODS.get().contains(modId)) {
            context.getSource().sendFailure(
                    Component.literal(modId + " is disabled in server config")
            );
            return 0;
        }

        return generate(context, player, List.of(modId), List.of());
    }

    private static int executeConfigured(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requireCreativePlayer(context);
        if (player == null) {
            return 0;
        }

        List<String> configuredMods = TestModsConfig.loadModList();

        if (configuredMods.isEmpty()) {
            context.getSource().sendFailure(
                    Component.literal("No mods configured in testmods.json")
            );
            return 0;
        }

        List<String> validMods = new ArrayList<>();
        for (String modId : configuredMods) {
            if (!ItemTester.collectModItems(modId).isEmpty()) {
                validMods.add(modId);
            }
        }

        Collections.sort(validMods);

        List<String> disabledMods = new ArrayList<>(ServerConfig.DISABLE_MODS.get());
        List<String> skippedMods = new ArrayList<>();
        validMods.removeIf(modId -> {
            if (disabledMods.contains(modId)) {
                skippedMods.add(modId);
                return true;
            }
            return false;
        });

        if (validMods.isEmpty()) {
            if (!skippedMods.isEmpty()) {
                context.getSource().sendFailure(
                        Component.literal("All configured mods are disabled in server config")
                );
            } else {
                context.getSource().sendFailure(
                        Component.literal("None of the configured mods have items")
                );
            }
            return 0;
        }

        return generate(context, player, validMods, skippedMods);
    }

    private static int generate(CommandContext<CommandSourceStack> context, ServerPlayer player,
                                List<String> modIds, List<String> skippedMods) {
        Level level = player.level();
        BlockPos basePos = player.blockPosition().east();

        Map<String, List<Item>> itemsByMod = new LinkedHashMap<>();
        int maxRows = 0;
        for (String modId : modIds) {
            List<Item> modItems = ItemTester.collectModItems(modId);
            itemsByMod.put(modId, modItems);
            maxRows = Math.max(maxRows, ItemTester.rowsFor(modItems.size()));
        }

        placeFloor(level, basePos, modIds.size(), maxRows);

        int totalStacks = 0;
        int expectedStacks = 0;
        int totalItems = 0;
        int modIndex = 0;

        for (List<Item> modItems : itemsByMod.values()) {
            BlockPos modStartPos = basePos.offset(modIndex * MOD_SPACING, 0, 0);
            totalStacks += ItemTester.createStorageStacks(level, modStartPos, modItems);
            expectedStacks += ItemTester.rowsFor(modItems.size());
            totalItems += modItems.size();
            modIndex++;
        }

        StringBuilder message = new StringBuilder("Created ")
                .append(totalStacks).append(" StorageStacks (")
                .append(totalItems).append(" items) for ");
        message.append(modIds.size() == 1 ? modIds.get(0) : modIds.size() + " mods");
        message.append(". Rows run north.");

        if (!skippedMods.isEmpty()) {
            message.append(" Skipped ").append(skippedMods.size())
                    .append(" disabled: ").append(String.join(", ", skippedMods)).append('.');
        }

        if (totalStacks < expectedStacks) {
            message.append(" WARNING: ").append(expectedStacks - totalStacks).append(" of ")
                    .append(expectedStacks).append(" placements failed; see log for positions and reasons.");
        }

        final String finalMessage = message.toString();
        context.getSource().sendSuccess(() -> Component.literal(finalMessage), true);

        return 1;
    }

    /**
     * Lays a smooth sandstone surface one level below the stacks, extending one block
     * past them on every side so the whole wall can be walked around and viewed against
     * a uniform background.
     */
    private static void placeFloor(Level level, BlockPos basePos, int modCount, int maxRows) {
        BlockState floor = Blocks.SMOOTH_SANDSTONE.defaultBlockState();

        int minX = basePos.getX() - 1;
        int maxX = basePos.getX() + (modCount - 1) * MOD_SPACING + 1;
        // Rows advance north, which is decreasing Z.
        int minZ = basePos.getZ() - maxRows;
        int maxZ = basePos.getZ() + 1;
        int y = basePos.getY() - 1;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.isOutsideBuildHeight(pos)) {
                    level.setBlock(pos, floor, Block.UPDATE_ALL);
                }
            }
        }
    }

    private static ServerPlayer requireCreativePlayer(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            return null;
        }
        if (!player.isCreative()) {
            context.getSource().sendFailure(
                    Component.literal("This command requires creative mode")
            );
            return null;
        }
        return player;
    }
}
