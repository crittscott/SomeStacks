package com.github.crittscott.somestacks.mixin;

import com.github.crittscott.somestacks.ServerConfigMigration;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Migrates world JSON before the loader reads its native world TOML override. */
@Mixin(targets = "net.neoforged.neoforge.server.ServerLifecycleHooks", remap = false)
public abstract class ServerPolicyMigrationMixin {
    @Inject(method = "handleServerAboutToStart", at = @At("HEAD"), remap = false)
    private static void somestacks$migratePolicy(MinecraftServer server, CallbackInfo callback) {
        ServerConfigMigration.prepareWorld(server);
    }
}
