package relic.client.module.impl.basehunting;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import relic.client.api.render.BoxRenderer;
import relic.client.api.render.WorldToScreen;
import relic.client.module.Module;
import relic.client.module.setting.BlockListSetting;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.ColorSetting;
import relic.client.module.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;

public class EndermanBlockESPModule extends Module {

    private static final float ER = 0.67f, EG = 0.20f, EB = 0.78f;

    private final BlockListSetting blocks = new BlockListSetting("Blocks");
    private final ColorSetting   color    = new ColorSetting("Color", ER, EG, EB, 1.0f);
    private final BooleanSetting filled   = new BooleanSetting("Filled", true);
    private final BooleanSetting outline  = new BooleanSetting("Outline", true);
    private final BooleanSetting labels   = new BooleanSetting("Labels", true);
    private final NumberSetting  fillOpacity = new NumberSetting("Fill Opacity", 30, 0, 100, true);
    private final NumberSetting  lineWidth   = new NumberSetting("Line Width", 2, 1, 6);
    private final NumberSetting  range       = new NumberSetting("Range", 128, 16, 256, true);

    public EndermanBlockESPModule() {
        super("EndermanBlockESP", "Highlights endermen carrying (selected) blocks", Category.BASE_HUNTING);
        addSettings(blocks, color, filled, outline, labels, fillOpacity, lineWidth, range);
    }

    private BlockState carriedTarget(Entity entity) {
        if (!(entity instanceof EndermanEntity enderman)) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.distanceTo(entity) > range.getValue()) return null;

        BlockState carried = enderman.getCarriedBlock();
        if (carried == null || carried.isAir()) return null;

        if (blocks.getValue().isEmpty()) return carried;
        String id = Registries.BLOCK.getId(carried.getBlock()).getPath();
        return blocks.isSelected(id) ? carried : null;
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        if (!filled.isOn() && !outline.isOn()) return;

        float tickDelta = mc.getRenderTickCounter().getTickProgress(false);
        float cr = color.red(), cg = color.green(), cb = color.blue();

        List<BoxRenderer.ColoredBox> boxes = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (carriedTarget(entity) == null) continue;

            Vec3d delta = entity.getLerpedPos(tickDelta).subtract(entity.getEntityPos());
            Box box = entity.getBoundingBox().offset(delta);
            boxes.add(new BoxRenderer.ColoredBox(box, cr, cg, cb, 1.0f));
        }
        if (boxes.isEmpty()) return;

        BoxRenderer.Mode mode = filled.isOn() && outline.isOn() ? BoxRenderer.Mode.BOTH
                : filled.isOn() ? BoxRenderer.Mode.FILLED
                : BoxRenderer.Mode.OUTLINED;
        BoxRenderer.draw(boxes, mode, lineWidth.getValue(), fillOpacity.getValue() / 100f);
    }

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        if (!labels.isOn()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        TextRenderer tr = mc.textRenderer;
        float scale = 0.75f;
        int textColor = 0xFF000000 | ((int) (ER * 255) << 16) | ((int) (EG * 255) << 8) | (int) (EB * 255);
        float interp = mc.getRenderTickCounter().getTickProgress(false);

        for (Entity entity : mc.world.getEntities()) {
            BlockState carried = carriedTarget(entity);
            if (carried == null) continue;

            Vec3d pos = entity.getLerpedPos(interp);
            float[] screen = WorldToScreen.project(new Vec3d(pos.x, pos.y + entity.getHeight() + 0.5, pos.z));
            if (screen == null) continue;

            String text = carried.getBlock().getName().getString();
            var matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.scale(scale, scale);
            float cx = screen[0] / scale;
            float y = screen[1] / scale;
            int w = tr.getWidth(text);
            int x = (int) (cx - w / 2.0f);
            context.fill(x - 2, (int) y - 1, x + w + 2, (int) y + tr.fontHeight, 0x90000000);
            context.drawText(tr, text, x, (int) y, textColor, true);
            matrices.popMatrix();
        }
    }
}
