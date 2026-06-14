package com.github.crittscott.somestacks;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SomeStacks.MODID);

    // Register custom sound events
    public static final RegistryObject<SoundEvent> BAR_DEPOSIT_SOUND =
            SOUND_EVENTS.register("bar_deposit",
                    () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SomeStacks.MODID, "bar_deposit")));
    public static final RegistryObject<SoundEvent> BAR_EXTRACT_SOUND =
            SOUND_EVENTS.register("bar_extract",
                    () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SomeStacks.MODID, "bar_extract")));
    public static final RegistryObject<SoundEvent> SINGLES_DEPOSIT_SOUND =
            SOUND_EVENTS.register("singles_deposit",
                    () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SomeStacks.MODID, "singles_deposit")));
    public static final RegistryObject<SoundEvent> SINGLES_EXTRACT_SOUND =
            SOUND_EVENTS.register("singles_extract",
                    () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SomeStacks.MODID, "singles_extract")));
    public static final RegistryObject<SoundEvent> STACK_DEPOSIT_SOUND =
            SOUND_EVENTS.register("stack_deposit",
                    () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SomeStacks.MODID, "stack_deposit")));
    public static final RegistryObject<SoundEvent> STACK_EXTRACT_SOUND =
            SOUND_EVENTS.register("stack_extract",
                    () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SomeStacks.MODID, "stack_extract")));

    // The actual sounds used (defaults to vanilla, overridden by SoundConfig)
    public static SoundEvent BAR_DEPOSIT = SoundEvents.WOOD_PLACE;
    public static SoundEvent BAR_EXTRACT = SoundEvents.WOOL_BREAK;
    public static SoundEvent SINGLES_DEPOSIT = SoundEvents.WOOD_PLACE;
    public static SoundEvent SINGLES_EXTRACT = SoundEvents.WOOL_BREAK;
    public static SoundEvent STORAGE_DEPOSIT = SoundEvents.WOOD_PLACE;
    public static SoundEvent STORAGE_EXTRACT = SoundEvents.WOOL_BREAK;
}
