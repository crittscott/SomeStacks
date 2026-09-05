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
 * Attaches {@code ForgeCapabilities.ITEM_HANDLER} to the mod's block entities from outside them,
 * through the ordinary Forge capability-attachment event rather than an overridden
 * {@code getCapability}. This is what keeps the block entity classes themselves loader-neutral: the
 * common module's unpatched compile environment has no {@code getCapability} to override in the
 * first place.
 *
 * <p>One {@link LazyOptional} is created per block entity and invalidated through the event's own
 * listener hook, the same lifecycle the block entity's own {@code invalidateCaps}/{@code reviveCaps}
 * would have driven if it owned the field itself.
 */
@Mod.EventBusSubscriber(modid = SomeStacks.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeCapabilityAttachment {
    private static final ResourceLocation ITEM_HANDLER_ID =
            ResourceLocation.fromNamespaceAndPath(SomeStacks.MODID, "item_handler");

    private ForgeCapabilityAttachment() {
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<BlockEntity> event) {
        if (event.getObject() instanceof StorageStackBE sbe) {
            attach(event, () -> new PileItemHandler(sbe));
        } else if (event.getObject() instanceof SinglesStackBE ssbe) {
            attach(event, () -> new SinglesColumnHandler(ssbe));
        } else if (event.getObject() instanceof BarStackBE bbe) {
            attach(event, () -> new BarColumnHandler(bbe));
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
