package relic.client.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import relic.client.gui.theme.ThemeManager;

@Mixin(Screen.class)
public class TitleScreenCreditsMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void relic$drawCredits(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!((Object) this instanceof TitleScreen)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int height = mc.getWindow().getScaledHeight();

        int accent = 0xFF000000 | (ThemeManager.get().accent() & 0xFFFFFF);

        context.drawTextWithShadow(tr, "Relic Client", 4, height - 30, accent);
        context.drawTextWithShadow(tr, "by Critical", 4, height - 19, 0xFFBBBBBB);
    }
}
