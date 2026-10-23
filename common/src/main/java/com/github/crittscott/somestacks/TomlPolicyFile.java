package com.github.crittscott.somestacks;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Documented TOML persistence and field validation, independent of loader lifecycle. */
public final class TomlPolicyFile {
    private final Path file;

    public TomlPolicyFile(Path file) { this.file = file; }

    /** A malformed field uses its default; a syntax error uses the complete default policy. */
    public ServerConfig.Settings read(ServerConfig.Settings defaults) {
        if (!Files.exists(file)) {
            if (write(defaults)) SomeStacksCommon.LOGGER.info("Created server policy at {}", file.toAbsolutePath());
            return defaults;
        }
        try {
            return decode(new TomlParser().parse(Files.readString(file)), defaults);
        } catch (IOException | RuntimeException exception) {
            SomeStacksCommon.LOGGER.warn("Failed to read server policy {}; using defaults. Fix it and run /ss reload before editing lists", file, exception);
            return defaults;
        }
    }

    static ServerConfig.Settings decode(UnmodifiableConfig root, ServerConfig.Settings defaults) {
        return new ServerConfig.Settings(
                integer(root, "piles.max_pile_height", defaults.maxPileHeight(), 1, 64),
                bool(root, "stacks.enable_storage_stack_block", defaults.enableStorageStackBlock()),
                bool(root, "stacks.enable_singles_stack_block", defaults.enableSinglesStackBlock()),
                bool(root, "stacks.enable_bar_stack_block", defaults.enableBarStackBlock()),
                strings(root, "compatibility.disable_mods", defaults.disableMods()),
                strings(root, "compatibility.disable_items", defaults.disableItems()),
                integer(root, "render_gallery.placements_per_tick", defaults.renderGalleryPlacementsPerTick(), 1, Integer.MAX_VALUE),
                bool(root, "render_gallery.enabled", defaults.galleryEnabled()),
                integer(root, "render_gallery.required_permission_level", defaults.galleryPermissionLevel(), 0, 4),
                strings(root, "render_gallery.gen_mods", defaults.genMods()),
                strings(root, "render_gallery.gen_items", defaults.genItems()));
    }

    private static Object value(UnmodifiableConfig root, String path) {
        String[] parts = path.split("\\.");
        Object section = root.get(parts[0]);
        if (section == null) return null;
        if (section instanceof UnmodifiableConfig config) return config.get(parts[1]);
        SomeStacksCommon.LOGGER.warn("Ignoring non-table server policy section {}", parts[0]);
        return null;
    }

    private static int integer(UnmodifiableConfig root, String path, int fallback, int min, int max) {
        Object value = value(root, path);
        if (value == null) return fallback;
        if (value instanceof Integer || value instanceof Long) {
            long number = ((Number) value).longValue();
            int bounded = (int) Math.max(min, Math.min(max, number));
            if (number != bounded) SomeStacksCommon.LOGGER.warn("Corrected {} from {} to {}", path, number, bounded);
            return bounded;
        }
        SomeStacksCommon.LOGGER.warn("Ignoring non-integer server policy field {}", path);
        return fallback;
    }

    private static boolean bool(UnmodifiableConfig root, String path, boolean fallback) {
        Object value = value(root, path);
        if (value == null) return fallback;
        if (value instanceof Boolean flag) return flag;
        SomeStacksCommon.LOGGER.warn("Ignoring non-boolean server policy field {}", path);
        return fallback;
    }

    private static List<String> strings(UnmodifiableConfig root, String path, List<String> fallback) {
        Object value = value(root, path);
        if (value == null) return fallback;
        if (!(value instanceof List<?> list)) {
            SomeStacksCommon.LOGGER.warn("Ignoring non-list server policy field {}", path);
            return fallback;
        }
        List<String> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof String text) result.add(text);
            else SomeStacksCommon.LOGGER.warn("Ignoring non-string entry in server policy field {}", path);
        }
        return List.copyOf(result);
    }

    /** Writes all current fields with the same explanations as the loader-managed TOML. */
    public boolean write(ServerConfig.Settings settings) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, new TomlWriter().writeToString(encode(settings)));
            return true;
        } catch (IOException | RuntimeException exception) {
            SomeStacksCommon.LOGGER.warn("Failed to write server policy {}", file, exception);
            return false;
        }
    }

    static CommentedConfig encode(ServerConfig.Settings s) {
        CommentedConfig root = CommentedConfig.of(LinkedHashMap::new, TomlFormat.instance());
        section(root, "piles", "Stack piles: vertical runs of one stack type that share one inventory.");
        field(root, "piles.max_pile_height", s.maxPileHeight(), "Tallest a run of one stack type may grow, in blocks. Player deposits and automation stop growing a run at this height.\nRange: 1 ~ 64");
        section(root, "stacks", "Which stack types players and automation may create.\nTurning a type off blocks new placement and growth only; existing stacks keep working and their contents stay extractable.");
        field(root, "stacks.enable_storage_stack_block", s.enableStorageStackBlock(), "Storage Stack: 27 item stacks per block that merge, sort, and pack down.");
        field(root, "stacks.enable_singles_stack_block", s.enableSinglesStackBlock(), "Singles Stack: a 4 x 4 x 4 display grid holding one item per cell.");
        field(root, "stacks.enable_bar_stack_block", s.enableBarStackBlock(), "Bar Stack: 64 ingots per block in eight alternating layers; ingots are the somestacks:ingots item tag.");
        section(root, "compatibility", "Items refused by stacks. Disabling never removes or ejects existing contents.");
        field(root, "compatibility.disable_mods", s.disableMods(), "Mod ids (item namespaces) whose items players and automation may not put into stacks, e.g. [\"examplemod\"].\nGalleries skip these mods. Edit in game with /ss deny.");
        field(root, "compatibility.disable_items", s.disableItems(), "Item ids players may not deposit into stacks, e.g. [\"minecraft:stone\"]. Automation is not affected.\nGalleries skip these items. The default lists items known to crash clients when drawn. Edit in game with /ss deny.");
        section(root, "render_gallery", "Render galleries: /ss gallery and /ss ingotgallery build rows of stacks showing every item of chosen mods,\nfor checking how items render. Galleries bypass placement protection, so they are off by default.");
        field(root, "render_gallery.enabled", s.galleryEnabled(), "Whether /ss gallery and /ss ingotgallery are available.");
        field(root, "render_gallery.required_permission_level", s.galleryPermissionLevel(), "Permission level needed to run the gallery commands (0 = everyone, 4 = owner).\nRange: 0 ~ 4");
        field(root, "render_gallery.placements_per_tick", s.renderGalleryPlacementsPerTick(), "Stacks a gallery places per server tick; lower values spread the work over more ticks.\nRange: 1 ~ 2147483647");
        field(root, "render_gallery.gen_mods", s.genMods(), "Mod ids the gallery 'list' form builds, one column per mod. Edit in game with /ss gen.");
        field(root, "render_gallery.gen_items", s.genItems(), "Item ids the gallery 'items' form builds as a single column. Edit in game with /ss gen.");
        return root;
    }

    private static void section(CommentedConfig root, String key, String comment) {
        root.set(key, root.createSubConfig());
        root.setComment(key, comment);
    }

    private static void field(CommentedConfig root, String key, Object value, String comment) {
        root.set(key, value);
        root.setComment(key, comment);
    }
}
