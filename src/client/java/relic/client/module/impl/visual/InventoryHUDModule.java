package relic.client.module.impl.visual;

import imgui.ImDrawList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import relic.client.gui.hud.HudElement;
import relic.client.gui.hud.HudProvider;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.NumberSetting;

import java.util.List;

public class InventoryHUDModule extends Module implements HudProvider {

    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int SLOT = 18;

    private final BooleanSetting background = new BooleanSetting("Background", true);

    private final NumberSetting posX = new NumberSetting("X %", 2, 0, 100, false);
    private final NumberSetting posY = new NumberSetting("Y %", 75, 0, 100, false);

    public InventoryHUDModule() {
        super("InventoryHUD", "Shows your inventory on screen", Category.VISUAL);
        addSettings(background, posX, posY);
    }

    private HudElement element;

    @Override
    public List<HudElement> hudElements() {
        if (element == null) {
            element = new HudElement() {
                @Override public String name() { return "Inventory HUD"; }
                @Override public float getXPercent() { return posX.getValue(); }
                @Override public float getYPercent() { return posY.getValue(); }
                @Override public void setPercent(float x, float y) { posX.setValue(x); posY.setValue(y); }
                @Override public float width(float dispW, float dispH, float scale) { return COLS * SLOT * scale; }
                @Override public float height(float dispW, float dispH, float scale) { return ROWS * SLOT * scale; }
                @Override public void renderPreview(ImDrawList dl, float x, float y, float dispW, float dispH, float scale) {
                    float slot = SLOT * scale;
                    if (background.isOn()) {
                        dl.addRectFilled(x - 2, y - 2, x + COLS * slot + 2, y + ROWS * slot + 2, col(0xB0101019));
                    }
                    for (int row = 0; row < ROWS; row++) {
                        for (int c = 0; c < COLS; c++) {
                            float sx = x + c * slot, sy = y + row * slot;
                            dl.addRectFilled(sx, sy, sx + slot - scale, sy + slot - scale, col(0x40FFFFFF));
                        }
                    }
                }
            };
        }
        return List.of(element);
    }

    private static int col(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        PlayerInventory inv = mc.player.getInventory();

        int gridW = COLS * SLOT;
        int gridH = ROWS * SLOT;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        int baseX = Math.round((sw - gridW) * posX.getValue() / 100f);
        int baseY = Math.round((sh - gridH) * posY.getValue() / 100f);

        if (background.isOn()) {
            context.fill(baseX - 2, baseY - 2, baseX + gridW + 2, baseY + gridH + 2, 0xB0101019);
        }

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = 9 + row * COLS + col;
                int x = baseX + col * SLOT;
                int y = baseY + row * SLOT;

                context.fill(x, y, x + SLOT - 1, y + SLOT - 1, 0x40FFFFFF);

                ItemStack stack = inv.getStack(index);
                if (!stack.isEmpty()) {
                    context.drawItem(stack, x + 1, y + 1);
                    context.drawStackOverlay(mc.textRenderer, stack, x + 1, y + 1);
                }
            }
        }
    }
}
