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
 * Enforces one stack gesture per player per tick and separately rate-limits rotation sounds.
 *
 * <p>The client matches at most one interaction rule per click and cancels the off-hand pass, but
 * custom packets bypass vanilla's interaction pacing. The server therefore restores that limit at
 * the packet boundary. An attempt spends the tick even if later validation rejects it.
 *
 * <p>Rotation remains available every tick, but its sound is limited to once every four ticks so a
 * held gesture does not broadcast twenty sounds per second.
 */
@Mod.EventBusSubscriber(modid = SomeStacks.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GestureThrottle {
    private GestureThrottle() {}

    /** Minimum ticks between rotation sounds initiated by the same player. */
    private static final long ROTATION_SOUND_INTERVAL = 4L;

    /** Last gesture-attempt tick by player. */
    private static final Map<UUID, Long> lastGestureTick = new HashMap<>();

    /** Last rotation-sound tick by initiating player. */
    private static final Map<UUID, Long> lastRotationSoundTick = new HashMap<>();

    /**
     * Claims this player's gesture allowance for the current tick.
     *
     * @return whether the gesture may proceed
     */
    public static boolean claimTick(ServerPlayer sp) {
        long tick = sp.serverLevel().getGameTime();
        Long last = lastGestureTick.put(sp.getUUID(), tick);
        return last == null || last != tick;
    }

    /**
     * Claims this player's rotation-sound allowance. This does not gate the rotation, which the
     * caller has already completed.
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
