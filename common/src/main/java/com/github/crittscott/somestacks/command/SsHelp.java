package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.client.renderconfig.OverrideJsonCodec;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

/**
 * The loader-neutral {@code ss help} subtree: one entry per top-level {@code ss} subcommand,
 * listing its forms, who may run it, and what it does.
 *
 * <p>Help has no permission gate, allowing users to see a subcommand's required permission even
 * when they cannot run it.
 */
final class SsHelp {
    private SsHelp() {}

    /** Who may run a subcommand, phrased for the player reading about it. */
    private enum Gate {
        OPERATOR("somestacks.command.help.gate.operator"),
        SERVER_ADMIN_IN_GAME("somestacks.command.help.gate.server_admin_in_game"),
        ANYONE("somestacks.command.help.gate.anyone");

        private final String audienceKey;

        Gate(String audienceKey) {
            this.audienceKey = audienceKey;
        }
    }

    /**
     * One top-level subcommand. The summary is its line in the index, the usage keys describe its
     * exact forms, and the detail is what a player needs to know before running it.
     */
    private enum Topic {
        ITEM(
                SsCommand.ITEM_COMMAND,
                "somestacks.command.help.item.summary",
                Gate.ANYONE,
                List.of(
                        "somestacks.command.help.item.usage.1",
                        "somestacks.command.help.item.usage.2"),
                List.of(
                        detail("somestacks.command.help.item.detail.1"),
                        detail(
                                "somestacks.command.help.item.detail.2",
                                OverrideJsonCodec.DEFAULT_SCALE),
                        detail("somestacks.command.help.item.detail.3"),
                        detail("somestacks.command.help.item.detail.4"))),

        GALLERY(
                SsCommand.GALLERY_COMMAND,
                "somestacks.command.help.gallery.summary",
                Gate.SERVER_ADMIN_IN_GAME,
                List.of(
                        "somestacks.command.help.gallery.usage.1",
                        "somestacks.command.help.gallery.usage.2",
                        "somestacks.command.help.gallery.usage.3",
                        "somestacks.command.help.gallery.usage.4"),
                List.of(
                        detail("somestacks.command.help.gallery.detail.1"),
                        detail("somestacks.command.help.gallery.detail.2"),
                        detail("somestacks.command.help.gallery.detail.3"),
                        detail("somestacks.command.help.gallery.detail.4"),
                        detail("somestacks.command.help.gallery.detail.5"))),

        INGOTGALLERY(
                SsCommand.INGOTGALLERY_COMMAND,
                "somestacks.command.help.ingotgallery.summary",
                Gate.SERVER_ADMIN_IN_GAME,
                List.of(
                        "somestacks.command.help.ingotgallery.usage.1",
                        "somestacks.command.help.ingotgallery.usage.2",
                        "somestacks.command.help.ingotgallery.usage.3"),
                List.of(
                        detail("somestacks.command.help.ingotgallery.detail.1"),
                        detail("somestacks.command.help.ingotgallery.detail.2"))),

        WRITE(
                SsCommand.WRITE_COMMAND,
                "somestacks.command.help.write.summary",
                Gate.ANYONE,
                List.of(
                        "somestacks.command.help.write.usage.1",
                        "somestacks.command.help.write.usage.2",
                        "somestacks.command.help.write.usage.3",
                        "somestacks.command.help.write.usage.4"),
                List.of(
                        detail("somestacks.command.help.write.detail.1"),
                        detail("somestacks.command.help.write.detail.2"),
                        detail("somestacks.command.help.write.detail.3"),
                        detail("somestacks.command.help.write.detail.4"))),

        RELOAD(
                SsCommand.RELOAD_COMMAND,
                "somestacks.command.help.reload.summary",
                Gate.OPERATOR,
                List.of("somestacks.command.help.reload.usage.1"),
                List.of(detail("somestacks.command.help.reload.detail.1"))),

        GEN(
                SsCommand.GEN_COMMAND,
                "somestacks.command.help.gen.summary",
                Gate.OPERATOR,
                List.of(
                        "somestacks.command.help.gen.usage.1",
                        "somestacks.command.help.gen.usage.2",
                        "somestacks.command.help.gen.usage.3",
                        "somestacks.command.help.gen.usage.4",
                        "somestacks.command.help.gen.usage.5",
                        "somestacks.command.help.gen.usage.6"),
                List.of(
                        detail("somestacks.command.help.gen.detail.1"),
                        detail("somestacks.command.help.gen.detail.2"),
                        detail(
                                "somestacks.command.help.gen.detail.3",
                                ServerConfig.MAX_NAMESPACE_ENTRIES,
                                ServerConfig.MAX_NAMESPACE_LENGTH))),

        DENY(
                SsCommand.DENY_COMMAND,
                "somestacks.command.help.deny.summary",
                Gate.OPERATOR,
                List.of(
                        "somestacks.command.help.deny.usage.1",
                        "somestacks.command.help.deny.usage.2",
                        "somestacks.command.help.deny.usage.3",
                        "somestacks.command.help.deny.usage.4",
                        "somestacks.command.help.deny.usage.5",
                        "somestacks.command.help.deny.usage.6"),
                List.of(
                        detail("somestacks.command.help.deny.detail.1"),
                        detail(
                                "somestacks.command.help.deny.detail.2",
                                ServerConfig.MAX_NAMESPACE_ENTRIES,
                                ServerConfig.MAX_NAMESPACE_LENGTH))),

        HELP(
                SsCommand.HELP_COMMAND,
                "somestacks.command.help.help.summary",
                Gate.ANYONE,
                List.of(
                        "somestacks.command.help.help.usage.1",
                        "somestacks.command.help.help.usage.2"),
                List.of(detail("somestacks.command.help.help.detail.1")));

        private final String name;
        private final String summaryKey;
        private final Gate gate;
        private final List<String> usageKeys;
        private final List<Component> details;

        Topic(
                String name,
                String summaryKey,
                Gate gate,
                List<String> usageKeys,
                List<Component> details) {
            this.name = name;
            this.summaryKey = summaryKey;
            this.gate = gate;
            this.usageKeys = usageKeys;
            this.details = details;
        }

        @Nullable
        private static Topic byName(String name) {
            String wanted = name.toLowerCase(Locale.ROOT);
            for (Topic topic : values()) {
                if (topic.name.equals(wanted)) {
                    return topic;
                }
            }
            return null;
        }

        private static List<String> names() {
            return Arrays.stream(values()).map(topic -> topic.name).toList();
        }
    }

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal(SsCommand.HELP_COMMAND)
                .executes(SsHelp::index)
                .then(
                        Commands.argument("command", StringArgumentType.word())
                                .suggests((ctx, builder) ->
                                        SharedSuggestionProvider.suggest(Topic.names(), builder))
                                .executes(SsHelp::topic));
    }

    /** Builds the command index from each subcommand's summary. */
    private static int index(CommandContext<CommandSourceStack> ctx) {
        send(
                ctx,
                Component.translatable("somestacks.command.help.title")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        for (Topic topic : Topic.values()) {
            send(
                    ctx,
                    Component.translatable("somestacks.command.help.index_command", topic.name)
                            .withStyle(ChatFormatting.YELLOW)
                            .append(
                                    Component.translatable(
                                                    "somestacks.command.help.index_summary",
                                                    Component.translatable(topic.summaryKey))
                                            .withStyle(ChatFormatting.GRAY)));
        }

        send(
                ctx,
                Component.translatable("somestacks.command.help.hint")
                        .withStyle(ChatFormatting.GRAY));
        return Topic.values().length;
    }

    /**
     * One detail line of a topic. Arguments carry the values the code owns, such as limits and
     * defaults, so the help text cannot drift from them.
     */
    private static Component detail(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static int topic(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "command");
        Topic topic = Topic.byName(name);

        if (topic == null) {
            ctx.getSource()
                    .sendFailure(
                            Component.translatable(
                                    "somestacks.command.help.unknown",
                                    name,
                                    String.join(", ", Topic.names())));
            return 0;
        }

        send(
                ctx,
                Component.translatable(
                                "somestacks.command.help.topic_title",
                                topic.name,
                                Component.translatable(topic.summaryKey))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        for (String usageKey : topic.usageKeys) {
            send(
                    ctx,
                    Component.translatable(
                                    "somestacks.command.help.usage",
                                    Component.translatable(usageKey))
                            .withStyle(ChatFormatting.YELLOW));
        }

        send(
                ctx,
                Component.translatable(
                                "somestacks.command.help.audience",
                                Component.translatable(topic.gate.audienceKey))
                        .withStyle(ChatFormatting.GRAY));

        for (Component detail : topic.details) {
            send(ctx, detail);
        }

        return 1;
    }

    /** Sends help only to the requesting source. */
    private static void send(CommandContext<CommandSourceStack> ctx, Component line) {
        ctx.getSource().sendSuccess(() -> line, false);
    }
}
