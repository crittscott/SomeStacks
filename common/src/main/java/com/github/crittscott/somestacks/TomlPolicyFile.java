package com.github.crittscott.somestacks;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.github.crittscott.somestacks.ServerConfig.Section;
import com.github.crittscott.somestacks.ServerConfig.Setting;
import com.github.crittscott.somestacks.ServerConfig.Settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * The documented TOML policy file built from the shared {@link Setting} declaration. Fabric reads
 * and writes its policy through it; on every loader, world JSON migration writes its TOML with it.
 */
public final class TomlPolicyFile {
    private final Path file;

    public TomlPolicyFile(Path file) {
        this.file = file;
    }

    /**
     * Parses a policy table; a malformed field uses its default and an out-of-range number is
     * clamped to its declared bounds. Warnings name {@code source}, the file the table came from.
     */
    static Settings decode(UnmodifiableConfig root, Settings defaults, Path source) {
        return Settings.from(setting -> {
            Object fallback = setting.get(defaults);
            Object value = value(root, setting, source);
            if (value == null) return fallback;
            return switch (setting.kind()) {
                case INTEGER -> integer(value, setting, fallback, source);
                case BOOLEAN -> bool(value, setting, fallback, source);
                case STRINGS -> strings(value, setting, fallback, source);
            };
        });
    }

    static CommentedConfig encode(Settings settings) {
        CommentedConfig root = CommentedConfig.of(LinkedHashMap::new, TomlFormat.instance());
        for (Section section : Section.values()) {
            root.set(section.key(), root.createSubConfig());
            root.setComment(section.key(), String.join("\n", section.comment()));
            for (Setting setting : section.settings()) {
                List<String> comment = new ArrayList<>(setting.comment());
                if (setting.kind() == Setting.Kind.INTEGER) {
                    comment.add("Range: " + setting.min() + " ~ " + setting.max());
                }
                root.set(setting.path(), setting.get(settings));
                root.setComment(setting.path(), String.join("\n", comment));
            }
        }
        return root;
    }

    private static Object bool(Object value, Setting setting, Object fallback, Path source) {
        if (value instanceof Boolean) return value;
        SomeStacksCommon.LOGGER.warn(
                "Ignoring non-boolean server policy field {} in {}", setting.path(), source);
        return fallback;
    }

    /**
     * Writes documented defaults when the file is absent, so an admin finds every field explained.
     *
     * @return whether the file was absent
     */
    public boolean createIfMissing(Settings defaults) {
        if (Files.exists(file)) return false;
        if (write(defaults)) {
            SomeStacksCommon.LOGGER.info("Created server policy at {}", file.toAbsolutePath());
        }
        return true;
    }

    private static Object integer(Object value, Setting setting, Object fallback, Path source) {
        if (value instanceof Integer || value instanceof Long) {
            long number = ((Number) value).longValue();
            int bounded = (int) Math.max(setting.min(), Math.min(setting.max(), number));
            if (number != bounded) {
                SomeStacksCommon.LOGGER.warn(
                        "Corrected {} in {} from {} to {}",
                        setting.path(),
                        source,
                        number,
                        bounded);
            }
            return bounded;
        }
        SomeStacksCommon.LOGGER.warn(
                "Ignoring non-integer server policy field {} in {}", setting.path(), source);
        return fallback;
    }

    /** A missing file is created with defaults; a syntax error uses the complete default policy. */
    public Settings read(Settings defaults) {
        if (createIfMissing(defaults)) return defaults;
        try {
            return decode(new TomlParser().parse(Files.readString(file)), defaults, file);
        } catch (IOException | RuntimeException exception) {
            SomeStacksCommon.LOGGER.warn(
                    "Failed to read server policy {}; using defaults. Fix it and run /ss reload before editing lists",
                    file,
                    exception);
            return defaults;
        }
    }

    private static Object strings(Object value, Setting setting, Object fallback, Path source) {
        if (!(value instanceof List<?> list)) {
            SomeStacksCommon.LOGGER.warn(
                    "Ignoring non-list server policy field {} in {}", setting.path(), source);
            return fallback;
        }
        List<String> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof String text) {
                result.add(text);
            } else {
                SomeStacksCommon.LOGGER.warn(
                        "Ignoring non-string entry in server policy field {} in {}",
                        setting.path(),
                        source);
            }
        }
        return List.copyOf(result);
    }

    private static Object value(UnmodifiableConfig root, Setting setting, Path source) {
        Object section = root.get(setting.section().key());
        if (section == null) return null;
        if (section instanceof UnmodifiableConfig config) return config.get(setting.key());
        SomeStacksCommon.LOGGER.warn(
                "Ignoring non-table server policy section {} in {}",
                setting.section().key(),
                source);
        return null;
    }

    /** Writes every field with its explanation and, for bounded numbers, its range. */
    public boolean write(Settings settings) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, new TomlWriter().writeToString(encode(settings)));
            return true;
        } catch (IOException | RuntimeException exception) {
            SomeStacksCommon.LOGGER.warn("Failed to write server policy {}", file, exception);
            return false;
        }
    }
}
