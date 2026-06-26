package relic.client.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import relic.client.gui.ImGuiManager;
import relic.client.gui.ImGuiOverlay;
import relic.client.gui.screen.ImGuiScreen;
import relic.client.module.impl.visual.ZoomModule;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "render", at = @At("RETURN"))
    private void relic$renderImGui(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        ImGuiManager imGui = ImGuiManager.getInstance();

        if (client.currentScreen instanceof ImGuiScreen screen) {

            if (imGui.newFrame()) {
                try {
                    screen.renderImGui();
                } finally {
                    imGui.render();
                }
            }
        } else if (ImGuiOverlay.anyActive()) {

            if (imGui.newFrame()) {
                try {
                    ImGuiOverlay.render();
                } finally {
                    imGui.render();
                }
            }
        }
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void relic$applyZoom(CallbackInfoReturnable<Float> cir) {
        ZoomModule zoom = ZoomModule.getInstance();
        if (zoom != null && zoom.isZooming()) {
            float divisor = zoom.getDivisor();
            if (divisor > 1.0f) {
                cir.setReturnValue(cir.getReturnValueF() / divisor);
            }
        }
    }
}
