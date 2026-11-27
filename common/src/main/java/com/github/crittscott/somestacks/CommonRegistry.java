package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

/**
 * Loader-neutral access to the registered blocks, block entity types, and sound events. Each
 * loader's registration class ({@code ForgeRegistry} or {@code FabricRegistry}) assigns these once
 * registration is set up; common code never registers anything itself, only reads these back.
 *
 * <p>There are no block items, recipes, or menus: stacks enter the world through player gestures or
 * automation, and players access contents by clicking rendered cells. Each block class supplies its
 * own shared properties. Singles and Bar use dynamic shapes; all types block pistons because moving
 * a stack would leave its block entity behind.
 */
public final class CommonRegistry {
    private CommonRegistry() {}

    /** Registry paths shared by the loader registration mechanisms. */
    public static final String STORAGE_STACK_BLOCK_ID = "storage_stack_block";

    public static final String SINGLES_STACK_BLOCK_ID = "singles_stack_block";
    public static final String BAR_STACK_BLOCK_ID = "bar_stack_block";
    public static final String STORAGE_STACK_BE_ID = "stack_be";
    public static final String SINGLES_STACK_BE_ID = "singles_stack_be";
    public static final String BAR_STACK_BE_ID = "bar_stack_be";

    public static Supplier<Block> STORAGE_STACK_BLOCK;
    public static Supplier<Block> SINGLES_STACK_BLOCK;
    public static Supplier<Block> BAR_STACK_BLOCK;

    public static Supplier<BlockEntityType<StorageStackBE>> STORAGE_STACK_BE;
    public static Supplier<BlockEntityType<SinglesStackBE>> SINGLES_STACK_BE;
    public static Supplier<BlockEntityType<BarStackBE>> BAR_STACK_BE;

    /**
     * One sound event per stack action, {@code somestacks:block.<type>.<action>}. Each loader
     * registers every constant under its path and binds the registered event to it; resource packs
     * replace what each plays through {@code assets/somestacks/sounds.json}.
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

        private final String path;
        private Supplier<SoundEvent> event;

        Sound(String path) {
            this.path = path;
        }

        /** Binds the loader's registered event; the loader calls this once per constant. */
        public void bind(Supplier<SoundEvent> registered) {
            event = registered;
        }

        /** The registered event. */
        public SoundEvent get() {
            return event.get();
        }

        /** The registry path under the mod's namespace. */
        public String path() {
            return path;
        }
    }
}
