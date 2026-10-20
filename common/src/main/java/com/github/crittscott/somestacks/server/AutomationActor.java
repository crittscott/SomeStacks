package com.github.crittscott.somestacks.server;

import com.mojang.authlib.GameProfile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.github.crittscott.somestacks.SomeStacksCommon;

/** The loader-independent identity attributed to Some Stacks automation. */
public final class AutomationActor {
    public static final UUID ID =
            UUID.nameUUIDFromBytes((SomeStacksCommon.MODID + ":automation").getBytes(StandardCharsets.UTF_8));
    public static final String NAME = "[SomeStacks]";
    public static final GameProfile PROFILE = new GameProfile(ID, NAME);

    private AutomationActor() {}

}
