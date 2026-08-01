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
 * <p>Help is the one part of {@code ss} that is gated by nothing. A player who cannot run a
 * subcommand is the player most likely to be reading about it, and each entry names the gate it
 * answers to, so being told what a command needs is itself the answer to why it was refused.
 */
final class SsHelp {
    private SsHelp() {}

    /** Who may run a subcommand, phrased for the player reading about it. */
    private enum Gate {
        OPERATOR("an operator, in game or from the server console"),
        OPERATOR_IN_GAME("an operator, in game only"),
        SERVER_ADMIN_IN_GAME("a server administrator, in game only"),
        ANYONE("anyone");

        private final String audience;

        Gate(String audience) {
            this.audience = audience;
        }
    }

    /**
     * One top-level subcommand. The summary is its line in the index, the usages are its exact
     * forms, and the detail is what a player needs to know before running it.
     */
    private enum Topic {
        ITEM("item", "Set how one item is drawn inside a stack", Gate.OPERATOR_IN_GAME,
                List.of("/ss item <item> <mode> [<scale> [<x> <y> [<z>]]]",
                        "/ss item <item> reset"),
                List.of("Modes: 2d flattens the item onto the faces of its cell, 3d uses its ordinary"
                                + " model, block draws a block item's own block, and gui uses its"
                                + " inventory look.",
                        "Only the mode is required. Scale defaults to 1 and offset to zero, and 2d uses"
                                + " the x and y offset only.",
                        "The change applies to your own view at once but lives in memory until"
                                + " /ss write changed saves it.",
                        "reset drops your entry, so the item goes back to the server, built-in or"
                                + " measured setting it had before.")),

        TEST("test", "Build walls of Storage Stacks to review item rendering", Gate.SERVER_ADMIN_IN_GAME,
                List.of("/ss test <modid>", "/ss test all", "/ss test list", "/ss test items"),
                List.of("Builds east of you over a sandstone floor, nine items to a stack, rows running"
                                + " north. Whatever blocks stand in the floor and stack positions are"
                                + " replaced.",
                        "It overwrites that region outright, without the protection checks a placement"
                                + " gesture answers to, which is why it is held a permission level"
                                + " above the rest of ss.",
                        "<modid> is one namespace and all is every loaded one, which in a large pack is"
                                + " thousands of rendered block entities. list builds the namespaces of"
                                + " the gen mod list.",
                        "items builds one row from the gen item list, sorted by mod id and then item"
                                + " name.",
                        "all, list and items skip an entry they cannot show and report it; naming one"
                                + " namespace that cannot be shown fails instead.")),

        TESTINGOT("testingot", "Build walls of Bar Stacks to review bar textures and tints",
                Gate.SERVER_ADMIN_IN_GAME,
                List.of("/ss testingot <modid>", "/ss testingot all", "/ss testingot list"),
                List.of("The same generator over Bar Stacks, covering only the items a Bar Stack accepts,"
                                + " one bar per ingot and eight to a block.",
                        "Its namespace completions offer only the namespaces that have an ingot.")),

        WRITE("write", "Write render overrides from your client to disk", Gate.OPERATOR_IN_GAME,
                List.of("/ss write changed", "/ss write <modid>", "/ss write all", "/ss write list"),
                List.of("changed saves what /ss item set to config/somestacks/item_overrides.json, which"
                                + " your client loads at startup. Only entries you set are ever written.",
                        "The namespace forms dump a complete profile for every item of those namespaces"
                                + " to config/somestacks/generated_overrides/<namespace>.json, measuring"
                                + " whatever no override layer configures.",
                        "That dump runs on your own client and holds it busy until it finishes, so name a"
                                + " namespace, or keep a review session's mods in the gen mod list and"
                                + " dump list, rather than reaching for all in a large pack.",
                        "Nothing reads generated_overrides back. A file there is ready to be corrected by"
                                + " hand and dropped into a resource pack or a server's"
                                + " server_item_overrides folder.")),

        RELOAD("reload", "Re-read the server override folder and re-sync players", Gate.OPERATOR,
                List.of("/ss reload"),
                List.of("Reads config/somestacks/server_item_overrides/ again and pushes the current"
                                + " server config to every player.",
                        "It does not re-read the server config file. An edit made to that file directly"
                                + " applies when Forge reports the config reloaded.")),

        GEN("gen", "Edit the mod and item lists the test walls build from", Gate.OPERATOR,
                List.of("/ss gen mod add <modid>", "/ss gen mod remove <modid>", "/ss gen mod list",
                        "/ss gen item add <item>", "/ss gen item remove <item>", "/ss gen item list"),
                List.of("The mod list feeds /ss test list and /ss testingot list, in the order it is"
                                + " stored, which is the order of the wall's columns.",
                        "The item list feeds /ss test items. The row is sorted by mod id and then item"
                                + " name when it is built, so the stored order carries no meaning.",
                        "An item id is checked against the registry as it is added. A mod id is taken as"
                                + " typed.")),

        DENY("deny", "Bar a mod's items or one item from being stored", Gate.OPERATOR,
                List.of("/ss deny mod add <modid>", "/ss deny mod remove <modid>", "/ss deny mod list",
                        "/ss deny item add <item>", "/ss deny item remove <item>", "/ss deny item list"),
                List.of("A disabled mod's items are refused by every stack; a disabled item is refused on"
                                + " the deposit gestures. Contents already stored can still be taken"
                                + " out.",
                        "A mod id is taken as typed, because the shipped defaults name mods that need not"
                                + " be installed. An item id is checked against the registry, because the"
                                + " list is matched by exact id and a typo would sit in it looking"
                                + " effective.")),

        INGOT("ingot", "Edit which item tags a Bar Stack accepts", Gate.OPERATOR,
                List.of("/ss ingot add <tag>", "/ss ingot remove <tag>", "/ss ingot list"),
                List.of("A Bar Stack holds the items in these tags, and a Singles Stack holds"
                                + " everything else, so widening this list narrows Singles by as much."
                                + " Contents already stored can still be taken out either way.",
                        "An entry may contain * to match a run of any characters. The default"
                                + " forge:ingots* covers forge:ingots and every forge:ingots/<metal>"
                                + " beneath it, which reaches a mod that tags its ingots only under the"
                                + " child tag.",
                        "To accept a hand-picked set of items, make an item tag holding them in a data"
                                + " pack and add it here.",
                        "An entry is taken as typed, since it may name a tag no data pack has declared"
                                + " yet. An edit reports how many items are accepted afterwards, and an"
                                + " entry matching no tag at all is logged when the list is read.")),

        HELP("help", "List these commands, or explain one", Gate.ANYONE,
                List.of("/ss help", "/ss help <command>"),
                List.of("Every ss subcommand has an entry, and each one names the gate it answers to:"
                        + " ss is an administrator's tool throughout, and the two test wall commands"
                        + " sit a permission level above the rest because they overwrite the world."));

        private final String name;
        private final String summary;
        private final Gate gate;
        private final List<String> usages;
        private final List<String> detail;

        Topic(String name, String summary, Gate gate, List<String> usages, List<String> detail) {
            this.name = name;
            this.summary = summary;
            this.gate = gate;
            this.usages = usages;
            this.detail = detail;
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

    /** The list of subcommands, each with the one line that says whether it is the one wanted. */
    private static int index(CommandContext<CommandSourceStack> ctx) {
        send(ctx, Component.literal("Some Stacks commands").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        for (Topic topic : Topic.values()) {
            send(ctx, Component.literal("  /ss " + topic.name).withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" - " + topic.summary).withStyle(ChatFormatting.GRAY)));
        }

        send(ctx, Component.literal("Run /ss help <command> for detail.").withStyle(ChatFormatting.GRAY));
        return Topic.values().length;
    }

    private static int topic(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "command");
        Topic topic = Topic.byName(name);

        if (topic == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "No such ss command: " + name + ". Try one of: " + String.join(", ", Topic.names())));
            return 0;
        }

        send(ctx, Component.literal("/ss " + topic.name + " - " + topic.summary)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        for (String usage : topic.usages) {
            send(ctx, Component.literal("  " + usage).withStyle(ChatFormatting.YELLOW));
        }

        send(ctx, Component.literal("Run by " + topic.gate.audience + ".").withStyle(ChatFormatting.GRAY));

        for (String paragraph : topic.detail) {
            send(ctx, Component.literal(paragraph));
        }

        return 1;
    }

    /** Help answers the sender alone: it changes nothing and is of no interest to the other ops. */
    private static void send(CommandContext<CommandSourceStack> ctx, Component line) {
        ctx.getSource().sendSuccess(() -> line, false);
    }
}
