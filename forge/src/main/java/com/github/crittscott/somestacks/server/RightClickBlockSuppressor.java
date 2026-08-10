package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cancels the vanilla right-click that follows a stack interaction within the same tick, so
 * the held item is not also placed or used against the position the interaction just changed.
 *
 * <p>The mark lives for one tick and is held in memory, keyed by player: it describes an
 * in-flight interaction, not player state worth saving. {@link Protection} owns when one is
 * placed, and is the only caller of {@link #suppress}; see its class documentation for why the
 * ordering against the protection consults matters.
 */
@Mod.EventBusSubscriber(modid = SomeStacks.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        marks.remove(event.getEntity().getUUID());
    }
}
