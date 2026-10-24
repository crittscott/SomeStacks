package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.client.ClientRenderCommands;
import com.github.crittscott.somestacks.client.FabricClientEvents;
import com.github.crittscott.somestacks.client.FabricClientRendering;
import com.github.crittscott.somestacks.client.FabricKeyMappings;
import com.github.crittscott.somestacks.command.ClientRenderCommandSyntax;
import com.github.crittscott.somestacks.network.FabricClientNetworking;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/** Fabric's client entry point. */
public final class SomeStacksFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricClientRendering.init();
        FabricKeyMappings.init();
        FabricClientNetworking.init();
        FabricClientEvents.init();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            ClientRenderCommands.register(
                    dispatcher, new ClientRenderCommands.Feedback<FabricClientCommandSource>() {
                        @Override
                        public void success(
                                FabricClientCommandSource source,
                                Component message) {
                            source.sendFeedback(message);
                        }

                        @Override
                        public void failure(
                                FabricClientCommandSource source,
                                Component message) {
                            source.sendError(message);
                        }
                    });
            registerServerPassThrough(dispatcher);
        });
    }

    /**
     * Fabric runs every command whose root is in the client dispatcher and sends it to the server
     * only when the client parse ends in an unknown-command or parse error. The client {@code ss}
     * tree holds only the local subcommands, so any other {@code ss} form would otherwise fail on
     * the client. Literal children win over this argument, so it catches only the server forms and
     * reports them unknown, which hands the command to vanilla's ordinary send.
     */
    private static void registerServerPassThrough(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal(ClientRenderCommandSyntax.ROOT)
                .then(ClientCommandManager.argument("server_command", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create();
                        })));
    }
}
