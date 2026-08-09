package com.github.crittscott.somestacks.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The {@code ss help} subtree: one entry per top-level {@code ss} subcommand, listing its forms,
 * who may run it, and what it does.
 *
 * <p>Help has no permission gate, allowing users to see a subcommand's required permission even
 * when they cannot run it.
 */
final class SsHelp {
    private SsHelp() {}

    /** Who may run a subcommand, phrased for the player reading about it. */
    private enum Gate {
        OPERATOR("somestacks.command.help.gate.operator"),
        OPERATOR_IN_GAME("somestacks.command.help.gate.operator_in_game"),
        SERVER_ADMIN_IN_GAME("somestacks.command.help.gate.server_admin_in_game"),
        ANYONE("somestacks.command.help.gate.anyone");

        private final String audienceKey;

        Gate(String audienceKey) {
            this.audienceKey = audienceKey;
        }
    }

    /**
     * One top-level subcommand. The summary is its line in the index, the usages are its exact
     * forms, and the detail is what a player needs to know before running it.
     */
    private enum Topic {
        ITEM("item", "somestacks.command.help.item.summary", Gate.OPERATOR_IN_GAME,
                List.of("/ss item <item> <mode> [<scale> [<x> <y> [<z>]]]",
                        "/ss item <item> reset"),
                List.of("somestacks.command.help.item.detail.1",
                        "somestacks.command.help.item.detail.2",
                        "somestacks.command.help.item.detail.3",
                        "somestacks.command.help.item.detail.4")),

        GALLERY("gallery", "somestacks.command.help.gallery.summary",
                Gate.SERVER_ADMIN_IN_GAME,
                List.of("/ss gallery <modid>", "/ss gallery all", "/ss gallery list",
                        "/ss gallery items"),
                List.of("somestacks.command.help.gallery.detail.1",
                        "somestacks.command.help.gallery.detail.2",
                        "somestacks.command.help.gallery.detail.3",
                        "somestacks.command.help.gallery.detail.4",
                        "somestacks.command.help.gallery.detail.5")),

        INGOTGALLERY("ingotgallery", "somestacks.command.help.ingotgallery.summary",
                Gate.SERVER_ADMIN_IN_GAME,
                List.of("/ss ingotgallery <modid>", "/ss ingotgallery all", "/ss ingotgallery list"),
                List.of("somestacks.command.help.ingotgallery.detail.1",
                        "somestacks.command.help.ingotgallery.detail.2")),

        WRITE("write", "somestacks.command.help.write.summary", Gate.OPERATOR_IN_GAME,
                List.of("/ss write changed", "/ss write <modid>", "/ss write all", "/ss write list"),
                List.of("somestacks.command.help.write.detail.1",
                        "somestacks.command.help.write.detail.2",
                        "somestacks.command.help.write.detail.3",
                        "somestacks.command.help.write.detail.4")),

        RELOAD("reload", "somestacks.command.help.reload.summary", Gate.OPERATOR,
                List.of("/ss reload"),
                List.of("somestacks.command.help.reload.detail.1",
                        "somestacks.command.help.reload.detail.2")),

        GEN("gen", "somestacks.command.help.gen.summary", Gate.OPERATOR,
                List.of("/ss gen mod add <modid>", "/ss gen mod remove <modid>", "/ss gen mod list",
                        "/ss gen item add <item>", "/ss gen item remove <item>", "/ss gen item list"),
                List.of("somestacks.command.help.gen.detail.1",
                        "somestacks.command.help.gen.detail.2",
                        "somestacks.command.help.gen.detail.3")),

        DENY("deny", "somestacks.command.help.deny.summary", Gate.OPERATOR,
                List.of("/ss deny mod add <modid>", "/ss deny mod remove <modid>", "/ss deny mod list",
                        "/ss deny item add <item>", "/ss deny item remove <item>", "/ss deny item list"),
                List.of("somestacks.command.help.deny.detail.1",
                        "somestacks.command.help.deny.detail.2")),

        INGOT("ingot", "somestacks.command.help.ingot.summary", Gate.OPERATOR,
                List.of("/ss ingot add <tag>", "/ss ingot remove <tag>", "/ss ingot list"),
                List.of("somestacks.command.help.ingot.detail.1",
                        "somestacks.command.help.ingot.detail.2",
                        "somestacks.command.help.ingot.detail.3",
                        "somestacks.command.help.ingot.detail.4")),

        HELP("help", "somestacks.command.help.help.summary", Gate.ANYONE,
                List.of("/ss help", "/ss help <command>"),
                List.of("somestacks.command.help.help.detail.1"));

        private final String name;
        private final String summaryKey;
        private final Gate gate;
        private final List<String> usages;
        private final List<String> detailKeys;

        Topic(String name, String summaryKey, Gate gate, List<String> usages, List<String> detailKeys) {
            this.name = name;
            this.summaryKey = summaryKey;
            this.gate = gate;
            this.usages = usages;
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
        return Commands.literal("help")
                .executes(SsHelp::index)
                .then(Commands.argument("command", StringArgumentType.word())
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
        String name = StringArgumentType.getString(ctx, "command");
        Topic topic = Topic.byName(name);

        if (topic == null) {
            ctx.getSource().sendFailure(Component.translatable(
                    "somestacks.command.help.unknown", name, String.join(", ", Topic.names())));
            return 0;
        }

        send(ctx, Component.translatable("somestacks.command.help.topic_title", topic.name,
                        Component.translatable(topic.summaryKey))
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        for (String usage : topic.usages) {
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

    /** Sends help only to the requesting source. */
    private static void send(CommandContext<CommandSourceStack> ctx, Component line) {
        ctx.getSource().sendSuccess(() -> line, false);
    }
}
