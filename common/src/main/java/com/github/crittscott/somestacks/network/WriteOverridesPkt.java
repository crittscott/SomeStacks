package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: asks the receiving client to write render override JSON, as directed by
 * the {@code ss write} command. Carrying no namespace means the user override layer, written
 * to the file the client loads at startup; carrying namespaces means a dump of every item in
 * them, written where nothing reads it back.
 */
public class WriteOverridesPkt implements CustomPacketPayload {
    public static final Type<WriteOverridesPkt> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "write_overrides"));
    public static final StreamCodec<FriendlyByteBuf, WriteOverridesPkt> STREAM_CODEC =
            StreamCodec.ofMember(WriteOverridesPkt::encode, WriteOverridesPkt::decode);

    /** Defensive upper bound on namespace count received from the server. */
    private static final int MAX_NAMESPACES = 4096;

    private final List<String> namespaces;

    private WriteOverridesPkt(List<String> namespaces) {
        this.namespaces = namespaces;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Writes the entries {@code ss item} set. */
    public static WriteOverridesPkt userLayer() {
        return new WriteOverridesPkt(List.of());
    }

    /** Dumps the resolved profile of every item in these namespaces. */
    public static WriteOverridesPkt dump(List<String> namespaces) {
        return new WriteOverridesPkt(List.copyOf(namespaces));
    }

    public static void encode(WriteOverridesPkt msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.namespaces.size());
        for (String namespace : msg.namespaces) {
            buf.writeUtf(namespace);
        }
    }

    /**
     * Decodes an invalid namespace count to a null-list sentinel for {@link #namespaces()} to reject. An
     * empty list is a valid request to write the user layer, so it cannot represent failure.
     */
    public static WriteOverridesPkt decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        if (size < 0 || size > MAX_NAMESPACES) {
            SomeStacksCommon.LOGGER.warn("Ignoring write request claiming {} namespaces", size);
            return new WriteOverridesPkt(null);
        }

        List<String> namespaces = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            namespaces.add(buf.readUtf());
        }
        return new WriteOverridesPkt(namespaces);
    }

    public List<String> namespaces() {
        return namespaces;
    }
}
