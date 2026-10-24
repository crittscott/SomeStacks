package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.renderconfig.OverrideJsonCodec;
import com.github.crittscott.somestacks.renderconfig.RenderMode;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/** Server-safe syntax shared by local render commands and server help. */
public final class ClientRenderCommandSyntax {
    public static final String ROOT = "ss";
    public static final String ITEM = "item";
    public static final String WRITE = "write";
    public static final String ARG_ITEM = "item";
    public static final String ARG_MODE = "mode";
    public static final String ARG_SCALE = "scale";
    public static final String ARG_X = "x";
    public static final String ARG_Y = "y";
    public static final String ARG_Z = "z";
    public static final String ARG_MODID = "modid";

    public enum Action {
        ITEM_DEFAULT, ITEM_SCALE, ITEM_XY, ITEM_XYZ, RESET,
        WRITE_CHANGED, WRITE_ALL, WRITE_LIST, WRITE_NAMESPACE
    }

    private ClientRenderCommandSyntax() {}

    public static <S> void register(CommandDispatcher<S> dispatcher,
                                    Function<Action, Command<S>> commandFor,
                                    SuggestionProvider<S> itemSuggestions,
                                    SuggestionProvider<S> namespaceSuggestions) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal(ROOT)
                .then(itemTree(commandFor, itemSuggestions))
                .then(writeTree(commandFor, namespaceSuggestions)));
    }

    /** Derives help from the same tree without loading or invoking client operations. */
    public static List<String> usages(String topic) {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        register(dispatcher, action -> ctx -> 1,
                (ctx, builder) -> builder.buildFuture(), (ctx, builder) -> builder.buildFuture());
        var node = dispatcher.getRoot().getChild(ROOT).getChild(topic);
        if (node == null) return List.of();
        return Arrays.stream(dispatcher.getAllUsage(node, new Object(), false))
                .map(form -> "/" + ROOT + " " + topic + " " + form)
                .toList();
    }

    private static <S> LiteralArgumentBuilder<S> itemTree(
            Function<Action, Command<S>> commandFor, SuggestionProvider<S> itemSuggestions) {
        RequiredArgumentBuilder<S, Float> z = RequiredArgumentBuilder
                .<S, Float>argument(ARG_Z, offsetArg())
                .executes(commandFor.apply(Action.ITEM_XYZ));
        RequiredArgumentBuilder<S, Float> y = RequiredArgumentBuilder
                .<S, Float>argument(ARG_Y, offsetArg())
                .executes(commandFor.apply(Action.ITEM_XY))
                .then(z);
        RequiredArgumentBuilder<S, Float> x = RequiredArgumentBuilder
                .<S, Float>argument(ARG_X, offsetArg())
                .then(y);
        RequiredArgumentBuilder<S, Float> scale = RequiredArgumentBuilder
                .<S, Float>argument(ARG_SCALE, FloatArgumentType.floatArg(
                        OverrideJsonCodec.MIN_SCALE, OverrideJsonCodec.MAX_SCALE))
                .executes(commandFor.apply(Action.ITEM_SCALE))
                .then(x);
        RequiredArgumentBuilder<S, String> mode = RequiredArgumentBuilder
                .<S, String>argument(ARG_MODE, StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        Arrays.stream(RenderMode.values())
                                .map(RenderMode::getId), builder))
                .executes(commandFor.apply(Action.ITEM_DEFAULT))
                .then(scale);
        RequiredArgumentBuilder<S, ResourceLocation> item = RequiredArgumentBuilder
                .<S, ResourceLocation>argument(ARG_ITEM, ResourceLocationArgument.id())
                .suggests(itemSuggestions)
                .then(LiteralArgumentBuilder.<S>literal("reset")
                        .executes(commandFor.apply(Action.RESET)))
                .then(mode);
        return LiteralArgumentBuilder.<S>literal(ITEM).then(item);
    }

    private static FloatArgumentType offsetArg() {
        return FloatArgumentType.floatArg(
                OverrideJsonCodec.MIN_OFFSET, OverrideJsonCodec.MAX_OFFSET);
    }

    private static <S> LiteralArgumentBuilder<S> writeTree(
            Function<Action, Command<S>> commandFor, SuggestionProvider<S> namespaceSuggestions) {
        return LiteralArgumentBuilder.<S>literal(WRITE)
                .then(LiteralArgumentBuilder.<S>literal("changed")
                        .executes(commandFor.apply(Action.WRITE_CHANGED)))
                .then(LiteralArgumentBuilder.<S>literal("all")
                        .executes(commandFor.apply(Action.WRITE_ALL)))
                .then(LiteralArgumentBuilder.<S>literal("list")
                        .executes(commandFor.apply(Action.WRITE_LIST)))
                .then(RequiredArgumentBuilder.<S, String>argument(
                                ARG_MODID, StringArgumentType.word())
                        .suggests(namespaceSuggestions)
                        .executes(commandFor.apply(Action.WRITE_NAMESPACE)));
    }
}
