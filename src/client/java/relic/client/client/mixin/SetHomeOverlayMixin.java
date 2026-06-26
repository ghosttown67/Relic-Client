package relic.client.client.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import relic.client.module.impl.misc.SetHomeModule;

@Mixin(InGameHud.class)
public abstract class SetHomeOverlayMixin {

    @Inject(method = "setOverlayMessage(Lnet/minecraft/text/Text;Z)V", at = @At("HEAD"), cancellable = true)
    private void relic$hideHomeSetOverlay(Text message, boolean tinted, CallbackInfo ci) {
        if (SetHomeModule.shouldHide(message)) {
            ci.cancel();
        }
    }
}
