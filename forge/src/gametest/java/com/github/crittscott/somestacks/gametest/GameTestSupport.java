package com.github.crittscott.somestacks.gametest;

import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Forge-native scaffolding for the GameTests: the unprefixed template name, {@code IItemHandler}
 * capability access and the shared scenarios' automation view of it, and a synthetic {@link
 * ServerPlayer}. Everything loader-neutral lives in {@link GameTestScaffold}.
 */
public final class GameTestSupport {
    /** Shared structure resource; test-name prefixes do not affect structure identifiers. */
    public static final String TEMPLATE = "somestacks:somestacks_empty";

    private static final GameProfile FAKE_PLAYER_PROFILE =
            new GameProfile(
                    UUID.nameUUIDFromBytes("somestacks:gametest".getBytes(StandardCharsets.UTF_8)),
                    "[SomeStacksGameTest]");

    private static final Map<ServerLevel, ServerPlayer> FAKE_PLAYERS = new WeakHashMap<>();

    private GameTestSupport() {}

    /**
     * The {@link GameTestScaffold.Automation} view of {@code blockEntity}'s item-handler
     * capability, as a hopper or pipe reaches it.
     */
    public static GameTestScaffold.Automation automation(BlockEntity blockEntity) {
        IItemHandler handler = capability(blockEntity);
        return new GameTestScaffold.Automation() {
            @Override
            public ItemStack extract(int slot, int amount, boolean simulate) {
                return handler.extractItem(slot, amount, simulate);
            }

            @Override
            public ItemStack insert(int slot, ItemStack stack, boolean simulate) {
                return handler.insertItem(slot, stack, simulate);
            }

            @Override
            public int slots() {
                return handler.getSlots();
            }

            @Override
            public ItemStack stackAt(int slot) {
                return handler.getStackInSlot(slot);
            }
        };
    }

    public static IItemHandler capability(BlockEntity blockEntity) {
        IItemHandler handler =
                blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        GameTestScaffold.check(
                handler != null, "Missing item-handler capability at " + blockEntity.getBlockPos());
        return handler;
    }

    /**
     * A synthetic {@link ServerPlayer} for {@code level}, one per level for the run, standing in
     * for the {@code FakePlayerFactory} Forge does not ship. Real clicks and breaks reach vanilla
     * code that writes to {@code player.connection}, so it has a connection that never sends a
     * packet.
     */
    public static ServerPlayer fakePlayer(ServerLevel level) {
        return FAKE_PLAYERS.computeIfAbsent(level, TestPlayer::new);
    }

    private static final class TestPlayer extends ServerPlayer {
        private TestPlayer(ServerLevel level) {
            super(
                    level.getServer(),
                    level,
                    FAKE_PLAYER_PROFILE,
                    ClientInformation.createDefault());
            connection =
                    new ServerGamePacketListenerImpl(
                            level.getServer(),
                            // Qualified: inside a ServerPlayer subclass, the simple name
                            // resolves to the inherited WaypointTransmitter.Connection.
                            new net.minecraft.network.Connection(PacketFlow.SERVERBOUND) {
                                @Override
                                public void setListenerForServerboundHandshake(
                                        PacketListener listener) {}
                            },
                            this,
                            CommonListenerCookie.createInitial(FAKE_PLAYER_PROFILE, false)) {
                        @Override
                        public void send(Packet<?> packet) {}
                    };
        }

        @Override
        public void sendSystemMessage(Component message) {}

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {}
    }

    /** A fake-player factory that resets the main hand for each shared check. */
    public static Function<ItemStack, ServerPlayer> playerFactory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return mainHand -> {
            ServerPlayer player = fakePlayer(level);
            player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
            return player;
        };
    }
}
