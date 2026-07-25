package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.client.RenderMode;
import com.github.crittscott.somestacks.network.ModNetworking;
import com.github.crittscott.somestacks.network.RenderOverridePkt;
import com.github.crittscott.somestacks.network.WriteOverridesPkt;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * The {@code ss} command: render override authoring, test wall generation, server list
 * editing, and override reloading.
 *
 * <p>The render subcommands are issued against the sender's own view, so they are player-only
 * and gated by the {@code ss_command_allowlist} server config list:
 *
 * <ul>
 *   <li>{@code ss item <item> <mode> <scale> <x> <y> <z>} sets an entry in the issuing
 *       player's user override layer, applied immediately.</li>
 *   <li>{@code ss item <item> reset} removes that entry, restoring built-in or measured
 *       behavior.</li>
 *   <li>{@code ss test <modid|all>} generates Storage Stack walls of every item in the
 *       given namespace, or in all loaded namespaces. The wall is built over the following
 *       ticks and reports again when it finishes.</li>
 *   <li>{@code ss testingot <modid|all>} does the same with Bar Stacks, over the ingots of
 *       those namespaces, one bar per ingot.</li>
 *   <li>{@code ss write} asks the issuing player's client to write its user override
 *       layer to its override file.</li>
 * </ul>
 *
 * <p>The administrative subcommands act on the server rather than on a view, so they are gated
 * by operator permission level and need no player, leaving them usable from the console:
 *
 * <ul>
 *   <li>{@code ss allow add|remove|list} edits the {@code ss_command_allowlist}.</li>
 *   <li>{@code ss deny mod add|remove|list} edits the disabled-mod list.</li>
 *   <li>{@code ss deny item add|remove|list} edits the disabled-item list.</li>
 *   <li>{@code ss reload} re-reads the server override directory and pushes the current
 *       server config to every player.</li>
 * </ul>
 *
 * <p>Gating the list editors on operator permission rather than on the allow list is what makes
 * an empty allow list recoverable: the list is empty by default, so a gate that consulted it
 * could never be opened from in game.
 */
public final class SsCommand {
    /** Vanilla's gamerule and op-command level, the gate on the administrative subcommands. */
    private static final int ADMIN_PERMISSION_LEVEL = 2;

    private static final String ALLOWLIST_LABEL = "ss allow list";
    private static final String DISABLED_MODS_LABEL = "disabled mod list";
    private static final String DISABLED_ITEMS_LABEL = "disabled item list";

    private SsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("ss")
                        .then(Commands.literal("item")
                                .requires(SsCommand::isPlayer)
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
                        .then(testTree("test", TestWallGenerator.Kind.STORAGE))
                        .then(testTree("testingot", TestWallGenerator.Kind.BAR))
                        .then(Commands.literal("write")
                                .requires(SsCommand::isPlayer)
                                .executes(SsCommand::write))
                        .then(Commands.literal("reload")
                                .requires(SsCommand::isAdmin)
                                .executes(SsCommand::reload))
                        .then(allowTree())
                        .then(denyTree())
        );
    }

    /**
     * The {@code <modid>|all} subtree one wall kind is generated from. The two kinds differ only
     * in which items they can show, which is what the kind itself answers.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> testTree(
            String name, TestWallGenerator.Kind kind) {
        return Commands.literal(name)
                .requires(SsCommand::isPlayer)
                .then(Commands.literal("all")
                        .executes(ctx -> testAll(ctx, kind)))
                .then(Commands.argument("modid", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(kind.modIds(), builder))
                        .executes(ctx -> testSingle(ctx, kind)));
    }

    /**
     * The {@code ss allow} subtree, editing which players may use the render subcommands. Adding
     * completes over the players currently online, since a name reaches the list before its owner
     * has ever needed it; removing completes over the list itself.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> allowTree() {
        return Commands.literal("allow")
                .requires(SsCommand::isAdmin)
                .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        ctx.getSource().getOnlinePlayerNames(), builder))
                                .executes(ctx -> addEntry(ctx, ServerConfig.SS_COMMAND_ALLOWLIST, ALLOWLIST_LABEL,
                                        StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestEntries(ServerConfig.SS_COMMAND_ALLOWLIST, builder))
                                .executes(ctx -> removeEntry(ctx, ServerConfig.SS_COMMAND_ALLOWLIST, ALLOWLIST_LABEL,
                                        StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("list")
                        .executes(ctx -> listEntries(ctx, ServerConfig.SS_COMMAND_ALLOWLIST, ALLOWLIST_LABEL)));
    }

    /**
     * The {@code ss deny} subtree, editing the two compatibility lists. A namespace is taken as
     * typed because the shipped defaults name mods that need not be installed, so the list is
     * expected to hold namespaces the registry cannot confirm; an item id is checked against the
     * registry, where a typo would otherwise sit in the list looking effective.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> denyTree() {
        return Commands.literal("deny")
                .requires(SsCommand::isAdmin)
                .then(Commands.literal("mod")
                        .then(Commands.literal("add")
                                .then(Commands.argument("modid", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                TestWallGenerator.getModIdsWithItems(), builder))
                                        .executes(ctx -> addEntry(ctx, ServerConfig.DISABLE_MODS, DISABLED_MODS_LABEL,
                                                StringArgumentType.getString(ctx, "modid")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("modid", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestEntries(ServerConfig.DISABLE_MODS, builder))
                                        .executes(ctx -> removeEntry(ctx, ServerConfig.DISABLE_MODS, DISABLED_MODS_LABEL,
                                                StringArgumentType.getString(ctx, "modid")))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listEntries(ctx, ServerConfig.DISABLE_MODS, DISABLED_MODS_LABEL))))
                .then(Commands.literal("item")
                        .then(Commands.literal("add")
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .suggests(SsCommand::suggestItems)
                                        .executes(SsCommand::denyItemAdd)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> suggestEntries(ServerConfig.DISABLE_ITEMS, builder))
                                        .executes(ctx -> removeEntry(ctx, ServerConfig.DISABLE_ITEMS, DISABLED_ITEMS_LABEL,
                                                ResourceLocationArgument.getId(ctx, "item").toString()))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listEntries(ctx, ServerConfig.DISABLE_ITEMS, DISABLED_ITEMS_LABEL))));
    }

    private static boolean isPlayer(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer;
    }

    private static boolean isAdmin(CommandSourceStack source) {
        return source.hasPermission(ADMIN_PERMISSION_LEVEL);
    }

    /**
     * Gate the render subcommands on the {@code ss_command_allowlist} server config list. A player
     * not on it is given the command an operator runs to add them. Returns whether the command may
     * proceed.
     */
    private static boolean checkAllowed(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("This /ss subcommand must be run by a player"));
            return false;
        }

        String name = player.getGameProfile().getName();
        if (ServerConfig.isSsAllowed(name)) {
            return true;
        }

        ctx.getSource().sendFailure(Component.literal(
                "You are not permitted to use /ss. Ask a server operator to run: /ss allow add " + name));
        return false;
    }

    private static CompletableFuture<Suggestions> suggestEntries(
            ForgeConfigSpec.ConfigValue<List<? extends String>> list, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.copyOf(list.get()), builder);
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
        if (!checkAllowed(ctx)) return 0;
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
        if (!checkAllowed(ctx)) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        ResourceLocation itemId = ResourceLocationArgument.getId(ctx, "item");
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), RenderOverridePkt.reset(itemId));

        ctx.getSource().sendSuccess(() -> Component.literal(
                "Reset render override for " + itemId + "; it now uses built-in or measured settings"), false);
        return 1;
    }

    private static int testSingle(CommandContext<CommandSourceStack> ctx, TestWallGenerator.Kind kind)
            throws CommandSyntaxException {
        if (!checkAllowed(ctx)) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String modId = StringArgumentType.getString(ctx, "modid");

        if (kind.itemsIn(modId).isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    modId + " is not loaded or has no " + kind.itemLabel()));
            return 0;
        }

        if (ServerConfig.isModDisabled(modId)) {
            ctx.getSource().sendFailure(Component.literal(modId + " is disabled in server config"));
            return 0;
        }

        return generate(ctx, player, kind, List.of(modId), List.of());
    }

    private static int testAll(CommandContext<CommandSourceStack> ctx, TestWallGenerator.Kind kind)
            throws CommandSyntaxException {
        if (!checkAllowed(ctx)) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        List<String> modIds = new ArrayList<>(kind.modIds());
        Collections.sort(modIds);

        List<String> skippedMods = new ArrayList<>();
        modIds.removeIf(modId -> {
            if (ServerConfig.isModDisabled(modId)) {
                skippedMods.add(modId);
                return true;
            }
            return false;
        });

        if (modIds.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("All loaded mods are disabled in server config"));
            return 0;
        }

        return generate(ctx, player, kind, modIds, skippedMods);
    }

    private static int generate(CommandContext<CommandSourceStack> ctx, ServerPlayer player,
                                TestWallGenerator.Kind kind, List<String> modIds, List<String> skippedMods) {
        String subject = modIds.size() == 1 ? modIds.get(0) : modIds.size() + " mods";

        TestWallGenerator.Plan plan = TestWallGenerator.enqueue(player, kind, modIds, result -> {
            StringBuilder done = new StringBuilder("Created ")
                    .append(result.totalStacks()).append(' ').append(kind.stackLabel()).append(" (")
                    .append(result.totalItems()).append(' ').append(kind.itemLabel()).append(") for ").append(subject)
                    .append(". Rows run north.");

            if (result.totalStacks() < result.expectedStacks()) {
                done.append(" WARNING: ").append(result.expectedStacks() - result.totalStacks()).append(" of ")
                        .append(result.expectedStacks()).append(" placements failed; see log for positions and reasons.");
            }

            player.sendSystemMessage(Component.literal(done.toString()));
        });

        StringBuilder message = new StringBuilder("Building ")
                .append(plan.expectedStacks()).append(' ').append(kind.stackLabel()).append(" (")
                .append(plan.totalItems()).append(' ').append(kind.itemLabel()).append(") for ")
                .append(subject).append('.');

        if (!skippedMods.isEmpty()) {
            message.append(" Skipped ").append(skippedMods.size())
                    .append(" disabled: ").append(String.join(", ", skippedMods)).append('.');
        }

        final String finalMessage = message.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(finalMessage), true);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        int synced = SomeStacks.syncAllPlayers(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Reloaded Some Stacks server overrides and synced " + synced + " player(s)"), true);
        return synced;
    }

    /**
     * Rejects an item the registry does not know before it reaches the disabled-item list. That
     * list is consulted by exact id, so a typo would sit in it looking effective while barring
     * nothing.
     */
    private static int denyItemAdd(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation itemId = ResourceLocationArgument.getId(ctx, "item");
        if (!ForgeRegistries.ITEMS.containsKey(itemId)) {
            ctx.getSource().sendFailure(Component.literal("Unknown item: " + itemId));
            return 0;
        }
        return addEntry(ctx, ServerConfig.DISABLE_ITEMS, DISABLED_ITEMS_LABEL, itemId.toString());
    }

    private static int addEntry(CommandContext<CommandSourceStack> ctx,
                                ForgeConfigSpec.ConfigValue<List<? extends String>> list,
                                String label, String entry) {
        if (!ServerConfig.addListEntry(list, entry)) {
            ctx.getSource().sendFailure(Component.literal(
                    "The " + label + " already contains " + entry));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal(
                "Added " + entry + " to the " + label), true);
        return 1;
    }

    private static int removeEntry(CommandContext<CommandSourceStack> ctx,
                                   ForgeConfigSpec.ConfigValue<List<? extends String>> list,
                                   String label, String entry) {
        if (!ServerConfig.removeListEntry(list, entry)) {
            ctx.getSource().sendFailure(Component.literal(
                    "The " + label + " does not contain " + entry));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal(
                "Removed " + entry + " from the " + label), true);
        return 1;
    }

    private static int listEntries(CommandContext<CommandSourceStack> ctx,
                                   ForgeConfigSpec.ConfigValue<List<? extends String>> list, String label) {
        List<String> entries = new ArrayList<>(list.get());
        if (entries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("The " + label + " is empty"), false);
            return 0;
        }

        Collections.sort(entries);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "The " + label + " holds " + entries.size() + ": " + String.join(", ", entries)), false);
        return entries.size();
    }

    private static int write(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!checkAllowed(ctx)) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new WriteOverridesPkt());
        return 1;
    }
}
