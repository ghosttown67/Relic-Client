package relic.client.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import relic.client.module.impl.privacy.PrivacyModule;

@Mixin(DrawContext.class)
public abstract class PrivacyDrawContextMixin {

    @ModifyVariable(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)V",
            at = @At("HEAD"), argsOnly = true)
    private String relic$spoofString(String text) {
        return PrivacyModule.spoofString(text);
    }

    @ModifyVariable(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V",
            at = @At("HEAD"), argsOnly = true)
    private Text relic$spoofText(Text text) {
        return PrivacyModule.spoofText(text);
    }

    @ModifyVariable(method = "drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V",
            at = @At("HEAD"), argsOnly = true)
    private Text relic$spoofCentered(Text text) {
        return PrivacyModule.spoofText(text);
    }
}
