package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(SomeStacks.MODID)
@PrefixGameTestTemplate(false)
public final class PacketBoundaryGameTests {
    private static final String TEMPLATE = "somestacks_empty";
    private static final BlockPos TARGET = new BlockPos(2, 1, 2);

    private PacketBoundaryGameTests() {}

    @GameTest(template = TEMPLATE)
    public static void reachCheckAcceptsNearTargetAndRejectsFarTarget(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        Vec3 targetCenter = Vec3.atCenterOf(helper.absolutePos(TARGET));
        player.setPos(targetCenter.x, targetCenter.y, targetCenter.z);

        check(PacketBoundary.withinReach(player, helper.absolutePos(TARGET)),
                "Near target was rejected");
        check(!PacketBoundary.withinReach(player, helper.absolutePos(TARGET.east(20))),
                "Far target was accepted");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void gestureChecksReadOnlyTheMainHand(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE));

        check(PacketBoundary.mainHandEmpty(player),
                "Off-hand item made main hand nonempty");
        check(!PacketBoundary.holdsInMainHand(player, Items.STONE),
                "Off-hand item satisfied main-hand check");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        check(!PacketBoundary.mainHandEmpty(player), "Occupied main hand reported empty");
        check(PacketBoundary.holdsInMainHand(player, Items.STONE),
                "Held main-hand item was rejected");
        check(!PacketBoundary.holdsInMainHand(player, Items.DIRT),
                "Wrong main-hand item was accepted");
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
