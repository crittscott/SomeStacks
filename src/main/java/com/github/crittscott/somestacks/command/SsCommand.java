package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.client.RenderMode;
import com.github.crittscott.somestacks.network.ModNetworking;
import com.github.crittscott.somestacks.network.RenderOverridePkt;
import com.github.crittscott.somestacks.network.WriteOverridesPkt;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * The {@code ss} command: render override authoring and test wall generation, for
 * creative-mode players.
 *
 * <ul>
 *   <li>{@code ss item <item> <mode> <scale> <x> <y> <z>} sets an entry in the issuing
 *       player's user override layer, applied immediately.</li>
 *   <li>{@code ss item <item> reset} removes that entry, restoring built-in or measured
 *       behavior.</li>
 *   <li>{@code ss test <modid|all>} generates Storage Stack walls of every item in the
 *       given namespace, or in all loaded namespaces.</li>
 *   <li>{@code ss write} asks the issuing player's client to write its user override
 *       layer to its override file.</li>
 * </ul>
 */
public final class SsCommand {
    private SsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("ss")
                        .requires(source -> source.getEntity() instanceof ServerPlayer player && player.isCreative())
                        .then(Commands.literal("item")
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .suggests(SsCommand::suggestItems)
                                        .then(Commands.literal("reset")
                                                .executes(SsCommand::resetItem))
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (RenderMode mode : RenderMode.values()) {
                                                        builder.suggest(mode.getId());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("scale", FloatArgumentType.floatArg())
                                                        .then(Commands.argument("x", FloatArgumentType.floatArg())
                                                                .then(Commands.argument("y", FloatArgumentType.floatArg())
                                                                        .then(Commands.argument("z", FloatArgumentType.floatArg())
                                                                                .executes(SsCommand::setItem))))))))
                        .then(Commands.literal("test")
                                .then(Commands.literal("all")
                                        .executes(SsCommand::testAll))
                                .then(Commands.argument("modid", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                TestWallGenerator.getModIdsWithItems(), builder))
                                        .executes(SsCommand::testSingle)))
                        .then(Commands.literal("write")
                                .executes(SsCommand::write))
        );
    }

    private static CompletableFuture<Suggestions> suggestItems(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();

        if (!remaining.contains(":")) {
            List<String> namespaces = TestWallGenerator.getModIdsWithItems()
                    .stream()
                    .map(ns -> ns + ":")
                    .collect(Collectors.toList());
            return SharedSuggestionProvider.suggest(namespaces, builder);
        }

        String namespace = remaining.split(":")[0];
        List<String> itemIds = TestWallGenerator.collectModItems(namespace)
                .stream()
                .map(item -> String.valueOf(ForgeRegistries.ITEMS.getKey(item)))
                .collect(Collectors.toList());
        return SharedSuggestionProvider.suggest(itemIds, builder);
    }

    private static int setItem(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        ResourceLocation itemId = ResourceLocationArgument.getId(ctx, "item");
        if (!ForgeRegistries.ITEMS.containsKey(itemId)) {
            ctx.getSource().sendFailure(Component.literal("Unknown item: " + itemId));
            return 0;
        }

        String modeString = StringArgumentType.getString(ctx, "mode");
        if (RenderMode.fromString(modeString) == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Unknown render mode '" + modeString + "', expected 2d, 3d, block, or gui"));
            return 0;
        }

        float scale = FloatArgumentType.getFloat(ctx, "scale");
        if (scale <= 0) {
            ctx.getSource().sendFailure(Component.literal("Scale must be positive"));
            return 0;
        }

        float x = FloatArgumentType.getFloat(ctx, "x");
        float y = FloatArgumentType.getFloat(ctx, "y");
        float z = FloatArgumentType.getFloat(ctx, "z");

        RenderOverridePkt packet = RenderOverridePkt.set(itemId, modeString, scale, new float[]{x, y, z});
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);

        ctx.getSource().sendSuccess(() -> Component.literal(
                "Set render override for " + itemId + ": mode=" + modeString
                        + ", scale=" + scale + ", offset=[" + x + "," + y + "," + z + "]"), false);
        return 1;
    }

    private static int resetItem(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        ResourceLocation itemId = ResourceLocationArgument.getId(ctx, "item");
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), RenderOverridePkt.reset(itemId));

        ctx.getSource().sendSuccess(() -> Component.literal(
                "Reset render override for " + itemId + "; it now uses built-in or measured settings"), false);
        return 1;
    }

    private static int testSingle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String modId = StringArgumentType.getString(ctx, "modid");

        if (TestWallGenerator.collectModItems(modId).isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(modId + " is not loaded or has no items"));
            return 0;
        }

        if (ServerConfig.DISABLE_MODS.get().contains(modId)) {
            ctx.getSource().sendFailure(Component.literal(modId + " is disabled in server config"));
            return 0;
        }

        return generate(ctx, player, List.of(modId), List.of());
    }

    private static int testAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        List<String> modIds = new ArrayList<>(TestWallGenerator.getModIdsWithItems());
        Collections.sort(modIds);

        List<String> disabledMods = new ArrayList<>(ServerConfig.DISABLE_MODS.get());
        List<String> skippedMods = new ArrayList<>();
        modIds.removeIf(modId -> {
            if (disabledMods.contains(modId)) {
                skippedMods.add(modId);
                return true;
            }
            return false;
        });

        if (modIds.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("All loaded mods are disabled in server config"));
            return 0;
        }

        return generate(ctx, player, modIds, skippedMods);
    }

    private static int generate(CommandContext<CommandSourceStack> ctx, ServerPlayer player,
                                List<String> modIds, List<String> skippedMods) {
        TestWallGenerator.Result result = TestWallGenerator.generate(player, modIds);

        StringBuilder message = new StringBuilder("Created ")
                .append(result.totalStacks()).append(" StorageStacks (")
                .append(result.totalItems()).append(" items) for ");
        message.append(modIds.size() == 1 ? modIds.get(0) : modIds.size() + " mods");
        message.append(". Rows run north.");

        if (!skippedMods.isEmpty()) {
            message.append(" Skipped ").append(skippedMods.size())
                    .append(" disabled: ").append(String.join(", ", skippedMods)).append('.');
        }

        if (result.totalStacks() < result.expectedStacks()) {
            message.append(" WARNING: ").append(result.expectedStacks() - result.totalStacks()).append(" of ")
                    .append(result.expectedStacks()).append(" placements failed; see log for positions and reasons.");
        }

        final String finalMessage = message.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(finalMessage), true);
        return 1;
    }

    private static int write(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new WriteOverridesPkt());
        return 1;
    }
}
