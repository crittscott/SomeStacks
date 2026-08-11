package com.github.crittscott.somestacks.util;

import net.minecraft.world.entity.player.Player;

/** Loader-neutral access to each loader's block-reach implementation. */
public final class PlayerReach {
    private PlayerReach() {}

    @FunctionalInterface
    public interface Provider {
        double blockReach(Player player);
    }

    private static Provider provider = player -> player.getAbilities().instabuild ? 5.0 : 4.5;

    public static void setProvider(Provider provider) {
        PlayerReach.provider = provider;
    }

    public static double blockReach(Player player) {
        return provider.blockReach(player);
    }
}
