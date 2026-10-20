package com.github.crittscott.somestacks.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * NeoForge-native scaffolding for the GameTests: {@code ResourceHandler}
 * capability access through {@code Capabilities.Item.BLOCK}, and the level's fake player.
 * Everything loader-neutral lives in {@link GameTestScaffold}.
 */
public final class GameTestSupport {
    private GameTestSupport() {}

    public static ResourceHandler<ItemResource> capability(BlockEntity blockEntity) {
        ResourceHandler<ItemResource> handler = handlerOn(blockEntity, null);
        GameTestScaffold.check(handler != null,
                "Missing item-resource capability at " + blockEntity.getBlockPos());
        return handler;
    }

    /** Whether the whole-run item handler is exposed on {@code side}; {@code null} is the sideless query. */
    public static boolean capabilityPresent(BlockEntity blockEntity, Direction side) {
        return handlerOn(blockEntity, side) != null;
    }

    private static ResourceHandler<ItemResource> handlerOn(BlockEntity blockEntity, Direction side) {
        return ((ServerLevel) blockEntity.getLevel()).getCapability(
                Capabilities.Item.BLOCK,
                blockEntity.getBlockPos(),
                blockEntity.getBlockState(),
                blockEntity,
                side);
    }

    /** {@link GameTestScaffold#count} for a real NeoForge capability, as {@link #capability} returns. */
    public static int count(ResourceHandler<ItemResource> handler, Item item) {
        int total = 0;
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemStack stack = handler.getResource(slot).toStack(handler.getAmountAsInt(slot));
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Offers through the native transaction API; an uncommitted offer is rolled back on close. */
    public static int offer(ResourceHandler<ItemResource> handler, int slot, ItemStack stack, boolean commit) {
        try (Transaction transaction = Transaction.open(null)) {
            int accepted = handler.insert(slot, ItemResource.of(stack), stack.getCount(), transaction);
            if (commit) transaction.commit();
            return accepted;
        }
    }

    /**
     * The level's shared fake player, standing in for automation. This is the same actor
     * {@link com.github.crittscott.somestacks.server.NeoForgeEditAuthority} uses, and NeoForge still
     * ships {@code FakePlayerFactory}, so there is nothing to reconstruct by hand.
     */
    public static ServerPlayer fakePlayer(ServerLevel level) {
        return FakePlayerFactory.getMinecraft(level);
    }

    /** A fake-player factory that puts the player in Survival and resets the main hand for each shared check. */
    public static Function<ItemStack, ServerPlayer> playerFactory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return mainHand -> {
            ServerPlayer player = fakePlayer(level);
            // Checks expect Survival spending; the shared fake player keeps whatever mode it last had.
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
            return player;
        };
    }

    /**
     * A factory for checks that observe advancement progress, which NeoForge's fake player does
     * not record. The player is an ordinary unconnected {@link ServerPlayer} with Survival
     * abilities; changing its mode would broadcast through the connection it lacks.
     */
    public static Function<ItemStack, ServerPlayer> advancementPlayerFactory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return mainHand -> {
            ServerPlayer player = ADVANCEMENT_PLAYERS.computeIfAbsent(level, key -> new ServerPlayer(
                    key.getServer(), key, ADVANCEMENT_PLAYER_PROFILE, ClientInformation.createDefault()) {
                @Override
                public void displayClientMessage(Component message, boolean actionBar) {
                    // GameTest players have no network connection.
                }
            });
            GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
            player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
            return player;
        };
    }

    private static final GameProfile ADVANCEMENT_PLAYER_PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("somestacks:gametest".getBytes(StandardCharsets.UTF_8)),
            "[SomeStacksGameTest]");
    private static final Map<ServerLevel, ServerPlayer> ADVANCEMENT_PLAYERS = new WeakHashMap<>();
}
