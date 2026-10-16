package com.github.crittscott.somestacks.server;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** The loader-independent identity attributed to Some Stacks automation. */
public final class AutomationActor {
    public static final UUID ID =
            UUID.nameUUIDFromBytes("somestacks:automation".getBytes(StandardCharsets.UTF_8));
    public static final String NAME = "[SomeStacks]";
    public static final GameProfile PROFILE = new GameProfile(ID, NAME);

    private AutomationActor() {}

    /** Whether {@code player} is the shared Some Stacks automation identity. */
    public static boolean is(ServerPlayer player) {
        return ID.equals(player.getUUID());
    }
}
