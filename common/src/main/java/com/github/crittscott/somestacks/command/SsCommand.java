package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.ServerLifecycle;
import com.mojang.brigadier.CommandDispatcher;
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
import net.minecraft.core.registries.BuiltInRegistries;
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
 * The server-side {@code ss} command: render gallery generation, server list editing, and policy
 * reloading. Client-local render authoring lives in {@code ClientRenderCommands}.
 *
 * <p>Everything here is an administrative tool, so every subcommand but {@code help} requires a
 * vanilla permission level. The two gallery generators need a player because a gallery is built
 * where the sender stands.
 *
 * <p>The gallery generators sit a level above the rest. They overwrite a region of the world
 * outright, without the protection checks applied to placement gestures, which is a wider authority
 * than editing config. Because of that, they are also the one pair of subcommands gated by server
 * config rather than a fixed level: {@code render_gallery.enabled} (off by default) and {@code
 * render_gallery.required_permission_level} (default 3).
 *
 * <p>{@link SsHelp} carries each subcommand's forms, gate, and summary, and {@code ss help} is
 * gated by nothing, so a player who cannot run a subcommand can still read what it needs.
 *
 * <p>The subcommand and argument names, and the messages both trees send, are declared here once
 * for the server tree, the client tree in {@code ClientRenderCommands}, and {@link SsHelp}.
 */
public final class SsCommand {
    /** Vanilla's gamerule and world-editing level, the gate on the administrative subcommands. */
    private static final int ADMIN_PERMISSION_LEVEL = Commands.LEVEL_GAMEMASTERS;

    public static final String ALL_MODS_DISABLED = "somestacks.command.all_mods_disabled";
    public static final String ARG_ITEM = "item";
    public static final String ARG_MODID = "modid";
    public static final String DENY_COMMAND = "deny";
    public static final String GALLERY_COMMAND = "gallery";
    public static final String GEN_COMMAND = "gen";
    public static final String GEN_MODS_EMPTY = "somestacks.command.gen_mods_empty";
    public static final String HELP_COMMAND = "help";
    public static final String INGOTGALLERY_COMMAND = "ingotgallery";
    public static final String ITEM_COMMAND = "item";
    public static final String MOD_DISABLED = "somestacks.command.mod_disabled";
    public static final String RELOAD_COMMAND = "reload";

    /** Command root shared by the client and server trees. */
    public static final String ROOT = "ss";

    public static final String SKIPPED_DISABLED_MODS = "somestacks.command.skipped.disabled_mods";
    public static final String UNKNOWN_ITEM = "somestacks.command.unknown_item";
    public static final String WRITE_COMMAND = "write";

    private SsCommand() {}

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

    /** The items one invocation acts on, with the gen item list entries it dropped and why. */
    private record ItemSelection(List<Item> items, List<Skips> skips) {}

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

    // spotless:off
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal(ROOT)
                        .then(galleryTree(GALLERY_COMMAND, RenderGalleryGenerator.Kind.STORAGE)
                                .then(Commands.literal("items")
                                        .executes(SsCommand::galleryItems)))
                        .then(galleryTree(INGOTGALLERY_COMMAND, RenderGalleryGenerator.Kind.BAR))
                        .then(Commands.literal(RELOAD_COMMAND)
                                .requires(SsCommand::isAdmin)
                                .executes(SsCommand::reload))
                        .then(genTree())
                        .then(denyTree())
                        .then(SsHelp.tree())
        );
    }
    // spotless:on

    /**
     * Rejects an item the registry does not know before it reaches one of the item lists. Both are
     * consulted by exact id, so a typo would sit in the list looking effective while barring
     * nothing or showing nothing.
     */
    private static int addItemEntry(
            CommandContext<CommandSourceStack> ctx, ServerConfig.ListSetting list) {
        ResourceLocation itemId = ResourceLocationArgument.getId(ctx, ARG_ITEM);
        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            ctx.getSource().sendFailure(Component.translatable(UNKNOWN_ITEM, itemId.toString()));
            return 0;
        }
        return addEntry(ctx, list, itemId.toString());
    }

    private static int addEntry(
            CommandContext<CommandSourceStack> ctx, ServerConfig.ListSetting list, String entry) {
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
            ctx.getSource().sendFailure(Component.translatable(listKey(list, "contains"), entry));
            return 0;
        }

        ctx.getSource()
                .sendSuccess(() -> Component.translatable(listKey(list, "added"), entry), true);
        return 1;
    }

    /**
     * The {@code ss deny} subtree edits the disabled namespace and exact-item lists. Namespace
     * entries may intentionally name unloaded mods and are not checked against loaded namespaces.
     * Exact item ids are checked against the registry so an unknown id does not appear effective.
     */
    // spotless:off
    private static LiteralArgumentBuilder<CommandSourceStack> denyTree() {
        return Commands.literal(DENY_COMMAND)
                .requires(SsCommand::isAdmin)
                .then(Commands.literal("mod")
                        .then(Commands.literal("add")
                                .then(Commands.argument(ARG_MODID, StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                RenderGalleryGenerator.Kind.STORAGE.modIds(), builder))
                                        .executes(ctx -> addEntry(ctx, ServerConfig.ListSetting.DISABLE_MODS,
                                                StringArgumentType.getString(ctx, ARG_MODID)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument(ARG_MODID, StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestEntries(ServerConfig.ListSetting.DISABLE_MODS, builder))
                                        .executes(ctx -> removeEntry(ctx, ServerConfig.ListSetting.DISABLE_MODS,
                                                StringArgumentType.getString(ctx, ARG_MODID)))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listEntries(ctx, ServerConfig.ListSetting.DISABLE_MODS))))
                .then(Commands.literal("item")
                        .then(Commands.literal("add")
                                .then(Commands.argument(ARG_ITEM, ResourceLocationArgument.id())
                                        .suggests(SsCommand::suggestItems)
                                        .executes(ctx -> addItemEntry(ctx, ServerConfig.ListSetting.DISABLE_ITEMS))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument(ARG_ITEM, ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> suggestEntries(ServerConfig.ListSetting.DISABLE_ITEMS, builder))
                                        .executes(ctx -> removeEntry(ctx, ServerConfig.ListSetting.DISABLE_ITEMS,
                                                ResourceLocationArgument.getId(ctx, ARG_ITEM).toString()))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listEntries(ctx, ServerConfig.ListSetting.DISABLE_ITEMS))));
    }
    // spotless:on

    private static int galleryAll(
            CommandContext<CommandSourceStack> ctx, RenderGalleryGenerator.Kind kind)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Selection selection = selectAll(ctx, kind);
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

    private static int galleryList(
            CommandContext<CommandSourceStack> ctx, RenderGalleryGenerator.Kind kind)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Selection selection = selectList(ctx, kind);
        return selection == null ? 0 : generate(ctx, player, selection);
    }

    private static int gallerySingle(
            CommandContext<CommandSourceStack> ctx, RenderGalleryGenerator.Kind kind)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Selection selection = selectSingle(ctx, kind, StringArgumentType.getString(ctx, ARG_MODID));
        return selection == null ? 0 : generate(ctx, player, selection);
    }

    /**
     * Builds the {@code <modid>|all|list} subtree for one gallery kind. Storage adds the separate
     * {@code items} form; Bar galleries are selected by namespace. Gallery commands require a
     * player location and the higher world-editing permission because they overwrite blocks without
     * ordinary placement protection.
     */
    // spotless:off
    private static LiteralArgumentBuilder<CommandSourceStack> galleryTree(
            String name, RenderGalleryGenerator.Kind kind) {
        return Commands.literal(name)
                .requires(source -> {
                    ServerConfig.Settings settings = ServerConfig.settings();
                    return isPlayer(source)
                            && settings.galleryEnabled()
                            && source.hasPermission(settings.galleryPermissionLevel());
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
    private static LiteralArgumentBuilder<CommandSourceStack> genTree() {
        return Commands.literal(GEN_COMMAND)
                .requires(SsCommand::isAdmin)
                .then(Commands.literal("mod")
                        .then(Commands.literal("add")
                                .then(Commands.argument(ARG_MODID, StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                RenderGalleryGenerator.Kind.STORAGE.modIds(), builder))
                                        .executes(ctx -> addEntry(ctx, ServerConfig.ListSetting.GEN_MODS,
                                                StringArgumentType.getString(ctx, ARG_MODID)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument(ARG_MODID, StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestEntries(ServerConfig.ListSetting.GEN_MODS, builder))
                                        .executes(ctx -> removeEntry(ctx, ServerConfig.ListSetting.GEN_MODS,
                                                StringArgumentType.getString(ctx, ARG_MODID)))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listEntries(ctx, ServerConfig.ListSetting.GEN_MODS))))
                .then(Commands.literal("item")
                        .then(Commands.literal("add")
                                .then(Commands.argument(ARG_ITEM, ResourceLocationArgument.id())
                                        .suggests(SsCommand::suggestItems)
                                        .executes(ctx -> addItemEntry(ctx, ServerConfig.ListSetting.GEN_ITEMS))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument(ARG_ITEM, ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> suggestEntries(ServerConfig.ListSetting.GEN_ITEMS, builder))
                                        .executes(ctx -> removeEntry(ctx, ServerConfig.ListSetting.GEN_ITEMS,
                                                ResourceLocationArgument.getId(ctx, ARG_ITEM).toString()))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listEntries(ctx, ServerConfig.ListSetting.GEN_ITEMS))));
    }
    // spotless:on

    private static boolean isAdmin(CommandSourceStack source) {
        return source.hasPermission(ADMIN_PERMISSION_LEVEL);
    }

    private static boolean isPlayer(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer;
    }

    /** Reports one of the server's text lists, sorted, since no list's order means anything. */
    private static int listEntries(
            CommandContext<CommandSourceStack> ctx, ServerConfig.ListSetting list) {
        List<String> entries = new ArrayList<>(list.get());
        if (entries.isEmpty()) {
            ctx.getSource()
                    .sendSuccess(() -> Component.translatable(listKey(list, "empty")), false);
            return 0;
        }

        Collections.sort(entries);
        ctx.getSource()
                .sendSuccess(
                        () ->
                                Component.translatable(
                                        listKey(list, "entries"),
                                        entries.size(),
                                        String.join(", ", entries)),
                        false);
        return entries.size();
    }

    /**
     * One message about one server list, a whole sentence naming that list: {@code added}, {@code
     * contains}, {@code empty}, {@code entries}, {@code missing}, or {@code removed}.
     */
    private static String listKey(ServerConfig.ListSetting list, String message) {
        String name =
                switch (list) {
                    case DISABLE_MODS -> "disabled_mods";
                    case DISABLE_ITEMS -> "disabled_items";
                    case GEN_MODS -> "gen_mods";
                    case GEN_ITEMS -> "gen_items";
                };
        return "somestacks.command.list." + name + "." + message;
    }

    /** Rereads world policy; publication synchronizes changed client fields. */
    private static int reload(CommandContext<CommandSourceStack> ctx) {
        ServerConfig.Settings previous = ServerConfig.settings();
        if (!ServerConfig.reload()) {
            ctx.getSource()
                    .sendFailure(Component.translatable("somestacks.command.policy_rejected"));
            return 0;
        }
        ServerLifecycle.refreshCommands(ctx.getSource().getServer(), previous);
        ServerConfig.logActivePolicy();
        int online = ctx.getSource().getServer().getPlayerList().getPlayerCount();
        ctx.getSource()
                .sendSuccess(
                        () -> Component.translatable("somestacks.command.reloaded", online), true);
        return 1;
    }

    private static int removeEntry(
            CommandContext<CommandSourceStack> ctx, ServerConfig.ListSetting list, String entry) {
        if (!ServerConfig.removeListEntry(list, entry)) {
            ctx.getSource().sendFailure(Component.translatable(listKey(list, "missing"), entry));
            return 0;
        }

        ctx.getSource()
                .sendSuccess(() -> Component.translatable(listKey(list, "removed"), entry), true);
        return 1;
    }

    /**
     * Every namespace this kind can use, minus those the server disabled and those left with no
     * usable items once disabled items are removed; null when none remain.
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
                itemIds.stream().map(BuiltInRegistries.ITEM::get).toList(),
                List.of(
                        new Skips("somestacks.command.skipped.disabled_items", disabledItems),
                        new Skips("somestacks.command.skipped.unknown_items", unknownItems)));
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
        List<String> configured = ServerConfig.ListSetting.GEN_MODS.get();

        for (String modId : configured) {
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
                            configured.isEmpty()
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

    private static CompletableFuture<Suggestions> suggestEntries(
            ServerConfig.ListSetting list, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.copyOf(list.get()), builder);
    }

    /**
     * Item ids a Storage Stack accepts, matched as vanilla matches resource arguments: by full id,
     * or by path alone.
     */
    private static CompletableFuture<Suggestions> suggestItems(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        RenderGalleryGenerator.Kind storage = RenderGalleryGenerator.Kind.STORAGE;
        return SharedSuggestionProvider.suggestResource(
                storage.modIds().stream()
                        .flatMap(namespace -> storage.itemsIn(namespace).stream())
                        .map(BuiltInRegistries.ITEM::getKey),
                builder);
    }
}
