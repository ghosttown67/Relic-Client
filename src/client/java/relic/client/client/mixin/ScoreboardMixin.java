package relic.client.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import relic.client.scoreboard.FakeScoreboardManager;

@Mixin(InGameHud.class)
public class ScoreboardMixin {

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"), cancellable = true)
    private void relic$suppressVanillaSidebar(DrawContext ctx, ScoreboardObjective objective, CallbackInfo ci) {
        if (FakeScoreboardManager.isActive() && !FakeScoreboardManager.isFakeObjective(objective)) {
            ci.cancel();
        }
    }
}
