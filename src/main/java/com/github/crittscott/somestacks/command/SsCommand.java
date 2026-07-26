package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.client.RenderMode;
import com.github.crittscott.somestacks.network.ModNetworking;
import com.github.crittscott.somestacks.network.RenderOverridePkt;
import com.github.crittscott.somestacks.network.WriteOverridesPkt;
import com.github.crittscott.somestacks.util.OverrideJsonCodec;
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
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
 *   <li>{@code ss item <item> <mode> [<scale> [<x> <y> [<z>]]]} sets an entry in the issuing
 *       player's user override layer, applied immediately. Arguments the shorter forms leave
 *       off take the defaults an override entry omitting them would take: scale 1 and zero
 *       offset.</li>
 *   <li>{@code ss item <item> reset} removes that entry, restoring built-in or measured
 *       behavior.</li>
 *   <li>{@code ss write changed} asks the issuing player's client to write its user override
 *       layer to its override file.</li>
 *   <li>{@code ss write <modid|all|list>} asks that client to dump a complete profile for every
 *       item of those namespaces to its generated-override folder, measuring what no layer
 *       configures. Namespaces are named as the wall commands name them.</li>
 * </ul>
 *
 * <p>The administrative subcommands act on the server rather than on a view, so they are gated
 * by operator permission level. Most need no player and are usable from the console; the two wall
 * generators need one, because a wall is built where the sender stands:
 *
 * <ul>
 *   <li>{@code ss test <modid|all|list>} generates Storage Stack walls of every item in the
 *       given namespace, in all loaded namespaces, or in the namespaces named by the
 *       {@code gen_mods} server config list. The wall is built over the following ticks and
 *       reports again when it finishes.</li>
 *   <li>{@code ss test items} builds one row from the items named by the {@code gen_items} server
 *       config list, sorted by mod id and then item name.</li>
 *   <li>{@code ss testingot <modid|all|list>} does the same with Bar Stacks, over the ingots of
 *       those namespaces, one bar per ingot.</li>
 *   <li>{@code ss allow add|remove|list} edits the {@code ss_command_allowlist}.</li>
 *   <li>{@code ss gen mod add|remove|list} edits the {@code gen_mods} list the {@code list} form
 *       of the two test-wall commands builds from.</li>
 *   <li>{@code ss gen item add|remove|list} edits the {@code gen_items} list
 *       {@code ss test items} builds from.</li>
 *   <li>{@code ss deny mod add|remove|list} edits the disabled-mod list.</li>
 *   <li>{@code ss deny item add|remove|list} edits the disabled-item list.</li>
 *   <li>{@code ss ingot add|remove|list} edits the item tags a Bar Stack takes its contents
 *       from.</li>
 *   <li>{@code ss reload} re-reads the server override directory and pushes the current
 *       server config to every player.</li>
 * </ul>
 *
 * <p>Gating the list editors on operator permission rather than on the allow list is what makes
 * an empty allow list recoverable: the list is empty by default, so a gate that consulted it
 * could never be opened from in game.
 *
 * <p>{@code ss help} in {@link SsHelp} describes all of the above in game and is gated by
 * nothing, so a player who cannot run a subcommand can still read what it needs.
 */
public final class SsCommand {
    /** Vanilla's gamerule and op-command level, the gate on the administrative subcommands. */
    private static final int ADMIN_PERMISSION_LEVEL = 2;

    private static final String ALLOWLIST_LABEL = "ss allow list";
    private static final String DISABLED_MODS_LABEL = "disabled mod list";
    private static final String DISABLED_ITEMS_LABEL = "disabled item list";
    private static final String GEN_MODS_LABEL = "gen mod list";
    private static final String GEN_ITEMS_LABEL = "gen item list";
    private static final String INGOT_TAGS_LABEL = "ingot tag list";

    /** A dump covers every item, which is the namespace and item set the Storage kind holds. */
    private static final TestWallGenerator.Kind DUMP_KIND = TestWallGenerator.Kind.STORAGE;

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
                                                .executes(SsCommand::setMode)
                                                .then(Commands.argument("scale", scaleArg())
                                                        .executes(SsCommand::setModeAndScale)
                                                        .then(Commands.argument("x", offsetArg())
                                                                .then(Commands.argument("y", offsetArg())
                                                                        .executes(SsCommand::setModeScaleAndXy)
                                                                        .then(Commands.argument("z", offsetArg())
                                                                                .executes(SsCommand::setModeScaleAndXyz))))))))
                        .then(testTree("test", TestWallGenerator.Kind.STORAGE)
                                .then(Commands.literal("items")
                                        .executes(SsCommand::testItems)))
                        .then(testTree("testingot", TestWallGenerator.Kind.BAR))
                        .then(writeTree())
                        .then(Commands.literal("reload")
                                .requires(SsCommand::isAdmin)
                                .executes(SsCommand::reload))
                        .then(allowTree())
                        .then(genTree())
                        .then(denyTree())
                        .then(ingotTree())
                        .then(SsHelp.tree())
        );
    }

    /**
     * The rails on the two numeric arguments of {@code ss item}, taken from the override schema so
     * the command and a hand-written entry accept the same values. Declaring them on the argument
     * rather than testing after the fact is what makes an out-of-range value a parse error the
     * player sees against the offending word, and it is also what keeps a run of digits long enough
     * to overflow a float to infinity from reaching the render transform.
     */
    private static FloatArgumentType scaleArg() {
        return FloatArgumentType.floatArg(OverrideJsonCodec.MIN_SCALE, OverrideJsonCodec.MAX_SCALE);
    }

    private static FloatArgumentType offsetArg() {
        return FloatArgumentType.floatArg(OverrideJsonCodec.MIN_OFFSET, OverrideJsonCodec.MAX_OFFSET);
    }

    /**
     * The {@code <modid>|all|list} subtree one wall kind is generated from. The two kinds differ
     * only in which items they can show, which is what the kind itself answers. The Storage tree
     * carries an {@code items} form on top of this, which the ingot tree has no use for: the
     * ingots of a pack are few enough to review a namespace at a time.
     *
     * <p>Operator-gated rather than allow-listed, and player-only because the wall is built where
     * the sender stands. A wall is not a view: it overwrites a large region of the world outright,
     * without the protection consults a placement gesture answers to. That is an operator's
     * authority, not the authority to tune how items are drawn for oneself.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> testTree(
            String name, TestWallGenerator.Kind kind) {
        return Commands.literal(name)
                .requires(source -> isPlayer(source) && isAdmin(source))
                .then(Commands.literal("all")
                        .executes(ctx -> testAll(ctx, kind)))
                .then(Commands.literal("list")
                        .executes(ctx -> testList(ctx, kind)))
                .then(Commands.argument("modid", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(kind.modIds(), builder))
                        .executes(ctx -> testSingle(ctx, kind)));
    }

    /**
     * The {@code ss write} subtree. {@code changed} writes the user override layer to the file
     * the client loads at startup, so it holds only what {@code ss item} set. The namespace forms
     * dump complete profiles for every item of those namespaces to a folder nothing reads back,
     * which is what keeps a dump of a whole modpack from freezing that pack into the user layer.
     * Namespaces are named exactly as the wall commands name them.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> writeTree() {
        return Commands.literal("write")
                .requires(SsCommand::isPlayer)
                .then(Commands.literal("changed")
                        .executes(SsCommand::writeChanged))
                .then(Commands.literal("all")
                        .executes(SsCommand::dumpAll))
                .then(Commands.literal("list")
                        .executes(SsCommand::dumpList))
                .then(Commands.argument("modid", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(DUMP_KIND.modIds(), builder))
                        .executes(SsCommand::dumpSingle));
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
                        .executes(ctx -> listEntries(ctx, ServerConfig.SS_COMMAND_ALLOWLIST, ALLOWLIST_LABEL, true)));
    }

    /**
     * The {@code ss gen} subtree, editing the two lists the wall commands build from: the
     * namespaces of {@code ss test list} and {@code ss testingot list}, and the items of
     * {@code ss test items}. These are operator commands like the other list editors, because they
     * edit server config rather than the issuing player's view; running the walls they describe
     * stays with the allow list.
     *
     * <p>Adding a namespace completes over those that have items at all rather than over one
     * kind's, since the one list serves both kinds and a namespace with no ingots is still worth
     * listing for {@code ss test list}. An item id is checked against the registry as
     * {@code ss deny item add} checks one, since a typo would otherwise sit in the list and be
     * skipped by every wall.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> genTree() {
        return Commands.literal("gen")
                .requires(SsCommand::isAdmin)
                .then(Commands.literal("mod")
                        .then(Commands.literal("add")
                                .then(Commands.argument("modid", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                TestWallGenerator.getModIdsWithItems(), builder))
                                        .executes(ctx -> addEntry(ctx, ServerConfig.GEN_MODS, GEN_MODS_LABEL,
                                                StringArgumentType.getString(ctx, "modid")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("modid", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestEntries(ServerConfig.GEN_MODS, builder))
                                        .executes(ctx -> removeEntry(ctx, ServerConfig.GEN_MODS, GEN_MODS_LABEL,
                                                StringArgumentType.getString(ctx, "modid")))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listEntries(ctx, ServerConfig.GEN_MODS, GEN_MODS_LABEL, false))))
                .then(Commands.literal("item")
                        .then(Commands.literal("add")
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .suggests(SsCommand::suggestItems)
                                        .executes(ctx -> addItemEntry(
                                                ctx, ServerConfig.GEN_ITEMS, GEN_ITEMS_LABEL))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> suggestEntries(ServerConfig.GEN_ITEMS, builder))
                                        .executes(ctx -> removeEntry(ctx, ServerConfig.GEN_ITEMS, GEN_ITEMS_LABEL,
                                                ResourceLocationArgument.getId(ctx, "item").toString()))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listEntries(ctx, ServerConfig.GEN_ITEMS, GEN_ITEMS_LABEL, true))));
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
                                .executes(ctx -> listEntries(ctx, ServerConfig.DISABLE_MODS, DISABLED_MODS_LABEL, true))))
                .then(Commands.literal("item")
                        .then(Commands.literal("add")
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .suggests(SsCommand::suggestItems)
                                        .executes(ctx -> addItemEntry(
                                                ctx, ServerConfig.DISABLE_ITEMS, DISABLED_ITEMS_LABEL))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> suggestEntries(ServerConfig.DISABLE_ITEMS, builder))
                                        .executes(ctx -> removeEntry(ctx, ServerConfig.DISABLE_ITEMS, DISABLED_ITEMS_LABEL,
                                                ResourceLocationArgument.getId(ctx, "item").toString()))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listEntries(ctx, ServerConfig.DISABLE_ITEMS, DISABLED_ITEMS_LABEL, true))));
    }

    /**
     * The {@code ss ingot} subtree, editing the tags a Bar Stack takes its contents from. An entry
     * may carry {@code *} wildcards, so it is read as a greedy string rather than as a resource
     * location: neither a wildcard nor an unquoted colon survives the word parser.
     *
     * <p>An entry is taken as typed, as a denied mod id is, because it may legitimately name a tag
     * no loaded data pack declares or match one by wildcard. A typo is caught where it shows: the
     * bake warns about an entry matching no tag, and an edit reports how many items the list now
     * accepts, so an entry that did nothing says so.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> ingotTree() {
        return Commands.literal("ingot")
                .requires(SsCommand::isAdmin)
                .then(Commands.literal("add")
                        .then(Commands.argument("tag", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        ServerConfig.itemTagNames(), builder))
                                .executes(ctx -> reportIngotEdit(ctx, addEntry(ctx, ServerConfig.INGOT_TAGS,
                                        INGOT_TAGS_LABEL,
                                        StringArgumentType.getString(ctx, "tag").trim())))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("tag", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> suggestEntries(ServerConfig.INGOT_TAGS, builder))
                                .executes(ctx -> reportIngotEdit(ctx, removeEntry(ctx, ServerConfig.INGOT_TAGS,
                                        INGOT_TAGS_LABEL,
                                        StringArgumentType.getString(ctx, "tag").trim())))))
                .then(Commands.literal("list")
                        .executes(ctx -> listEntries(ctx, ServerConfig.INGOT_TAGS, INGOT_TAGS_LABEL, true)));
    }

    /**
     * Follows an ingot list edit with what it did to the accepted set, which is the answer the
     * editor is after: the list names tags, and what a Bar Stack holds is the items in them.
     */
    private static int reportIngotEdit(CommandContext<CommandSourceStack> ctx, int result) {
        if (result != 0) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Bar Stacks now accept " + ServerConfig.ingotItemCount() + " item(s)"), false);
        }
        return result;
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

    /**
     * The four forms of {@code ss item <item> <mode> ...}, each supplying the arguments the
     * shorter ones leave off. The defaults match what an override entry omitting those fields
     * resolves to, so {@code ss item foo:bar gui} and a hand-written {@code {"mode": "gui"}}
     * render the same way. Brigadier cannot report which optional nodes it parsed, so each form
     * passes its own values down rather than probing the context.
     */
    private static int setMode(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return setItem(ctx, 1.0f, new float[3]);
    }

    private static int setModeAndScale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return setItem(ctx, FloatArgumentType.getFloat(ctx, "scale"), new float[3]);
    }

    private static int setModeScaleAndXy(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return setItem(ctx, FloatArgumentType.getFloat(ctx, "scale"), new float[]{
                FloatArgumentType.getFloat(ctx, "x"), FloatArgumentType.getFloat(ctx, "y"), 0.0f});
    }

    private static int setModeScaleAndXyz(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return setItem(ctx, FloatArgumentType.getFloat(ctx, "scale"), new float[]{
                FloatArgumentType.getFloat(ctx, "x"),
                FloatArgumentType.getFloat(ctx, "y"),
                FloatArgumentType.getFloat(ctx, "z")});
    }

    private static int setItem(CommandContext<CommandSourceStack> ctx, float scale, float[] offset)
            throws CommandSyntaxException {
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

        RenderOverridePkt packet = RenderOverridePkt.set(itemId, modeString, scale, offset);
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);

        ctx.getSource().sendSuccess(() -> Component.literal(
                "Set render override for " + itemId + ": mode=" + modeString
                        + ", scale=" + scale + ", offset=["
                        + offset[0] + "," + offset[1] + "," + offset[2] + "]"), false);
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

    /** Entries an invocation dropped from a request, and the one reason they share. */
    private record Skips(String reason, List<String> entries) {}

    /**
     * The namespaces one invocation acts on, with the ones dropped from the request and why.
     * The wall commands and the dump select namespaces the same way, so a review session can
     * dump exactly what it just looked at.
     */
    private record Selection(TestWallGenerator.Kind kind, List<String> modIds,
                             List<String> disabledMods, List<String> unusableMods) {
        private List<Skips> skips() {
            return List.of(new Skips("disabled", disabledMods),
                    new Skips("not loaded or with no " + kind.itemLabel(), unusableMods));
        }

        private List<List<Item>> groups() {
            return modIds.stream().map(kind::itemsIn).toList();
        }
    }

    /** The items one invocation acts on, with the gen item list entries it dropped and why. */
    private record ItemSelection(List<Item> items, List<Skips> skips) {}

    /**
     * The one namespace named, or null when it cannot be used. Naming a single namespace fails
     * rather than skipping, because the command names one thing and it did not happen.
     *
     * <p>Being disabled is reported ahead of having nothing to show, as it is in the bulk forms: a
     * kind answers what it can show by asking the stack itself, which refuses a disabled mod's
     * items outright, so a disabled namespace looks empty and the emptiness would be reported as
     * the typo it is not.
     */
    @Nullable
    private static Selection selectSingle(CommandContext<CommandSourceStack> ctx,
                                          TestWallGenerator.Kind kind, String modId) {
        if (ServerConfig.isModDisabled(modId)) {
            ctx.getSource().sendFailure(Component.literal(modId + " is disabled in server config"));
            return null;
        }

        if (kind.itemsIn(modId).isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    modId + " is not loaded or has no " + kind.itemLabel()));
            return null;
        }

        return new Selection(kind, List.of(modId), List.of(), List.of());
    }

    /** Every namespace this kind can use, minus those the server disabled; null when none remain. */
    @Nullable
    private static Selection selectAll(CommandContext<CommandSourceStack> ctx, TestWallGenerator.Kind kind) {
        List<String> modIds = new ArrayList<>(kind.modIds());
        Collections.sort(modIds);

        List<String> disabledMods = new ArrayList<>();
        modIds.removeIf(modId -> {
            if (ServerConfig.isModDisabled(modId)) {
                disabledMods.add(modId);
                return true;
            }
            return false;
        });

        if (modIds.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("All loaded mods are disabled in server config"));
            return null;
        }

        return new Selection(kind, modIds, disabledMods, List.of());
    }

    /**
     * The namespaces of the {@code gen_mods} server config list, in the order that list holds.
     * Like {@code all} and unlike a single namespace, an entry that cannot be used is skipped and
     * reported rather than failing the command, since the list is edited ahead of use and one bad
     * entry should not withhold the rest. The two reasons are reported apart: a namespace the
     * server disabled is doing what it was told, while one that is unloaded or holds none of this
     * kind's items is usually a typo or an entry meant for the other kind.
     */
    @Nullable
    private static Selection selectList(CommandContext<CommandSourceStack> ctx, TestWallGenerator.Kind kind) {
        List<String> modIds = new ArrayList<>();
        List<String> disabledMods = new ArrayList<>();
        List<String> unusableMods = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String entry : ServerConfig.GEN_MODS.get()) {
            String modId = entry.trim().toLowerCase(Locale.ROOT);
            if (modId.isEmpty() || !seen.add(modId)) {
                continue;
            }

            if (ServerConfig.isModDisabled(modId)) {
                disabledMods.add(modId);
            } else if (kind.itemsIn(modId).isEmpty()) {
                unusableMods.add(modId);
            } else {
                modIds.add(modId);
            }
        }

        if (modIds.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(seen.isEmpty()
                    ? "The " + GEN_MODS_LABEL + " is empty; add a mod with /ss gen mod add <modid>"
                    : "No mod in the " + GEN_MODS_LABEL + " can show " + kind.itemLabel() + ": "
                            + disabledMods.size() + " disabled, "
                            + unusableMods.size() + " not loaded or with none"));
            return null;
        }

        return new Selection(kind, modIds, disabledMods, unusableMods);
    }

    /**
     * The items of the {@code gen_items} server config list, sorted by mod id and then item name so
     * a row reads the same way however the list was built up. Like {@code all} and {@code list} and
     * unlike a single named namespace, an entry that cannot be used is skipped and reported rather
     * than failing the command. The two reasons are reported apart: an item barred by the
     * disabled-mod list is doing what the server was told, while one the registry does not know is
     * a typo, or a mod that has been removed since the entry was added.
     */
    @Nullable
    private static ItemSelection selectItems(CommandContext<CommandSourceStack> ctx) {
        List<ResourceLocation> itemIds = new ArrayList<>();
        List<String> disabledItems = new ArrayList<>();
        List<String> unknownItems = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String entry : ServerConfig.GEN_ITEMS.get()) {
            String trimmed = entry.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty() || !seen.add(trimmed)) {
                continue;
            }

            ResourceLocation itemId = ResourceLocation.tryParse(trimmed);
            if (itemId == null || !ForgeRegistries.ITEMS.containsKey(itemId)) {
                unknownItems.add(trimmed);
            } else if (ServerConfig.isModDisabled(itemId.getNamespace())) {
                disabledItems.add(trimmed);
            } else {
                itemIds.add(itemId);
            }
        }

        if (itemIds.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(seen.isEmpty()
                    ? "The " + GEN_ITEMS_LABEL + " is empty; add an item with /ss gen item add <item>"
                    : "No item in the " + GEN_ITEMS_LABEL + " can be shown: "
                            + disabledItems.size() + " from disabled mods, "
                            + unknownItems.size() + " unknown"));
            return null;
        }

        itemIds.sort(Comparator.comparing(ResourceLocation::getNamespace)
                .thenComparing(ResourceLocation::getPath));

        return new ItemSelection(
                itemIds.stream().map(ForgeRegistries.ITEMS::getValue).toList(),
                List.of(new Skips("from disabled mods", disabledItems),
                        new Skips("unknown", unknownItems)));
    }

    private static int testSingle(CommandContext<CommandSourceStack> ctx, TestWallGenerator.Kind kind)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Selection selection = selectSingle(ctx, kind, StringArgumentType.getString(ctx, "modid"));
        return selection == null ? 0 : generate(ctx, player, selection);
    }

    private static int testAll(CommandContext<CommandSourceStack> ctx, TestWallGenerator.Kind kind)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Selection selection = selectAll(ctx, kind);
        return selection == null ? 0 : generate(ctx, player, selection);
    }

    private static int testList(CommandContext<CommandSourceStack> ctx, TestWallGenerator.Kind kind)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Selection selection = selectList(ctx, kind);
        return selection == null ? 0 : generate(ctx, player, selection);
    }

    /**
     * Builds the row of the {@code gen_items} server config list. One group means one column, so a
     * list longer than a stack holds runs on north exactly as a namespace with many items does.
     */
    private static int testItems(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        ItemSelection selection = selectItems(ctx);
        return selection == null ? 0 : generate(ctx, player, TestWallGenerator.Kind.STORAGE,
                List.of(selection.items()), "the " + GEN_ITEMS_LABEL, selection.skips());
    }

    private static String subject(List<String> modIds) {
        return modIds.size() == 1 ? modIds.get(0) : modIds.size() + " mods";
    }

    /** Reports what a bulk form dropped, one clause per reason and none for a reason with nothing. */
    private static void appendSkips(StringBuilder message, List<Skips> skips) {
        for (Skips skip : skips) {
            if (!skip.entries().isEmpty()) {
                message.append(" Skipped ").append(skip.entries().size()).append(' ')
                        .append(skip.reason()).append(": ")
                        .append(String.join(", ", skip.entries())).append('.');
            }
        }
    }

    private static int generate(CommandContext<CommandSourceStack> ctx, ServerPlayer player,
                                Selection selection) {
        return generate(ctx, player, selection.kind(), selection.groups(),
                subject(selection.modIds()), selection.skips());
    }

    /**
     * Queues one wall and reports it twice: what it will hold now, and what it did hold once the
     * generator has finished placing it some ticks later.
     */
    private static int generate(CommandContext<CommandSourceStack> ctx, ServerPlayer player,
                                TestWallGenerator.Kind kind, List<List<Item>> groups,
                                String subject, List<Skips> skips) {
        TestWallGenerator.Plan plan = TestWallGenerator.enqueue(player, kind, groups, result -> {
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

        appendSkips(message, skips);

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
     * Rejects an item the registry does not know before it reaches one of the item lists. Both are
     * consulted by exact id, so a typo would sit in the list looking effective while barring
     * nothing or showing nothing.
     */
    private static int addItemEntry(CommandContext<CommandSourceStack> ctx,
                                    ForgeConfigSpec.ConfigValue<List<? extends String>> list, String label) {
        ResourceLocation itemId = ResourceLocationArgument.getId(ctx, "item");
        if (!ForgeRegistries.ITEMS.containsKey(itemId)) {
            ctx.getSource().sendFailure(Component.literal("Unknown item: " + itemId));
            return 0;
        }
        return addEntry(ctx, list, label, itemId.toString());
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

    /**
     * Reports one of the server's text lists. Sorting is for the lists whose order means nothing;
     * the gen mod list is shown as stored, because that order is the order of a wall's columns.
     */
    private static int listEntries(CommandContext<CommandSourceStack> ctx,
                                   ForgeConfigSpec.ConfigValue<List<? extends String>> list, String label,
                                   boolean sort) {
        List<String> entries = new ArrayList<>(list.get());
        if (entries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("The " + label + " is empty"), false);
            return 0;
        }

        if (sort) {
            Collections.sort(entries);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "The " + label + " holds " + entries.size() + ": " + String.join(", ", entries)), false);
        return entries.size();
    }

    private static int writeChanged(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!checkAllowed(ctx)) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), WriteOverridesPkt.userLayer());
        return 1;
    }

    private static int dumpSingle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!checkAllowed(ctx)) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Selection selection = selectSingle(ctx, DUMP_KIND, StringArgumentType.getString(ctx, "modid"));
        return selection == null ? 0 : dump(ctx, player, selection);
    }

    private static int dumpAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!checkAllowed(ctx)) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Selection selection = selectAll(ctx, DUMP_KIND);
        return selection == null ? 0 : dump(ctx, player, selection);
    }

    private static int dumpList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!checkAllowed(ctx)) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Selection selection = selectList(ctx, DUMP_KIND);
        return selection == null ? 0 : dump(ctx, player, selection);
    }

    /**
     * Hands the client the namespaces to dump. The work is all on that client: it resolves and
     * measures every item and reports what it wrote, which for a large pack takes long enough to
     * be worth saying so here.
     */
    private static int dump(CommandContext<CommandSourceStack> ctx, ServerPlayer player, Selection selection) {
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                WriteOverridesPkt.dump(selection.modIds()));

        StringBuilder message = new StringBuilder("Dumping render profiles for ")
                .append(subject(selection.modIds()))
                .append("; unmeasured items are measured now, and the client reports what it wrote.");
        appendSkips(message, selection.skips());

        final String finalMessage = message.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(finalMessage), false);
        return 1;
    }
}
