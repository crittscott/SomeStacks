package com.github.crittscott.somestacks.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The loader-neutral {@code ss help} subtree: one entry per top-level {@code ss} subcommand, listing its forms,
 * who may run it, and what it does.
 *
 * <p>Help has no permission gate, allowing users to see a subcommand's required permission even
 * when they cannot run it.
 */
final class SsHelp {
    private static final String ARG_COMMAND = "command";

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
     * One top-level subcommand. The summary is its line in the index; usage comes from the server
     * tree or the explicit client-only forms.
     */
    private enum Topic {
        ITEM(SsCommand.COMMAND_ITEM, "somestacks.command.help.item.summary", Gate.ANYONE,
                List.of("somestacks.command.help.item.detail.1",
                        "somestacks.command.help.item.detail.2",
                        "somestacks.command.help.item.detail.3",
                        "somestacks.command.help.item.detail.4")),

        GALLERY(SsCommand.COMMAND_GALLERY, "somestacks.command.help.gallery.summary",
                Gate.SERVER_ADMIN_IN_GAME,
                List.of("somestacks.command.help.gallery.detail.1",
                        "somestacks.command.help.gallery.detail.2",
                        "somestacks.command.help.gallery.detail.3",
                        "somestacks.command.help.gallery.detail.4",
                        "somestacks.command.help.gallery.detail.5")),

        INGOTGALLERY(SsCommand.COMMAND_INGOT_GALLERY, "somestacks.command.help.ingotgallery.summary",
                Gate.SERVER_ADMIN_IN_GAME,
                List.of("somestacks.command.help.ingotgallery.detail.1",
                        "somestacks.command.help.ingotgallery.detail.2")),

        WRITE(SsCommand.COMMAND_WRITE, "somestacks.command.help.write.summary", Gate.ANYONE,
                List.of("somestacks.command.help.write.detail.1",
                        "somestacks.command.help.write.detail.2",
                        "somestacks.command.help.write.detail.3",
                        "somestacks.command.help.write.detail.4")),

        RELOAD(SsCommand.COMMAND_RELOAD, "somestacks.command.help.reload.summary", Gate.OPERATOR,
                List.of("somestacks.command.help.reload.detail.1")),

        GEN(SsCommand.COMMAND_GEN, "somestacks.command.help.gen.summary", Gate.OPERATOR,
                List.of("somestacks.command.help.gen.detail.1",
                        "somestacks.command.help.gen.detail.2",
                        "somestacks.command.help.gen.detail.3")),

        DENY(SsCommand.COMMAND_DENY, "somestacks.command.help.deny.summary", Gate.OPERATOR,
                List.of("somestacks.command.help.deny.detail.1",
                        "somestacks.command.help.deny.detail.2"));

        private final String name;
        private final String summaryKey;
        private final Gate gate;
        private final List<String> detailKeys;

        Topic(String name, String summaryKey, Gate gate, List<String> detailKeys) {
            this.name = name;
            this.summaryKey = summaryKey;
            this.gate = gate;
            this.detailKeys = detailKeys;
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
        return Commands.literal(SsCommand.COMMAND_HELP)
                .executes(SsHelp::index)
                .then(Commands.argument(ARG_COMMAND, StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Topic.names(), builder))
                        .executes(SsHelp::topic));
    }

    /** Builds the command index from each subcommand's summary. */
    private static int index(CommandContext<CommandSourceStack> ctx) {
        send(ctx, Component.translatable("somestacks.command.help.title")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        for (Topic topic : Topic.values()) {
            send(ctx, Component.translatable("somestacks.command.help.index_command", topic.name)
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable("somestacks.command.help.index_summary",
                            Component.translatable(topic.summaryKey)).withStyle(ChatFormatting.GRAY)));
        }

        send(ctx, Component.translatable("somestacks.command.help.hint").withStyle(ChatFormatting.GRAY));
        return Topic.values().length;
    }

    private static int topic(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, ARG_COMMAND);
        Topic topic = Topic.byName(name);

        if (topic == null) {
            ctx.getSource().sendFailure(Component.translatable(
                    "somestacks.command.help.unknown", name,
                    ComponentUtils.formatList(Topic.names(), Component::literal)));
            return 0;
        }

        send(ctx, Component.translatable("somestacks.command.help.topic_title", topic.name,
                        Component.translatable(topic.summaryKey))
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        for (String usage : usages(ctx, topic)) {
            send(ctx, Component.translatable("somestacks.command.help.usage", usage)
                    .withStyle(ChatFormatting.YELLOW));
        }

        send(ctx, Component.translatable("somestacks.command.help.audience",
                        Component.translatable(topic.gate.audienceKey))
                .withStyle(ChatFormatting.GRAY));

        for (String detailKey : topic.detailKeys) {
            send(ctx, Component.translatable(detailKey));
        }

        return 1;
    }

    /** Derives server forms from Brigadier and names the two client-only trees explicitly. */
    private static List<String> usages(CommandContext<CommandSourceStack> ctx, Topic topic) {
        if (topic == Topic.ITEM) {
            return List.of(
                    "/ss item <item> reset",
                    "/ss item <item> <mode>",
                    "/ss item <item> <mode> <scale>",
                    "/ss item <item> <mode> <scale> <x> <y>",
                    "/ss item <item> <mode> <scale> <x> <y> <z>");
        }
        if (topic == Topic.WRITE) {
            return List.of(
                    "/ss write changed",
                    "/ss write all",
                    "/ss write list",
                    "/ss write <modid>");
        }

        CommandDispatcher<CommandSourceStack> dispatcher =
                ctx.getSource().getServer().getCommands().getDispatcher();
        CommandNode<CommandSourceStack> root =
                dispatcher.getRoot().getChild(SsCommand.COMMAND_ROOT);
        CommandNode<CommandSourceStack> topicNode = root == null ? null : root.getChild(topic.name);
        if (topicNode == null) {
            return List.of();
        }
        String prefix = "/" + SsCommand.COMMAND_ROOT + " " + topic.name;
        return Arrays.stream(dispatcher.getAllUsage(topicNode, ctx.getSource(), false))
                .map(form -> form.isEmpty() ? prefix : prefix + " " + form)
                .toList();
    }

    /** Sends help only to the requesting source. */
    private static void send(CommandContext<CommandSourceStack> ctx, Component line) {
        ctx.getSource().sendSuccess(() -> line, false);
    }
}
