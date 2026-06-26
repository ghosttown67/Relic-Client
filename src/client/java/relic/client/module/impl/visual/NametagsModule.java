package relic.client.module.impl.visual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import relic.client.api.render.WorldToScreen;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;

public class NametagsModule extends Module {

    private static final EquipmentSlot[] EQUIP_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };
    private static final int ITEM_SLOT = 18;

    private static final double REF_DIST = 8.0;
    private static final float  MIN_MUL  = 0.35f;

    private final BooleanSetting players      = new BooleanSetting("Players", true);
    private final BooleanSetting items        = new BooleanSetting("Items", true);
    private final BooleanSetting health        = new BooleanSetting("Health", true);
    private final BooleanSetting ping          = new BooleanSetting("Ping", true);
    private final BooleanSetting equipment      = new BooleanSetting("Equipment", true);
    private final BooleanSetting shadow         = new BooleanSetting("Shadow", true);
    private final NumberSetting  scale         = new NumberSetting("Scale", 0.6f, 0.3f, 1.5f);
    private final BooleanSetting distanceScale = new BooleanSetting("Distance Scale", true);
    private final NumberSetting  opacity       = new NumberSetting("BG Opacity", 70, 0, 100, true);
    private final NumberSetting  range         = new NumberSetting("Range", 96, 16, 256, true);

    public NametagsModule() {
        super("Nametags", "Shows player name, health, ping, gear and item tags", Category.VISUAL);
        addSettings(players, items, health, ping, equipment, shadow, scale, distanceScale, opacity, range);
    }

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        TextRenderer tr = mc.textRenderer;
        float rangeSq = range.getValue() * range.getValue();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;

            boolean isPlayer = entity instanceof PlayerEntity;
            boolean isItem = entity instanceof ItemEntity;
            if (isPlayer && !players.isOn()) continue;
            if (isItem && !items.isOn()) continue;
            if (!isPlayer && !isItem) continue;

            Vec3d lerped = entity.getLerpedPos(tickDelta);
            if (camPos.squaredDistanceTo(lerped) > rangeSq) continue;

            Vec3d above = lerped.add(0, entity.getHeight() + 0.5, 0);
            float[] screen = WorldToScreen.project(above);
            if (screen == null) continue;

            float es = effectiveScale(camPos.distanceTo(lerped));
            float cx = screen[0] / es;
            float cy = screen[1] / es;

            var matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.scale(es, es);

            if (isPlayer) {
                drawPlayerTag(context, tr, (PlayerEntity) entity, cx, cy);
            } else {
                drawItemTag(context, tr, ((ItemEntity) entity).getStack(), cx, cy);
            }

            matrices.popMatrix();
        }
    }

    private void drawPlayerTag(DrawContext context, TextRenderer tr, PlayerEntity player, float cx, float cy) {

        List<Segment> line = new ArrayList<>();
        line.add(new Segment(player.getName().getString(), 0xFFFFFFFF));

        if (health.isOn()) {
            int hp = (int) Math.ceil(player.getHealth() + player.getAbsorptionAmount());
            line.add(new Segment("  " + hp + " HP", healthColor(player)));
        }
        if (ping.isOn()) {
            int ms = pingOf(player);
            if (ms >= 0) line.add(new Segment("  " + ms + " ms", pingColor(ms)));
        }

        int textW = 0;
        for (Segment seg : line) textW += tr.getWidth(seg.text);
        int textH = tr.fontHeight;

        List<ItemStack> gear = equipment.isOn() ? collectGear(player) : List.of();
        int gearW = gear.isEmpty() ? 0 : gear.size() * ITEM_SLOT - 2;
        int gearH = gear.isEmpty() ? 0 : 16 + 2;

        int boxW = Math.max(textW, gearW) + 4;
        float bgLeft = cx - boxW / 2f;
        float textTop = cy - textH / 2f;

        drawBackground(context, bgLeft, textTop - gearH - 1, bgLeft + boxW, textTop + textH + 1);

        if (!gear.isEmpty()) {
            int gearLeft = Math.round(cx - gearW / 2f);
            int gearTop = Math.round(textTop) - gearH;
            for (int i = 0; i < gear.size(); i++) {
                ItemStack stack = gear.get(i);
                int ix = gearLeft + i * ITEM_SLOT;
                context.drawItem(stack, ix, gearTop);
                context.drawStackOverlay(tr, stack, ix, gearTop);
            }
        }

        drawSegments(context, tr, line, cx - textW / 2f, textTop);
    }

    private void drawItemTag(DrawContext context, TextRenderer tr, ItemStack stack, float cx, float cy) {
        if (stack.isEmpty()) return;
        String text = stack.getCount() + "x " + stack.getName().getString();
        int w = tr.getWidth(text);
        float left = cx - w / 2f;
        float top = cy - tr.fontHeight / 2f;
        drawBackground(context, left - 2, top - 1, left + w + 2, top + tr.fontHeight + 1);
        context.drawText(tr, text, Math.round(left), Math.round(top), 0xFFFFFFFF, shadow.isOn());
    }

    private float effectiveScale(double dist) {
        float s = scale.getValue();
        if (distanceScale.isOn()) {
            s *= (float) MathHelper.clamp(REF_DIST / Math.max(dist, 0.01), MIN_MUL, 1.0);
        }
        return s;
    }

    private void drawBackground(DrawContext context, float x1, float y1, float x2, float y2) {
        int alpha = Math.round(opacity.getValue() / 100f * 255f);
        if (alpha <= 0) return;
        context.fill(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2), alpha << 24);
    }

    private void drawSegments(DrawContext context, TextRenderer tr, List<Segment> segments, float x, float y) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        for (Segment seg : segments) {
            context.drawText(tr, seg.text, ix, iy, seg.color, shadow.isOn());
            ix += tr.getWidth(seg.text);
        }
    }

    private List<ItemStack> collectGear(PlayerEntity player) {
        List<ItemStack> gear = new ArrayList<>();
        for (EquipmentSlot slot : EQUIP_SLOTS) {
            ItemStack stack = player.getEquippedStack(slot);
            if (!stack.isEmpty()) gear.add(stack);
        }
        return gear;
    }

    private int pingOf(PlayerEntity player) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return -1;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
        return entry == null ? -1 : entry.getLatency();
    }

    private int healthColor(PlayerEntity player) {
        float max = Math.max(1f, player.getMaxHealth());
        float ratio = MathHelper.clamp((player.getHealth() + player.getAbsorptionAmount()) / max, 0f, 1f);
        return hsvToArgb(ratio * 120f / 360f, 0.85f, 1.0f);
    }

    private int pingColor(int ms) {
        if (ms < 100) return 0xFF55FF55;
        if (ms < 300) return 0xFFFFFF55;
        return 0xFFFF5555;
    }

    private int hsvToArgb(float h, float s, float v) {
        int i = (int) (h * 6f) % 6;
        float f = h * 6f - (float) Math.floor(h * 6f);
        float p = v * (1f - s);
        float q = v * (1f - f * s);
        float t = v * (1f - (1f - f) * s);
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000
                | (Math.round(r * 255f) << 16)
                | (Math.round(g * 255f) << 8)
                | Math.round(b * 255f);
    }

    private record Segment(String text, int color) {}
}
