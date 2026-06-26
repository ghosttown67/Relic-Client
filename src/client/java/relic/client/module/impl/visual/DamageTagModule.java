package relic.client.module.impl.visual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import relic.client.api.render.WorldToScreen;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.NumberSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DamageTagModule extends Module {

    private static DamageTagModule instance;

    private static final long LIFETIME = 1200L;

    private static final int MAX_POPUPS = 40;

    private final NumberSetting  scale    = new NumberSetting("Scale", 1.0f, 0.3f, 2.5f);
    private final BooleanSetting shadow   = new BooleanSetting("Shadow", true);
    private final NumberSetting  lifetime = new NumberSetting("Lifetime", 1200, 300, 3000, true);
    private final NumberSetting  range    = new NumberSetting("Range", 64, 8, 128, true);

    private final Map<Integer, Pending> pending = new HashMap<>();
    private final List<Popup> popups = new ArrayList<>();

    public DamageTagModule() {
        super("DamageTag", "Shows floating damage numbers when you hit", Category.VISUAL);
        addSettings(scale, shadow, lifetime, range);
        instance = this;
    }

    public static DamageTagModule getInstance() {
        return instance;
    }

    public void onAttack(Entity target) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (target instanceof LivingEntity le && le != mc.player) {

            pending.put(le.getId(),
                    new Pending(le.getHealth() + le.getAbsorptionAmount(),
                            System.currentTimeMillis() + 900));
        }
    }

    @Override
    protected void onDisable() {
        pending.clear();
        popups.clear();
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            pending.clear();
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Pending> en = it.next();
            Pending p = en.getValue();
            Entity ent = mc.world.getEntityById(en.getKey());

            if (!(ent instanceof LivingEntity le)) {
                if (now > p.expire) it.remove();
                continue;
            }

            float cur = le.getHealth() + le.getAbsorptionAmount();
            if (cur < p.health - 0.01f) {
                spawn(le, p.health - cur);
                it.remove();
            } else if (now > p.expire) {
                it.remove();
            }
        }
    }

    private void spawn(LivingEntity le, float dmg) {
        double x = le.getX() + (Math.random() - 0.5) * 0.4;
        double y = le.getY() + le.getHeight() * 0.85 + 0.3;
        double z = le.getZ() + (Math.random() - 0.5) * 0.4;
        popups.add(new Popup(dmg, x, y, z, System.currentTimeMillis()));
        while (popups.size() > MAX_POPUPS) popups.remove(0);
    }

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        if (popups.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) { popups.clear(); return; }

        long now = System.currentTimeMillis();
        long life = lifetime.getInt();
        TextRenderer tr = mc.textRenderer;
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        float rangeSq = range.getValue() * range.getValue();

        Iterator<Popup> it = popups.iterator();
        while (it.hasNext()) {
            Popup p = it.next();
            long age = now - p.birth;
            if (age > life) { it.remove(); continue; }

            float t = age / (float) life;
            double rise = t * 0.7;

            Vec3d base = new Vec3d(p.x, p.y, p.z);
            if (cam.squaredDistanceTo(base) > rangeSq) continue;

            float[] screen = WorldToScreen.project(new Vec3d(p.x, p.y + rise, p.z));
            if (screen == null) continue;

            double dist = cam.distanceTo(base);
            float es = (float) (2 * (4.0 / (4.0 + dist)));
            es *= 1f + 0.7f * (float) Math.exp(-age / 90.0);
            es = Math.max(0.12f, Math.min(es, 0.8f));
            es *= scale.getValue();

            float alpha = t < 0.65f ? 1f : 1f - (t - 0.65f) / 0.35f;
            int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255);

            String text = fmt(p.damage);
            int color = damageColor(p.damage, a);

            float cx = screen[0] / es;
            float cy = screen[1] / es;
            float w = tr.getWidth(text);
            float h = tr.fontHeight;

            var matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.scale(es, es);
            context.drawText(tr, text, Math.round(cx - w / 2f), Math.round(cy - h / 2f), color, shadow.isOn());
            matrices.popMatrix();
        }
    }

    private String fmt(float d) {
        if (d >= 10f) return String.valueOf(Math.round(d));
        double r = Math.round(d * 10.0) / 10.0;
        if (r == (long) r) return String.valueOf((long) r);
        return String.format("%.1f", r);
    }

    private int damageColor(float dmg, int alpha) {
        float frac = Math.max(0f, Math.min(1f, dmg / 12f));
        int r = 255;
        int g = (int) (230 - frac * 180);
        int b = (int) (120 - frac * 70);
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    private static final class Pending {
        final float health;
        final long expire;
        Pending(float health, long expire) { this.health = health; this.expire = expire; }
    }

    private static final class Popup {
        final float damage;
        final double x, y, z;
        final long birth;
        Popup(float damage, double x, double y, double z, long birth) {
            this.damage = damage; this.x = x; this.y = y; this.z = z; this.birth = birth;
        }
    }
}
