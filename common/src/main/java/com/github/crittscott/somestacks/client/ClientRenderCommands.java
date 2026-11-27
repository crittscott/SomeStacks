package com.github.crittscott.somestacks.client;

import static com.github.crittscott.somestacks.command.ClientRenderCommandSyntax.ARG_ITEM;
import static com.github.crittscott.somestacks.command.ClientRenderCommandSyntax.ARG_MODE;
import static com.github.crittscott.somestacks.command.ClientRenderCommandSyntax.ARG_MODID;
import static com.github.crittscott.somestacks.command.ClientRenderCommandSyntax.ARG_SCALE;
import static com.github.crittscott.somestacks.command.ClientRenderCommandSyntax.ARG_X;
import static com.github.crittscott.somestacks.command.ClientRenderCommandSyntax.ARG_Y;
import static com.github.crittscott.somestacks.command.ClientRenderCommandSyntax.ARG_Z;

import com.github.crittscott.somestacks.command.ClientRenderCommandSyntax;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.github.crittscott.somestacks.command.SsCommand;
import com.github.crittscott.somestacks.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.renderconfig.OverrideJsonCodec;
import com.github.crittscott.somestacks.renderconfig.RenderMode;
import com.github.crittscott.somestacks.renderconfig.RenderOffset;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.SharedSuggestionProvider;
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
 * Client-only render-authoring commands; no server packet can invoke these operations.
 *
 * <p>A dump covers every item of a namespace, which is what a Storage gallery shows, so the
 * messages about namespaces that cannot be dumped are the Storage gallery's.
 */
public final class ClientRenderCommands {
    private ClientRenderCommands() {}

    public interface Feedback<S> {
        void success(S source, Component message);

        void failure(S source, Component message);
    }

    // spotless:off
    public static <S> void register(CommandDispatcher<S> dispatcher, Feedback<S> feedback) {
        ClientRenderCommandSyntax.register(dispatcher, action -> ctx -> switch (action) {
            case ITEM_DEFAULT -> setItem(ctx, feedback,
                    OverrideJsonCodec.DEFAULT_SCALE, RenderOffset.ZERO);
            case ITEM_SCALE -> setItem(ctx, feedback,
                    FloatArgumentType.getFloat(ctx, ARG_SCALE), RenderOffset.ZERO);
            case ITEM_XY, ITEM_XYZ -> setItem(ctx, feedback,
                    FloatArgumentType.getFloat(ctx, ARG_SCALE), new RenderOffset(
                            FloatArgumentType.getFloat(ctx, ARG_X), FloatArgumentType.getFloat(ctx, ARG_Y),
                            action == ClientRenderCommandSyntax.Action.ITEM_XYZ
                                    ? FloatArgumentType.getFloat(ctx, ARG_Z) : 0.0f));
            case RESET -> resetItem(ctx, feedback);
            case WRITE_CHANGED -> writeChanged(ctx, feedback);
            case WRITE_ALL -> dumpAll(ctx, feedback);
            case WRITE_LIST -> dumpList(ctx, feedback);
            case WRITE_NAMESPACE -> dumpSingle(ctx, feedback);
        }, (ctx, builder) -> SharedSuggestionProvider.suggest(
                BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::toString), builder),
                (ctx, builder) -> SharedSuggestionProvider.suggest(
                        ItemRenderOverrides.installedNamespaces(), builder));
    }
    // spotless:on

    private static <S> int setItem(
            CommandContext<S> ctx, Feedback<S> feedback, float scale, RenderOffset offset) {
        ResourceLocation itemId = ctx.getArgument(ARG_ITEM, ResourceLocation.class);
        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            feedback.failure(
                    ctx.getSource(),
                    Component.translatable("somestacks.command.unknown_item", itemId));
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

    private static <S> int resetItem(CommandContext<S> ctx, Feedback<S> feedback) {
        ResourceLocation itemId = ctx.getArgument(ARG_ITEM, ResourceLocation.class);
        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            feedback.failure(
                    ctx.getSource(),
                    Component.translatable("somestacks.command.unknown_item", itemId));
            return 0;
        }

        ItemRenderOverrides.removeUser(itemId);
        feedback.success(
                ctx.getSource(),
                Component.translatable("somestacks.command.override_reset", itemId.toString()));
        return 1;
    }

    private static <S> int dumpSingle(CommandContext<S> ctx, Feedback<S> feedback) {
        String namespace = StringArgumentType.getString(ctx, ARG_MODID).toLowerCase(Locale.ROOT);
        if (ClientServerSettings.isDisabled(namespace)) {
            feedback.failure(
                    ctx.getSource(),
                    Component.translatable(SsCommand.MOD_DISABLED, namespace));
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

    /**
     * Saves the user override layer, failing with nothing written while the file on disk cannot
     * be read.
     */
    private static <S> int writeChanged(CommandContext<S> ctx, Feedback<S> feedback) {
        ItemRenderOverrides.WriteResult result = ItemRenderOverrides.writeUserFile();
        if (!result.written()) {
            feedback.failure(ctx.getSource(), result.message());
            return 0;
        }
        feedback.success(ctx.getSource(), result.message());
        return 1;
    }
}
