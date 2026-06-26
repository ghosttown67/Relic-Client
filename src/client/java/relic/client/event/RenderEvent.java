package relic.client.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public class RenderEvent {
    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {

        });
    }
}
