package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.StorageStackBlock;
import com.github.crittscott.somestacks.server.AutomationActor;
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
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.*;

/** World edits and their notifications, observed through the world's native game-event dispatcher. */
public final class WorldEditChecks {
    private WorldEditChecks() {}

    /**
     * To reproduce: have a claim or logging mod report who grows a Storage pile through a hopper.
     * Both loaders report the same [SomeStacks] player.
     */
    public static void automationUsesSharedIdentity(GameTestHelper helper) {
        var actual = WorldEdits.automationActor(helper.getLevel()).getGameProfile();
        checkEquals(AutomationActor.PROFILE.getId(), actual.getId(), "Automation UUID");
        checkEquals(AutomationActor.PROFILE.getName(), actual.getName(), "Automation name");
        helper.succeed();
    }

    /**
     * To reproduce: V-place a temporary Storage Stack into a water source and extract its only
     * item. Settlement removes the empty block and the water source remains.
     */
    public static void cleanupOfWaterloggedStackKeepsTheWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = placeStorage(helper, ORIGIN).getBlockPos();
        level.setBlock(pos, level.getBlockState(pos).setValue(StorageStackBlock.WATERLOGGED, true),
                Block.UPDATE_ALL);
        StorageStackBE storage = (StorageStackBE) level.getBlockEntity(pos);
        storage.getItems().insertItem(0, new ItemStack(Items.STONE), false);
        StoragePile pile = storage.pile();
        check(pile != null, "Pile did not resolve");
        checkEquals(1, pile.extract(0, 1, false).getCount(), "Extraction count");
        pile.settle();
        helper.assertBlockNotPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN);
        check(level.getFluidState(pos).getType() == Fluids.WATER && level.getFluidState(pos).isSource(),
                "Cleanup removed the water source");
        helper.succeed();
    }

    /**
     * To reproduce: in a claim that admits a player but denies [SomeStacks], extract the last item
     * from a temporary Storage, Singles, and Bar Stack, and toggle an empty permanent Storage pile
     * back to temporary. Each empty block is removed on the player's behalf, so the claim allows
     * it and logging mods record the player. Extracting one of two Storage items by hand and the
     * other by automation before settlement attributes the cleanup to [SomeStacks] instead, so
     * that claim keeps the empty block.
     */
    public static void playerCausedCleanupCarriesThePlayer(GameTestHelper helper, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        check(!AutomationActor.is(player), "Fixture player is the automation actor");

        StorageStackBE storage = placeStorage(helper, ORIGIN);
        storage.getItems().insertItem(0, new ItemStack(Items.STONE), false);
        expectRemovalBy(level, storage.getBlockPos(), player, () -> {
            checkEquals(1, storage.extractAt(0, 1, ItemStack.EMPTY, player).getCount(), "Storage extraction");
            storage.pile().settle();
        });

        StorageStackBE permanent = placeStorage(helper, ORIGIN);
        permanent.pile().setPermanent(true, null);
        permanent.pile().settle();
        helper.assertBlockPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN);
        expectRemovalBy(level, permanent.getBlockPos(), player, () -> {
            permanent.pile().setPermanent(false, player);
            permanent.pile().settle();
        });

        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        singles.getItems().insertItem(0, new ItemStack(Items.STONE), false);
        expectRemovalBy(level, singles.getBlockPos(), player,
                () -> checkEquals(1, singles.extractAt(0, player).getCount(), "Singles extraction"));

        BarStackBE bars = placeBar(helper, ORIGIN);
        bars.getItems().insertItem(0, new ItemStack(firstBarItem()), false);
        expectRemovalBy(level, bars.getBlockPos(), player,
                () -> checkEquals(1, bars.extractAt(0, player).getCount(), "Bar extraction"));

        StorageStackBE mixed = placeStorage(helper, ORIGIN);
        mixed.getItems().insertItem(0, new ItemStack(Items.STONE, 2), false);
        expectRemovalBy(level, mixed.getBlockPos(), WorldEdits.automationActor(level), () -> {
            checkEquals(1, mixed.extractAt(0, 1, ItemStack.EMPTY, player).getCount(), "Player extraction");
            StoragePile pile = mixed.pile();
            checkEquals(1, pile.extract(0, 1, false).getCount(), "Automation extraction");
            pile.settle();
        });
        helper.succeed();
    }

    private static void expectRemovalBy(ServerLevel level, BlockPos pos, ServerPlayer actor, Runnable edit) {
        BlockState state = level.getBlockState(pos);
        try (EventProbe events = new EventProbe(level, pos)) {
            edit.run();
            check(level.getBlockState(pos).isAir(), "Emptied stack survived at " + pos);
            events.expectOne(GameEvent.BLOCK_DESTROY, actor, state);
        }
    }

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
            checkEquals(1, grown.extractAt(0, 1, ItemStack.EMPTY, null).getCount(), "Upper extraction");
            storage.pile().settle();
            check(!level.getBlockState(above).is(CommonRegistry.STORAGE_STACK_BLOCK.get()), "Empty top survived");
            upperEvents.expectOne(GameEvent.BLOCK_DESTROY, WorldEdits.automationActor(level), grownState);
            baseEvents.expectNone();
        }
        helper.succeed();
    }

    /**
     * Records placement, destruction, and change events at one position and unregisters at the end
     * of the check.
     */
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
            if ((event == GameEvent.BLOCK_PLACE || event == GameEvent.BLOCK_DESTROY
                    || event == GameEvent.BLOCK_CHANGE)
                    && location.equals(Vec3.atCenterOf(pos))) {
                events.add(new Observed(event, context));
                return true;
            }
            return false;
        }

        public void expectOne(GameEvent event, ServerPlayer actor, BlockState state) {
            checkEquals(1, events.size(), "Block event count at " + pos);
            Observed observed = events.get(0);
            check(observed.event == event, "Wrong block event at " + pos);
            check(observed.context.sourceEntity() == actor, "Wrong block event actor");
            checkEquals(state, observed.context.affectedState(), "Block event affected state");
            events.clear();
        }

        public void expectNone() {
            checkEquals(0, events.size(), "Unexpected block event at " + pos);
        }

        @Override
        public void close() {
            registry.unregister(this);
        }

        private record Observed(GameEvent event, GameEvent.Context context) {}
    }
}
