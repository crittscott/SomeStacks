package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;

/** Login payload validation independent of a live remote client. */
public final class FabricHandshakeChecks {
    private FabricHandshakeChecks() {}

    /**
     * To reproduce in-game: connect matching Fabric builds, then try a client without Some Stacks
     * and one with a different protocol version; only the matching build joins the server. From a
     * matching client, also try a server without Some Stacks, then reconnect to the matching server;
     * the missing handshake is rejected and does not affect the subsequent connection. The fixture
     * checks the payload codec; connection event ordering needs this two-process manual check.
     */
    public static void loginProtocolPayloadIsBounded(GameTestHelper helper) {
        FriendlyByteBuf supported = FabricNetworking.protocolPayload();
        try {
            check(FabricNetworking.matchesProtocol(supported), "Own protocol payload rejected");
        } finally {
            supported.release();
        }
        for (String version : List.of("", "different", "9".repeat(17))) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            try {
                buf.writeUtf(version);
                check(!FabricNetworking.matchesProtocol(buf), "Invalid protocol payload accepted");
            } finally {
                buf.release();
            }
        }
        FriendlyByteBuf truncated = PacketByteBufs.create();
        FriendlyByteBuf trailing = PacketByteBufs.create();
        try {
            truncated.writeVarInt(5).writeByte('0');
            check(!FabricNetworking.matchesProtocol(truncated), "Truncated protocol accepted");
            trailing.writeUtf(String.valueOf(SomeStacksCommon.PROTOCOL_VERSION)).writeByte(0);
            check(!FabricNetworking.matchesProtocol(trailing), "Trailing protocol data accepted");
        } finally {
            truncated.release();
            trailing.release();
        }
        helper.succeed();
    }
}
