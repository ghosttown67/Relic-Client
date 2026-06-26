package relic.client.client.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import relic.client.module.impl.visual.XrayModule;

import java.util.List;

@Mixin(BlockModelRenderer.class)
public abstract class XrayBlockModelRendererMixin {

    @Unique
    private final ThreadLocal<Integer> relic$alpha = new ThreadLocal<>();

    @Inject(method = {"renderSmooth", "renderFlat"}, at = @At("HEAD"), cancellable = true)
    private void relic$captureAlpha(BlockRenderView world, List<BlockModelPart> parts, BlockState state,
                                    BlockPos pos, MatrixStack matrices, VertexConsumer vertexConsumer,
                                    boolean cull, int overlay, CallbackInfo ci) {
        int alpha = XrayModule.getAlpha(state);
        if (alpha == 0) ci.cancel();
        else relic$alpha.set(alpha);
    }

    @ModifyArgs(method = "renderQuad", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;[FFFFF[II)V"))
    private void relic$applyAlpha(Args args) {
        Integer alpha = relic$alpha.get();
        if (alpha != null && alpha != -1) {
            args.set(6, alpha / 255f);
        }
    }
}
