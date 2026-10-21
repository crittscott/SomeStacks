package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.SomeStacksCommon;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashSet;
import java.util.List;

/** Complete client policy snapshot; Fabric sends it as one bounded play payload. */
public record ConfigSyncPkt(boolean enableStorage, boolean enableSingles, boolean enableBar,
                            List<String> genMods, List<String> disabledMods) implements CustomPacketPayload {
    /** ASCII namespaces: both lists together encode to at most about 130 KiB. */
    public static final int MAX_NAMESPACES = 256;
    public static final int MAX_NAMESPACE_LENGTH = 256;
    public static final Type<ConfigSyncPkt> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SomeStacksCommon.MODID, "config_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPkt> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ConfigSyncPkt::enableStorage,
            ByteBufCodecs.BOOL, ConfigSyncPkt::enableSingles,
            ByteBufCodecs.BOOL, ConfigSyncPkt::enableBar,
            ByteBufCodecs.stringUtf8(MAX_NAMESPACE_LENGTH).apply(ByteBufCodecs.list(MAX_NAMESPACES)), ConfigSyncPkt::genMods,
            ByteBufCodecs.stringUtf8(MAX_NAMESPACE_LENGTH).apply(ByteBufCodecs.list(MAX_NAMESPACES)), ConfigSyncPkt::disabledMods,
            ConfigSyncPkt::new);

    public ConfigSyncPkt {
        genMods = validated(genMods);
        disabledMods = validated(disabledMods);
    }

    private static List<String> validated(List<String> namespaces) {
        if (namespaces.size() > MAX_NAMESPACES) throw new DecoderException("Too many synchronized namespaces");
        var unique = new LinkedHashSet<String>();
        for (String namespace : namespaces) {
            if (namespace.isEmpty() || namespace.length() > MAX_NAMESPACE_LENGTH
                    || !Identifier.isValidNamespace(namespace) || !unique.add(namespace)) {
                throw new DecoderException("Invalid or duplicate synchronized namespace");
            }
        }
        return List.copyOf(namespaces);
    }

    /** Normalizes policy for either native NeoForge receipt or Fabric payload delivery. */
    public static ConfigSyncPkt fromSettings(ServerConfig.Settings settings) {
        return new ConfigSyncPkt(settings.enableStorageStackBlock(), settings.enableSinglesStackBlock(),
                settings.enableBarStackBlock(), boundedNamespaces(settings.genMods(), "gallery mod"),
                boundedNamespaces(settings.disableMods(), "disabled mod"));
    }

    private static List<String> boundedNamespaces(List<String> entries, String label) {
        var result = new LinkedHashSet<String>();
        boolean omitted = false;
        for (String entry : entries) {
            String namespace = ServerConfig.normalizeNamespace(entry);
            if (namespace.isEmpty()) continue;
            if (namespace.length() > MAX_NAMESPACE_LENGTH || !Identifier.isValidNamespace(namespace)) {
                SomeStacksCommon.LOGGER.warn("Ignoring invalid {} namespace '{}' in client policy", label, entry);
            } else if (result.contains(namespace)) {
                continue;
            } else if (result.size() < MAX_NAMESPACES) {
                result.add(namespace);
            } else {
                omitted = true;
            }
        }
        if (omitted) SomeStacksCommon.LOGGER.warn(
                "The {} list exceeds the client policy limit of {}; remaining entries are omitted",
                label, MAX_NAMESPACES);
        return List.copyOf(result);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
