package com.github.crittscott.somestacks.client;

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

    public static RenderMode fromString(String s) {
        for (RenderMode mode : values()) {
            if (mode.id.equals(s)) {
                return mode;
            }
        }

        return null;
    }
}
