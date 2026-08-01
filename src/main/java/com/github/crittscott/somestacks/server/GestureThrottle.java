package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds each player to one gesture per tick.
 *
 * <p>A gesture displaces a right-click, and a right-click is one interaction per tick: the client
 * matches at most one interaction rule per click and cancels the event, so the off-hand pass never
 * runs and no legitimate client sends a second gesture in the same tick. The mod's packets reach
 * the world outside the vanilla interaction path, though, and so arrive without the throttle that
 * path carries. This restores it.
 *
 * <p>The budget is spent by the attempt rather than by the change it makes, so a packet refused
 * further down has still used the tick. Every gesture the mod recognizes is answered by exactly one
 * packet, so a player sending one gesture per tick never meets this.
 */
@Mod.EventBusSubscriber(modid = SomeStacks.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GestureThrottle {
    private GestureThrottle() {}

    /** The last tick each player spent a gesture on; in memory, like the gestures it counts. */
    private static final Map<UUID, Long> lastGestureTick = new HashMap<>();

    /**
     * Spends this player's gesture for the current tick.
     *
     * @return whether the gesture may proceed
     */
    public static boolean claimTick(ServerPlayer sp) {
        long tick = sp.serverLevel().getGameTime();
        Long last = lastGestureTick.put(sp.getUUID(), tick);
        return last == null || last != tick;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        lastGestureTick.remove(event.getEntity().getUUID());
    }
}
