package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.TestModsConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestCommand {
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
                                .executes(TestCommand::execute)
                        )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            return 0;
        }

        if (!player.isCreative()) {
            context.getSource().sendFailure(
                    Component.literal("This command requires creative mode")
            );
            return 0;
        }

        Level level = player.level();

        List<String> configuredMods = TestModsConfig.loadModList();

        if (configuredMods.isEmpty()) {
            context.getSource().sendFailure(
                    Component.literal("No mods configured in testmods.json")
            );
            return 0;
        }

        List<String> validMods = new ArrayList<>();
        for (String modId : configuredMods) {
            List<Item> items = ItemTester.collectModItems(modId);
            if (!items.isEmpty()) {
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

        BlockPos playerPos = player.blockPosition();
        BlockPos basePos = playerPos.east();

        int totalStacks = 0;
        int modIndex = 0;

        for (String modId : validMods) {
            List<Item> modItems = ItemTester.collectModItems(modId);
            BlockPos modStartPos = basePos.offset(modIndex * 2, 0, 0);
            int stacksCreated = ItemTester.createStorageStacks(level, modStartPos, modItems);
            totalStacks += stacksCreated;
            modIndex++;
        }

        String messageText = "Created " + totalStacks + " StorageStacks for " + validMods.size() + " mods";

        if (!skippedMods.isEmpty()) {
            messageText += " (skipped " + skippedMods.size() + " disabled: " + String.join(", ", skippedMods) + ")";
        }

        final String finalMessage = messageText;
        context.getSource().sendSuccess(() -> Component.literal(finalMessage), true);

        return 1;
    }
}
