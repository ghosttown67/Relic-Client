package relic.client.client.mixin;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.bar.Bar;
import net.minecraft.client.gui.hud.bar.ExperienceBar;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import relic.client.module.impl.visual.PaperDollModule;

@Mixin(InGameHud.class)
public abstract class PaperDollHudMixin {

    @Inject(method = "renderHealthBar", at = @At("HEAD"), cancellable = true)
    private void relic$hideHearts(CallbackInfo ci) {
        if (PaperDollModule.hideVanillaHearts()) ci.cancel();
    }

    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
    private void relic$hideFood(CallbackInfo ci) {
        if (PaperDollModule.hideVanillaHunger()) ci.cancel();
    }

    @Redirect(method = "renderMainHud",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/bar/Bar;renderBar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"))
    private void relic$hideXpBar(Bar bar, DrawContext context, RenderTickCounter tickCounter) {
        if (PaperDollModule.hideVanillaXp() && bar instanceof ExperienceBar) return;
        bar.renderBar(context, tickCounter);
    }

    @Redirect(method = "renderMainHud",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/bar/Bar;drawExperienceLevel(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;I)V"))
    private void relic$hideXpLevel(DrawContext context, TextRenderer textRenderer, int level) {
        if (PaperDollModule.hideVanillaXp()) return;
        Bar.drawExperienceLevel(context, textRenderer, level);
    }
}
