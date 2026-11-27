package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.client.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.client.renderconfig.OverrideJsonCodec;
import com.github.crittscott.somestacks.client.renderconfig.RenderMode;
import com.github.crittscott.somestacks.client.renderconfig.RenderOffset;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.github.crittscott.somestacks.command.SsCommand;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The client-local {@code ss item} and {@code ss write} commands. They edit and write this client's
 * own render profiles, so anyone may run them on any server, and no server packet can invoke them.
 * The {@code list} and {@code all} write forms read the server's gallery and disabled namespace
 * lists from {@link ClientServerSettings}.
 *
 * <p>A dump covers every item of a namespace, which is what a Storage gallery shows, so the
 * messages about namespaces that cannot be dumped are the Storage gallery's.
 */
public final class ClientRenderCommands {
    private static final String ARG_MODE = "mode";
    private static final String ARG_SCALE = "scale";
    private static final String ARG_X = "x";
    private static final String ARG_Y = "y";
    private static final String ARG_Z = "z";

    private ClientRenderCommands() {}

    /** Loader-specific delivery of command feedback to the local player. */
    public interface Feedback<S> {
        void success(S source, Component message);

        void failure(S source, Component message);
    }

    // spotless:off
    public static <S> void register(CommandDispatcher<S> dispatcher, Feedback<S> feedback) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal(SsCommand.ROOT)
                .then(itemTree(feedback))
                .then(writeTree(feedback)));
    }
    // spotless:on

    /** Reports the namespaces being dumped and those skipped, then writes the dump files. */
    private static <S> int dump(
            CommandContext<S> ctx,
            Feedback<S> feedback,
            List<String> namespaces,
            List<String> disabled,
            List<String> unusable) {
        feedback.success(
                ctx.getSource(),
                Component.translatable("somestacks.command.dumping", namespaces.size()));
        SsCommand.Skips.report(
                List.of(
                        new SsCommand.Skips(SsCommand.SKIPPED_DISABLED_MODS, disabled),
                        new SsCommand.Skips(
                                RenderGalleryGenerator.Kind.STORAGE.skippedUnusableModsKey(),
                                unusable)),
                line -> feedback.success(ctx.getSource(), line));
        ItemRenderOverrides.handleDumpRequest(namespaces);
        return 1;
    }

    private static <S> int dumpAll(CommandContext<S> ctx, Feedback<S> feedback) {
        List<String> namespaces = new ArrayList<>(ItemRenderOverrides.installedNamespaces());
        List<String> disabled = new ArrayList<>();
        namespaces.removeIf(namespace -> {
            if (ClientServerSettings.isDisabled(namespace)) {
                disabled.add(namespace);
                return true;
            }
            return false;
        });
        if (namespaces.isEmpty()) {
            feedback.failure(ctx.getSource(), Component.translatable(SsCommand.ALL_MODS_DISABLED));
            return 0;
        }
        return dump(ctx, feedback, namespaces, disabled, List.of());
    }

    private static <S> int dumpList(CommandContext<S> ctx, Feedback<S> feedback) {
        List<String> namespaces = new ArrayList<>();
        List<String> disabled = new ArrayList<>();
        List<String> unusable = new ArrayList<>();
        Set<String> installed = ItemRenderOverrides.installedNamespaces();

        for (String namespace : ClientServerSettings.genMods()) {
            if (ClientServerSettings.isDisabled(namespace)) {
                disabled.add(namespace);
            } else if (!installed.contains(namespace)) {
                unusable.add(namespace);
            } else {
                namespaces.add(namespace);
            }
        }
        if (namespaces.isEmpty()) {
            feedback.failure(
                    ctx.getSource(),
                    ClientServerSettings.genMods().isEmpty()
                            ? Component.translatable(SsCommand.GEN_MODS_EMPTY)
                            : Component.translatable(
                                    RenderGalleryGenerator.Kind.STORAGE.genModsUnusableKey(),
                                    disabled.size(),
                                    unusable.size()));
            return 0;
        }
        Collections.sort(namespaces);
        return dump(ctx, feedback, namespaces, disabled, unusable);
    }

    private static <S> int dumpSingle(CommandContext<S> ctx, Feedback<S> feedback) {
        String namespace =
                StringArgumentType.getString(ctx, SsCommand.ARG_MODID).toLowerCase(Locale.ROOT);
        if (ClientServerSettings.isDisabled(namespace)) {
            feedback.failure(
                    ctx.getSource(), Component.translatable(SsCommand.MOD_DISABLED, namespace));
            return 0;
        }
        if (!ItemRenderOverrides.installedNamespaces().contains(namespace)) {
            feedback.failure(
                    ctx.getSource(),
                    Component.translatable(
                            RenderGalleryGenerator.Kind.STORAGE.unusableModKey(), namespace));
            return 0;
        }
        return dump(ctx, feedback, List.of(namespace), List.of(), List.of());
    }

    /**
     * The four forms of {@code ss item <item> <mode> ...} each supply the arguments the shorter
     * ones leave off: {@link OverrideJsonCodec#DEFAULT_SCALE} and a zero offset. Numeric arguments
     * are bounded to the override schema, so Brigadier reports an invalid value at the argument
     * itself.
     */
    // spotless:off
    private static <S> LiteralArgumentBuilder<S> itemTree(Feedback<S> feedback) {
        RequiredArgumentBuilder<S, Float> z = RequiredArgumentBuilder
                .<S, Float>argument(ARG_Z, offsetArg())
                .executes(ctx -> setItem(ctx, feedback, FloatArgumentType.getFloat(ctx, ARG_SCALE),
                        new RenderOffset(FloatArgumentType.getFloat(ctx, ARG_X),
                                FloatArgumentType.getFloat(ctx, ARG_Y),
                                FloatArgumentType.getFloat(ctx, ARG_Z))));
        RequiredArgumentBuilder<S, Float> y = RequiredArgumentBuilder
                .<S, Float>argument(ARG_Y, offsetArg())
                .executes(ctx -> setItem(ctx, feedback, FloatArgumentType.getFloat(ctx, ARG_SCALE),
                        new RenderOffset(FloatArgumentType.getFloat(ctx, ARG_X),
                                FloatArgumentType.getFloat(ctx, ARG_Y), 0.0f)))
                .then(z);
        RequiredArgumentBuilder<S, Float> x = RequiredArgumentBuilder
                .<S, Float>argument(ARG_X, offsetArg())
                .then(y);
        RequiredArgumentBuilder<S, Float> scale = RequiredArgumentBuilder
                .<S, Float>argument(ARG_SCALE, FloatArgumentType.floatArg(
                        OverrideJsonCodec.MIN_SCALE, OverrideJsonCodec.MAX_SCALE))
                .executes(ctx -> setItem(ctx, feedback,
                        FloatArgumentType.getFloat(ctx, ARG_SCALE), RenderOffset.ZERO))
                .then(x);
        RequiredArgumentBuilder<S, String> mode = RequiredArgumentBuilder
                .<S, String>argument(ARG_MODE, StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        Arrays.stream(RenderMode.values()).map(RenderMode::getId), builder))
                .executes(ctx -> setItem(ctx, feedback, OverrideJsonCodec.DEFAULT_SCALE, RenderOffset.ZERO))
                .then(scale);
        RequiredArgumentBuilder<S, ResourceLocation> item = RequiredArgumentBuilder
                .<S, ResourceLocation>argument(SsCommand.ARG_ITEM, ResourceLocationArgument.id())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::toString), builder))
                .then(LiteralArgumentBuilder.<S>literal("reset")
                        .executes(ctx -> resetItem(ctx, feedback)))
                .then(mode);
        return LiteralArgumentBuilder.<S>literal(SsCommand.ITEM_COMMAND).then(item);
    }
    // spotless:on

    private static FloatArgumentType offsetArg() {
        return FloatArgumentType.floatArg(
                OverrideJsonCodec.MIN_OFFSET, OverrideJsonCodec.MAX_OFFSET);
    }

    private static <S> int resetItem(CommandContext<S> ctx, Feedback<S> feedback) {
        ResourceLocation itemId = ctx.getArgument(SsCommand.ARG_ITEM, ResourceLocation.class);
        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            feedback.failure(
                    ctx.getSource(),
                    Component.translatable(SsCommand.UNKNOWN_ITEM, itemId.toString()));
            return 0;
        }

        ItemRenderOverrides.removeUser(itemId);
        feedback.success(
                ctx.getSource(),
                Component.translatable("somestacks.command.override_reset", itemId.toString()));
        return 1;
    }

    private static <S> int setItem(
            CommandContext<S> ctx, Feedback<S> feedback, float scale, RenderOffset offset) {
        ResourceLocation itemId = ctx.getArgument(SsCommand.ARG_ITEM, ResourceLocation.class);
        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            feedback.failure(
                    ctx.getSource(),
                    Component.translatable(SsCommand.UNKNOWN_ITEM, itemId.toString()));
            return 0;
        }

        String modeString = StringArgumentType.getString(ctx, ARG_MODE);
        RenderMode mode = RenderMode.fromString(modeString);
        if (mode == null) {
            feedback.failure(
                    ctx.getSource(),
                    Component.translatable(
                            "somestacks.command.unknown_render_mode",
                            modeString,
                            Arrays.stream(RenderMode.values())
                                    .map(RenderMode::getId)
                                    .collect(Collectors.joining(", "))));
            return 0;
        }

        ItemRenderOverrides.putUser(itemId, new ItemRenderConfig(mode, scale, offset));
        feedback.success(
                ctx.getSource(),
                Component.translatable(
                        "somestacks.command.override_set",
                        itemId.toString(),
                        modeString,
                        scale,
                        offset.x(),
                        offset.y(),
                        offset.z()));
        return 1;
    }

    /**
     * {@code changed} writes the user override layer to the file the client loads at startup, so it
     * holds only what {@code ss item} set. The namespace forms dump complete profiles for every
     * item of those namespaces to a folder nothing reads back, which keeps a dump of a whole
     * modpack from freezing that pack into the user layer.
     */
    // spotless:off
    private static <S> LiteralArgumentBuilder<S> writeTree(Feedback<S> feedback) {
        Command<S> changed = ctx -> {
            ItemRenderOverrides.WriteResult result = ItemRenderOverrides.writeUserFile();
            if (!result.written()) {
                feedback.failure(ctx.getSource(), result.message());
                return 0;
            }
            feedback.success(ctx.getSource(), result.message());
            return 1;
        };
        return LiteralArgumentBuilder.<S>literal(SsCommand.WRITE_COMMAND)
                .then(LiteralArgumentBuilder.<S>literal("changed").executes(changed))
                .then(LiteralArgumentBuilder.<S>literal("all")
                        .executes(ctx -> dumpAll(ctx, feedback)))
                .then(LiteralArgumentBuilder.<S>literal("list")
                        .executes(ctx -> dumpList(ctx, feedback)))
                .then(RequiredArgumentBuilder.<S, String>argument(SsCommand.ARG_MODID, StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                ItemRenderOverrides.installedNamespaces(), builder))
                        .executes(ctx -> dumpSingle(ctx, feedback)));
    }
    // spotless:on
}
