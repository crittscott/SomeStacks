package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.client.NeoForgeClientEvents;
import com.github.crittscott.somestacks.client.NeoForgeKeyMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** NeoForge development-client registration for {@link ClientCallbackChecks#run}. */
@EventBusSubscriber(modid = SomeStacksGameTests.MOD_ID, value = Dist.CLIENT)
public final class ClientCallbackGameTests {
    private ClientCallbackGameTests() {}

    /** Registers the local /ssclienttest smoke command in a client running the test mod. */
    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ssclienttest").executes(ctx ->
                ClientCallbackChecks.run(NeoForgeKeyMappings.STACK_MODE_KEY.isDown(), (hand, hit) -> {
                    var click = new PlayerInteractEvent.RightClickBlock(
                            Minecraft.getInstance().player, hand, hit.getBlockPos(), hit);
                    NeoForgeClientEvents.onRightClick(click);
                    return new ClientCallbackChecks.Result(click.isCanceled()
                            && click.getCancellationResult() == InteractionResult.SUCCESS,
                            click.getUseBlock() == TriState.FALSE, click.getUseItem() == TriState.FALSE);
                }, (be, tag, initial) -> {
                    var input = TagValueInput.create(ProblemReporter.DISCARDING, be.getLevel().registryAccess(), tag);
                    IBlockEntityExtension nativeEntity = (IBlockEntityExtension) be;
                    if (initial) nativeEntity.handleUpdateTag(input);
                    else nativeEntity.onDataPacket(null, input);
                })));
    }
}
