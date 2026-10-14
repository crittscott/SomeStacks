package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.WorldEdits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.*;

/** Structural notifications observed through the world's native game-event dispatcher. */
public final class WorldEditChecks {
    private WorldEditChecks() {}

    /**
     * To reproduce: put a sculk sensor near a floor, V-place Storage holding dirt, fill its slots,
     * and deposit stone to grow it. Extract the stone and let settlement trim the upper block.
     * Each structural edit produces its placement or destruction vibration, subject to ordinary
     * sculk filtering. Inventory edits alone do not produce these structural events.
     */
    public static void placementGrowthAndCleanupNotify(GameTestHelper helper, ServerPlayer player,
                                                     Consumer<ServerPlayer> placeGesture) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(ORIGIN);
        BlockPos above = base.above();
        level.setBlock(base.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        try (EventProbe baseEvents = new EventProbe(level, base);
             EventProbe upperEvents = new EventProbe(level, above);
             InteractionChecks.PlayerScope scope = new InteractionChecks.PlayerScope(player)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
            placeGesture.accept(player);
            BlockState baseState = level.getBlockState(base);
            check(baseState.is(CommonRegistry.STORAGE_STACK_BLOCK.get()), "Gesture did not place Storage");
            baseEvents.expectOne(GameEvent.BLOCK_PLACE, player, baseState);
            upperEvents.expectNone();
            StorageStackBE storage = (StorageStackBE) level.getBlockEntity(base);
            for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
                storage.getItems().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
            }
            checkEquals(1, storage.deposit(new ItemStack(Items.STONE)), "Automated growth deposit");
            BlockState grownState = level.getBlockState(above);
            check(grownState.is(CommonRegistry.STORAGE_STACK_BLOCK.get()), "Growth did not place Storage");
            upperEvents.expectOne(GameEvent.BLOCK_PLACE, WorldEdits.automationActor(level), grownState);
            baseEvents.expectNone();
            StorageStackBE grown = (StorageStackBE) level.getBlockEntity(above);
            checkEquals(1, grown.extractAt(0, 1, ItemStack.EMPTY).getCount(), "Upper extraction");
            storage.pile().settle();
            check(!level.getBlockState(above).is(CommonRegistry.STORAGE_STACK_BLOCK.get()), "Empty top survived");
            upperEvents.expectOne(GameEvent.BLOCK_DESTROY, WorldEdits.automationActor(level), grownState);
            baseEvents.expectNone();
        }
        helper.succeed();
    }

    /** Records only structural events at one position and unregisters at the end of the check. */
    public static final class EventProbe implements GameEventListener, AutoCloseable {
        private final BlockPos pos;
        private final PositionSource source;
        private final GameEventListenerRegistry registry;
        private final List<Observed> events = new ArrayList<>();

        public EventProbe(ServerLevel level, BlockPos pos) {
            this.pos = pos.immutable();
            source = new BlockPositionSource(this.pos);
            registry = level.getChunkAt(pos).getListenerRegistry(SectionPos.blockToSectionCoord(pos.getY()));
            registry.register(this);
        }

        @Override
        public PositionSource getListenerSource() {
            return source;
        }

        @Override
        public int getListenerRadius() {
            return 16;
        }

        @Override
        public boolean handleGameEvent(ServerLevel level, GameEvent event,
                                       GameEvent.Context context, Vec3 location) {
            if ((event == GameEvent.BLOCK_PLACE || event == GameEvent.BLOCK_DESTROY)
                    && location.equals(Vec3.atCenterOf(pos))) {
                events.add(new Observed(event, context));
                return true;
            }
            return false;
        }

        public void expectOne(GameEvent event, ServerPlayer actor, BlockState state) {
            checkEquals(1, events.size(), "Structural event count at " + pos);
            Observed observed = events.get(0);
            check(observed.event == event, "Wrong structural event");
            check(observed.context.sourceEntity() == actor, "Wrong structural actor");
            checkEquals(state, observed.context.affectedState(), "Structural affected state");
            events.clear();
        }

        public void expectNone() {
            checkEquals(0, events.size(), "Unexpected structural event at " + pos);
        }

        @Override
        public void close() {
            registry.unregister(this);
        }

        private record Observed(GameEvent event, GameEvent.Context context) {}
    }
}
