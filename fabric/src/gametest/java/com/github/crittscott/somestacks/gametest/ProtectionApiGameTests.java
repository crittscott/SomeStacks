package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.mojang.authlib.GameProfile;
import eu.pb4.common.protection.api.CommonProtection;
import eu.pb4.common.protection.api.ProtectionProvider;
import net.fabricmc.fabric.api.entity.FakePlayer;
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
import net.minecraft.world.phys.AABB;

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
            check(!WorldEdits.placeChecked(player, level, key.pos(),
                    Blocks.STONE.defaultBlockState(), Direction.DOWN), "Denied placement was accepted");
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
}
