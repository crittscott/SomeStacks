package com.github.crittscott.somestacks.client.measure;

import com.github.crittscott.somestacks.client.ItemRenderOverrides;
import com.github.crittscott.somestacks.client.RenderMode;
import com.github.crittscott.somestacks.command.ItemTester;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Client-only authoring commands for the measurement pipeline. These never run in
 * production play; they exist to validate guesses against the authored corpus and to
 * export a lookup corpus for shipping.
 */
public final class DevCommands {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final double SCALE_RATIO_LOW = 0.85;
    private static final double SCALE_RATIO_HIGH = 1.18;
    private static final float OFFSET_TOLERANCE = 0.03f;

    private DevCommands() {}

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("somestacksdev")
                        .then(Commands.literal("dump")
                                .then(Commands.argument("modid", StringArgumentType.word())
                                        .suggests(DevCommands::suggestNamespaces)
                                        .executes(ctx -> dump(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "modid")))))
                        .then(Commands.literal("diff")
                                .then(Commands.argument("modid", StringArgumentType.word())
                                        .suggests(DevCommands::suggestNamespaces)
                                        .executes(ctx -> diff(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "modid")))))
                        .then(Commands.literal("fill")
                                .then(Commands.argument("fraction", FloatArgumentType.floatArg(0.05f, 2.0f))
                                        .executes(ctx -> setFill(ctx.getSource(),
                                                FloatArgumentType.getFloat(ctx, "fraction")))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                ForgeRegistries.ITEMS.getKeys(), builder))
                                        .executes(ctx -> info(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "item")))))
        );
    }

    private static CompletableFuture<Suggestions> suggestNamespaces(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        List<String> options = new ArrayList<>(ItemTester.getModIdsWithItems());
        options.add("all");
        return SharedSuggestionProvider.suggest(options, builder);
    }

    private static List<String> resolveNamespaces(String modid) {
        if (modid.equals("all")) {
            return ItemTester.getModIdsWithItems();
        }
        if (ItemTester.collectModItems(modid).isEmpty()) {
            return List.of();
        }
        return List.of(modid);
    }

    private static List<Item> sortedItems(String namespace) {
        List<Item> items = ItemTester.collectModItems(namespace);
        items.sort(Comparator.comparing(item -> String.valueOf(ForgeRegistries.ITEMS.getKey(item))));
        return items;
    }

    private static int dump(CommandSourceStack source, String modid) {
        List<String> namespaces = resolveNamespaces(modid);
        if (namespaces.isEmpty()) {
            source.sendFailure(Component.literal("No items found for '" + modid + "'"));
            return 0;
        }

        Path dir = FMLPaths.CONFIGDIR.get().resolve("somestacks/generated_overrides");
        int itemCount = 0;
        int correctionCount = 0;
        int failureCount = 0;

        try {
            Files.createDirectories(dir);

            for (String namespace : namespaces) {
                JsonObject root = new JsonObject();

                for (Item item : sortedItems(namespace)) {
                    ItemStack stack = new ItemStack(item);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                    if (id == null) {
                        continue;
                    }

                    AutoRenderProfile profile = AutoRenderProfiles.get(stack);
                    ItemRenderOverrides.ItemRenderConfig correction = SessionCorrections.get(id);

                    RenderMode mode = correction != null && correction.mode() != null
                            ? correction.mode() : profile.mode();
                    float scale = correction != null && correction.scale() != null
                            ? correction.scale() : profile.scale();
                    float[] offset = correction != null && correction.offset() != null
                            ? correction.offset() : profile.offset();

                    if (correction != null) {
                        correctionCount++;
                    }
                    if (profile.failure() != null) {
                        failureCount++;
                    }

                    JsonObject entry = new JsonObject();
                    entry.addProperty("mode", mode.getId());
                    entry.addProperty("scale", round3(scale));
                    JsonArray offsetArray = new JsonArray();
                    for (float component : offset) {
                        offsetArray.add(round3(component));
                    }
                    entry.add("offset", offsetArray);

                    root.add(id.toString(), entry);
                    itemCount++;
                }

                Files.writeString(dir.resolve(namespace + ".json"), GSON.toJson(root));
            }
        } catch (IOException e) {
            source.sendFailure(Component.literal("Dump failed: " + e.getMessage()));
            return 0;
        }

        final String message = "Dumped " + itemCount + " entries for " + namespaces.size()
                + " namespace(s) to " + dir + " (" + correctionCount + " forced corrections, "
                + failureCount + " measurement failures)";
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int diff(CommandSourceStack source, String modid) {
        List<String> namespaces = resolveNamespaces(modid);
        if (namespaces.isEmpty()) {
            source.sendFailure(Component.literal("No items found for '" + modid + "'"));
            return 0;
        }

        StringBuilder csv = new StringBuilder(
                "item,authored_mode,guess_mode,authored_scale,guess_scale,scale_ratio,guess_source,classification\n");
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<Double> ratios = new ArrayList<>();
        int unconfigured = 0;

        for (String namespace : namespaces) {
            for (Item item : sortedItems(namespace)) {
                ItemStack stack = new ItemStack(item);
                if (stack.isEmpty()) {
                    continue;
                }
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                if (id == null) {
                    continue;
                }

                ItemRenderOverrides.ItemRenderConfig authored = ItemRenderOverrides.CONFIG_MAP.get(id);
                if (authored == null) {
                    unconfigured++;
                    continue;
                }

                AutoRenderProfile profile = AutoRenderProfiles.get(stack);
                String classification;
                double ratio = Double.NaN;

                if (profile.failure() != null) {
                    classification = "FAILED";
                } else {
                    RenderMode authoredMode = authored.mode();
                    float authoredScale = authored.scale() != null ? authored.scale() : 1.0f;
                    ratio = authoredScale / profile.scale();

                    boolean modeOk = authoredMode == null || authoredMode == profile.mode();
                    boolean scaleOk = ratio >= SCALE_RATIO_LOW && ratio <= SCALE_RATIO_HIGH;
                    float[] authoredOffset = authored.offset() != null ? authored.offset() : new float[3];
                    boolean offsetOk = true;
                    for (int axis = 0; axis < 3; axis++) {
                        if (Math.abs(authoredOffset[axis] - profile.offset()[axis]) > OFFSET_TOLERANCE) {
                            offsetOk = false;
                            break;
                        }
                    }

                    if (modeOk) {
                        ratios.add(ratio);
                    }

                    classification = !modeOk ? "MODE_DIFFERS"
                            : !scaleOk ? "SCALE_DIFFERS"
                            : !offsetOk ? "OFFSET_DIFFERS"
                            : "MATCH";
                }

                counts.merge(classification, 1, Integer::sum);
                csv.append(id)
                        .append(',').append(authored.mode() != null ? authored.mode().getId() : "")
                        .append(',').append(profile.mode().getId())
                        .append(',').append(authored.scale() != null ? fmt(authored.scale()) : "")
                        .append(',').append(fmt(profile.scale()))
                        .append(',').append(Double.isNaN(ratio) ? "" : fmt((float) ratio))
                        .append(',').append(profile.source())
                        .append(',').append(classification)
                        .append('\n');
            }
        }

        Path out = FMLPaths.CONFIGDIR.get().resolve("somestacks/measure_diff_" + modid + ".csv");
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, csv.toString());
        } catch (IOException e) {
            source.sendFailure(Component.literal("Diff report write failed: " + e.getMessage()));
            return 0;
        }

        ratios.sort(null);
        double medianRatio = ratios.isEmpty() ? Double.NaN : ratios.get(ratios.size() / 2);

        StringBuilder summary = new StringBuilder("Diff '" + modid + "': ");
        counts.forEach((classification, count) -> summary.append(classification).append("=").append(count).append(" "));
        summary.append("unconfigured=").append(unconfigured);
        if (!Double.isNaN(medianRatio)) {
            summary.append(String.format(Locale.ROOT,
                    " | median authored/guess scale ratio %.3f (current fill %.2f; a stable ratio suggests scaling fill by it)",
                    medianRatio, AutoRenderProfiles.getTargetFill()));
        }
        summary.append(" | report: ").append(out);

        final String message = summary.toString();
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int setFill(CommandSourceStack source, float fraction) {
        AutoRenderProfiles.setTargetFill(fraction);
        final String message = String.format(Locale.ROOT,
                "Target cell fill set to %.2f; auto profiles recomputed", fraction);
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int info(CommandSourceStack source, ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            source.sendFailure(Component.literal("Unknown item: " + id));
            return 0;
        }
        ItemStack stack = new ItemStack(item);
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("Item has no usable stack: " + id));
            return 0;
        }

        AutoRenderProfile profile = AutoRenderProfiles.get(stack);
        ItemRenderOverrides.ItemRenderConfig authored = ItemRenderOverrides.CONFIG_MAP.get(id);
        ItemRenderOverrides.ItemRenderConfig correction = SessionCorrections.get(id);

        StringBuilder text = new StringBuilder(id.toString());
        text.append("\n guess: ").append(describe(profile.mode(), profile.scale(), profile.offset()))
                .append(" [").append(profile.source()).append(']');
        text.append("\n gui3d: ").append(profile.gui3d());
        if (profile.customRenderer()) {
            text.append(", custom renderer");
        }
        if (profile.failure() != null) {
            text.append("\n reason: ").append(profile.failure());
        }
        AABB bounds = profile.bounds();
        if (bounds != null) {
            double minExtent = Math.min(bounds.getXsize(), Math.min(bounds.getYsize(), bounds.getZsize()));
            double maxExtent = Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize()));
            text.append(String.format(Locale.ROOT,
                    "\n bounds: [%.3f %.3f %.3f] .. [%.3f %.3f %.3f]"
                            + "\n extents: %.3f x %.3f x %.3f, thinnest/longest %.3f",
                    bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ,
                    bounds.getXsize(), bounds.getYsize(), bounds.getZsize(),
                    maxExtent > 0 ? minExtent / maxExtent : 0.0));
        }
        text.append("\n authored: ").append(authored == null ? "none"
                : describe(authored.mode(), authored.scale(), authored.offset()));
        text.append("\n correction: ").append(correction == null ? "none"
                : describe(correction.mode(), correction.scale(), correction.offset()));

        final String message = text.toString();
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static String describe(RenderMode mode, Float scale, float[] offset) {
        return "mode=" + (mode != null ? mode.getId() : "auto")
                + " scale=" + (scale != null ? fmt(scale) : "default")
                + " offset=" + (offset != null
                ? "[" + fmt(offset[0]) + ", " + fmt(offset[1]) + ", " + fmt(offset[2]) + "]"
                : "default");
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static BigDecimal round3(float value) {
        return BigDecimal.valueOf(Math.round(value * 1000.0) / 1000.0);
    }
}
