package com.github.crittscott.somestacks.mixin;

import com.github.crittscott.somestacks.block.StackBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rejects malformed stack updates before vanilla changes inherited component state. */
@Mixin(BlockEntity.class)
abstract class StackBlockEntityClientUpdateMixin {
    @Inject(method = "loadWithComponents", at = @At("HEAD"), cancellable = true)
    private void somestacks$validateClientUpdate(ValueInput input, CallbackInfo callback) {
        if ((Object) this instanceof StackBlockEntity stack
                && stack.getLevel() != null && stack.getLevel().isClientSide
                && !stack.isValidClientUpdate(input)) {
            callback.cancel();
        }
    }
}
