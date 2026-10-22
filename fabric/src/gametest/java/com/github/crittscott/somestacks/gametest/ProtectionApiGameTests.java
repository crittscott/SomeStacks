package com.github.crittscott.somestacks.gametest;

import net.minecraft.world.phys.shapes.CollisionContext;
import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.mojang.authlib.GameProfile;
import eu.pb4.common.protection.api.CommonProtection;
import eu.pb4.common.protection.api.ProtectionProvider;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/**
 * Provider-backed checks, loaded only in development runs started with
 * {@code -PwithCommonProtectionApi=true}. A test provider denies the positions a scenario names
 * and records who asked and what stood there when it did.
 */
public final class ProtectionApiGameTests implements FabricGameTest {
    private record Target(Level level, BlockPos pos) {}

    private static final class Probe {
        Player asker;
        boolean sawAir;
        boolean invoked;
    }

    private static final Map<Target, Probe> DENIED = new ConcurrentHashMap<>();

    private record RemovalNotice(String phase, Player actor, BlockState state, BlockEntity entity) {}

    private static final class RemovalProbe {
        boolean denyBreak;
        boolean denyEvent;
        int queries;
        Player asker;
        GameProfile profile;
        BlockEntity entityAtQuery;
        final List<RemovalNotice> notices = new ArrayList<>();
    }

    private static final Map<Target, RemovalProbe> REMOVALS = new ConcurrentHashMap<>();

    static {
        CommonProtection.register(new ResourceLocation("somestacks", "test_protection"),
                new ProtectionProvider() {
                    @Override
                    public boolean isProtected(Level level, BlockPos pos) {
                        return false;
                    }

                    @Override
                    public boolean isAreaProtected(Level level, AABB area) {
                        return false;
                    }

                    @Override
                    public boolean canPlaceBlock(Level level, BlockPos pos, GameProfile profile, Player player) {
                        return consult(level, pos, player);
                    }

                    @Override
                    public boolean canInteractBlock(Level level, BlockPos pos, GameProfile profile, Player player) {
                        return consult(level, pos, player);
                    }

                    @Override
                    public boolean canBreakBlock(Level level, BlockPos pos, GameProfile profile, Player player) {
                        RemovalProbe probe = REMOVALS.get(new Target(level, pos));
                        if (probe == null) return true;
                        probe.queries++;
                        probe.asker = player;
                        probe.profile = profile;
                        probe.entityAtQuery = level.getBlockEntity(pos);
                        return !probe.denyBreak;
                    }
                });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> {
            RemovalProbe probe = REMOVALS.get(new Target(level, pos));
            if (probe == null) return true;
            probe.notices.add(new RemovalNotice("before", player, state, entity));
            return !probe.denyEvent;
        });
        PlayerBlockBreakEvents.CANCELED.register((level, player, pos, state, entity) -> {
            RemovalProbe probe = REMOVALS.get(new Target(level, pos));
            if (probe != null) {
                probe.notices.add(new RemovalNotice("canceled", player, state, entity));
                check(level.getBlockEntity(pos) == entity, "CANCELED ran after entity removal");
            }
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
            RemovalProbe probe = REMOVALS.get(new Target(level, pos));
            if (probe != null) {
                probe.notices.add(new RemovalNotice("after", player, state, entity));
                check(level.getBlockEntity(pos) != entity, "AFTER ran before entity removal");
            }
        });
    }

    private static boolean consult(Level level, BlockPos pos, Player player) {
        Probe probe = DENIED.get(new Target(level, pos));
        if (probe == null) {
            return true;
        }
        probe.invoked = true;
        probe.asker = player;
        probe.sawAir = level.getBlockState(pos).isAir();
        return false;
    }

    /**
     * To reproduce in-game: deny [SomeStacks] placement in a claim managed through Common
     * Protection API, then pipe items into a full Storage pile whose top sits under the claim.
     * Growth accepts nothing, the input keeps its items, and no block appears.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void automationGrowthConsultsProtectionApi(GameTestHelper helper) {
        StorageStackBE base = GameTestScaffold.placeStorage(helper, ORIGIN);
        for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
            base.getItems().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
        }
        Target key = new Target(helper.getLevel(), base.getBlockPos().above());
        Probe probe = new Probe();
        DENIED.put(key, probe);
        try {
            var storage = FabricGameTestSupport.capability(base);
            checkEquals(4, FabricGameTestSupport.insertAt(storage, StorageStackBE.SLOTS,
                    new ItemStack(Items.STONE, 4), true).getCount(), "Denied growth was advertised");
            checkEquals(4, FabricGameTestSupport.insertAt(storage, StorageStackBE.SLOTS,
                    new ItemStack(Items.STONE, 4), false).getCount(), "Denied growth remainder");
            check(probe.invoked, "Automation skipped the protection query");
            check(probe.asker == WorldEdits.automationActor(helper.getLevel()),
                    "Protection query lost the automation actor");
            check(probe.sawAir, "Automation queried protection after placement");
            helper.assertBlockNotPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN.above());
        } finally {
            DENIED.remove(key);
        }
        helper.succeed();
    }

    /**
     * To reproduce in-game: stand outside a claim managed through Common Protection API and
     * place a stack against a block whose face points into it. Nothing is placed.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void placementConsultsProtectionApiBeforePlacing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayer.get(level);
        Target key = new Target(level, helper.absolutePos(ORIGIN));
        Probe probe = new Probe();
        DENIED.put(key, probe);
        try {
            check(!WorldEdits.placeChecked(player, level, key.pos(), Blocks.STONE.defaultBlockState(), Direction.DOWN, (Blocks.STONE.defaultBlockState()).getCollisionShape(level, key.pos(), CollisionContext.empty())), "Denied placement was accepted");
        } finally {
            DENIED.remove(key);
        }
        check(probe.invoked, "Placement skipped the protection query");
        check(probe.sawAir, "Placement queried protection after placing");
        helper.assertBlockNotPresent(Blocks.STONE, ORIGIN);
        helper.succeed();
    }

    /**
     * To reproduce in-game: deny interaction at a Singles Stack inside a claim managed through
     * Common Protection API, then hold the modifier and right-click the neighboring block toward
     * it from outside. Nothing is deposited.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void adjacentDepositConsultsProtectionApi(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayer.get(level);
        Target key = new Target(level, helper.absolutePos(ORIGIN));
        Probe probe = new Probe();
        DENIED.put(key, probe);
        try {
            check(!WorldEdits.mayUseAdjacent(player, key.pos()),
                    "Protection query did not veto the adjacent deposit");
        } finally {
            DENIED.remove(key);
        }
        check(probe.invoked, "Adjacent deposit skipped the protection query");
        check(probe.asker == player, "Protection query lost the depositing player");
        helper.succeed();
    }

    /**
     * To reproduce: allow placement and interaction in a Common Protection API claim, but deny
     * breaking for [SomeStacks]. Empty a temporary Storage Stack through a hopper; its empty block
     * remains. Next allow the API query but deny breaking through a Fabric event listener; it
     * still remains. Allow both and empty it again; cleanup removes it and emits destruction.
     * The test verifies one cancellation on either denial and the original actor/state/entity.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void automationCleanupConsultsProtectionApi(GameTestHelper helper) {
        checkCleanupProtection(helper, null);
    }

    /**
     * To reproduce: allow placement and interaction in a Common Protection API claim, but deny
     * breaking for your player. Extract the last item from a temporary Storage Stack; its empty
     * block remains. Repeat with the API allowing breaks and a Fabric listener denying them;
     * it still remains. Allow both and empty it again; cleanup removes it as your player.
     * The test checks callback context and destruction vibrations for all three cases.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerCleanupConsultsProtectionApi(GameTestHelper helper) {
        checkCleanupProtection(helper, FakePlayer.get(helper.getLevel()));
    }

    private static void checkCleanupProtection(GameTestHelper helper, @Nullable ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        StorageStackBE stack = GameTestScaffold.placeStorage(helper, ORIGIN);
        BlockPos pos = stack.getBlockPos();
        BlockState state = stack.getBlockState();
        ServerPlayer actor = player != null ? player : WorldEdits.automationActor(level);
        Target key = new Target(level, pos);
        RemovalProbe probe = new RemovalProbe();
        REMOVALS.put(key, probe);
        List<List<String>> phases = List.of(
                List.of("canceled"), List.of("before", "canceled"), List.of("before", "after"));
        try (var events = new WorldEditChecks.EventProbe(level, pos)) {
            for (int phase = 0; phase < phases.size(); phase++) {
                probe.denyBreak = phase == 0;
                probe.denyEvent = phase == 1;
                probe.notices.clear();
                check(CommonProtection.canPlaceBlock(level, pos, actor.getGameProfile(), actor),
                        "Break-only protection denied placement");
                check(CommonProtection.canInteractBlock(level, pos, actor.getGameProfile(), actor),
                        "Break-only protection denied interaction");

                stack.getItems().setStackInSlot(0, new ItemStack(Items.STONE));
                ItemStack taken = player == null ? stack.pile().extract(0, 1, false)
                        : stack.extractAt(0, 1, ItemStack.EMPTY, player);
                checkEquals(1, taken.getCount(), "Extraction was blocked by removal protection");
                stack.pile().settle();

                checkEquals(phase + 1, probe.queries, "Break query count");
                check(probe.asker == actor, "Break query lost the cleanup actor");
                checkEquals(actor.getGameProfile(), probe.profile, "Break query lost the actor profile");
                check(probe.entityAtQuery == stack, "Break query ran after entity removal");
                checkEquals(phases.get(phase), probe.notices.stream().map(RemovalNotice::phase).toList(),
                        "Removal callback sequence");
                for (RemovalNotice notice : probe.notices) {
                    check(notice.actor() == actor, "Removal callback lost the cleanup actor");
                    checkEquals(state, notice.state(), "Removal callback lost the original state");
                    check(notice.entity() == stack, "Removal callback lost the original entity");
                }
                if (phase < 2) {
                    check(level.getBlockEntity(pos) == stack, "Denied cleanup removed the block entity");
                    checkEquals(state, level.getBlockState(pos), "Denied cleanup changed the block state");
                    check(stack.isEmpty(), "Denied cleanup restored extracted contents");
                    events.expectNone();
                } else {
                    helper.assertBlockNotPresent(CommonRegistry.STORAGE_STACK_BLOCK.get(), ORIGIN);
                    events.expectOne(GameEvent.BLOCK_DESTROY, actor, state);
                }
            }
        } finally {
            REMOVALS.remove(key);
        }
        helper.succeed();
    }
}
