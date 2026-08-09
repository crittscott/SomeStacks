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
import net.minecraft.network.chat.MutableComponent;
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
 * The {@code ss} command: render override authoring, render gallery generation, server list
 * editing, and override reloading.
 *
 * <p>Everything here is an administrative tool, so every subcommand but {@code help} requires a
 * vanilla permission level. The render subcommands, {@code item} and {@code write}, are issued
 * against the sender's own view and so are player-only on top of that; the two gallery generators
 * need a player because a gallery is built where the sender stands.
 *
 * <p>The gallery generators sit a level above the rest. They overwrite a region of the world outright,
 * without the protection checks applied to placement gestures, which is a wider authority than
 * editing config or tuning how an item is drawn.
 *
 * <p>{@link SsHelp} carries each subcommand's forms, gate, and summary, and {@code ss help} is
 * gated by nothing, so a player who cannot run a subcommand can still read what it needs.
 */
public final class SsCommand {
    /** Vanilla's gamerule and world-editing level, the gate on the administrative subcommands. */
    private static final int ADMIN_PERMISSION_LEVEL = 2;

    /**
     * Vanilla's server-administration level, used for gallery commands that overwrite world
     * regions without ordinary placement protection.
     */
    private static final int GALLERY_PERMISSION_LEVEL = 3;
    private static final String DISABLED_MODS_LABEL = "somestacks.command.label.disabled_mods";
    private static final String DISABLED_ITEMS_LABEL = "somestacks.command.label.disabled_items";
    private static final String GEN_MODS_LABEL = "somestacks.command.label.gen_mods";
    private static final String GEN_ITEMS_LABEL = "somestacks.command.label.gen_items";
    private static final String INGOT_TAGS_LABEL = "somestacks.command.label.ingot_tags";

    /** Override dumps cover all items, matching the Storage gallery's item selection. */
    private static final RenderGalleryGenerator.Kind DUMP_KIND = RenderGalleryGenerator.Kind.STORAGE;

    private SsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("ss")
                        .then(Commands.literal("item")
                                .requires(SsCommand::isAdminPlayer)
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
                        .then(galleryTree("gallery", RenderGalleryGenerator.Kind.STORAGE)
                                .then(Commands.literal("items")
                                        .executes(SsCommand::galleryItems)))
                        .then(galleryTree("ingotgallery", RenderGalleryGenerator.Kind.BAR))
                        .then(writeTree())
                        .then(Commands.literal("reload")
                                .requires(SsCommand::isAdmin)
                                .executes(SsCommand::reload))
                        .then(genTree())
                        .then(denyTree())
                        .then(ingotTree())
                        .then(SsHelp.tree())
        );
    }

    /**
     * Bounds the numeric {@code ss item} arguments to the override schema. Brigadier reports an
     * invalid value at the argument itself and rejects overflow before it reaches a render
     * transform.
     */
    private static FloatArgumentType scaleArg() {
        return FloatArgumentType.floatArg(OverrideJsonCodec.MIN_SCALE, OverrideJsonCodec.MAX_SCALE);
    }

    private static FloatArgumentType offsetArg() {
        return FloatArgumentType.floatArg(OverrideJsonCodec.MIN_OFFSET, OverrideJsonCodec.MAX_OFFSET);
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
                .requires(source -> isPlayer(source) && source.hasPermission(GALLERY_PERMISSION_LEVEL))
                .then(Commands.literal("all")
                        .executes(ctx -> galleryAll(ctx, kind)))
                .then(Commands.literal("list")
                        .executes(ctx -> galleryList(ctx, kind)))
                .then(Commands.argument("modid", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(kind.modIds(), builder))
                        .executes(ctx -> gallerySingle(ctx, kind)));
    }

    /**
     * The {@code ss write} subtree. {@code changed} writes the user override layer to the file
     * the client loads at startup, so it holds only what {@code ss item} set. The namespace forms
     * dump complete profiles for every item of those namespaces to a folder nothing reads back,
     * which is what keeps a dump of a whole modpack from freezing that pack into the user layer.
     * Namespaces are named exactly as the gallery commands name them.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> writeTree() {
        return Commands.literal("write")
                .requires(SsCommand::isAdminPlayer)
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
    private static LiteralArgumentBuilder<CommandSourceStack> genTree() {
        return Commands.literal("gen")
                .requires(SsCommand::isAdmin)
                .then(Commands.literal("mod")
                        .then(Commands.literal("add")
                                .then(Commands.argument("modid", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                RenderGalleryGenerator.getModIdsWithItems(), builder))
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
                                                RenderGalleryGenerator.getModIdsWithItems(), builder))
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
     * Reports how an ingot-list edit changed the accepted item set. The config names tags, while
     * Bar Stack behavior depends on the items those tags contain.
     */
    private static int reportIngotEdit(CommandContext<CommandSourceStack> ctx, int result) {
        if (result != 0) {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "somestacks.command.ingot.accepted", ServerConfig.ingotItemCount()), false);
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
     * The gate on the render subcommands: they configure the server's rendering, which is an
     * operator's job, and they act on the sender's own view, which needs a sender to have one.
     */
    private static boolean isAdminPlayer(CommandSourceStack source) {
        return isPlayer(source) && isAdmin(source);
    }

    private static CompletableFuture<Suggestions> suggestEntries(
            ForgeConfigSpec.ConfigValue<List<? extends String>> list, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.copyOf(list.get()), builder);
    }

    private static CompletableFuture<Suggestions> suggestItems(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();

        if (!remaining.contains(":")) {
            List<String> namespaces = RenderGalleryGenerator.getModIdsWithItems()
                    .stream()
                    .map(ns -> ns + ":")
                    .collect(Collectors.toList());
            return SharedSuggestionProvider.suggest(namespaces, builder);
        }

        String namespace = remaining.split(":")[0];
        List<String> itemIds = RenderGalleryGenerator.collectModItems(namespace)
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
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        ResourceLocation itemId = ResourceLocationArgument.getId(ctx, "item");
        if (!ForgeRegistries.ITEMS.containsKey(itemId)) {
            ctx.getSource().sendFailure(Component.translatable(
                    "somestacks.command.unknown_item", itemId.toString()));
            return 0;
        }

        String modeString = StringArgumentType.getString(ctx, "mode");
        if (RenderMode.fromString(modeString) == null) {
            ctx.getSource().sendFailure(Component.translatable(
                    "somestacks.command.unknown_render_mode", modeString));
            return 0;
        }

        RenderOverridePkt packet = RenderOverridePkt.set(itemId, modeString, scale, offset);
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);

        ctx.getSource().sendSuccess(() -> Component.translatable(
                "somestacks.command.override_set", itemId.toString(), modeString, scale,
                offset[0], offset[1], offset[2]), false);
        return 1;
    }

    private static int resetItem(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        ResourceLocation itemId = ResourceLocationArgument.getId(ctx, "item");
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), RenderOverridePkt.reset(itemId));

        ctx.getSource().sendSuccess(() -> Component.translatable(
                "somestacks.command.override_reset", itemId.toString()), false);
        return 1;
    }

    /** Entries omitted from one request, grouped by reason. */
    private record Skips(Component reason, List<String> entries) {}

    /**
     * The namespaces one invocation acts on, with the ones dropped from the request and why.
     * The gallery commands and the dump select namespaces the same way, so a review session can
     * dump exactly what it just looked at.
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
            return false;
        });

        if (modIds.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable(
                    "somestacks.command.all_mods_disabled"));
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
                itemIds.stream().map(ForgeRegistries.ITEMS::getValue).toList(),
                List.of(new Skips(Component.translatable(
                                "somestacks.command.skip.from_disabled_mods"), disabledItems),
                        new Skips(Component.translatable("somestacks.command.skip.unknown"), unknownItems)));
    }

    private static int gallerySingle(CommandContext<CommandSourceStack> ctx, RenderGalleryGenerator.Kind kind)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Selection selection = selectSingle(ctx, kind, StringArgumentType.getString(ctx, "modid"));
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
                        skip.entries().size(), skip.reason(), String.join(", ", skip.entries())));
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
        int synced = SomeStacks.syncAllPlayers(ctx.getSource().getServer());
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
                                    ForgeConfigSpec.ConfigValue<List<? extends String>> list, String label) {
        ResourceLocation itemId = ResourceLocationArgument.getId(ctx, "item");
        if (!ForgeRegistries.ITEMS.containsKey(itemId)) {
            ctx.getSource().sendFailure(Component.translatable(
                    "somestacks.command.unknown_item", itemId.toString()));
            return 0;
        }
        return addEntry(ctx, list, label, itemId.toString());
    }

    private static int addEntry(CommandContext<CommandSourceStack> ctx,
                                ForgeConfigSpec.ConfigValue<List<? extends String>> list,
                                String label, String entry) {
        if (!ServerConfig.addListEntry(list, entry)) {
            ctx.getSource().sendFailure(Component.translatable(
                    "somestacks.command.list_contains", Component.translatable(label), entry));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.translatable(
                "somestacks.command.list_added", entry, Component.translatable(label)), true);
        return 1;
    }

    private static int removeEntry(CommandContext<CommandSourceStack> ctx,
                                   ForgeConfigSpec.ConfigValue<List<? extends String>> list,
                                   String label, String entry) {
        if (!ServerConfig.removeListEntry(list, entry)) {
            ctx.getSource().sendFailure(Component.translatable(
                    "somestacks.command.list_missing", Component.translatable(label), entry));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.translatable(
                "somestacks.command.list_removed", entry, Component.translatable(label)), true);
        return 1;
    }

    /**
     * Reports one of the server's text lists. Sorting is for the lists whose order means nothing;
     * the gen mod list is shown as stored, because that order is the order of a gallery's columns.
     */
    private static int listEntries(CommandContext<CommandSourceStack> ctx,
                                   ForgeConfigSpec.ConfigValue<List<? extends String>> list, String label,
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
                String.join(", ", entries)), false);
        return entries.size();
    }

    private static int writeChanged(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), WriteOverridesPkt.userLayer());
        return 1;
    }

    private static int dumpSingle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Selection selection = selectSingle(ctx, DUMP_KIND, StringArgumentType.getString(ctx, "modid"));
        return selection == null ? 0 : dump(ctx, player, selection);
    }

    private static int dumpAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Selection selection = selectAll(ctx, DUMP_KIND);
        return selection == null ? 0 : dump(ctx, player, selection);
    }

    private static int dumpList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Selection selection = selectList(ctx, DUMP_KIND);
        return selection == null ? 0 : dump(ctx, player, selection);
    }

    /**
     * Sends the requested namespaces to the client, which resolves and measures their items and
     * reports the files it writes.
     */
    private static int dump(CommandContext<CommandSourceStack> ctx, ServerPlayer player, Selection selection) {
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                WriteOverridesPkt.dump(selection.modIds()));

        MutableComponent message = Component.translatable(
                "somestacks.command.dumping", subject(selection.modIds()));
        appendSkips(message, selection.skips());

        ctx.getSource().sendSuccess(() -> message, false);
        return 1;
    }
}
