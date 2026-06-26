package relic.client.client.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BlockStateModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import relic.client.module.impl.privacy.BlockObfuscatorModule;

@Mixin(BlockRenderManager.class)
public abstract class BlockObfuscatorModelMixin {

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void relic$obfuscate(BlockState state, CallbackInfoReturnable<BlockStateModel> cir) {
        BlockState replacement = BlockObfuscatorModule.getReplacement(state);
        if (replacement != null) {
            cir.setReturnValue(((BlockRenderManager) (Object) this).getModel(replacement));
        }
    }
}
