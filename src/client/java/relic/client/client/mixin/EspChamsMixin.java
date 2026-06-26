package relic.client.client.mixin;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import relic.client.module.impl.visual.ESPModule;

@Mixin(EntityRenderer.class)
public abstract class EspChamsMixin {

    @Inject(method = "getAndUpdateRenderState(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/client/render/entity/state/EntityRenderState;",
            at = @At("RETURN"))
    private void relic$chams(Entity entity, float tickDelta, CallbackInfoReturnable<EntityRenderState> cir) {
        int color = ESPModule.chamsColor(entity);
        if (color != EntityRenderState.NO_OUTLINE) {
            cir.getReturnValue().outlineColor = color;
        }
    }
}
