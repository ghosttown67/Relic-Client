package relic.client.client.mixin;

import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import relic.client.module.impl.visual.XrayModule;

@Mixin(ChunkOcclusionDataBuilder.class)
public class XrayChunkOcclusionMixin {

    @Inject(method = "markClosed", at = @At("HEAD"), cancellable = true)
    private void relic$noOcclusion(BlockPos pos, CallbackInfo ci) {
        if (XrayModule.isActive()) {
            ci.cancel();
        }
    }
}
