package relic.client.module.impl.visual;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import relic.client.api.render.BoxRenderer;
import relic.client.module.Module;
import relic.client.module.setting.ColorSetting;
import relic.client.module.setting.ModeSetting;
import relic.client.module.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;

public class TracersModule extends Module {

    private final ModeSetting target = new ModeSetting("Target", "Players", "Players", "Mobs", "Both");
    private final ColorSetting color = new ColorSetting("Color", 1.0f, 1.0f, 1.0f, 0.9f);
    private final NumberSetting width = new NumberSetting("Width", 1.5f, 0.5f, 5.0f);
    private final NumberSetting range = new NumberSetting("Range", 128, 16, 256, true);

    public TracersModule() {
        super("Tracers", "Draws lines to entities", Category.VISUAL);
        addSettings(target, color, width, range);
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        float tickDelta = mc.getRenderTickCounter().getTickProgress(false);

        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d start = camPos.add(mc.player.getRotationVec(tickDelta));

        float cr = color.red(), cg = color.green(), cb = color.blue(), ca = color.alpha();

        List<BoxRenderer.Line> lines = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity == mc.player) continue;
            if (mc.player.distanceTo(entity) > range.getValue()) continue;

            boolean isPlayer = entity instanceof PlayerEntity;
            if (isPlayer && target.is("Mobs")) continue;
            if (!isPlayer && target.is("Players")) continue;

            Vec3d lerped = entity.getLerpedPos(tickDelta);
            Vec3d to = lerped.add(0, entity.getHeight() / 2.0, 0);
            lines.add(new BoxRenderer.Line(start, to, cr, cg, cb, ca));
        }

        BoxRenderer.drawLines(lines, width.getValue());
    }
}
