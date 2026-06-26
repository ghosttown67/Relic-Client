package relic.client.module.impl.visual;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import relic.client.gui.ImGuiManager;
import relic.client.gui.hud.HudElement;
import relic.client.gui.hud.HudProvider;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.ColorSetting;
import relic.client.module.setting.NumberSetting;

import java.util.List;

public class RegionMapModule extends Module implements HudProvider {

    private static final int MAP_SIZE = 9;
    private static final double REGION_SIZE = 50000.0;
    private static final double MAP_OFFSET = 225000.0;

    private static final int[][] REGION_IDS = {
            {82, 100, 101, 102, 103, 104, 105, 106, 91},
            {83,  44,  75,  42,  41,  40,  39,  38, 92},
            {84,  45,  14,  13,  12,  11,  10,  37, 93},
            {85,  46,  74,   3,   2,   1,  25,  36, 94},
            {86,  47,  72,  71,   5,   4,  24,  35, 95},
            {87,  51,  17,   9,   8,   7,  23,  34, 96},
            {88,  54,  18,  61,  62,  21,  22,  33, 97},
            {89,  26,  27,  28,  29,  30,  59,  32, 98},
            {90, 107, 108, 109, 110, 111, 112, 113, 99},
    };
    private static final int[][] REGION_TYPES = {
            {5, 3, 3, 3, 2, 2, 2, 2, 2},
            {5, 3, 3, 3, 2, 2, 2, 2, 2},
            {5, 3, 3, 3, 2, 2, 2, 2, 2},
            {5, 5, 5, 3, 2, 2, 2, 2, 2},
            {4, 4, 4, 4, 2, 2, 2, 2, 2},
            {4, 1, 1, 0, 0, 0, 0, 0, 2},
            {4, 1, 1, 0, 0, 0, 0, 0, 0},
            {0, 1, 0, 0, 0, 0, 0, 0, 0},
            {0, 1, 1, 1, 1, 1, 1, 1, 0},
    };

    private static final int[] TYPE_RGB = {
            0x5BA37A,
            0x3F7D5C,
            0x5B86C4,
            0x3C5A8A,
            0xC9A24B,
            0xC56B3A,
    };

    private final NumberSetting posX     = new NumberSetting("X %", 1, 0, 100, false);
    private final NumberSetting posY     = new NumberSetting("Y %", 1, 0, 100, false);
    private final NumberSetting cellSize = new NumberSetting("Cell Size", 14, 8, 50, true);

    private final BooleanSetting showGrid    = new BooleanSetting("Grid", true);
    private final BooleanSetting showLabels  = new BooleanSetting("Region Labels", true);
    private final BooleanSetting showCoords  = new BooleanSetting("Coordinates", true);
    private final BooleanSetting showPlayer  = new BooleanSetting("Player Indicator", true);

    private final NumberSetting transparency = new NumberSetting("Transparency", 0.75f, 0.1f, 1.0f, false);
    private final NumberSetting labelSize    = new NumberSetting("Label Size", 0.9f, 0.4f, 2.5f, false);

    private final ColorSetting background  = new ColorSetting("Background", 0xB4191919);
    private final ColorSetting playerColor = new ColorSetting("Player Color", 0xFFFF3232);
    private final ColorSetting gridColor   = new ColorSetting("Grid Color", 0xFF0F0F0F);

    public RegionMapModule() {
        super("RegionMap", "DonutSMP region map with live player tracking", Category.VISUAL);
        addSettings(posX, posY, cellSize, showGrid, showLabels, showCoords, showPlayer,
                transparency, labelSize, background, playerColor, gridColor);
    }

    @Override
    public boolean wantsImGuiOverlay() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && !mc.options.hudHidden;
    }

    @Override
    public void onImGuiRender(ImDrawList dl) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        ImFont font = font();
        float s = (float) mc.getWindow().getScaleFactor();
        float sw = ImGui.getIO().getDisplaySizeX();
        float sh = ImGui.getIO().getDisplaySizeY();
        if (sw <= 0 || sh <= 0) return;

        float[] b = mapSize(font, s);
        drawMap(dl, font, s, anchor(sw, b[0], posX), anchor(sh, b[1], posY));
    }

    private HudElement element;

    @Override
    public List<HudElement> hudElements() {
        if (element == null) {
            element = new HudElement() {
                @Override public String name() { return "Region Map"; }
                @Override public float getXPercent() { return posX.getValue(); }
                @Override public float getYPercent() { return posY.getValue(); }
                @Override public void setPercent(float x, float y) { posX.setValue(x); posY.setValue(y); }
                @Override public float width(float dispW, float dispH, float scale) { return mapSize(font(), scale)[0]; }
                @Override public float height(float dispW, float dispH, float scale) { return mapSize(font(), scale)[1]; }
                @Override public void renderPreview(ImDrawList dl, float x, float y, float dispW, float dispH, float scale) {
                    drawMap(dl, font(), scale, x, y);
                }
            };
        }
        return List.of(element);
    }

    private static float labelFontSize(float s, float labelScale) { return 7f * s * labelScale; }
    private static float coordsFontSize(float s) { return 8f * s; }
    private static float textHeight(ImFont font, float size) { return font.calcTextSizeAY(size, Float.MAX_VALUE, 0f, "Ay"); }

    private float[] mapSize(ImFont font, float s) {
        float cell = cellSize.getInt() * s;
        float mapPx = MAP_SIZE * cell;
        float h = mapPx;
        if (showCoords.isOn()) h += textHeight(font, coordsFontSize(s)) + 3 * s;
        return new float[]{mapPx, h};
    }

    private void drawMap(ImDrawList dl, ImFont font, float s, float x, float y) {
        float cell = cellSize.getInt() * s;
        float mapPx = MAP_SIZE * cell;
        float pad = 2 * s;

        dl.addRectFilled(x - pad, y - pad, x + mapPx + pad, y + mapPx + pad, col(background.getValue()));

        int cellAlpha = Math.round(255 * transparency.getValue());
        for (int gz = 0; gz < MAP_SIZE; gz++) {
            for (int gx = 0; gx < MAP_SIZE; gx++) {
                float cx = x + gx * cell;
                float cy = y + gz * cell;
                int argb = (cellAlpha << 24) | TYPE_RGB[REGION_TYPES[gz][gx]];
                dl.addRectFilled(cx, cy, cx + cell, cy + cell, col(argb));
            }
        }

        if (showGrid.isOn()) {
            int g = col(gridColor.getValue());
            for (int i = 0; i <= MAP_SIZE; i++) {
                float gp = i * cell;
                dl.addLine(x + gp, y, x + gp, y + mapPx, g, s);
                dl.addLine(x, y + gp, x + mapPx, y + gp, g, s);
            }
        }

        if (showLabels.isOn()) {
            float fs = labelFontSize(s, labelSize.getValue());
            int sz = Math.max(1, Math.round(fs));
            float fh = textHeight(font, fs);
            for (int gz = 0; gz < MAP_SIZE; gz++) {
                for (int gx = 0; gx < MAP_SIZE; gx++) {
                    String id = Integer.toString(REGION_IDS[gz][gx]);
                    float tw = font.calcTextSizeAX(fs, Float.MAX_VALUE, 0f, id);
                    float tx = x + gx * cell + (cell - tw) / 2f;
                    float ty = y + gz * cell + (cell - fh) / 2f;
                    text(dl, font, sz, tx, ty, 0xFFFFFFFF, id);
                }
            }
        }

        if (showPlayer.isOn()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                Vec3d p = mc.player.getEntityPos();
                boolean nether = mc.world != null && mc.world.getRegistryKey() == World.NETHER;

                double wx = nether ? p.x * 8 : p.x;
                double wz = nether ? p.z * 8 : p.z;
                double gxf = (wx + MAP_OFFSET) / REGION_SIZE;
                double gzf = (wz + MAP_OFFSET) / REGION_SIZE;
                if (gxf >= 0 && gxf <= MAP_SIZE && gzf >= 0 && gzf <= MAP_SIZE) {
                    int pc = col(playerColor.getValue());

                    float px = x + (float) gxf * cell;
                    float py = y + (float) gzf * cell;
                    double yaw = Math.toRadians(mc.player.getYaw());
                    double dirX = -Math.sin(yaw), dirY = Math.cos(yaw);
                    double perpX = -dirY, perpY = dirX;
                    float r = Math.max(4 * s, cell * 0.45f);
                    float bx = px - (float) dirX * r * 0.6f;
                    float by = py - (float) dirY * r * 0.6f;
                    float half = r * 0.5f;
                    dl.addTriangleFilled(
                            px + (float) dirX * r, py + (float) dirY * r,
                            bx + (float) perpX * half, by + (float) perpY * half,
                            bx - (float) perpX * half, by - (float) perpY * half,
                            pc);
                }
            }
        }

        if (showCoords.isOn()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                Vec3d p = mc.player.getEntityPos();
                boolean nether = mc.world != null && mc.world.getRegistryKey() == World.NETHER;
                double owX = nether ? p.x * 8 : p.x;
                double owZ = nether ? p.z * 8 : p.z;
                String line = String.format("X %.0f  Z %.0f", owX, owZ) + (nether ? "  (OW)" : "");
                float fs = coordsFontSize(s);
                text(dl, font, Math.round(fs), x, y + mapPx + 3 * s, 0xFFFFFFFF, line);
            }
        }
    }

    private static ImFont font() {
        ImFont f = ImGuiManager.getOverlayFont();
        return f != null ? f : ImGui.getFont();
    }

    private static float anchor(float screen, float content, NumberSetting pct) {
        return (screen - content) * pct.getValue() / 100f;
    }

    private static void text(ImDrawList dl, ImFont font, int size, float x, float y, int col, String s) {
        if (s.isEmpty()) return;
        float fx = (float) Math.floor(x), fy = (float) Math.floor(y);
        dl.addText(font, size, fx + 1, fy + 1, 0xC0000000, s);
        dl.addText(font, size, fx, fy, col, s);
    }

    private static int col(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
