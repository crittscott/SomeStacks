package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.client.FabricClientEvents;
import com.github.crittscott.somestacks.client.FabricClientRendering;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Fabric development-client registration for {@link ClientCallbackChecks#run}. Fabric's {@code
 * UseBlockCallback} returns one {@link InteractionResult} with no separate block-use or item-use
 * denial, so this reports only whether the click was consumed.
 */
public final class ClientCallbackGameTests implements ClientModInitializer {
    /** Registers the local /ssclienttest smoke command in a client running the test mod. */
    // spotless:off
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                dispatcher.register(ClientCommandManager.literal("ssclienttest").executes(ctx ->
                        ClientCallbackChecks.run(FabricClientRendering.STACK_MODE_KEY.isDown(), (hand, hit) -> {
                            Minecraft client = Minecraft.getInstance();
                            InteractionResult result = FabricClientEvents.onUseBlock(client.player, client.level, hand, hit);
                            return ClientCallbackChecks.Result.consumedOnly(result == InteractionResult.SUCCESS);
                        }, (be, tag, initial) -> be.loadWithComponents(TagValueInput.create(
                                ProblemReporter.DISCARDING, be.getLevel().registryAccess(), tag)), hit -> {}))));
    }
    // spotless:on
}
