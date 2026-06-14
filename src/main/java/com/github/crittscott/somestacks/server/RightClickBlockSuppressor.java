package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SomeStacks.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RightClickBlockSuppressor {

    private static final String NBT_TICK = "somestacks_suppress_rcb_tick";
    private static final String NBT_POS = "somestacks_suppress_rcb_pos";

    private RightClickBlockSuppressor() {
    }

    public static void suppress(Player player, BlockPos pos, Level level) {
        if (level.isClientSide) return;

        CompoundTag tag = player.getPersistentData();
        tag.putLong(NBT_TICK, level.getGameTime());
        tag.putLong(NBT_POS, pos.asLong());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;

        Player player = event.getEntity();
        Level level = event.getLevel();

        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(NBT_TICK) || !tag.contains(NBT_POS)) return;

        long tick = tag.getLong(NBT_TICK);
        if (tick != level.getGameTime()) {
            tag.remove(NBT_TICK);
            tag.remove(NBT_POS);
            return;
        }

        long suppressedPos = tag.getLong(NBT_POS);
        if (suppressedPos != event.getPos().asLong()) return;

        event.setCanceled(true);
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
    }
}
