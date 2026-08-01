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
 * Per-player gesture pacing: how often a player may act, and how often an act may be heard.
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
 *
 * <p>Rotation carries a second, longer pace. Deposit and extract stop of their own accord when the
 * hand or the stack runs out, but rotation is free and endless, and at one gesture per tick a held
 * click would sound twenty times a second. The rotation itself is left alone; only its sound waits.
 */
@Mod.EventBusSubscriber(modid = SomeStacks.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GestureThrottle {
    private GestureThrottle() {}

    /** Ticks a player's rotation sound waits before it may be heard again. */
    private static final long ROTATION_SOUND_INTERVAL = 4L;

    /** The last tick each player spent a gesture on; in memory, like the gestures it counts. */
    private static final Map<UUID, Long> lastGestureTick = new HashMap<>();

    /** The last tick each player's rotation was heard on. */
    private static final Map<UUID, Long> lastRotationSoundTick = new HashMap<>();

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

    /**
     * Asks whether this player's rotation may be heard, and records it if so. A refusal silences
     * the sound alone; the rotation it belongs to has already happened.
     *
     * @return whether the sound may play
     */
    public static boolean claimRotationSound(ServerPlayer sp) {
        long tick = sp.serverLevel().getGameTime();
        Long last = lastRotationSoundTick.get(sp.getUUID());
        if (last != null && tick - last < ROTATION_SOUND_INTERVAL) {
            return false;
        }
        lastRotationSoundTick.put(sp.getUUID(), tick);
        return true;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        lastGestureTick.remove(id);
        lastRotationSoundTick.remove(id);
    }
}
