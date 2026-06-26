package relic.client.client.mixin;

import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import relic.client.module.impl.privacy.CoordObfuscatorModule;

@Mixin(DrawContext.class)
public abstract class CoordObfuscatorDrawContextMixin {

    @ModifyVariable(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)V",
            at = @At("HEAD"), argsOnly = true)
    private String relic$redactCoords(String text) {
        return CoordObfuscatorModule.redactString(text);
    }
}
