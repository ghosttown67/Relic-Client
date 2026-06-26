package relic.client.client.mixin;

import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractTerrainRenderContext;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import relic.client.module.impl.visual.XrayModule;

@Mixin(AbstractTerrainRenderContext.class)
public abstract class XrayTerrainRenderContextMixin {

    @Shadow
    @Final
    protected BlockRenderInfo blockInfo;

    @Inject(method = "bufferQuad(Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/MutableQuadViewImpl;)V",
            at = @At("HEAD"), cancellable = true)
    private void relic$xrayOpacity(MutableQuadViewImpl quad, CallbackInfo ci) {
        int alpha = XrayModule.getAlpha(blockInfo.blockState);
        if (alpha == -1) return;
        if (alpha == 0) {
            ci.cancel();
            return;
        }
        for (int i = 0; i < 4; i++) {
            quad.color(i, (quad.color(i) & 0x00FFFFFF) | (alpha << 24));
        }
    }
}
