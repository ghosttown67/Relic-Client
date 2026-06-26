package relic.client.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import relic.client.module.impl.visual.GhostModeModule;

@Mixin(MinecraftClient.class)
public class GhostModeScreenMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void relic$suppressDeathScreen(Screen screen, CallbackInfo ci) {
        if (screen instanceof DeathScreen) {
            GhostModeModule ghost = GhostModeModule.getInstance();
            if (ghost != null && ghost.isEnabled()) {
                ghost.onDeath();
                ci.cancel();
            }
        }
    }
}
