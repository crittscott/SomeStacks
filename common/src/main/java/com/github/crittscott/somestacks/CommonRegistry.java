package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Shared identifiers and access to values after loader-native registration completes. */
public final class CommonRegistry {
    private CommonRegistry() {}

    public static final ResourceLocation STORAGE_STACK_BLOCK = id("storage_stack_block");
    public static final ResourceLocation SINGLES_STACK_BLOCK = id("singles_stack_block");
    public static final ResourceLocation BAR_STACK_BLOCK = id("bar_stack_block");
    public static final ResourceLocation STORAGE_STACK_BE = id("stack_be");
    public static final ResourceLocation SINGLES_STACK_BE = id("singles_stack_be");
    public static final ResourceLocation BAR_STACK_BE = id("bar_stack_be");

    /**
     * The mod's sound events, declared once. Each loader registers every entry under its {@link
     * #id()}, and common code plays {@link #get()}.
     */
    public enum Sound {
        BAR_DEPOSIT("block.bar_stack.deposit"),
        BAR_EXTRACT("block.bar_stack.extract"),
        SINGLES_DEPOSIT("block.singles_stack.deposit"),
        SINGLES_EXTRACT("block.singles_stack.extract"),
        SINGLES_ROTATE("block.singles_stack.rotate"),
        SINGLES_ROTATE_ITEM("block.singles_stack.rotate_item"),
        STORAGE_DEPOSIT("block.storage_stack.deposit"),
        STORAGE_EXTRACT("block.storage_stack.extract"),
        STORAGE_ROTATE("block.storage_stack.rotate");

        private final ResourceLocation id;

        Sound(String path) {
            id = CommonRegistry.id(path);
        }

        /** The registered event, available once the loader has registered sounds. */
        public SoundEvent get() {
            return require(BuiltInRegistries.SOUND_EVENT, id);
        }

        public ResourceLocation id() {
            return id;
        }
    }

    public static Block storageStackBlock() {
        return require(BuiltInRegistries.BLOCK, STORAGE_STACK_BLOCK);
    }

    public static Block singlesStackBlock() {
        return require(BuiltInRegistries.BLOCK, SINGLES_STACK_BLOCK);
    }

    public static Block barStackBlock() {
        return require(BuiltInRegistries.BLOCK, BAR_STACK_BLOCK);
    }

    public static BlockEntityType<StorageStackBE> storageStackBe() {
        return blockEntityType(STORAGE_STACK_BE);
    }

    public static BlockEntityType<SinglesStackBE> singlesStackBe() {
        return blockEntityType(SINGLES_STACK_BE);
    }

    public static BlockEntityType<BarStackBE> barStackBe() {
        return blockEntityType(BAR_STACK_BE);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, path);
    }

    private static <T> T require(Registry<T> registry, ResourceLocation id) {
        if (!registry.containsKey(id)) {
            throw new IllegalStateException("Some Stacks registry entry is missing: " + id);
        }
        return registry.get(id);
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> BlockEntityType<T> blockEntityType(ResourceLocation id) {
        return (BlockEntityType<T>) require(BuiltInRegistries.BLOCK_ENTITY_TYPE, id);
    }
}
