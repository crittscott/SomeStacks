package com.github.crittscott.somestacks.client;

/**
 * How a stored item is drawn inside its cell. The string ids are the stable form: they appear in
 * override JSON, in {@code ss item} commands, and on the wire, so they outrank the constant names.
 */
public enum RenderMode {
    TWO_D("2d"),
    THREE_D("3d"),
    BLOCK("block"),
    GUI("gui");

    private final String id;

    RenderMode(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /** The mode with this id, or null when the id names none. */
    public static RenderMode fromString(String s) {
        for (RenderMode mode : values()) {
            if (mode.id.equals(s)) {
                return mode;
            }
        }

        return null;
    }
}
