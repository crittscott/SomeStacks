package com.github.crittscott.somestacks.block;

import net.minecraftforge.items.IItemHandler;

/** Forge item-handler view over whichever complete run owns the attached stack block. */
public final class RunItemHandler extends RunItemHandlerBase implements IItemHandler {
    public RunItemHandler(StackBlockEntity blockEntity) {
        super(blockEntity);
    }
}
