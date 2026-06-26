package relic.client.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.DefaultSkinHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import relic.client.module.impl.privacy.PrivacyModule;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class PrivacyPlayerSkinMixin {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void relic$hideSkin(CallbackInfoReturnable cir) {
        if (!PrivacyModule.shouldHideSkin()) return;
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        if (self != MinecraftClient.getInstance().player) return;
        cir.setReturnValue(DefaultSkinHelper.getSkinTextures(self.getUuid()));
    }
}
