package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.mojang.authlib.GameProfile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** The loader-independent identity attributed to Some Stacks automation. */
public final class AutomationActor {
    public static final GameProfile PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes((SomeStacksCommon.MODID + ":automation").getBytes(StandardCharsets.UTF_8)),
            "[SomeStacks]");

    private AutomationActor() {}
}
