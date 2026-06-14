package com.github.crittscott.somestacks.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.List;

public class ModCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("somestacks")
                        .requires(source -> {
                            if (source.getEntity() instanceof ServerPlayer player) {
                                return player.isCreative();
                            }
                            return false;
                        })
                        .then(Commands.literal("mod")
                                .then(Commands.argument("modid", StringArgumentType.string())
                                        .suggests((context, builder) ->
                                                SharedSuggestionProvider.suggest(
                                                        ItemTester.getModIdsWithItems(),
                                                        builder
                                                )
                                        )
                                        .executes(ModCommand::execute)
                                )
                        )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        String modId = StringArgumentType.getString(context, "modid");

        List<Item> modItems = ItemTester.collectModItems(modId);

        if (modItems.isEmpty()) {
            String currentMod = ItemTester.getTargetModId();
            context.getSource().sendFailure(
                    Component.literal(modId + " not loaded, testmod remains " + currentMod)
            );
            return 0;
        }

        ItemTester.setTargetModId(modId);

        context.getSource().sendSuccess(
                () -> Component.literal("Test mod set to: " + modId + " (" + modItems.size() + " items)"),
                true
        );

        return 1;
    }
}
