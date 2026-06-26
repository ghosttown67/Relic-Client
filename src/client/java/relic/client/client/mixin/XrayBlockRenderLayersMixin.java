package relic.client.client.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.BlockRenderLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import relic.client.module.impl.visual.XrayModule;

@Mixin(BlockRenderLayers.class)
public class XrayBlockRenderLayersMixin {

    @Inject(method = "getBlockLayer", at = @At("HEAD"), cancellable = true)
    private static void relic$xrayLayer(BlockState state, CallbackInfoReturnable<BlockRenderLayer> cir) {
        int alpha = XrayModule.getAlpha(state);
        if (alpha > 0 && alpha < 255) {
            cir.setReturnValue(BlockRenderLayer.TRANSLUCENT);
        }
    }
}
