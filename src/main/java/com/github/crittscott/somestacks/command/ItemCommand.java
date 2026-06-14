package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.network.ModNetworking;
import com.github.crittscott.somestacks.network.RenderOverridePkt;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.stream.Collectors;

public final class ItemCommand {
    private ItemCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("somestacks")
                        .requires(source -> {
                            if (source.getEntity() instanceof ServerPlayer player) {
                                return player.isCreative();
                            }
                            return false;
                        })
                        .then(Commands.literal("item")
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> {
                                            String remaining = builder.getRemaining();

                                            if (!remaining.contains(":")) {
                                                // Stage 1: Suggest namespaces
                                                List<String> namespaces = ItemTester.getModIdsWithItems()
                                                        .stream()
                                                        .map(ns -> ns + ":")
                                                        .collect(Collectors.toList());
                                                return SharedSuggestionProvider.suggest(namespaces, builder);
                                            } else {
                                                // Stage 2: Suggest items from namespace
                                                String namespace = remaining.split(":")[0];
                                                List<String> itemIds = ItemTester.collectModItems(namespace)
                                                        .stream()
                                                        .map(item -> ForgeRegistries.ITEMS.getKey(item).toString())
                                                        .collect(Collectors.toList());
                                                return SharedSuggestionProvider.suggest(itemIds, builder);
                                            }
                                        })
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    builder.suggest("2d");
                                                    builder.suggest("3d");
                                                    builder.suggest("block");
                                                    builder.suggest("gui");
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("scale", FloatArgumentType.floatArg())
                                                        .then(Commands.argument("x", FloatArgumentType.floatArg())
                                                                .then(Commands.argument("y", FloatArgumentType.floatArg())
                                                                        .then(Commands.argument("z", FloatArgumentType.floatArg())
                                                                                .executes(ctx -> {
                                                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();

                                                                                    ResourceLocation itemId = ResourceLocationArgument.getId(ctx, "item");
                                                                                    String mode = StringArgumentType.getString(ctx, "mode");
                                                                                    float scale = FloatArgumentType.getFloat(ctx, "scale");
                                                                                    float x = FloatArgumentType.getFloat(ctx, "x");
                                                                                    float y = FloatArgumentType.getFloat(ctx, "y");
                                                                                    float z = FloatArgumentType.getFloat(ctx, "z");

                                                                                    float[] offset = new float[]{x, y, z};
                                                                                    RenderOverridePkt packet = new RenderOverridePkt(itemId, mode, scale, offset);
                                                                                    ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);

                                                                                    player.sendSystemMessage(Component.literal(
                                                                                            "Set render override for " + itemId + ": mode=" + mode +
                                                                                                    ", scale=" + scale + ", offset=[" + x + "," + y + "," + z + "]"
                                                                                    ));

                                                                                    return 1;
                                                                                })
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
        );
    }
}
