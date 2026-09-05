package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.SomeStacksNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cancels the vanilla right-click that follows a stack interaction within the same tick, so the held
 * item is not also placed or used against the position the interaction just changed. The mark lives
 * one tick, keyed by player. {@link Protection} owns when one is placed and is the only caller of
 * {@link #suppress}.
 */
@EventBusSubscriber(modid = SomeStacksNeoForge.MODID)
public final class RightClickBlockSuppressor {

    private record Mark(long tick, long pos) {}

    private static final Map<UUID, Mark> marks = new HashMap<>();

    private RightClickBlockSuppressor() {
    }

    static void suppress(Player player, BlockPos pos, Level level) {
        if (level.isClientSide) return;

        marks.put(player.getUUID(), new Mark(level.getGameTime(), pos.asLong()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;

        Player player = event.getEntity();
        Mark mark = marks.get(player.getUUID());
        if (mark == null) return;

        if (mark.tick() != event.getLevel().getGameTime()) {
            marks.remove(player.getUUID());
            return;
        }

        if (mark.pos() != event.getPos().asLong()) return;

        event.setCanceled(true);
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        marks.remove(event.getEntity().getUUID());
    }
}
