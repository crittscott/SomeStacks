package com.github.crittscott.somestacks.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Forge-native scaffolding for the GameTests: the unprefixed template name, {@code IItemHandler}
 * capability access, and a synthetic {@link ServerPlayer}. Everything loader-neutral lives in
 * {@link GameTestScaffold}.
 */
public final class GameTestSupport {
    /**
     * Fully qualified so Forge does not dot-prefix it with the {@code @GameTestHolder} namespace;
     * it resolves straight to {@code data/somestacks/structure/somestacks_empty.nbt}.
     */
    public static final String TEMPLATE = "somestacks:somestacks_empty";

    private static final GameProfile FAKE_PLAYER_PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("somestacks:gametest".getBytes(StandardCharsets.UTF_8)),
            "[SomeStacksGameTest]");

    private static final Map<ServerLevel, ServerPlayer> FAKE_PLAYERS = new WeakHashMap<>();

    private GameTestSupport() {}

    public static IItemHandler capability(BlockEntity blockEntity) {
        IItemHandler handler =
                blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        GameTestScaffold.check(handler != null,
                "Missing item-handler capability at " + blockEntity.getBlockPos());
        return handler;
    }

    /** {@link GameTestScaffold#count} for a real Forge capability, as {@link #capability} returns. */
    public static int count(IItemHandler handler, Item item) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * A synthetic {@link ServerPlayer} for {@code level}, one per level for the run, standing in for
     * the {@code FakePlayerFactory} Forge 1.21.1 no longer ships. Built the same way
     * {@link com.github.crittscott.somestacks.server.ForgeEditAuthority} builds its automation actor.
     */
    public static ServerPlayer fakePlayer(ServerLevel level) {
        return FAKE_PLAYERS.computeIfAbsent(level, key -> new ServerPlayer(
                key.getServer(), key, FAKE_PLAYER_PROFILE, ClientInformation.createDefault()));
    }
}
