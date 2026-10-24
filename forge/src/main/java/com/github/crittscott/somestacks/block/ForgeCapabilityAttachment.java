package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullSupplier;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Attaches one whole-run {@code ForgeCapabilities.ITEM_HANDLER} view to each stack block entity
 * through Forge's capability-attachment event. The same view serves every queried side.
 *
 * <p>One {@link LazyOptional} is created per block entity and invalidated through the event's own
 * listener hook when the attached provider is invalidated.
 */
@Mod.EventBusSubscriber(modid = SomeStacks.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeCapabilityAttachment {
    private static final ResourceLocation ITEM_HANDLER_ID =
            ResourceLocation.fromNamespaceAndPath(SomeStacks.MODID, "item_handler");

    private ForgeCapabilityAttachment() {
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<BlockEntity> event) {
        if (event.getObject() instanceof StackBlockEntity stack) {
            attach(event, () -> new RunItemHandler(stack));
        }
    }

    private static void attach(AttachCapabilitiesEvent<BlockEntity> event, NonNullSupplier<IItemHandler> handler) {
        LazyOptional<IItemHandler> lazy = LazyOptional.of(handler);

        event.addCapability(ITEM_HANDLER_ID, new ICapabilityProvider() {
            @Nonnull
            @Override
            public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
                return cap == ForgeCapabilities.ITEM_HANDLER ? lazy.cast() : LazyOptional.empty();
            }
        });
        event.addListener(lazy::invalidate);
    }
}
