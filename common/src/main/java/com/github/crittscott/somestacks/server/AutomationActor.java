package com.github.crittscott.somestacks.server;

import com.mojang.authlib.GameProfile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** The loader-independent identity attributed to Some Stacks automation. */
public final class AutomationActor {
    public static final GameProfile PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("somestacks:automation".getBytes(StandardCharsets.UTF_8)),
            "[SomeStacks]");

    private AutomationActor() {}
}
