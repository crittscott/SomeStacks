package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.network.ConfigSyncNetwork;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

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
 * The server-side {@code ss} command: render gallery generation, server list editing, and
 * override reloading. Client-local render authoring lives in {@code ClientRenderCommands}.
 *
 * <p>Everything here is an administrative tool, so every subcommand but {@code help} requires a
 * vanilla permission level. The two gallery generators need a player because a gallery is built
 * where the sender stands.
 *
 * <p>The gallery generators sit a level above the rest. They overwrite a region of the world outright,
 * without the protection checks applied to placement gestures, which is a wider authority than
 * editing config or tuning how an item is drawn. Because of that, they are also the one pair of
 * subcommands gated by server config rather than a fixed level: {@code render_gallery.enabled}
 * (off by default) and {@code render_gallery.required_permission_level} (default 3).
 *
 * <p>{@link SsHelp} derives server forms from this tree, supplies the two client-only forms, and
 * describes each gate. {@code ss help} is gated by nothing.
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

    private static final String ARG_ITEM = "item";
    private static final String ARG_MODID = "modid";
    private static final String ARG_ENTRY = "entry";

    private static final String DISABLED_MODS_LABEL = "somestacks.command.label.disabled_mods";
    private static final String DISABLED_ITEMS_LABEL = "somestacks.command.label.disabled_items";
    private static final String GEN_MODS_LABEL = "somestacks.command.label.gen_mods";
    private static final String GEN_ITEMS_LABEL = "somestacks.command.label.gen_items";

    private SsCommand() {}

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

    /**
     * Builds the {@code <modid>|all|list} subtree for one gallery kind. Storage adds the separate
     * {@code items} form; Bar galleries are selected by namespace. Gallery commands require a
     * player location and the higher world-editing permission because they overwrite blocks without
     * ordinary placement protection.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> galleryTree(
            String name, RenderGalleryGenerator.Kind kind) {
        return Commands.literal(name)
                .requires(source -> isPlayer(source) && ServerConfig.galleryEnabled()
                        && source.hasPermission(ServerConfig.galleryRequiredPermissionLevel()))
                .then(Commands.literal("all")
                        .executes(ctx -> galleryAll(ctx, kind)))
                .then(Commands.literal("list")
                        .executes(ctx -> galleryList(ctx, kind)))
                .then(Commands.argument(ARG_MODID, StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(kind.modIds(), builder))
                        .executes(ctx -> gallerySingle(ctx, kind)));
    }

    /**
     * The {@code ss gen} subtree, editing the two lists the gallery commands build from: the
     * namespaces of {@code ss gallery list} and {@code ss ingotgallery list}, and the items of
     * {@code ss gallery items}. Describing a gallery is an ordinary config edit and sits with the other
     * list editors; building the gallery it describes needs the higher level the gallery commands
     * hold.
     *
     * <p>Adding a namespace completes over those that have items at all rather than over one
     * kind's, since the one list serves both kinds and a namespace with no ingots is still worth
     * listing for {@code ss gallery list}. An item id is checked against the registry as
     * {@code ss deny item add} checks one, since a typo would otherwise sit in the list and be
     * skipped by every gallery.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> genTree(
            CommandBuildContext buildContext) {
        return Commands.literal(COMMAND_GEN)
                .requires(SsCommand::isAdmin)
                .then(Commands.literal("mod")
                        .then(Commands.literal("add")
                                .then(Commands.argument(ARG_MODID, StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                RenderGalleryGenerator.Kind.STORAGE.modIds(), builder))
                                        .executes(ctx -> addEntry(ctx, ServerConfig.GEN_MODS, GEN_MODS_LABEL,
                                                StringArgumentType.getString(ctx, ARG_MODID)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument(ARG_MODID, StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestEntries(ServerConfig.GEN_MODS, builder))
                                        .executes(ctx -> removeEntry(ctx, ServerConfig.GEN_MODS, GEN_MODS_LABEL,
                                                StringArgumentType.getString(ctx, ARG_MODID)))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listEntries(ctx, ServerConfig.GEN_MODS, GEN_MODS_LABEL, false))))
                .then(Commands.literal("item")
                        .then(Commands.literal("add")
                                .then(Commands.argument(
                                                ARG_ITEM,
                                                ResourceArgument.resource(buildContext, Registries.ITEM))
                                        .executes(ctx -> addItemEntry(
                                                ctx, ServerConfig.GEN_ITEMS, GEN_ITEMS_LABEL))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument(ARG_ITEM, ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> suggestEntries(ServerConfig.GEN_ITEMS, builder))
                                        .executes(ctx -> removeEntry(ctx, ServerConfig.GEN_ITEMS, GEN_ITEMS_LABEL,
                                                ResourceLocationArgument.getId(ctx, ARG_ITEM).toString()))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listEntries(ctx, ServerConfig.GEN_ITEMS, GEN_ITEMS_LABEL, true))));
    }

    /**
     * The {@code ss deny} subtree, editing the two compatibility lists. A namespace is taken as
     * typed because the shipped defaults name mods that need not be installed, so the list is
     * expected to hold namespaces the registry cannot confirm; an item id is checked against the
     * registry, where a typo would otherwise sit in the list looking effective.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> denyTree(
            CommandBuildContext buildContext) {
        return Commands.literal(COMMAND_DENY)
                .requires(SsCommand::isAdmin)
                .then(Commands.literal("mod")
                        .then(Commands.literal("add")
                                .then(Commands.argument(ARG_MODID, StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                RenderGalleryGenerator.Kind.STORAGE.modIds(), builder))
                                        .executes(ctx -> addEntry(ctx, ServerConfig.DISABLE_MODS, DISABLED_MODS_LABEL,
                                                StringArgumentType.getString(ctx, ARG_MODID)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument(ARG_MODID, StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestEntries(ServerConfig.DISABLE_MODS, builder))
                                        .executes(ctx -> removeEntry(ctx, ServerConfig.DISABLE_MODS, DISABLED_MODS_LABEL,
                                                StringArgumentType.getString(ctx, ARG_MODID)))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listEntries(ctx, ServerConfig.DISABLE_MODS, DISABLED_MODS_LABEL, true))))
                .then(Commands.literal("item")
                        .then(Commands.literal("add")
                                .then(Commands.argument(
                                                ARG_ITEM,
                                                ResourceArgument.resource(buildContext, Registries.ITEM))
                                        .executes(ctx -> addItemEntry(
                                                ctx, ServerConfig.DISABLE_ITEMS, DISABLED_ITEMS_LABEL))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument(ARG_ITEM, ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> suggestEntries(ServerConfig.DISABLE_ITEMS, builder))
                                        .executes(ctx -> removeEntry(ctx, ServerConfig.DISABLE_ITEMS, DISABLED_ITEMS_LABEL,
                                                ResourceLocationArgument.getId(ctx, ARG_ITEM).toString()))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listEntries(ctx, ServerConfig.DISABLE_ITEMS, DISABLED_ITEMS_LABEL, true))));
    }

    private static boolean isPlayer(CommandSourceStack source) {
        return source.isPlayer();
    }

    private static boolean isAdmin(CommandSourceStack source) {
        return source.hasPermission(GAME_MASTER_PERMISSION_LEVEL);
    }

    private static CompletableFuture<Suggestions> suggestEntries(
            ServerConfig.ListSetting list, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.copyOf(list.get()), builder);
    }

    private static CompletableFuture<Suggestions> suggestItems(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();

        if (!remaining.contains(":")) {
            List<String> namespaces = RenderGalleryGenerator.Kind.STORAGE.modIds()
                    .stream()
                    .map(ns -> ns + ":")
                    .collect(Collectors.toList());
            return SharedSuggestionProvider.suggest(namespaces, builder);
        }

        String namespace = remaining.split(":")[0];
        List<String> itemIds = RenderGalleryGenerator.Kind.STORAGE.itemsIn(namespace)
                .stream()
                .map(item -> String.valueOf(BuiltInRegistries.ITEM.getKey(item)))
                .collect(Collectors.toList());
        return SharedSuggestionProvider.suggest(itemIds, builder);
    }

    /** Entries omitted from one request, grouped by reason. */
    private record Skips(Component reason, List<String> entries) {}

    /**
     * The namespaces one gallery invocation acts on, with the ones dropped from the request and
     * why.
     */
    private record Selection(RenderGalleryGenerator.Kind kind, List<String> modIds,
                             List<String> disabledMods, List<String> unusableMods) {
        private List<Skips> skips() {
            return List.of(new Skips(Component.translatable("somestacks.command.skip.disabled"), disabledMods),
                    new Skips(Component.translatable("somestacks.command.skip.not_loaded",
                            Component.translatable(kind.itemLabelKey())), unusableMods));
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
    private static Selection selectSingle(CommandContext<CommandSourceStack> ctx,
                                          RenderGalleryGenerator.Kind kind, String modId) {
        if (ServerConfig.isModDisabled(modId)) {
            ctx.getSource().sendFailure(Component.translatable(
                    "somestacks.command.mod_disabled", modId));
            return null;
        }

        if (kind.itemsIn(modId).isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable(
                    "somestacks.command.mod_unusable", modId,
                    Component.translatable(kind.itemLabelKey())));
            return null;
        }

        return new Selection(kind, List.of(modId), List.of(), List.of());
    }

    /** Every namespace this kind can use, minus those the server disabled; null when none remain. */
    @Nullable
    private static Selection selectAll(CommandContext<CommandSourceStack> ctx, RenderGalleryGenerator.Kind kind) {
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
            ctx.getSource().sendFailure(Component.translatable(
                    "somestacks.command.all_mods_disabled"));
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
    private static Selection selectList(CommandContext<CommandSourceStack> ctx, RenderGalleryGenerator.Kind kind) {
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
            ctx.getSource().sendFailure(seen.isEmpty()
                    ? Component.translatable("somestacks.command.gen_mods_empty",
                            Component.translatable(GEN_MODS_LABEL))
                    : Component.translatable("somestacks.command.gen_mods_unusable",
                            Component.translatable(GEN_MODS_LABEL),
                            Component.translatable(kind.itemLabelKey()), disabledMods.size(),
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
     * disabled-mod or disabled-item list is doing what the server was told, while one the registry does not know is
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
            ctx.getSource().sendFailure(seen.isEmpty()
                    ? Component.translatable("somestacks.command.gen_items_empty",
                            Component.translatable(GEN_ITEMS_LABEL))
                    : Component.translatable("somestacks.command.gen_items_unusable",
                            Component.translatable(GEN_ITEMS_LABEL), disabledItems.size(),
                            unknownItems.size()));
            return null;
        }

        itemIds.sort(Comparator.comparing(ResourceLocation::getNamespace)
                .thenComparing(ResourceLocation::getPath));

        return new ItemSelection(
                itemIds.stream().map(BuiltInRegistries.ITEM::getValue).toList(),
                List.of(new Skips(Component.translatable(
                                "somestacks.command.skip.disabled"), disabledItems),
                        new Skips(Component.translatable("somestacks.command.skip.unknown"), unknownItems)));
    }

    private static int gallerySingle(CommandContext<CommandSourceStack> ctx, RenderGalleryGenerator.Kind kind)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Selection selection = selectSingle(ctx, kind, StringArgumentType.getString(ctx, ARG_MODID));
        return selection == null ? 0 : generate(ctx, player, selection);
    }

    private static int galleryAll(CommandContext<CommandSourceStack> ctx, RenderGalleryGenerator.Kind kind)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Selection selection = selectAll(ctx, kind);
        return selection == null ? 0 : generate(ctx, player, selection);
    }

    private static int galleryList(CommandContext<CommandSourceStack> ctx, RenderGalleryGenerator.Kind kind)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Selection selection = selectList(ctx, kind);
        return selection == null ? 0 : generate(ctx, player, selection);
    }

    /**
     * Builds the row of the {@code gen_items} server config list. One group means one column, so a
     * list longer than a stack holds runs on north exactly as a namespace with many items does.
     */
    private static int galleryItems(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        ItemSelection selection = selectItems(ctx);
        return selection == null ? 0 : generate(ctx, player, RenderGalleryGenerator.Kind.STORAGE,
                List.of(selection.items()), Component.translatable(
                        "somestacks.command.subject.list", Component.translatable(GEN_ITEMS_LABEL)),
                selection.skips());
    }

    private static Component subject(List<String> modIds) {
        return modIds.size() == 1
                ? Component.translatable("somestacks.command.subject.mod", modIds.get(0))
                : Component.translatable("somestacks.command.subject.mods", modIds.size());
    }

    /** Reports what a bulk form dropped, one clause per reason and none for a reason with nothing. */
    private static void appendSkips(MutableComponent message, List<Skips> skips) {
        for (Skips skip : skips) {
            if (!skip.entries().isEmpty()) {
                message.append(Component.translatable("somestacks.command.skipped",
                        skip.entries().size(), skip.reason(),
                        ComponentUtils.formatList(skip.entries(), Component::literal)));
            }
        }
    }

    private static int generate(CommandContext<CommandSourceStack> ctx, ServerPlayer player,
                                Selection selection) {
        return generate(ctx, player, selection.kind(), selection.groups(),
                subject(selection.modIds()), selection.skips());
    }

    /**
     * Queues one gallery, reports its planned contents immediately, and reports the actual result
     * when the tick-budgeted generator finishes.
     */
    private static int generate(CommandContext<CommandSourceStack> ctx, ServerPlayer player,
                                RenderGalleryGenerator.Kind kind, List<List<Item>> groups,
                                Component subject, List<Skips> skips) {
        RenderGalleryGenerator.Plan plan = RenderGalleryGenerator.enqueue(player, kind, groups, result -> {
            MutableComponent done = Component.translatable("somestacks.command.gallery_created",
                    result.totalStacks(), Component.translatable(kind.stackLabelKey()),
                    result.totalItems(), Component.translatable(kind.itemLabelKey()), subject);

            if (result.totalStacks() < result.expectedStacks()) {
                done.append(Component.translatable("somestacks.command.gallery_warning",
                        result.expectedStacks() - result.totalStacks(), result.expectedStacks()));
            }

            player.sendSystemMessage(done);
        });

        MutableComponent message = Component.translatable("somestacks.command.gallery_building",
                plan.expectedStacks(), Component.translatable(kind.stackLabelKey()), plan.totalItems(),
                Component.translatable(kind.itemLabelKey()), subject);

        appendSkips(message, skips);

        ctx.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        ServerConfig.reload();
        ConfigSyncPkt.rebuildCurrent();
        int synced = ConfigSyncNetwork.syncAllPlayers(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "somestacks.command.reloaded", synced), true);
        return synced;
    }

    /**
     * Rejects an item the registry does not know before it reaches one of the item lists. Both are
     * consulted by exact id, so a typo would sit in the list looking effective while barring
     * nothing or showing nothing.
     */
    private static int addItemEntry(CommandContext<CommandSourceStack> ctx,
                                    ServerConfig.ListSetting list, String label)
            throws CommandSyntaxException {
        ResourceLocation itemId = resourceItemId(ctx);
        return addEntry(ctx, list, label, itemId.toString());
    }

    private static ResourceLocation resourceItemId(
            CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ResourceArgument.getResource(ctx, ARG_ITEM, Registries.ITEM).key().location();
    }

    private static int addEntry(CommandContext<CommandSourceStack> ctx,
                                ServerConfig.ListSetting list,
                                String label, String entry) {
        if (!ServerConfig.addListEntry(list, entry)) {
            ctx.getSource().sendFailure(Component.translatable(
                    "somestacks.command.list_contains", Component.translatable(label), entry));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.translatable(
                "somestacks.command.list_added", entry, Component.translatable(label)), true);
        syncClientToolState(ctx, list);
        return 1;
    }

    private static int removeEntry(CommandContext<CommandSourceStack> ctx,
                                   ServerConfig.ListSetting list,
                                   String label, String entry) {
        if (!ServerConfig.removeListEntry(list, entry)) {
            ctx.getSource().sendFailure(Component.translatable(
                    "somestacks.command.list_missing", Component.translatable(label), entry));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.translatable(
                "somestacks.command.list_removed", entry, Component.translatable(label)), true);
        syncClientToolState(ctx, list);
        return 1;
    }

    /** Keeps client-local {@code ss write list} inputs current after their server lists change. */
    private static void syncClientToolState(CommandContext<CommandSourceStack> ctx,
                                            ServerConfig.ListSetting list) {
        if (list != ServerConfig.GEN_MODS && list != ServerConfig.DISABLE_MODS) {
            return;
        }
        ConfigSyncPkt.rebuildCurrent();
        ConfigSyncNetwork.syncAllPlayers(ctx.getSource().getServer());
    }

    /**
     * Reports one of the server's text lists. Sorting is for the lists whose order means nothing;
     * the gen mod list is shown as stored, because that order is the order of a gallery's columns.
     */
    private static int listEntries(CommandContext<CommandSourceStack> ctx,
                                   ServerConfig.ListSetting list, String label,
                                   boolean sort) {
        List<String> entries = new ArrayList<>(list.get());
        if (entries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "somestacks.command.list_empty", Component.translatable(label)), false);
            return 0;
        }

        if (sort) {
            Collections.sort(entries);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "somestacks.command.list_entries", Component.translatable(label), entries.size(),
                ComponentUtils.formatList(entries, Component::literal)), false);
        return entries.size();
    }

}
