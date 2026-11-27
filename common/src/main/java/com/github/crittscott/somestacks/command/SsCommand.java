package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.SomeStacksServer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.Nullable;

/**
 * The server-side {@code ss} command: render gallery generation, server list editing, and server
 * policy reloading. Client-local render authoring lives in {@code ClientRenderCommands}.
 *
 * <p>Everything here is an administrative tool, so every subcommand but {@code help} requires a
 * vanilla permission level. The two gallery generators need a player because a gallery is built
 * where the sender stands.
 *
 * <p>The gallery generators sit a level above the rest. They overwrite a region of the world
 * outright, without the protection checks applied to placement gestures, which is a wider authority
 * than editing config or tuning how an item is drawn. Because of that, they are also the one pair
 * of subcommands gated by server config rather than a fixed level: {@code render_gallery.enabled}
 * (off by default) and {@code render_gallery.required_permission_level} (default 3).
 *
 * <p>{@link SsHelp} carries each subcommand's forms, gate, and summary, and {@code ss help} is
 * gated by nothing, so a player who cannot run a subcommand can still read what it needs.
 *
 * <p>The subcommand names are declared here for this tree and {@link SsHelp}, taking the ones the
 * client tree also registers from {@link ClientRenderCommandSyntax}; the messages both trees send
 * are declared here.
 */
public final class SsCommand {
    /** Vanilla's gamerule and world-editing level, the gate on the administrative subcommands. */
    private static final int GAME_MASTER_PERMISSION_LEVEL = Commands.LEVEL_GAMEMASTERS;

    static final String COMMAND_ROOT = ClientRenderCommandSyntax.ROOT;
    static final String COMMAND_ITEM = ClientRenderCommandSyntax.ITEM;
    static final String COMMAND_GALLERY = "gallery";
    static final String COMMAND_INGOT_GALLERY = "ingotgallery";
    static final String COMMAND_WRITE = ClientRenderCommandSyntax.WRITE;
    static final String COMMAND_RELOAD = "reload";
    static final String COMMAND_GEN = "gen";
    static final String COMMAND_DENY = "deny";
    static final String COMMAND_HELP = "help";

    private static final String ARG_ITEM = ClientRenderCommandSyntax.ARG_ITEM;
    private static final String ARG_MODID = ClientRenderCommandSyntax.ARG_MODID;

    /** Messages both this tree and the client tree in {@code ClientRenderCommands} send. */
    public static final String ALL_MODS_DISABLED = "somestacks.command.all_mods_disabled";

    public static final String GEN_MODS_EMPTY = "somestacks.command.gen_mods_empty";
    public static final String MOD_DISABLED = "somestacks.command.mod_disabled";
    public static final String SKIPPED_DISABLED_MODS = "somestacks.command.skipped.disabled_mods";

    /**
     * Prefixes of the messages about each server list. Each message is a whole sentence naming its
     * list, keyed by the prefix plus {@code .added}, {@code .contains}, {@code .empty}, {@code
     * .entries}, {@code .missing}, or {@code .removed}.
     */
    private static final String DISABLED_MODS_LIST = "somestacks.command.list.disabled_mods";

    private static final String DISABLED_ITEMS_LIST = "somestacks.command.list.disabled_items";
    private static final String GEN_MODS_LIST = "somestacks.command.list.gen_mods";
    private static final String GEN_ITEMS_LIST = "somestacks.command.list.gen_items";

    private SsCommand() {}

    // spotless:off
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext buildContext) {
        dispatcher.register(
                Commands.literal(COMMAND_ROOT)
                        .then(galleryTree(COMMAND_GALLERY, RenderGalleryGenerator.Kind.STORAGE)
                                .then(Commands.literal("items")
                                        .executes(SsCommand::galleryItems)))
                        .then(galleryTree(COMMAND_INGOT_GALLERY, RenderGalleryGenerator.Kind.BAR))
                        .then(Commands.literal(COMMAND_RELOAD)
                                .requires(SsCommand::isAdmin)
                                .executes(SsCommand::reload))
                        .then(genTree(buildContext))
                        .then(denyTree(buildContext))
                        .then(SsHelp.tree())
        );
    }
    // spotless:on

    /**
     * Builds the {@code <modid>|all|list} subtree for one gallery kind; the caller adds Storage's
     * separate {@code items} form, and Bar galleries are selected by namespace. Gallery commands
     * require a player, {@code render_gallery.enabled}, and {@code
     * render_gallery.required_permission_level}, because they overwrite blocks without ordinary
     * placement protection.
     */
    // spotless:off
    private static LiteralArgumentBuilder<CommandSourceStack> galleryTree(
            String name, RenderGalleryGenerator.Kind kind) {
        return Commands.literal(name)
                .requires(source -> {
                    ServerConfig.Settings policy = ServerConfig.settings();
                    return source.isPlayer() && policy.galleryEnabled()
                            && source.hasPermission(policy.galleryPermissionLevel());
                })
                .then(Commands.literal("all")
                        .executes(ctx -> galleryAll(ctx, kind)))
                .then(Commands.literal("list")
                        .executes(ctx -> galleryList(ctx, kind)))
                .then(Commands.argument(ARG_MODID, StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(kind.modIds(), builder))
                        .executes(ctx -> gallerySingle(ctx, kind)));
    }
    // spotless:on

    /**
     * The {@code ss gen} subtree, editing the two lists the gallery commands build from: the
     * namespaces of {@code ss gallery list} and {@code ss ingotgallery list}, and the items of
     * {@code ss gallery items}. Describing a gallery is an ordinary config edit and sits with the
     * other list editors; building the gallery it describes needs the higher level the gallery
     * commands hold.
     *
     * <p>Adding a namespace completes over those that have items at all rather than over one
     * kind's, since the one list serves both kinds and a namespace with no ingots is still worth
     * listing for {@code ss gallery list}. An item id is checked against the registry as {@code ss
     * deny item add} checks one, since a typo would otherwise sit in the list and be skipped by
     * every gallery.
     */
    // spotless:off
    private static LiteralArgumentBuilder<CommandSourceStack> genTree(
            CommandBuildContext buildContext) {
        return Commands.literal(COMMAND_GEN)
                .requires(SsCommand::isAdmin)
                .then(Commands.literal("mod")
                        .then(Commands.literal("add")
                                .then(Commands.argument(ARG_MODID, StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                RenderGalleryGenerator.Kind.STORAGE.modIds(), builder))
                                        .executes(ctx -> addEntry(ctx, ServerConfig.ListSetting.GEN_MODS, GEN_MODS_LIST,
                                                StringArgumentType.getString(ctx, ARG_MODID)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument(ARG_MODID, StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestEntries(ServerConfig.ListSetting.GEN_MODS, builder))
                                        .executes(ctx -> removeEntry(ctx, ServerConfig.ListSetting.GEN_MODS, GEN_MODS_LIST,
                                                StringArgumentType.getString(ctx, ARG_MODID)))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listEntries(ctx, ServerConfig.ListSetting.GEN_MODS, GEN_MODS_LIST))))
                .then(Commands.literal("item")
                        .then(Commands.literal("add")
                                .then(Commands.argument(
                                                ARG_ITEM,
                                                ResourceArgument.resource(buildContext, Registries.ITEM))
                                        .executes(ctx -> addItemEntry(
                                                ctx, ServerConfig.ListSetting.GEN_ITEMS, GEN_ITEMS_LIST))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument(ARG_ITEM, ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> suggestEntries(ServerConfig.ListSetting.GEN_ITEMS, builder))
                                        .executes(ctx -> removeEntry(ctx, ServerConfig.ListSetting.GEN_ITEMS, GEN_ITEMS_LIST,
                                                ResourceLocationArgument.getId(ctx, ARG_ITEM).toString()))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listEntries(ctx, ServerConfig.ListSetting.GEN_ITEMS, GEN_ITEMS_LIST))));
    }
    // spotless:on

    /**
     * The {@code ss deny} subtree, editing the two compatibility lists. A namespace is lowercased and
     * syntax-checked but need not be installed, since a server may bar a mod it does not have yet;
     * an item id is checked against the registry, where a typo would otherwise sit in the list looking effective.
     */
    // spotless:off
    private static LiteralArgumentBuilder<CommandSourceStack> denyTree(
            CommandBuildContext buildContext) {
        return Commands.literal(COMMAND_DENY)
                .requires(SsCommand::isAdmin)
                .then(Commands.literal("mod")
                        .then(Commands.literal("add")
                                .then(Commands.argument(ARG_MODID, StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                RenderGalleryGenerator.Kind.STORAGE.modIds(), builder))
                                        .executes(ctx -> addEntry(ctx, ServerConfig.ListSetting.DISABLE_MODS, DISABLED_MODS_LIST,
                                                StringArgumentType.getString(ctx, ARG_MODID)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument(ARG_MODID, StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestEntries(ServerConfig.ListSetting.DISABLE_MODS, builder))
                                        .executes(ctx -> removeEntry(ctx, ServerConfig.ListSetting.DISABLE_MODS, DISABLED_MODS_LIST,
                                                StringArgumentType.getString(ctx, ARG_MODID)))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listEntries(ctx, ServerConfig.ListSetting.DISABLE_MODS, DISABLED_MODS_LIST))))
                .then(Commands.literal("item")
                        .then(Commands.literal("add")
                                .then(Commands.argument(
                                                ARG_ITEM,
                                                ResourceArgument.resource(buildContext, Registries.ITEM))
                                        .executes(ctx -> addItemEntry(
                                                ctx, ServerConfig.ListSetting.DISABLE_ITEMS, DISABLED_ITEMS_LIST))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument(ARG_ITEM, ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> suggestEntries(ServerConfig.ListSetting.DISABLE_ITEMS, builder))
                                        .executes(ctx -> removeEntry(ctx, ServerConfig.ListSetting.DISABLE_ITEMS, DISABLED_ITEMS_LIST,
                                                ResourceLocationArgument.getId(ctx, ARG_ITEM).toString()))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listEntries(ctx, ServerConfig.ListSetting.DISABLE_ITEMS, DISABLED_ITEMS_LIST))));
    }
    // spotless:on

    private static boolean isAdmin(CommandSourceStack source) {
        return source.hasPermission(GAME_MASTER_PERMISSION_LEVEL);
    }

    private static CompletableFuture<Suggestions> suggestEntries(
            ServerConfig.ListSetting list, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.copyOf(list.get()), builder);
    }

    /**
     * Entries a bulk form left out for one reason. {@code key} is a whole sentence taking the count
     * and the comma-separated entries.
     */
    public record Skips(String key, List<String> entries) {
        /** Sends one sentence for each reason that left anything out. */
        public static void report(List<Skips> skips, Consumer<Component> send) {
            for (Skips skip : skips) {
                if (!skip.entries().isEmpty()) {
                    send.accept(
                            Component.translatable(
                                    skip.key(),
                                    skip.entries().size(),
                                    String.join(", ", skip.entries())));
                }
            }
        }
    }

    /**
     * What one gallery covers, as the last part of its progress messages: one named namespace,
     * several namespaces counted, or the gen item list.
     */
    private record Subject(String id, List<Object> args) {
        private static final Subject GEN_ITEMS = new Subject("list", List.of());

        private static Subject of(List<String> modIds) {
            return modIds.size() == 1
                    ? new Subject("mod", List.of(modIds.get(0)))
                    : new Subject("mods", List.of(modIds.size()));
        }
    }

    /**
     * The namespaces one gallery invocation acts on, with the ones dropped from the request and
     * why.
     */
    private record Selection(
            RenderGalleryGenerator.Kind kind,
            List<String> modIds,
            List<String> disabledMods,
            List<String> unusableMods) {
        private List<Skips> skips() {
            return List.of(
                    new Skips(SKIPPED_DISABLED_MODS, disabledMods),
                    new Skips(kind.skippedUnusableModsKey(), unusableMods));
        }

        private List<List<Item>> groups() {
            return modIds.stream().map(kind::itemsIn).toList();
        }
    }

    /** The items one invocation acts on, with the gen item list entries it dropped and why. */
    private record ItemSelection(List<Item> items, List<Skips> skips) {}

    /**
     * Resolves one explicitly named namespace, failing rather than skipping if it is unusable.
     * Disabled namespaces are checked before item selection because stack validity would otherwise
     * make them appear merely empty.
     */
    @Nullable
    private static Selection selectSingle(
            CommandContext<CommandSourceStack> ctx,
            RenderGalleryGenerator.Kind kind,
            String modId) {
        if (ServerConfig.isModDisabled(modId)) {
            ctx.getSource().sendFailure(Component.translatable(MOD_DISABLED, modId));
            return null;
        }

        if (kind.itemsIn(modId).isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable(kind.unusableModKey(), modId));
            return null;
        }

        return new Selection(kind, List.of(modId), List.of(), List.of());
    }

    /**
     * Every namespace this kind can use, minus those the server disabled; null when none remain.
     */
    @Nullable
    private static Selection selectAll(
            CommandContext<CommandSourceStack> ctx, RenderGalleryGenerator.Kind kind) {
        List<String> modIds = new ArrayList<>(kind.modIds());
        Collections.sort(modIds);

        List<String> disabledMods = new ArrayList<>();
        modIds.removeIf(modId -> {
            if (ServerConfig.isModDisabled(modId)) {
                disabledMods.add(modId);
                return true;
            }
            return kind.itemsIn(modId).isEmpty();
        });

        if (modIds.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable(ALL_MODS_DISABLED));
            return null;
        }

        return new Selection(kind, modIds, disabledMods, List.of());
    }

    /**
     * The namespaces of the {@code gen_mods} server config list, in id order like every gallery.
     * Like {@code all} and unlike a single namespace, an entry that cannot be used is skipped and
     * reported rather than failing the command, since the list is edited ahead of use and one bad
     * entry should not withhold the rest. The two reasons are reported apart: a namespace the
     * server disabled is doing what it was told, while one that is unloaded or holds none of this
     * kind's items is usually a typo or an entry meant for the other kind.
     */
    @Nullable
    private static Selection selectList(
            CommandContext<CommandSourceStack> ctx, RenderGalleryGenerator.Kind kind) {
        List<String> modIds = new ArrayList<>();
        List<String> disabledMods = new ArrayList<>();
        List<String> unusableMods = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String entry : ServerConfig.ListSetting.GEN_MODS.get()) {
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
            ctx.getSource()
                    .sendFailure(
                            seen.isEmpty()
                                    ? Component.translatable(GEN_MODS_EMPTY)
                                    : Component.translatable(
                                            kind.genModsUnusableKey(),
                                            disabledMods.size(),
                                            unusableMods.size()));
            return null;
        }

        Collections.sort(modIds);
        return new Selection(kind, modIds, disabledMods, unusableMods);
    }

    /**
     * The items of the {@code gen_items} server config list, sorted by mod id and then item name so
     * a row reads the same way however the list was built up. Like {@code all} and {@code list} and
     * unlike a single named namespace, an entry that cannot be used is skipped and reported rather
     * than failing the command. The two reasons are reported apart: an item barred by the
     * disabled-mod or disabled-item list is doing what the server was told, while one the registry
     * does not know is a typo, or a mod that has been removed since the entry was added.
     */
    @Nullable
    private static ItemSelection selectItems(CommandContext<CommandSourceStack> ctx) {
        List<ResourceLocation> itemIds = new ArrayList<>();
        List<String> disabledItems = new ArrayList<>();
        List<String> unknownItems = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String entry : ServerConfig.ListSetting.GEN_ITEMS.get()) {
            String trimmed = entry.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty() || !seen.add(trimmed)) {
                continue;
            }

            ResourceLocation itemId = ResourceLocation.tryParse(trimmed);
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                unknownItems.add(trimmed);
            } else if (ServerConfig.isModDisabled(itemId.getNamespace())
                    || ServerConfig.isItemDisabled(itemId)) {
                disabledItems.add(trimmed);
            } else {
                itemIds.add(itemId);
            }
        }

        if (itemIds.isEmpty()) {
            ctx.getSource()
                    .sendFailure(
                            seen.isEmpty()
                                    ? Component.translatable("somestacks.command.gen_items_empty")
                                    : Component.translatable(
                                            "somestacks.command.gen_items_unusable",
                                            disabledItems.size(),
                                            unknownItems.size()));
            return null;
        }

        itemIds.sort(
                Comparator.comparing(ResourceLocation::getNamespace)
                        .thenComparing(ResourceLocation::getPath));

        return new ItemSelection(
                itemIds.stream().map(BuiltInRegistries.ITEM::getValue).toList(),
                List.of(
                        new Skips("somestacks.command.skipped.disabled_items", disabledItems),
                        new Skips("somestacks.command.skipped.unknown_items", unknownItems)));
    }

    private static int gallerySingle(
            CommandContext<CommandSourceStack> ctx, RenderGalleryGenerator.Kind kind)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Selection selection = selectSingle(ctx, kind, StringArgumentType.getString(ctx, ARG_MODID));
        return selection == null ? 0 : generate(ctx, player, selection);
    }

    private static int galleryAll(
            CommandContext<CommandSourceStack> ctx, RenderGalleryGenerator.Kind kind)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Selection selection = selectAll(ctx, kind);
        return selection == null ? 0 : generate(ctx, player, selection);
    }

    private static int galleryList(
            CommandContext<CommandSourceStack> ctx, RenderGalleryGenerator.Kind kind)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Selection selection = selectList(ctx, kind);
        return selection == null ? 0 : generate(ctx, player, selection);
    }

    /**
     * Builds the row of the {@code gen_items} server config list. One group means one column, so a
     * list longer than a stack holds runs on north exactly as a namespace with many items does.
     */
    private static int galleryItems(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        ItemSelection selection = selectItems(ctx);
        return selection == null
                ? 0
                : generate(
                        ctx,
                        player,
                        RenderGalleryGenerator.Kind.STORAGE,
                        List.of(selection.items()),
                        Subject.GEN_ITEMS,
                        selection.skips());
    }

    private static int generate(
            CommandContext<CommandSourceStack> ctx, ServerPlayer player, Selection selection) {
        return generate(
                ctx,
                player,
                selection.kind(),
                selection.groups(),
                Subject.of(selection.modIds()),
                selection.skips());
    }

    /**
     * Queues one gallery, reports its planned contents and what was left out immediately, and
     * reports the actual result when the tick-budgeted generator finishes.
     */
    private static int generate(
            CommandContext<CommandSourceStack> ctx,
            ServerPlayer player,
            RenderGalleryGenerator.Kind kind,
            List<List<Item>> groups,
            Subject subject,
            List<Skips> skips) {
        RenderGalleryGenerator.Plan plan =
                RenderGalleryGenerator.enqueue(
                        player,
                        kind,
                        groups,
                        result ->
                                player.sendSystemMessage(
                                        result.totalStacks() < result.expectedStacks()
                                                ? galleryMessage(
                                                        kind,
                                                        "created_partial",
                                                        subject,
                                                        result.totalStacks(),
                                                        result.expectedStacks(),
                                                        result.totalItems())
                                                : galleryMessage(
                                                        kind,
                                                        "created",
                                                        subject,
                                                        result.totalStacks(),
                                                        result.totalItems())));

        Component building =
                galleryMessage(kind, "building", subject, plan.expectedStacks(), plan.totalItems());
        ctx.getSource().sendSuccess(() -> building, true);
        Skips.report(skips, line -> ctx.getSource().sendSuccess(() -> line, true));
        return 1;
    }

    /** One gallery progress sentence: the counts, then whatever names the subject. */
    private static Component galleryMessage(
            RenderGalleryGenerator.Kind kind, String phase, Subject subject, Object... counts) {
        Object[] args =
                Stream.concat(Stream.of(counts), subject.args().stream()).toArray(Object[]::new);
        return Component.translatable(kind.messageKey(phase, subject.id()), args);
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        if (SomeStacksServer.reloadPolicy(ctx.getSource().getServer()) < 0) {
            ctx.getSource()
                    .sendFailure(Component.translatable("somestacks.command.policy_rejected"));
            return 0;
        }
        int online = ctx.getSource().getServer().getPlayerList().getPlayerCount();
        ctx.getSource()
                .sendSuccess(
                        () -> Component.translatable("somestacks.command.reloaded", online), true);
        return 1;
    }

    /**
     * Rejects an item the registry does not know before it reaches one of the item lists. Both are
     * consulted by exact id, so a typo would sit in the list looking effective while barring
     * nothing or showing nothing.
     */
    private static int addItemEntry(
            CommandContext<CommandSourceStack> ctx,
            ServerConfig.ListSetting list,
            String listKey)
            throws CommandSyntaxException {
        ResourceLocation itemId = resourceItemId(ctx);
        return addEntry(ctx, list, listKey, itemId.toString());
    }

    private static ResourceLocation resourceItemId(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return ResourceArgument.getResource(ctx, ARG_ITEM, Registries.ITEM).key().location();
    }

    private static int addEntry(
            CommandContext<CommandSourceStack> ctx,
            ServerConfig.ListSetting list,
            String listKey,
            String entry) {
        boolean added;
        try {
            added = ServerConfig.addListEntry(list, entry);
        } catch (IllegalArgumentException rejected) {
            ctx.getSource()
                    .sendFailure(
                            Component.translatable(
                                    "somestacks.command.namespace_rejected",
                                    entry,
                                    ServerConfig.MAX_NAMESPACE_LENGTH,
                                    ServerConfig.MAX_NAMESPACE_ENTRIES));
            return 0;
        }
        if (!added) {
            ctx.getSource()
                    .sendFailure(Component.translatable(listKey + ".contains", entry));
            return 0;
        }

        ctx.getSource()
                .sendSuccess(
                        () -> Component.translatable(listKey + ".added", entry), true);
        // Keeps client-local `ss write list` inputs current.
        SomeStacksServer.publishPolicy(ctx.getSource().getServer());
        return 1;
    }

    private static int removeEntry(
            CommandContext<CommandSourceStack> ctx,
            ServerConfig.ListSetting list,
            String listKey,
            String entry) {
        if (!ServerConfig.removeListEntry(list, entry)) {
            ctx.getSource()
                    .sendFailure(Component.translatable(listKey + ".missing", entry));
            return 0;
        }

        ctx.getSource()
                .sendSuccess(
                        () -> Component.translatable(listKey + ".removed", entry), true);
        // Keeps client-local `ss write list` inputs current.
        SomeStacksServer.publishPolicy(ctx.getSource().getServer());
        return 1;
    }

    /** Reports one of the server's text lists, sorted, since no list's order means anything. */
    private static int listEntries(
            CommandContext<CommandSourceStack> ctx,
            ServerConfig.ListSetting list,
            String listKey) {
        List<String> entries = new ArrayList<>(list.get());
        if (entries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable(listKey + ".empty"), false);
            return 0;
        }

        Collections.sort(entries);
        ctx.getSource()
                .sendSuccess(
                        () ->
                                Component.translatable(
                                        listKey + ".entries",
                                        entries.size(),
                                        String.join(", ", entries)),
                        false);
        return entries.size();
    }
}
