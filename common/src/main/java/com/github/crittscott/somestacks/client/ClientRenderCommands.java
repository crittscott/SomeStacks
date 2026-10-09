package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.command.ClientRenderCommandSyntax;
import com.github.crittscott.somestacks.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.renderconfig.RenderMode;
import com.github.crittscott.somestacks.renderconfig.RenderOffset;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static com.github.crittscott.somestacks.command.ClientRenderCommandSyntax.ARG_ITEM;
import static com.github.crittscott.somestacks.command.ClientRenderCommandSyntax.ARG_MODE;
import static com.github.crittscott.somestacks.command.ClientRenderCommandSyntax.ARG_MODID;
import static com.github.crittscott.somestacks.command.ClientRenderCommandSyntax.ARG_SCALE;
import static com.github.crittscott.somestacks.command.ClientRenderCommandSyntax.ARG_X;
import static com.github.crittscott.somestacks.command.ClientRenderCommandSyntax.ARG_Y;
import static com.github.crittscott.somestacks.command.ClientRenderCommandSyntax.ARG_Z;

/** Client-only render-authoring commands; no server packet can invoke these operations. */
public final class ClientRenderCommands {
    private ClientRenderCommands() {}

    public interface Feedback<S> {
        void success(S source, Component message);
        void failure(S source, Component message);
    }

    public static <S> void register(CommandDispatcher<S> dispatcher, Feedback<S> feedback) {
        ClientRenderCommandSyntax.register(dispatcher, action -> ctx -> switch (action) {
            case ITEM_DEFAULT -> setItem(ctx, feedback, 1.0f, RenderOffset.ZERO);
            case ITEM_SCALE -> setItem(ctx, feedback,
                    FloatArgumentType.getFloat(ctx, ARG_SCALE), RenderOffset.ZERO);
            case ITEM_XY, ITEM_XYZ -> setItem(ctx, feedback,
                    FloatArgumentType.getFloat(ctx, ARG_SCALE), new RenderOffset(
                            FloatArgumentType.getFloat(ctx, ARG_X), FloatArgumentType.getFloat(ctx, ARG_Y),
                            action == ClientRenderCommandSyntax.Action.ITEM_XYZ
                                    ? FloatArgumentType.getFloat(ctx, ARG_Z) : 0.0f));
            case RESET -> resetItem(ctx, feedback);
            case WRITE_CHANGED -> {
                ItemRenderOverrides.handleWriteRequest();
                yield 1;
            }
            case WRITE_ALL -> dumpAll(ctx, feedback);
            case WRITE_LIST -> dumpList(ctx, feedback);
            case WRITE_NAMESPACE -> dumpSingle(ctx, feedback);
        }, (ctx, builder) -> SharedSuggestionProvider.suggest(
                BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::toString), builder),
                (ctx, builder) -> SharedSuggestionProvider.suggest(installedNamespaces(), builder));
    }

    private static <S> int setItem(
            CommandContext<S> ctx, Feedback<S> feedback, float scale, RenderOffset offset) {
        ResourceLocation itemId = ctx.getArgument(ARG_ITEM, ResourceLocation.class);
        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            feedback.failure(ctx.getSource(),
                    Component.translatable("somestacks.command.unknown_item", itemId));
            return 0;
        }

        String modeString = StringArgumentType.getString(ctx, ARG_MODE);
        RenderMode mode = RenderMode.fromString(modeString);
        if (mode == null) {
            feedback.failure(ctx.getSource(), Component.translatable(
                    "somestacks.command.unknown_render_mode", modeString));
            return 0;
        }

        ItemRenderOverrides.putUser(
                itemId, new ItemRenderConfig(mode, scale, offset));
        feedback.success(ctx.getSource(), Component.translatable(
                "somestacks.command.override_set", itemId.toString(), modeString, scale,
                offset.x(), offset.y(), offset.z()));
        return 1;
    }

    private static <S> int resetItem(CommandContext<S> ctx, Feedback<S> feedback) {
        ResourceLocation itemId = ctx.getArgument(ARG_ITEM, ResourceLocation.class);
        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            feedback.failure(ctx.getSource(),
                    Component.translatable("somestacks.command.unknown_item", itemId));
            return 0;
        }

        ItemRenderOverrides.removeUser(itemId);
        feedback.success(ctx.getSource(), Component.translatable(
                "somestacks.command.override_reset", itemId.toString()));
        return 1;
    }

    private static <S> int dumpSingle(CommandContext<S> ctx, Feedback<S> feedback) {
        String namespace = StringArgumentType.getString(ctx, ARG_MODID)
                .toLowerCase(Locale.ROOT);
        if (ClientRenderToolState.isDisabled(namespace)) {
            feedback.failure(ctx.getSource(), Component.translatable(
                    "somestacks.command.mod_disabled", namespace));
            return 0;
        }
        if (!installedNamespaces().contains(namespace)) {
            feedback.failure(ctx.getSource(), Component.translatable(
                    "somestacks.command.mod_unusable", namespace,
                    Component.translatable("somestacks.command.gallery.items")));
            return 0;
        }
        return dump(ctx, feedback, List.of(namespace), List.of(), List.of());
    }

    private static <S> int dumpAll(CommandContext<S> ctx, Feedback<S> feedback) {
        List<String> namespaces = new ArrayList<>(installedNamespaces());
        List<String> disabled = new ArrayList<>();
        namespaces.removeIf(namespace -> {
            if (ClientRenderToolState.isDisabled(namespace)) {
                disabled.add(namespace);
                return true;
            }
            return false;
        });
        if (namespaces.isEmpty()) {
            feedback.failure(ctx.getSource(), Component.translatable(
                    "somestacks.command.all_mods_disabled"));
            return 0;
        }
        return dump(ctx, feedback, namespaces, disabled, List.of());
    }

    private static <S> int dumpList(CommandContext<S> ctx, Feedback<S> feedback) {
        List<String> namespaces = new ArrayList<>();
        List<String> disabled = new ArrayList<>();
        List<String> unusable = new ArrayList<>();
        Set<String> installed = installedNamespaces();

        for (String namespace : ClientRenderToolState.genMods()) {
            if (ClientRenderToolState.isDisabled(namespace)) {
                disabled.add(namespace);
            } else if (!installed.contains(namespace)) {
                unusable.add(namespace);
            } else {
                namespaces.add(namespace);
            }
        }
        if (namespaces.isEmpty()) {
            feedback.failure(ctx.getSource(), ClientRenderToolState.genMods().isEmpty()
                    ? Component.translatable(
                            "somestacks.command.gen_mods_empty",
                            Component.translatable("somestacks.command.label.gen_mods"))
                    : Component.translatable(
                            "somestacks.command.gen_mods_unusable",
                            Component.translatable("somestacks.command.label.gen_mods"),
                            Component.translatable("somestacks.command.gallery.items"),
                            disabled.size(), unusable.size()));
            return 0;
        }
        Collections.sort(namespaces);
        return dump(ctx, feedback, namespaces, disabled, unusable);
    }

    private static <S> int dump(
            CommandContext<S> ctx,
            Feedback<S> feedback,
            List<String> namespaces,
            List<String> disabled,
            List<String> unusable) {
        MutableComponent message = Component.translatable(
                "somestacks.command.dumping",
                Component.translatable("somestacks.command.subject.mods", namespaces.size()));
        appendSkips(message, disabled, Component.translatable("somestacks.command.skip.disabled"));
        appendSkips(message, unusable, Component.translatable(
                "somestacks.command.skip.not_loaded",
                Component.translatable("somestacks.command.gallery.items")));
        feedback.success(ctx.getSource(), message);
        ItemRenderOverrides.handleDumpRequest(namespaces);
        return 1;
    }

    private static void appendSkips(
            MutableComponent message, List<String> entries, Component reason) {
        if (!entries.isEmpty()) {
            message.append(Component.translatable(
                    "somestacks.command.skipped", entries.size(), reason,
                    ComponentUtils.formatList(entries, Component::literal)));
        }
    }

    private static Set<String> installedNamespaces() {
        Set<String> namespaces = new TreeSet<>();
        for (ResourceLocation itemId : BuiltInRegistries.ITEM.keySet()) {
            namespaces.add(itemId.getNamespace());
        }
        return namespaces;
    }
}
