package relic.client.module.impl.visual;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import relic.client.api.render.BoxRenderer;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.ColorSetting;
import relic.client.module.setting.EntityListSetting;
import relic.client.module.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;

public class ESPModule extends Module {

    private static ESPModule instance;

    private final BooleanSetting players  = new BooleanSetting("Players", true);
    private final EntityListSetting mobs  = new EntityListSetting("Mobs", "zombie", "skeleton", "creeper", "spider");
    private final ColorSetting   color    = new ColorSetting("Color", 1.0f, 0.2f, 0.2f, 1.0f);
    private final BooleanSetting filled   = new BooleanSetting("Filled", true);
    private final BooleanSetting outline  = new BooleanSetting("Outline", true);
    private final BooleanSetting chams    = new BooleanSetting("Chams", false);
    private final BooleanSetting skeleton = new BooleanSetting("Skeleton", false);
    private final NumberSetting  fillOpacity = new NumberSetting("Fill Opacity", 30, 0, 100, true);
    private final NumberSetting  lineWidth   = new NumberSetting("Line Width", 2, 1, 6);
    private final NumberSetting  range       = new NumberSetting("Range", 128, 16, 256, true);

    public ESPModule() {
        super("ESP", "Highlights selected entities", Category.VISUAL);
        addSettings(players, mobs, color, filled, outline, chams, skeleton, fillOpacity, lineWidth, range);
        instance = this;
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        boolean wantBox = filled.isOn() || outline.isOn();
        if (!wantBox && !skeleton.isOn()) return;

        float tickDelta = mc.getRenderTickCounter().getTickProgress(false);
        float cr = color.red(), cg = color.green(), cb = color.blue();

        List<BoxRenderer.ColoredBox> boxes = new ArrayList<>();
        List<BoxRenderer.Line> bones = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            if (!isTarget(entity)) continue;

            Vec3d lerped = entity.getLerpedPos(tickDelta);
            Vec3d delta = lerped.subtract(entity.getEntityPos());

            if (wantBox) {
                Box box = entity.getBoundingBox().offset(delta);
                boxes.add(new BoxRenderer.ColoredBox(box, cr, cg, cb, 1.0f));
            }
            if (skeleton.isOn()) {
                addSkeleton(bones, entity, lerped, cr, cg, cb);
            }
        }

        if (wantBox && !boxes.isEmpty()) {
            BoxRenderer.Mode mode = filled.isOn() && outline.isOn() ? BoxRenderer.Mode.BOTH
                    : filled.isOn() ? BoxRenderer.Mode.FILLED
                    : BoxRenderer.Mode.OUTLINED;
            BoxRenderer.draw(boxes, mode, lineWidth.getValue(), fillOpacity.getValue() / 100f);
        }
        if (!bones.isEmpty()) {
            BoxRenderer.drawLines(bones, lineWidth.getValue());
        }
    }

    private boolean isTarget(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity == mc.player) return false;
        if (mc.player != null && mc.player.distanceTo(entity) > range.getValue()) return false;

        if (entity instanceof PlayerEntity) return players.isOn();
        String id = Registries.ENTITY_TYPE.getId(entity.getType()).getPath();
        return mobs.isSelected(id);
    }

    private void addSkeleton(List<BoxRenderer.Line> out, Entity entity, Vec3d feet, float r, float g, float b) {
        float h = entity.getHeight();
        float w = entity.getWidth();
        double yaw = Math.toRadians(((LivingEntity) entity).getBodyYaw());
        double rx = Math.cos(yaw), rz = Math.sin(yaw);
        double cx = feet.x, cz = feet.z;

        double footY  = feet.y;
        double hipY   = feet.y + h * 0.50;
        double chestY = feet.y + h * 0.72;
        double neckY  = feet.y + h * 0.82;
        double headY  = feet.y + h;
        double shoulder = w * 0.5;
        double hipHalf  = w * 0.22;

        Vec3d hip   = new Vec3d(cx, hipY, cz);
        Vec3d neck  = new Vec3d(cx, neckY, cz);
        Vec3d head  = new Vec3d(cx, headY, cz);
        Vec3d shL   = new Vec3d(cx - rx * shoulder, chestY, cz - rz * shoulder);
        Vec3d shR   = new Vec3d(cx + rx * shoulder, chestY, cz + rz * shoulder);
        Vec3d handL = new Vec3d(cx - rx * shoulder, hipY, cz - rz * shoulder);
        Vec3d handR = new Vec3d(cx + rx * shoulder, hipY, cz + rz * shoulder);
        Vec3d footL = new Vec3d(cx - rx * hipHalf, footY, cz - rz * hipHalf);
        Vec3d footR = new Vec3d(cx + rx * hipHalf, footY, cz + rz * hipHalf);

        bone(out, hip, neck, r, g, b);
        bone(out, neck, head, r, g, b);
        bone(out, neck, shL, r, g, b);
        bone(out, neck, shR, r, g, b);
        bone(out, shL, handL, r, g, b);
        bone(out, shR, handR, r, g, b);
        bone(out, hip, footL, r, g, b);
        bone(out, hip, footR, r, g, b);
    }

    private void bone(List<BoxRenderer.Line> out, Vec3d from, Vec3d to, float r, float g, float b) {
        out.add(new BoxRenderer.Line(from, to, r, g, b, 1.0f));
    }

    public static int chamsColor(Entity entity) {
        if (instance == null || !instance.isEnabled() || !instance.chams.isOn()) return 0;
        if (!instance.isTarget(entity)) return 0;
        return instance.color.opaque();
    }
}
